package info.kgeorgiy.ja.garipov.concurrent;

import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;

public class ParallelMapperImpl implements ParallelMapper {
    private final TasksQueue queue;
    private final List<Thread> workers;

    public ParallelMapperImpl(int threads) {
        queue = new TasksQueue();
        workers = new ArrayList<>();
        IntStream.range(0, threads).mapToObj(i -> new Thread(() -> {
            try {
                while (!Thread.interrupted()) {
                    queue.poll().run();
                }
            } catch (InterruptedException ignored) {
            } finally {
                Thread.currentThread().interrupt();
            }
        })).forEach(workers::add);
        workers.forEach(Thread::start);
    }

    @Override
    public <T, R> List<R> map(Function<? super T, ? extends R> f, List<? extends T> args) throws InterruptedException {
        TaskResult<R> taskResult = new TaskResult<>(args.size());
        queue.add(args, taskResult, f);
        return taskResult.getResult();
    }

    @Override
    public void close() {
        for (int i = 0; i < workers.size(); i++) {
            workers.get(i).interrupt();
            try {
                workers.get(i).join();
            } catch (InterruptedException ignored) {
                i--;
            }

        }
    }

    private static class TasksQueue {
        private final Queue<Runnable> queue = new ArrayDeque<>();

        public synchronized Runnable poll() throws InterruptedException {
            while (queue.isEmpty()) {
                wait();
            }
            return queue.poll();
        }

        public synchronized <T, R> void add(List<T> args, TaskResult<R> taskResult, Function<? super T, ? extends R> f) {
            IntStream.range(0, args.size())
                    .<Runnable>mapToObj(finalI -> () -> taskResult.calculate(finalI, args.get(finalI), f))
                    .forEach(queue::add);
            notifyAll();
        }
    }

    private static class TaskResult<R> {
        private int counter;
        private final List<R> mapped;
        private RuntimeException taskException;

        public TaskResult(int size) {
            counter = size;
            mapped = new ArrayList<>(Collections.nCopies(size, null));
            taskException = null;
        }

        public <T> void calculate(final int index, final T functionArgument, final Function<? super T, ? extends R> f) {
            try {
                R value = f.apply(functionArgument);
                synchronized (this) {
                    mapped.set(index, value);
                }
            } catch (RuntimeException runtimeException) {
                synchronized (this) {
                    if (taskException == null) {
                        taskException = runtimeException;
                    } else {
                        taskException.addSuppressed(runtimeException);
                    }
                }
            }
            decreaseCounter();
        }

        private synchronized void decreaseCounter() {
            counter--;
            if (counter == 0) {
                notify();
            }
        }

        public synchronized List<R> getResult() throws InterruptedException, RuntimeException {
            while (counter != 0) {
                wait();
            }
            if (taskException != null) {
                throw taskException;
            }
            return mapped;
        }
    }
}
