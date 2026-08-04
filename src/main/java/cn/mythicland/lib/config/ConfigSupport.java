package cn.mythicland.lib.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Shared helpers for loading and validating plugin configuration files.
 */
public final class ConfigSupport {

    private ConfigSupport() {
    }

    /**
     * Loads a plugin configuration and writes missing values from {@code config.yml} into the file.
     * Unknown keys already present in the file are preserved and are not migrated or removed.
     *
     * @param plugin the plugin that owns the configuration
     * @return the loaded configuration with default values attached and missing values materialized
     */
    public static FileConfiguration loadDefault(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration configuration = plugin.getConfig();
        configuration.options().copyDefaults(true);
        plugin.saveConfig();
        return configuration;
    }

    /**
     * Reads a required string and resets missing or invalid values to the supplied default.
     *
     * @param plugin        the plugin that owns the configuration and receives validation warnings
     * @param configuration the loaded plugin configuration
     * @param path          the configuration path
     * @param defaultValue  the value written when the configured value is absent or invalid
     * @return the trimmed configured value or the default value
     */
    public static String getString(
            JavaPlugin plugin,
            FileConfiguration configuration,
            String path,
            String defaultValue
    ) {
        Object rawValue = configuration.get(path);
        if (!configuration.contains(path)) {
            configuration.set(path, defaultValue);
            plugin.saveConfig();
            return defaultValue;
        }
        if (!(rawValue instanceof String value) || value.isBlank()) {
            return resetToDefault(plugin, configuration, path, defaultValue, "expected a non-empty string");
        }
        return value.trim();
    }

    /**
     * Reads a strict boolean and resets missing or invalid values to the supplied default.
     *
     * @param plugin        the plugin that owns the configuration and receives validation warnings
     * @param configuration the loaded plugin configuration
     * @param path          the configuration path
     * @param defaultValue  the value written when the configured value is absent or invalid
     * @return the configured boolean or the default value
     */
    public static boolean getBoolean(
            JavaPlugin plugin,
            FileConfiguration configuration,
            String path,
            boolean defaultValue
    ) {
        Object rawValue = configuration.get(path);
        if (!configuration.contains(path)) {
            configuration.set(path, defaultValue);
            plugin.saveConfig();
            return defaultValue;
        }
        if (!(rawValue instanceof Boolean value)) {
            return resetToDefault(plugin, configuration, path, defaultValue, "expected true or false");
        }
        return value;
    }

    /**
     * Replaces an invalid configuration value, logs a warning, and persists the default.
     *
     * @param plugin        the plugin that owns the configuration
     * @param configuration the loaded plugin configuration
     * @param path          the invalid configuration path
     * @param defaultValue  the replacement value
     * @param reason        a concise explanation of the validation failure
     * @param <T>           the replacement value type
     * @return the supplied default value
     */
    public static <T> T resetToDefault(
            JavaPlugin plugin,
            FileConfiguration configuration,
            String path,
            T defaultValue,
            String reason
    ) {
        plugin.getLogger().warning(
                "Invalid configuration '" + path + "': " + reason + "; resetting to the default value."
        );
        configuration.set(path, defaultValue);
        plugin.saveConfig();
        return defaultValue;
    }
}
