package cn.mythicland.lib.command;

import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;

/**
 * One executable command node managed by {@link CommandRouter}.
 */
public interface Subcommand {

    /**
     * Returns the primary command name.
     *
     * @return the lower-case command name
     */
    String name();

    /**
     * Returns the usage line shown in help and validation errors.
     *
     * @return the complete usage line
     */
    String usage();

    /**
     * Returns optional aliases for the command.
     *
     * @return an immutable or newly-created collection of aliases
     */
    default Collection<String> aliases() {
        return List.of();
    }

    /**
     * Returns the permission required to execute this command.
     *
     * @return the permission node, or an empty string when no permission is required
     */
    default String permission() {
        return "";
    }

    /**
     * Executes the command with arguments after the command name.
     *
     * @param sender the command sender
     * @param arguments the arguments after the command name
     */
    void execute(CommandSender sender, List<String> arguments);

    /**
     * Supplies tab completions for arguments after the command name.
     *
     * @param sender the command sender requesting completions
     * @param arguments the arguments after the command name
     * @return immutable or newly-created completion values
     */
    default List<String> tabComplete(CommandSender sender, List<String> arguments) {
        return List.of();
    }
}
