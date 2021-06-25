package info.kgeorgiy.ja.garipov.hello;

import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static info.kgeorgiy.ja.garipov.hello.UDPUtils.closeExecutorService;

public class HelloUDPServer implements HelloServer {
    private DatagramSocket socket;
    private ExecutorService service;

    @Override
    public void start(int port, int threads) {
        try {
            socket = new DatagramSocket(port);
        } catch (SocketException e) {
            System.err.println("An error occurred : " + e);
            return;
        }
        service = Executors.newFixedThreadPool(threads);
        IntStream.range(0, threads).forEach(i -> service.submit(() -> {
            while(!socket.isClosed()) {
                try {
                    byte[] buf = new byte[socket.getReceiveBufferSize()];
                    DatagramPacket receivedPacket = new DatagramPacket(buf, buf.length);
                    socket.receive(receivedPacket);
                    String request = new String(receivedPacket.getData(), receivedPacket.getOffset(),
                            receivedPacket.getLength(), StandardCharsets.UTF_8);
                    byte[] response = ("Hello, " + request).getBytes(StandardCharsets.UTF_8);
                    DatagramPacket sendPacket = new DatagramPacket(response, response.length,
                            receivedPacket.getSocketAddress());
                    socket.send(sendPacket);
                } catch (IOException e) {
                    System.err.println("An error occurred : " + e);
                }
            }
        }));
    }

    @Override
    public void close() {
        socket.close();
        closeExecutorService(service);
    }

    public static void main(String[] args) {
        UDPUtils.runServer(HelloUDPServer::new, args);
    }
}
