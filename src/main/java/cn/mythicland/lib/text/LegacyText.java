package cn.mythicland.lib.text;

import org.bukkit.ChatColor;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Utilities for the legacy Bukkit text format used by the self-written plugins.
 */
public final class LegacyText {

    private LegacyText() {
    }

    /**
     * Converts ampersand colour codes to Bukkit section-sign colour codes.
     *
     * @param text legacy text, or {@code null}
     * @return translated text, or {@code null} when the input is null
     */
    public static String colorize(String text) {
        if (text == null) return null;
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Removes Bukkit colour codes from a legacy text value.
     *
     * @param text text, or {@code null}
     * @return text without colour codes
     */
    public static String stripColor(String text) {
        if (text == null) return null;
        return ChatColor.stripColor(colorize(text));
    }

    /**
     * Returns the first Minecraft color code in a legacy text fragment.
     * Formatting codes such as bold are ignored, while both {@code &} and {@code §} prefixes
     * are accepted.
     *
     * @param text text containing legacy codes, or {@code null}
     * @return a normalized section-sign color code, or an empty string when none is present
     */
    public static String firstColorCode(String text) {
        if (text == null) return "";
        String translated = colorize(text);
        for (int index = 0; index + 1 < translated.length(); index++) {
            if (translated.charAt(index) != ChatColor.COLOR_CHAR) continue;
            ChatColor color = ChatColor.getByChar(translated.charAt(index + 1));
            if (color != null && color.isColor()) return color.toString();
        }
        return "";
    }

    /**
     * Converts every value in a collection while preserving its order.
     *
     * @param texts source values
     * @return immutable translated values
     */
    public static List<String> colorize(Collection<String> texts) {
        Objects.requireNonNull(texts, "texts");
        return texts.stream().map(LegacyText::colorize).toList();
    }
}
