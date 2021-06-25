package info.kgeorgiy.ja.garipov.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;
import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UDPUtils {

    public final static long SELECT_TIMEOUT = 300;
    private final static Pattern RESPONSE_PATTERN = Pattern.compile("[\\D]*([\\d]+)[\\D]+([\\d]+)[\\D]*");

    static String clientRequest(String prefix, int channelIndex, int requestIndex) {
        String request = prefix + channelIndex + "_" + requestIndex;
        return request;
    }

    static boolean checkResponse(String response, int threadNumber, int requestNumber) {
        Matcher matcher = RESPONSE_PATTERN.matcher(response);
        return (matcher.matches()
                && matcher.group(1).equals(Integer.toString(threadNumber))
                && matcher.group(2).equals(Integer.toString(requestNumber)));
    }

    static void processException(Exception e) {
        System.err.println("An error occurred : " + e);
    }

    static final class ClientChannelData {
        private final ByteBuffer buffer;
        private final int channelIndex;
        private int requestIndex;

        ClientChannelData(DatagramChannel channel, int channelIndex) throws SocketException {
            this.buffer = ByteBuffer.allocate(channel.socket().getReceiveBufferSize());
            this.channelIndex = channelIndex;
            this.requestIndex = 0;
        }

        public void nextRequest() {
            requestIndex++;
        }

        public ByteBuffer getBuffer() {
            return buffer;
        }

        public int getChannelIndex() {
            return channelIndex;
        }

        public int getRequestIndex() {
            return requestIndex;
        }

    }

    static final class ServerChannelData {
        private final ByteBuffer buffer;
        private final SocketAddress address;

        public ServerChannelData(ByteBuffer buffer, SocketAddress address) {
            this.buffer = buffer;
            this.address = address;
        }

        public ByteBuffer getBuffer() {
            return buffer;
        }

        public SocketAddress getAddress() {
            return address;
        }
    }

    static void runClient(HelloClient client, String[] args) {
        if (args.length != 5) {
            System.err.println("Invalid args format");
            return;
        }
        String hostname = args[0], prefix = args[2];
        int port, threads, requests;
        try {
            port = Integer.parseInt(args[1]);
            threads = Integer.parseInt(args[3]);
            requests = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid args format, cannot parse int");
            return;
        }
        client.run(hostname, port, prefix, threads, requests);
    }

    static void runServer(Supplier<HelloServer> supplier, String[] args) {
        if (args.length != 2) {
            System.err.println("Invalid args format");
            return;
        }
        int port;
        int threads;
        try {
            port = Integer.parseInt(args[0]);
            threads = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid args format, cannot parse int");
            return;
        }
        try (HelloServer server = (supplier.get())) {
            server.start(port, threads);
        }
    }

    static void closeResource(Closeable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (IOException e) {
                processException(e);
            }
        }
    }

    static void closeExecutorService(ExecutorService service) {
        if (service != null) {
            service.shutdown();
            try {
                if (!service.awaitTermination(2, TimeUnit.SECONDS)) {
                    service.shutdownNow();
                    if (!service.awaitTermination(2, TimeUnit.SECONDS)) {
                        System.err.println("Service did not terminate");
                    }
                }
            } catch (InterruptedException ie) {
                service.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

}