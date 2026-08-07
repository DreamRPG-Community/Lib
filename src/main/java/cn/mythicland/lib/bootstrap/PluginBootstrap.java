package cn.mythicland.lib.bootstrap;

import cn.mythicland.lib.api.LibApi;
import cn.mythicland.lib.bootstrap.annotation.CommandComponent;
import cn.mythicland.lib.bootstrap.annotation.ListenerComponent;
import cn.mythicland.lib.bootstrap.annotation.ServiceComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;

/**
 * Discovers, constructs, registers, reloads, and closes one plugin's Lib components.
 */
public final class PluginBootstrap implements AutoCloseable {

    private final JavaPlugin plugin;
    private final ComponentContainer components;
    private final List<LibPluginLifecycle> lifecycles = new ArrayList<>();
    private final List<RegisteredService> services = new ArrayList<>();
    private final List<GlobalCommandTakeover> commandTakeovers = new ArrayList<>();
    private boolean enabled;

    /**
     * Creates a package-scoped plugin bootstrap.
     *
     * @param plugin plugin being bootstrapped
     * @param lib shared Lib service
     * @param basePackage package to scan for Lib annotations
     */
    public PluginBootstrap(JavaPlugin plugin, LibApi lib, String basePackage) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.components = new ComponentContainer(plugin, lib, basePackage);
    }

    /**
     * Enables all discovered components.
     *
     * <p>This method registers Bukkit services, listeners, and commands, so it must run on the
     * Bukkit primary thread during plugin startup.</p>
     *
     * @throws IllegalStateException if a component cannot be constructed or registered
     */
    public void enable() {
        if (enabled) throw new IllegalStateException("Plugin bootstrap is already enabled");
        try {
            registerServices();
            lifecycles.addAll(components.resolveAll(LibPluginLifecycle.class));
            for (LibPluginLifecycle lifecycle : lifecycles) lifecycle.enable();
            registerListeners();
            registerCommands();
            enabled = true;
        } catch (RuntimeException exception) {
            disableInternal();
            throw exception;
        }
    }

    /**
     * Reloads every lifecycle component in dependency discovery order.
     *
     * <p>Reload is a Bukkit lifecycle operation and must run on the primary thread. Components
     * decide which mutable resources are reloadable.</p>
     *
     * @throws IllegalStateException if this bootstrap is not enabled
     */
    public void reload() {
        if (!enabled) throw new IllegalStateException("Plugin bootstrap is not enabled");
        for (LibPluginLifecycle lifecycle : lifecycles) lifecycle.reload();
    }

    /**
     * Disables listeners, command overrides, services, and lifecycle components.
     */
    @Override
    public void close() {
        disable();
    }

    /**
     * Disables the bootstrap. The operation is idempotent for Bukkit shutdown handling.
     *
     * <p>This method must run on the Bukkit primary thread because component cleanup may touch
     * Bukkit listeners, commands, services, and scoreboards.</p>
     */
    public void disable() {
        if (!enabled && lifecycles.isEmpty() && services.isEmpty() && commandTakeovers.isEmpty()) {
            components.clear();
            return;
        }
        disableInternal();
    }

    /**
     * Resolves a component for a plugin-owned facade or test.
     *
     * @param type component type
     * @param <T> component type
     * @return cached component
     */
    public <T> T resolve(Class<T> type) {
        return components.resolve(type);
    }

    private void registerServices() {
        Set<Class<?>> contracts = new HashSet<>();
        for (Class<?> componentType : components.annotatedTypes(ServiceComponent.class)) {
            ServiceComponent annotation = componentType.getAnnotation(ServiceComponent.class);
            Object provider = components.resolve(componentType);
            Class<?> contract = annotation.value();
            if (!contract.isInstance(provider)) {
                throw new IllegalStateException(
                        "Service component does not implement " + contract.getName() + ": " + componentType.getName()
                );
            }
            if (!contracts.add(contract)) {
                throw new IllegalStateException("Multiple Lib service components implement: " + contract.getName());
            }
            registerService(contract, provider);
            services.add(new RegisteredService(contract, provider));
        }
    }

    private void registerListeners() {
        for (Class<?> componentType : components.annotatedTypes(ListenerComponent.class)) {
            Object component = components.resolve(componentType);
            if (!(component instanceof Listener listener)) {
                throw new IllegalStateException("Listener component is not a Bukkit Listener: " + componentType.getName());
            }
            Bukkit.getPluginManager().registerEvents(listener, plugin);
        }
    }

    private void registerCommands() {
        Set<String> commandNames = new HashSet<>();
        for (Class<?> componentType : components.annotatedTypes(CommandComponent.class)) {
            Object value = components.resolve(componentType);
            if (!(value instanceof BukkitCommandComponent commandComponent)) {
                throw new IllegalStateException(
                        "Command component is not a BukkitCommandComponent: " + componentType.getName()
                );
            }
            String commandName = commandComponent.commandName();
            if (commandName == null || commandName.isBlank() || !commandNames.add(commandName)) {
                throw new IllegalStateException("Invalid or duplicate Lib command component: " + commandName);
            }
            PluginCommand command = plugin.getCommand(commandName);
            if (command == null) throw new IllegalStateException("Command is missing from plugin.yml: " + commandName);
            commandComponent.register(command);
            if (commandComponent.takeOverGlobalMapping()) {
                GlobalCommandTakeover takeover = new GlobalCommandTakeover(plugin, commandName);
                takeover.install(command);
                commandTakeovers.add(takeover);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerService(Class<?> contract, Object provider) {
        plugin.getServer().getServicesManager().register(
                (Class) contract,
                provider,
                plugin,
                ServicePriority.Normal
        );
    }

    private void disableInternal() {
        HandlerList.unregisterAll(plugin);
        for (int index = commandTakeovers.size() - 1; index >= 0; index--) {
            commandTakeovers.get(index).restore();
        }
        commandTakeovers.clear();
        for (int index = services.size() - 1; index >= 0; index--) {
            RegisteredService service = services.get(index);
            unregisterService(service.contract(), service.provider());
        }
        services.clear();
        for (int index = lifecycles.size() - 1; index >= 0; index--) {
            LibPluginLifecycle lifecycle = lifecycles.get(index);
            try {
                lifecycle.disable();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(
                        Level.SEVERE,
                        "Lib component shutdown failed: " + lifecycle.getClass().getName(),
                        exception
                );
            }
        }
        lifecycles.clear();
        enabled = false;
        components.clear();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void unregisterService(Class<?> contract, Object provider) {
        plugin.getServer().getServicesManager().unregister((Class) contract, provider);
    }

    private record RegisteredService(Class<?> contract, Object provider) {
    }
}
