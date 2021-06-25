package info.kgeorgiy.ja.garipov.hello;

import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static info.kgeorgiy.ja.garipov.hello.UDPUtils.*;

public class HelloUDPNonblockingServer implements HelloServer {

    private Selector selector;
    private DatagramChannel listenChannel;
    private ExecutorService responseThreads, mainPool;
    private Queue<ByteBuffer> buffers;
    private final Queue<ServerChannelData> datagramPacketQueue = new ConcurrentLinkedQueue<>();
    @Override
    public void start(int port, int threads) {
        if (!init(port, threads)) {
            return;
        }
        mainPool.submit(this::listen);
    }

    private DatagramChannel openServerChannel(Selector selector, int port) throws IOException {
        DatagramChannel channel = DatagramChannel.open();
        channel.configureBlocking(false);
        InetSocketAddress socketAddress = new InetSocketAddress(port);
        channel.bind(socketAddress);
        channel.register(selector, SelectionKey.OP_READ);
        return channel;
    }

    private boolean init(int port, int threads) {
        try {
            selector = Selector.open();
        } catch (IOException e) {
            System.err.println("An error occurred : " + e.getMessage());
        }
        if (selector == null) {
            return false;
        }
        try {
            listenChannel = openServerChannel(selector, port);
        } catch (IOException e) {
            processException(e);
            close();
            return false;
        }
        responseThreads = Executors.newFixedThreadPool(threads);
        mainPool = Executors.newSingleThreadExecutor();
        buffers = new ConcurrentLinkedQueue<>();
        int bufferSize;
        try {
            bufferSize = listenChannel.socket().getReceiveBufferSize();
        } catch (SocketException e) {
            processException(e);
            close();
            return false;
        }
        int finalBufferSize = bufferSize;
        IntStream.range(0, threads).forEach(i -> buffers.add(ByteBuffer.allocate(finalBufferSize)));
        return true;
    }

    private void listen() {
        while(!Thread.interrupted() && !selector.keys().isEmpty()) {
            try {
                selector.select((SelectionKey key) -> {
                    receive(key);
                    send(key);
                });
            } catch (IOException e) {
                processException(e);
                close();
                return;
            }
        }
    }

    private boolean checkKeyToSend(SelectionKey key) {
        if (!key.isWritable()) {
            return false;
        }
        if (datagramPacketQueue.isEmpty()) {
            key.interestOps(SelectionKey.OP_READ);
            return false;
        }
        return true;
    }

    private void send(SelectionKey key) {
        if (!checkKeyToSend(key)) {
            return;
        }
        ServerChannelData data = datagramPacketQueue.poll();
        SocketAddress address = data.getAddress();
        try {
            listenChannel.send(data.getBuffer(), address);
        } catch (IOException e) {
            processException(e);
            return;
        }
        key.interestOps(SelectionKey.OP_READ);
        key.interestOpsOr(SelectionKey.OP_WRITE);
    }

    private boolean checkKeyToReceive(SelectionKey key) {
        if (!key.isReadable()) {
            return false;
        }
        if (buffers.isEmpty()) {
            key.interestOps(SelectionKey.OP_WRITE);
            return false;
        }
        return true;
    }

    private void receive(SelectionKey key) {
        if (!checkKeyToReceive(key)) {
            return;
        }
        ByteBuffer buffer = buffers.poll();
        buffer.clear();
        SocketAddress address;
        try {
            address = listenChannel.receive(buffer);
        } catch (IOException e) {
            processException(e);
            return;
        }
        responseThreads.submit(() -> {
            buffer.flip();
            byte[] response = ("Hello, " + StandardCharsets.UTF_8.decode(buffer)).getBytes(StandardCharsets.UTF_8);
            datagramPacketQueue.add(new ServerChannelData(ByteBuffer.wrap(response), address));
            buffers.add(buffer);
            key.interestOpsOr(SelectionKey.OP_WRITE);
            selector.wakeup();
        });
    }

    @Override
    public void close() {
        closeResource(selector);
        closeResource(listenChannel);
        closeExecutorService(mainPool);
        closeExecutorService(responseThreads);
    }

    public static void main(String[] args) {
        runServer(HelloUDPNonblockingServer::new, args);
    }
}
