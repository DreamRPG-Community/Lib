package cn.mythicland.lib.bootstrap;

/**
 * Optional lifecycle boundary for an injected plugin module.
 */
public interface LibPluginLifecycle {

    /**
     * Starts the module after its constructor dependencies have been resolved.
     */
    void enable();

    /**
     * Reloads mutable configuration owned by the module.
     */
    void reload();

    /**
     * Releases the module's resources.
     */
    void disable();
}
