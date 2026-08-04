package cn.mythicland.lib.command;

import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;

public interface Subcommand {

    String name();

    String usage();

    default Collection<String> aliases() {
        return List.of();
    }

    default String permission() {
        return "";
    }

    void execute(CommandSender sender, List<String> arguments);

    default List<String> tabComplete(CommandSender sender, List<String> arguments) {
        return List.of();
    }
}
