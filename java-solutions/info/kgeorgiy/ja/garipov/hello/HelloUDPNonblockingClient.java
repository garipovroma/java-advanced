package info.kgeorgiy.ja.garipov.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;

import static info.kgeorgiy.ja.garipov.hello.UDPUtils.*;

public class HelloUDPNonblockingClient implements HelloClient {

    private int requests;
    private String prefix;

    @Override
    public void run(String host, int port, String prefix, int threads, int requests) {
        this.requests = requests;
        this.prefix = prefix;
        Selector selector = init(host, port, threads);
        if (selector == null) {
            return;
        }
        processClient(selector);
    }

    private DatagramChannel openClientChannel(Selector selector, SocketAddress address,
                                              int channelIndex) throws IOException {
        DatagramChannel channel = DatagramChannel.open();
        channel.configureBlocking(false);
        ClientChannelData data = new ClientChannelData(channel, channelIndex);
        channel.connect(address);
        channel.register(selector, SelectionKey.OP_WRITE, data);
        return channel;
    }

    private Selector init(String host, int port, int threads) {
        InetSocketAddress address;
        try {
            address = new InetSocketAddress(InetAddress.getByName(host), port);
        } catch (UnknownHostException e) {
            processException(e);
            return null;
        }
        Selector selector = null;
        try {
            selector = Selector.open();
        } catch (IOException e) {
            System.err.println("An error occurred : " + e.getMessage());
        }
        if (selector == null) {
            return null;
        }
        try {
            for (int i = 0; i < threads; i++) {
                openClientChannel(selector, address, i);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return selector;
    }

    private void processClient(Selector selector) {
        while(!selector.keys().isEmpty()) {
            try {
                int selected = selector.select(SELECT_TIMEOUT);
                if (selected == 0) {
                    selector.keys().stream().filter(SelectionKey::isWritable).forEach(this::send);
                    continue;
                }
                selector.select((SelectionKey key) -> {
                    send(key);
                    receive(key);
                });
            } catch (IOException e) {
                processException(e);
            }
        }
    }

    private void send(SelectionKey key) {
        if (!key.isWritable()) {
            return;
        }
        ClientChannelData attachment = (ClientChannelData) key.attachment();
        ByteBuffer buffer = attachment.getBuffer();
        int channelIndex = attachment.getChannelIndex();
        int requestIndex = attachment.getRequestIndex();
        DatagramChannel channel = (DatagramChannel) key.channel();
        try {
            SocketAddress address = channel.getRemoteAddress();
            String request = clientRequest(prefix, channelIndex, requestIndex);
            System.out.println("[request] " + request);
            bufferPut(buffer, request.getBytes(StandardCharsets.UTF_8));
            channel.send(buffer, address);
            buffer.flip();
            key.interestOps(SelectionKey.OP_READ);
        } catch (IOException e) {
            processException(e);
        }
    }

    private void bufferPut(ByteBuffer buffer, byte[] bytes) {
        buffer.clear();
        buffer.put(bytes);
        buffer.flip();
    }

    private void bufferReceive(ByteBuffer buffer, DatagramChannel channel) throws IOException {
        buffer.clear();
        channel.receive(buffer);
        buffer.flip();
    }

    private void receive(SelectionKey key) {
        if (!key.isReadable()) {
            return;
        }
        ClientChannelData attachment = (ClientChannelData) key.attachment();
        ByteBuffer buffer = attachment.getBuffer();
        int channelIndex = attachment.getChannelIndex();
        int requestIndex = attachment.getRequestIndex();
        DatagramChannel channel = (DatagramChannel) key.channel();

        try {
            bufferReceive(buffer, channel);
            String response = StandardCharsets.UTF_8.decode(buffer).toString();
            if (checkResponse(response, channelIndex, requestIndex)) {
                attachment.nextRequest();
            }
            System.out.println("[response] " + response);
        } catch (IOException e) {
            processException(e);
        }
        closeChannel(key, requests, attachment);
    }

    private void closeChannel(SelectionKey key, int requests, ClientChannelData attachment) {
        if (attachment.getRequestIndex() != requests) {
            key.interestOps(SelectionKey.OP_WRITE);
        } else {
            try {
                key.channel().close();
            } catch (IOException ignored) {
                //
            }
        }
    }

    public static void main(String[] args) {
        runClient(new HelloUDPNonblockingClient(), args);
    }
}
