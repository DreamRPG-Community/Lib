package cn.mythicland.lib.item;

import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Serializes Bukkit item stacks through Bukkit's configuration serialization contract.
 */
public final class ItemStackCodec {

    private ItemStackCodec() {
    }

    /**
     * Serializes an item stack into a detached map.
     *
     * @param itemStack the item stack to serialize
     * @return a mutable map containing the serialized item stack
     * @throws NullPointerException if {@code itemStack} is null
     */
    public static Map<String, Object> serialize(ItemStack itemStack) {
        Objects.requireNonNull(itemStack, "itemStack");
        return new LinkedHashMap<>(itemStack.serialize());
    }

    /**
     * Deserializes an item stack from a Bukkit configuration map.
     *
     * @param values the serialized item values
     * @return the decoded item stack
     * @throws NullPointerException if {@code values} is null
     * @throws IllegalArgumentException if Bukkit cannot decode the values
     */
    public static ItemStack deserialize(Map<String, Object> values) {
        Objects.requireNonNull(values, "values");
        return ItemStack.deserialize(new LinkedHashMap<>(values));
    }
}
