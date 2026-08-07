package cn.mythicland.lib.bootstrap;

import cn.mythicland.lib.api.LibApi;
import cn.mythicland.lib.bootstrap.annotation.DefaultImplementation;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.lib.bootstrap.annotation.ListenerComponent;
import cn.mythicland.lib.bootstrap.annotation.CommandComponent;
import cn.mythicland.lib.bootstrap.annotation.ServiceComponent;
import cn.mythicland.lib.container.ContainerAnimationService;
import cn.mythicland.lib.loading.PlayerLoadingGate;
import cn.mythicland.lib.menu.MenuService;
import org.bukkit.Server;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Small constructor-injection container for one plugin classloader.
 *
 * <p>The container deliberately scans only the requesting plugin's package and uses JDK classpath
 * APIs. Lib therefore exposes the annotation mechanism without adding a reflection framework to
 * either Lib or its dependent plugin JARs.</p>
 */
public final class ComponentContainer {

    private final String basePackage;
    private final Set<Class<?>> managedTypes;
    private final Map<Class<?>, Object> singletons = new ConcurrentHashMap<>();
    private final ThreadLocal<List<Class<?>>> resolvingTypes = ThreadLocal.withInitial(ArrayList::new);

    /**
     * Creates a container for one plugin package.
     *
     * @param plugin     requesting plugin and built-in JavaPlugin dependency
     * @param lib        shared Lib service dependency
     * @param basePackage package scanned for Lib annotations
     */
    public ComponentContainer(
            JavaPlugin plugin,
            LibApi lib,
            String basePackage
    ) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(lib, "lib");
        this.basePackage = requireBasePackage(basePackage);
        this.managedTypes = discoverManagedTypes(plugin.getClass().getClassLoader(), this.basePackage);
        registerBuiltIns(plugin, lib);
    }

    /**
     * Resolves one component or built-in dependency as a cached singleton.
     *
     * @param requestedType requested type
     * @param <T> requested type
     * @return resolved singleton
     */
    public <T> T resolve(Class<T> requestedType) {
        Objects.requireNonNull(requestedType, "requestedType");
        Object singleton = singletons.get(requestedType);
        if (singleton != null) return requestedType.cast(singleton);

        Class<? extends T> implementation = resolveImplementation(requestedType);
        Object existing = singletons.get(implementation);
        if (existing != null) {
            singletons.putIfAbsent(requestedType, existing);
            return requestedType.cast(existing);
        }
        return instantiate(requestedType, implementation);
    }

    /**
     * Resolves every managed component assignable to a contract in stable class-name order.
     *
     * @param contract component contract
     * @param <T> contract type
     * @return immutable resolved components
     */
    public <T> List<T> resolveAll(Class<T> contract) {
        Objects.requireNonNull(contract, "contract");
        List<Class<?>> candidates = managedTypes.stream()
                .filter(contract::isAssignableFrom)
                .sorted(Comparator.comparing(Class::getName))
                .toList();
        List<T> result = new ArrayList<>();
        for (Class<?> candidate : candidates) result.add(contract.cast(resolve(candidate)));
        return List.copyOf(result);
    }

    /**
     * Returns managed classes carrying one bootstrap annotation.
     *
     * @param annotation annotation type
     * @return immutable matching classes
     */
    public List<Class<?>> annotatedTypes(Class<? extends Annotation> annotation) {
        Objects.requireNonNull(annotation, "annotation");
        return managedTypes.stream()
                .filter(type -> type.isAnnotationPresent(annotation))
                .sorted(Comparator.comparing(Class::getName))
                .toList();
    }

    /**
     * Clears cached component instances.
     */
    public void clear() {
        singletons.clear();
        resolvingTypes.remove();
    }

    private static Set<Class<?>> discoverManagedTypes(ClassLoader classLoader, String basePackage) {
        Set<Class<?>> discovered = new LinkedHashSet<>();
        for (Class<?> type : ComponentScanner.scan(classLoader, basePackage)) {
            if (isManaged(type)) discovered.add(type);
        }
        if (discovered.isEmpty()) {
            throw new IllegalStateException("No Lib components found below package: " + basePackage);
        }
        return Set.copyOf(discovered);
    }

    private static boolean isManaged(Class<?> type) {
        return type.isAnnotationPresent(InjectComponent.class)
                || type.isAnnotationPresent(ListenerComponent.class)
                || type.isAnnotationPresent(CommandComponent.class)
                || type.isAnnotationPresent(ServiceComponent.class);
    }

    private void registerBuiltIns(JavaPlugin plugin, LibApi lib) {
        singletons.put(plugin.getClass(), plugin);
        singletons.put(JavaPlugin.class, plugin);
        singletons.put(Server.class, plugin.getServer());
        singletons.put(BukkitScheduler.class, plugin.getServer().getScheduler());
        singletons.put(Logger.class, plugin.getLogger());
        singletons.put(LibApi.class, lib);
        singletons.put(ContainerAnimationService.class, lib.containerAnimationService());
        singletons.put(MenuService.class, lib.menuService());
        singletons.put(PlayerLoadingGate.class, lib.playerLoadingGate());
        singletons.put(ComponentContainer.class, this);
    }

    private <T> T instantiate(Class<T> requestedType, Class<? extends T> implementation) {
        List<Class<?>> resolving = resolvingTypes.get();
        if (resolving.contains(implementation)) {
            throw new IllegalStateException(formatCircularDependency(resolving, implementation));
        }
        resolving.add(implementation);
        try {
            Constructor<? extends T> constructor = resolveConstructor(implementation);
            Object[] arguments = new Object[constructor.getParameterCount()];
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            for (int index = 0; index < parameterTypes.length; index++) {
                arguments[index] = resolve(parameterTypes[index]);
            }
            if (!constructor.trySetAccessible()) {
                throw new IllegalStateException(
                        "Cannot access injectable constructor: " + implementation.getName()
                );
            }
            T instance = constructor.newInstance(arguments);
            singletons.put(implementation, instance);
            if (requestedType != implementation) singletons.put(requestedType, instance);
            return instance;
        } catch (InvocationTargetException exception) {
            throw componentFailure(implementation, exception.getCause());
        } catch (ReflectiveOperationException | RuntimeException exception) {
            throw componentFailure(implementation, exception);
        } finally {
            resolving.remove(resolving.size() - 1);
            if (resolving.isEmpty()) resolvingTypes.remove();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Constructor<? extends T> resolveConstructor(Class<? extends T> implementation) {
        List<Constructor<?>> constructors = new ArrayList<>();
        for (Constructor<?> constructor : implementation.getDeclaredConstructors()) {
            if (!Modifier.isPrivate(constructor.getModifiers())) constructors.add(constructor);
        }
        if (constructors.size() != 1) {
            throw new IllegalStateException(
                    "Injectable component must declare exactly one non-private constructor: "
                            + implementation.getName()
            );
        }
        return (Constructor<? extends T>) constructors.getFirst();
    }

    private <T> Class<? extends T> resolveImplementation(Class<T> requestedType) {
        if (!requestedType.isInterface() && !Modifier.isAbstract(requestedType.getModifiers())) {
            if (!managedTypes.contains(requestedType)) {
                throw new IllegalStateException("Type is not a Lib component: " + requestedType.getName());
            }
            return requestedType;
        }

        List<Class<? extends T>> candidates = new ArrayList<>();
        for (Class<?> type : managedTypes) {
            if (!requestedType.isAssignableFrom(type)) continue;
            if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) continue;
            @SuppressWarnings("unchecked")
            Class<? extends T> candidate = (Class<? extends T>) type;
            candidates.add(candidate);
        }
        candidates.sort(Comparator.comparing(Class::getName));
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No Lib component implements: " + requestedType.getName());
        }
        if (candidates.size() == 1) return candidates.getFirst();

        Class<? extends T> defaultImplementation = null;
        for (Class<? extends T> candidate : candidates) {
            if (!candidate.isAnnotationPresent(DefaultImplementation.class)) continue;
            if (defaultImplementation != null) {
                throw new IllegalStateException(
                        "Multiple default Lib components implement " + requestedType.getName()
                );
            }
            defaultImplementation = candidate;
        }
        if (defaultImplementation != null) return defaultImplementation;
        throw new IllegalStateException(
                "Multiple Lib components implement " + requestedType.getName() + ": " + candidates
        );
    }

    private static IllegalStateException componentFailure(Class<?> implementation, Throwable failure) {
        return new IllegalStateException(
                "Failed to create Lib component: " + implementation.getName(),
                failure
        );
    }

    private static String formatCircularDependency(List<Class<?>> resolving, Class<?> repeatedType) {
        List<String> names = new ArrayList<>();
        for (Class<?> type : resolving) names.add(type.getName());
        names.add(repeatedType.getName());
        return "Circular Lib component dependency detected: " + String.join(" -> ", names);
    }

    private static String requireBasePackage(String value) {
        String packageName = Objects.requireNonNull(value, "basePackage").trim();
        if (packageName.isBlank()) throw new IllegalArgumentException("basePackage cannot be blank");
        return packageName;
    }
}
