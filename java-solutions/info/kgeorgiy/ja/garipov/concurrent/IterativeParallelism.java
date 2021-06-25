package info.kgeorgiy.ja.garipov.concurrent;

import info.kgeorgiy.java.advanced.concurrent.AdvancedIP;
import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IterativeParallelism implements AdvancedIP {

    private final ParallelMapper mapper;

    public IterativeParallelism() {
        mapper = null;
    }

    public IterativeParallelism(ParallelMapper mapper) {
        this.mapper = mapper;
    }

    private <T> int getActualThreadsCount(int threads, List<T> values) {
        threads = Math.min(threads, values.size());
        return threads;
    }

    public <T> List<Stream<? extends T>> buildStreams(int threads, List<T> values) {
        if (threads < 1) {
            throw new IllegalArgumentException("Number of threads should be greater or equal to 1");
        }
        threads = getActualThreadsCount(threads, values);
        int streamSize = values.size() / threads;
        int left, right = 0;
        int additionalElements = 0;
        List<Stream<? extends T>> streamList = new ArrayList<>();
        int mod = (values.size() % threads);
        while (true) {
            left = right;
            right = left + streamSize + ((additionalElements < mod) ? 1 : 0);
            if (right > values.size()) {
                break;
            }
            if (mod != 0) {
                additionalElements++;
            }
            List<T> subList = values.subList(left, right);
            streamList.add(subList.stream());
        }
        return streamList;
    }

    private <T, R> R monoidOperation(int threads, List<T> values, Function<T, R> lift, Monoid<R> monoid) throws InterruptedException {
        return concurrentOperation(threads,
                values,
                stream -> stream.map(lift).reduce(monoid.getIdentity(), monoid.getOperator(), monoid.getOperator()),
                stream -> stream.reduce(monoid.getIdentity(), monoid.getOperator()));
    }

    @Override
    public <T> T reduce(int threads, List<T> values, Monoid<T> monoid) throws InterruptedException {
        return monoidOperation(threads,
                values,
                Function.identity(),
                monoid);
    }

    @Override
    public <T, R> R mapReduce(int threads, List<T> values, Function<T, R> lift, Monoid<R> monoid) throws InterruptedException {
        return monoidOperation(threads,
                values,
                lift,
                monoid);
    }

    @Override
    public String join(int threads, List<?> values) throws InterruptedException {
        return concurrentOperation(threads,
                values,
                (stream -> (stream.map(Object::toString).collect(Collectors.joining()))),
                (stream -> stream.collect(Collectors.joining())));
    }

    private <T, R> List<R> flatConcurrentOperation(int threads, List<? extends T> values, Function<Stream<? extends T>, Stream<? extends R>> streamFunction) throws InterruptedException {
        return concurrentOperation(threads,
                values,
                stream -> streamFunction.apply(stream).collect(Collectors.toList()),
                streamStream -> streamStream.flatMap(Collection::stream).collect(Collectors.toList()));
    }

    @Override
    public <T> List<T> filter(int threads, List<? extends T> values, Predicate<? super T> predicate) throws InterruptedException {
        return flatConcurrentOperation(threads,
                values,
                stream -> stream.filter(predicate));
    }

    @Override
    public <T, U> List<U> map(int threads, List<? extends T> values, Function<? super T, ? extends U> f) throws InterruptedException {
        return flatConcurrentOperation(threads,
                values,
                stream -> stream.map(f));
    }

    private void joinAll(List<Thread> threads) throws InterruptedException {
        InterruptedException exception = null;
        for (Thread thread : threads) {
            while (true) {
                try {
                    thread.join();
                    break;
                } catch (InterruptedException e) {
                    if (exception == null) {
                        exception = new InterruptedException("Cannot join all threads because of interrupt");
                    }
                    exception.addSuppressed(e);
                }
            }
        }
        if (exception != null) {
            throw exception;
        }
    }

    private <T, R> List<Thread> buildThreads(int threads, List<T> values, Function<? super Stream<? extends T>, R> streamRFunction,
                                             List<R> threadOutput) {
        List<Stream<? extends T>> streamList = buildStreams(threads, values);
        List<Thread> threadList = new ArrayList<>();
        for (int i = 0; i < streamList.size(); i++) {
            final int index = i;
            threadList.add(new Thread(() -> threadOutput.set(index,
                    streamRFunction.apply(streamList.get(index)))));
        }
        return threadList;
    }

    private <R> R joinResults(List<R> threadOutput, Function<? super Stream<R>, R> resultJoiner) {
        return resultJoiner.apply(threadOutput.stream());
    }

    private void startThreads(List<Thread> threads) {
        for (Thread thread : threads) {
            thread.start();
        }
    }

    private <T, R> R concurrentOperation(int threads, List<T> values, Function<? super Stream<? extends T>, R> streamRFunction,
                                         Function<? super Stream<R>, R> resultJoiner) throws InterruptedException {
        List<R> threadOutput;
        if (mapper == null) {
            threads = getActualThreadsCount(threads, values);
            threadOutput = new ArrayList<>(Collections.nCopies(threads, null));
            List<Thread> threadList = buildThreads(threads, values, streamRFunction, threadOutput);
            startThreads(threadList);
            joinAll(threadList);
        } else {
            List<Stream<? extends T>> streamList = buildStreams(threads, values);
            threadOutput = mapper.map(streamRFunction, streamList);
        }
        return joinResults(threadOutput, resultJoiner);
    }

    @Override
    public <T> T maximum(int threads, List<? extends T> values, Comparator<? super T> comparator) throws InterruptedException {
        return minimum(threads, values, comparator.reversed());
    }

    @Override
    public <T> T minimum(int threads, List<? extends T> values, Comparator<? super T> comparator) throws InterruptedException {
        Function<Stream<? extends T>, T> minimumFunction = (stream -> stream.min(comparator).orElse(null));
        return concurrentOperation(threads, values, minimumFunction, minimumFunction);
    }

    @Override
    public <T> boolean all(int threads, List<? extends T> values, Predicate<? super T> predicate) throws InterruptedException {
        return concurrentOperation(threads, values, (stream -> stream.allMatch(predicate)),
                (stream -> stream.allMatch(Boolean::booleanValue)));
    }

    @Override
    public <T> boolean any(int threads, List<? extends T> values, Predicate<? super T> predicate) throws InterruptedException {
        return !all(threads, values, predicate.negate());
    }
}
