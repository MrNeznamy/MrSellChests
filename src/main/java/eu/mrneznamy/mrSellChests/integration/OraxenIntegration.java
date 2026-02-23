package eu.mrneznamy.mrSellChests.integration;

import eu.mrneznamy.mrlibcore.utils.MrLibConsoleSayer;
import org.bukkit.inventory.ItemStack;

/**
 * Integration for the Oraxen custom items plugin.
 * Allows MrSellChests to identify Oraxen items by their string ID.
 *
 * Oraxen exposes its API through io.th0rgal.oraxen.api.OraxenItems.
 *
 * @author graffs444 (https://github.com/graffs444)
 */
public class OraxenIntegration {

    private boolean available = false;

    public OraxenIntegration() {
        try {
            Class.forName("io.th0rgal.oraxen.api.OraxenItems");
            available = true;
            MrLibConsoleSayer.MrSay_Success("Oraxen integration initialized successfully!");
        } catch (ClassNotFoundException e) {
            MrLibConsoleSayer.MrSay_Info("Oraxen not found - skipping integration");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Returns the Oraxen item ID for the given ItemStack, or null if it is not
     * an Oraxen item.
     */
    public String getItemId(ItemStack item) {
        if (!available || item == null) return null;
        try {
            Class<?> oraxenItemsClass = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
            // OraxenItems.getIdByItem(ItemStack) -> String | null
            java.lang.reflect.Method getIdByItem = oraxenItemsClass.getMethod("getIdByItem", ItemStack.class);
            Object result = getIdByItem.invoke(null, item);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns true if the given ItemStack is the Oraxen item identified by
     * {@code oraxenId}.
     */
    public boolean matchesId(ItemStack item, String oraxenId) {
        String id = getItemId(item);
        return id != null && id.equalsIgnoreCase(oraxenId);
    }

    /**
     * Builds an ItemStack for the given Oraxen item ID, or returns null if the
     * ID is not registered.
     */
    public ItemStack buildItem(String oraxenId) {
        if (!available) return null;
        try {
            Class<?> oraxenItemsClass = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
            // OraxenItems.getItemById(String) -> ItemBuilder | null
            java.lang.reflect.Method getItemById = oraxenItemsClass.getMethod("getItemById", String.class);
            Object itemBuilder = getItemById.invoke(null, oraxenId);
            if (itemBuilder == null) return null;
            // ItemBuilder.build() -> ItemStack
            java.lang.reflect.Method build = itemBuilder.getClass().getMethod("build");
            Object result = build.invoke(itemBuilder);
            return result instanceof ItemStack ? (ItemStack) result : null;
        } catch (Exception e) {
            return null;
        }
    }
}
