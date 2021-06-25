package info.kgeorgiy.ja.garipov.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static info.kgeorgiy.ja.garipov.hello.UDPUtils.clientRequest;

public class HelloUDPClient implements HelloClient {
    private final static int SO_TIMEOUT = 100;
    private final static int AWAIT_TERMINATION_MINUTES = 2;
    private void makeRequests(String prefix, int requests, int threadNumber, InetSocketAddress address) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(SO_TIMEOUT);
            for (int j = 0; j < requests; j++) {
                String requestString = clientRequest(prefix, threadNumber, j);
                byte[] requestBytes = requestString.getBytes(StandardCharsets.UTF_8);
                byte[] buffer = new byte[socket.getReceiveBufferSize()];
                DatagramPacket sentPacket = new DatagramPacket(requestBytes, requestBytes.length, address);
                while (!socket.isClosed()) {
                    try {
                        socket.send(sentPacket);
                        System.out.println("[request] " + requestString);
                        DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length, address);
                        socket.receive(receivedPacket);
                        String responseString = new String(receivedPacket.getData(), receivedPacket.getOffset(),
                                receivedPacket.getLength(), StandardCharsets.UTF_8);
                        System.out.println("[response] " + responseString);
                        if (!UDPUtils.checkResponse(responseString, threadNumber, j)) {
                            continue;
                        }
                        break;
                    } catch (IOException ignored) {
                        // ignored
                    }
                }
            }
        } catch (SocketException e) {
            System.err.println("Cannot create socket : " + e);
        }
    }

    @Override
    public void run(String host, int port, String prefix, int threads, int requests) {
        ExecutorService service = Executors.newFixedThreadPool(threads);
        InetSocketAddress address = new InetSocketAddress(host, port);
        IntStream.range(0, threads)
                .<Runnable>mapToObj(i -> () -> makeRequests(prefix, requests, i, address))
                .forEach(service::submit);
        service.shutdown();
        try {
            if (!service.awaitTermination(AWAIT_TERMINATION_MINUTES, TimeUnit.MINUTES)) {
                service.shutdownNow();
                if (!service.awaitTermination(AWAIT_TERMINATION_MINUTES, TimeUnit.MINUTES)) {
                    System.err.println("Service did not terminate");
                }
            }
        } catch (InterruptedException ie) {
            service.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        UDPUtils.runClient(new HelloUDPClient(), args);
    }
}
