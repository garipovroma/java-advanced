package info.kgeorgiy.ja.garipov.crawler;

import info.kgeorgiy.java.advanced.crawler.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.*;

public class WebCrawler implements AdvancedCrawler {

    private static final int DOWLOADERS = 16;
    private static final int EXTRACTORS = 16;
    private static final int DEPTH = 2;
    private static final int PER_HOST = 8;
    private static final int[] defaultValues = new int[]{DOWLOADERS, EXTRACTORS, PER_HOST};
    private final Downloader downloader;
    private final int perHost;
    private final ExecutorService downloadService, extractService;
    private final Map<String, HostQueue> hostQueueMap;

    public WebCrawler(Downloader downloader, int downloaders, int extractors) {
        this(downloader, downloaders, extractors, Integer.MAX_VALUE);
    }

    public WebCrawler(Downloader downloader, int downloaders, int extractors, int perHost) {
        this.downloader = downloader;
        this.perHost = perHost;
        this.downloadService = Executors.newFixedThreadPool(downloaders);
        this.extractService = Executors.newFixedThreadPool(extractors);
        this.hostQueueMap = new ConcurrentHashMap<>();
    }

    public static void main(String[] args) {
        if (args.length == 0 || Arrays.stream(args).anyMatch(Objects::isNull)) {
            System.err.println("should be at least 1 args and all of them cannot be null");
            return;
        }
        int[] crawlerArgs = defaultValues;
        int depth;
        try {
            depth = Integer.parseInt(args[1]);
            for (int i = 2; i < args.length; i++) {
                crawlerArgs[i] = Integer.parseInt(args[i]);
            }
        } catch (NumberFormatException e) {
            System.err.println("arguments 1 - 4 should be integers");
            return;
        }
        try {
            WebCrawler crawler = new WebCrawler(new CachingDownloader(), crawlerArgs[0], crawlerArgs[1], crawlerArgs[2]);
            crawler.download(args[0], depth);
        } catch (IOException e) {
            System.err.println("An error occurred : " + e.getMessage());
        }
    }

    private Result startBFS(String startUrl, int depth, List<String> hosts, boolean allAllowed) {
        final List<String> hostsSet = (!allAllowed ? hosts : null);
        Set<String> downloadedDocuments = ConcurrentHashMap.newKeySet();
        Map<String, IOException> errors = new ConcurrentHashMap<>();
        String startHost = null;
        if (!allAllowed) {
            try {
                startHost = URLUtils.getHost(startUrl);
            } catch (MalformedURLException e) {
                errors.put(startUrl, e);
                return new Result(new ArrayList<>(downloadedDocuments), errors);
            }
        }
        // :NOTE: code hygiene
        if (!allAllowed && !hostsSet.contains(startHost)) {
            return new Result(new ArrayList<>(downloadedDocuments), errors);
        }
        Set<String> visited = ConcurrentHashMap.newKeySet();
        Phaser phaser = new Phaser(1);
        Queue<String> queue = new ConcurrentLinkedQueue<>();
        visited.add(startUrl);
        queue.add(startUrl);
        int currentLayer = 1;
        while (!queue.isEmpty()) {
            String url = queue.poll();
            currentLayer--;
            try {
                String hostName = URLUtils.getHost(url);
                HostQueue hostQueue = hostQueueMap.computeIfAbsent(
                        hostName,
                        absent -> new HostQueue(downloadService));
                phaser.register();
                final int finalCurrentDepth = phaser.getPhase() + 1;
                hostQueue.put(() -> {
                    try {
                        Document document = downloader.download(url);
                        downloadedDocuments.add(url);
                        if (finalCurrentDepth < depth) {
                            phaser.register();
                            extractService.submit(() -> {
                                try {
                                    List<String> newURLs = document.extractLinks();
                                    for (String newURL : newURLs) {
                                        String newHostName = URLUtils.getHost(newURL);
                                        if (!allAllowed && !hostsSet.contains(newHostName)) {
                                            continue;
                                        }
                                        if (!visited.contains(newURL)) {
                                            queue.add(newURL);
                                            visited.add(newURL);
                                        }
                                    }
                                } catch (IOException e) {
                                    errors.put(url, e);
                                } finally {
                                    phaser.arriveAndDeregister();
                                }
                            });
                        }
                    } catch (IOException e) {
                        errors.put(url, e);
                    } finally {
                        phaser.arriveAndDeregister();
                        hostQueue.take();
                    }
                });
            } catch (MalformedURLException e) {
                errors.put(url, e);
            }
            if (currentLayer == 0) {
                phaser.arriveAndAwaitAdvance();
                currentLayer = queue.size();
            }
        }
        return new Result(new ArrayList<>(downloadedDocuments), errors);
    }

    @Override
    public Result download(String url, int depth) {
        return startBFS(url, depth, null, true);
    }

    @Override
    public Result download(String url, int depth, List<String> hosts) {
        return startBFS(url, depth, hosts, false);
    }

    @Override
    public void close() {
        downloadService.shutdown();
        extractService.shutdown();
    }

    public class HostQueue {
        private final ExecutorService downloadService;
        private final Queue<Runnable> queue;
        private int freeSpace;

        public HostQueue(ExecutorService downloadService) {
            this.downloadService = downloadService;
            this.freeSpace = perHost;
            this.queue = new ArrayDeque<>();
        }

        public synchronized void put(Runnable runnable) {
            if (freeSpace > 0) {
                downloadService.submit(runnable);
                freeSpace--;
            } else {
                queue.add(runnable);
            }
        }

        public synchronized void take() {
            if (queue.isEmpty()) {
                // :NOTE: might violate perHost limitations
                freeSpace++;
            } else {
                downloadService.submit(queue.poll());
            }
        }
    }
}
