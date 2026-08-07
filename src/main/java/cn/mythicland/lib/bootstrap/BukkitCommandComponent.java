package cn.mythicland.lib.bootstrap;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;

/**
 * Injectable binding between one plugin command declaration and its Bukkit executors.
 */
public interface BukkitCommandComponent {

    /**
     * Returns the command key declared in plugin.yml.
     *
     * @return command key
     */
    String commandName();

    /**
     * Returns the command executor.
     *
     * @return command executor
     */
    CommandExecutor executor();

    /**
     * Returns the command tab completer.
     *
     * @return tab completer
     */
    TabCompleter tabCompleter();

    /**
     * Applies the binding to a Bukkit command object.
     *
     * @param command declared plugin command
     */
    default void register(PluginCommand command) {
        command.setExecutor(executor());
        command.setTabCompleter(tabCompleter());
    }

    /**
     * Indicates whether Lib should replace the unqualified global command mapping.
     *
     * @return true when the command must take over an existing global command
     */
    default boolean takeOverGlobalMapping() {
        return false;
    }
}
