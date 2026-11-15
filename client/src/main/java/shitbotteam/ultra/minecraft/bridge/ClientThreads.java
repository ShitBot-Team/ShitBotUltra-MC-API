package shitbotteam.ultra.minecraft.bridge;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

final class ClientThreads {
    private ClientThreads() { }
    static ThreadFactory factory(String name) {
        AtomicInteger serial = new AtomicInteger();
        return task -> {
            Thread t = new Thread(task, "sbu-" + name + "-" + serial.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
    static ThreadPoolExecutor pool(String name, int threads, int capacity) {
        return new ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(capacity), factory(name), new ThreadPoolExecutor.AbortPolicy());
    }
}
