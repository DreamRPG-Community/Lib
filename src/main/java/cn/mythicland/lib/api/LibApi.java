package cn.mythicland.lib.api;

import cn.mythicland.lib.command.CommandRouter;
import cn.mythicland.lib.menu.MenuService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Shared lifecycle, scheduling, asynchronous execution, and command infrastructure for plugins.
 *
 * <p>The Lib plugin owns the service lifecycle. Dependent plugins may use the service while Lib is
 * enabled, but must not close it.</p>
 */
public final class LibApi implements AutoCloseable {

    private final JavaPlugin owner;
    private final MenuService menuService;
    private final ExecutorService asyncExecutor;
    private final Set<BukkitTask> scheduledTasks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private volatile boolean closed;

    /**
     * Creates a Lib service for its owning plugin.
     *
     * @param owner the plugin that owns the service lifecycle and scheduler tasks
     * @param poolSize the number of asynchronous worker threads
     * @throws NullPointerException if {@code owner} is null
     * @throws IllegalArgumentException if {@code poolSize} is less than one
     */
    public LibApi(JavaPlugin owner, int poolSize) {
        this(owner, poolSize, new MenuService(owner));
    }

    /**
     * Creates a Lib service with a shared menu service.
     *
     * @param owner the plugin that owns the service lifecycle and scheduler tasks
     * @param poolSize the number of asynchronous worker threads
     * @param menuService the shared menu service
     */
    public LibApi(JavaPlugin owner, int poolSize, MenuService menuService) {
        if (poolSize < 1) throw new IllegalArgumentException("poolSize must be positive");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.menuService = Objects.requireNonNull(menuService, "menuService");
        this.asyncExecutor = Executors.newFixedThreadPool(poolSize, namedThreadFactory());
    }

    /**
     * Returns the default asynchronous worker count bounded for a small Bukkit plugin runtime.
     *
     * @return a worker count between one and four
     */
    public static int defaultPoolSize() {
        return Math.clamp(Runtime.getRuntime().availableProcessors(), 1, 4);
    }

    /**
     * Resolves the shared Lib service required by a dependent plugin.
     *
     * @param plugin the dependent plugin requesting the service
     * @return the registered Lib service
     * @throws NullPointerException if {@code plugin} is null
     * @throws IllegalStateException if Lib is not registered or has no provider
     */
    public static LibApi require(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        RegisteredServiceProvider<LibApi> registration = plugin.getServer()
                .getServicesManager()
                .getRegistration(LibApi.class);
        if (registration == null || registration.getProvider() == null) {
            throw new IllegalStateException(
                    "Lib service is unavailable; " + plugin.getDescription().getName() + " cannot start"
            );
        }
        return registration.getProvider();
    }

    /**
     * Returns the most useful message from a nested exception chain.
     *
     * @param throwable the exception whose root message should be returned
     * @return the deepest non-blank exception message, or the deepest exception type when no
     *         message is available
     * @throws NullPointerException if {@code throwable} is null
     */
    public static String rootCauseMessage(Throwable throwable) {
        Objects.requireNonNull(throwable, "throwable");
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
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

    /**
     * Runs an action on Lib's asynchronous executor.
     *
     * @param action the action to execute
     * @return a future completed when the action finishes
     * @throws NullPointerException if {@code action} is null
     */
    public CompletableFuture<Void> runAsync(Runnable action) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Lib API is closed"));
        return CompletableFuture.runAsync(action, asyncExecutor);
    }

    /**
     * Supplies a value on Lib's asynchronous executor.
     *
     * @param supplier the asynchronous value supplier
     * @param <T> the supplied value type
     * @return a future completed with the supplied value
     * @throws NullPointerException if {@code supplier} is null
     */
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Lib API is closed"));
        return CompletableFuture.supplyAsync(supplier, asyncExecutor);
    }

    /**
     * Runs an action on the Bukkit primary thread.
     *
     * @param action the action to execute
     * @return a future completed after the action finishes
     * @throws NullPointerException if {@code action} is null
     */
    public CompletableFuture<Void> runOnMain(Runnable action) {
        return scheduleOnMain(() -> {
            action.run();
            return null;
        });
    }

    /**
     * Supplies a value on the Bukkit primary thread.
     *
     * @param supplier the primary-thread value supplier
     * @param <T> the supplied value type
     * @return a future completed with the supplied value
     * @throws NullPointerException if {@code supplier} is null
     */
    public <T> CompletableFuture<T> supplyOnMain(Supplier<T> supplier) {
        return scheduleOnMain(supplier);
    }

    /**
     * Schedules an action on the Bukkit primary thread after a delay.
     *
     * @param delayTicks the scheduler delay in server ticks
     * @param action the action to execute
     * @return the scheduled Bukkit task
     * @throws IllegalStateException if this service has been closed
     * @throws NullPointerException if {@code action} is null
     */
    @SuppressWarnings("UnusedReturnValue")
    public BukkitTask runLater(long delayTicks, Runnable action) {
        if (closed) throw new IllegalStateException("Lib API is closed");

        BukkitTask task = owner.getServer().getScheduler().runTaskLater(owner, action, delayTicks);
        scheduledTasks.add(task);
        return task;
    }

    /**
     * Creates a shared command router for a plugin command.
     *
     * @param commandOwner the plugin whose logger handles command failures
     * @param rootCommand the command root used in usage messages
     * @return a new command router
     * @throws NullPointerException if an argument is null
     */
    public CommandRouter createCommandRouter(JavaPlugin commandOwner, String rootCommand) {
        return new CommandRouter(commandOwner, rootCommand);
    }

    /**
     * Returns the shared asynchronous executor.
     *
     * @return the executor owned by this service
     */
    public ExecutorService asyncExecutor() {
        return asyncExecutor;
    }

    /**
     * Returns the shared menu lifecycle service.
     *
     * @return the menu service owned by Lib
     */
    public MenuService menuService() {
        return menuService;
    }

    /**
     * Cancels tracked Bukkit tasks and shuts down the asynchronous executor.
     */
    @Override
    public void close() {
        if (closed) return;
        closed = true;

        scheduledTasks.forEach(BukkitTask::cancel);
        scheduledTasks.clear();
        asyncExecutor.shutdownNow();
        menuService.close();
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
