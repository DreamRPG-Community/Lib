package cn.mythicland.lib.api;

import cn.mythicland.lib.command.CommandRouter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class LibApi implements AutoCloseable {

    private final JavaPlugin owner;
    private final ExecutorService asyncExecutor;
    private final Set<BukkitTask> scheduledTasks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private volatile boolean closed;

    public LibApi(JavaPlugin owner, int poolSize) {
        this.owner = owner;
        this.asyncExecutor = Executors.newFixedThreadPool(poolSize, namedThreadFactory());
    }

    public static int defaultPoolSize() {
        return Math.clamp(Runtime.getRuntime().availableProcessors(), 1, 4);
    }

    private static <T> void completeOnMainThread(CompletableFuture<T> future, Supplier<T> supplier) {
        try {
            future.complete(supplier.get());
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
    }

    private static ThreadFactory namedThreadFactory() {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "Lib-Async-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    public CompletableFuture<Void> runAsync(Runnable action) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Lib API is closed"));
        return CompletableFuture.runAsync(action, asyncExecutor);
    }

    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Lib API is closed"));
        return CompletableFuture.supplyAsync(supplier, asyncExecutor);
    }

    public CompletableFuture<Void> runOnMain(Runnable action) {
        return scheduleOnMain(() -> {
            action.run();
            return null;
        });
    }

    public <T> CompletableFuture<T> supplyOnMain(Supplier<T> supplier) {
        return scheduleOnMain(supplier);
    }

    @SuppressWarnings("UnusedReturnValue")
    public BukkitTask runLater(long delayTicks, Runnable action) {
        if (closed) throw new IllegalStateException("Lib API is closed");

        BukkitTask task = owner.getServer().getScheduler().runTaskLater(owner, action, delayTicks);
        scheduledTasks.add(task);
        return task;
    }

    public CommandRouter createCommandRouter(JavaPlugin commandOwner, String rootCommand) {
        return new CommandRouter(commandOwner, rootCommand);
    }

    public ExecutorService asyncExecutor() {
        return asyncExecutor;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;

        scheduledTasks.forEach(BukkitTask::cancel);
        scheduledTasks.clear();
        asyncExecutor.shutdownNow();
    }

    private <T> CompletableFuture<T> scheduleOnMain(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (Bukkit.isPrimaryThread()) {
            completeOnMainThread(future, supplier);
            return future;
        }

        try {
            BukkitTask task = owner.getServer().getScheduler().runTask(
                    owner,
                    () -> completeOnMainThread(future, supplier)
            );
            scheduledTasks.add(task);
        } catch (RuntimeException exception) {
            future.completeExceptionally(exception);
        }
        return future;
    }
}
