package cn.mythicland.lib;

import cn.mythicland.lib.api.LibApi;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bukkit entry point that owns and publishes the shared {@link LibApi} service.
 */
public final class LibPlugin extends JavaPlugin {

    private LibApi api;

    @Override
    public void onEnable() {
        this.api = new LibApi(this, LibApi.defaultPoolSize());
        getServer().getServicesManager().register(
                LibApi.class,
                api,
                this,
                ServicePriority.Normal
        );

        getLogger().info("Lib runtime initialized.");
    }

    @Override
    public void onDisable() {
        if (api == null) return;

        getServer().getServicesManager().unregister(LibApi.class, api);
        api.close();
        api = null;
    }

    /**
     * Returns the service owned by this plugin.
     *
     * @return the active Lib service, or {@code null} after the plugin has been disabled
     */
    public LibApi getApi() {
        return api;
    }
}
