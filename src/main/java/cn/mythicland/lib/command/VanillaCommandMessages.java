package cn.mythicland.lib.command;

import org.bukkit.ChatColor;

public final class VanillaCommandMessages {

    private VanillaCommandMessages() {
    }

    public static String red(String message) {
        return ChatColor.RED + message;
    }

    public static String usage(String usage) {
        return red("用法: " + usage);
    }
}
