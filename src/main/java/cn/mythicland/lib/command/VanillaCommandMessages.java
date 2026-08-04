package cn.mythicland.lib.command;

import org.bukkit.ChatColor;

public final class VanillaCommandMessages {

    private VanillaCommandMessages() {
    }

    /**
     * Formats a message as a red error or denial.
     *
     * @param message the message body
     * @return the colored message
     */
    public static String red(String message) {
        return ChatColor.RED + message;
    }

    /**
     * Formats a message as a yellow informational prompt.
     *
     * @param message the message body
     * @return the colored message
     */
    public static String yellow(String message) {
        return ChatColor.YELLOW + message;
    }

    /**
     * Formats a message as a green success prompt.
     *
     * @param message the message body
     * @return the colored message
     */
    public static String green(String message) {
        return ChatColor.GREEN + message;
    }

    /**
     * Formats a usage line as a red prompt.
     *
     * @param usage the command usage without the localized usage prefix
     * @return the colored usage prompt
     */
    public static String usage(String usage) {
        return red("用法: " + usage);
    }
}
