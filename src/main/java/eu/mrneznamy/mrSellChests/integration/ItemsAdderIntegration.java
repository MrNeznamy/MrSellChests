package eu.mrneznamy.mrSellChests.integration;

import eu.mrneznamy.mrlibcore.utils.MrLibConsoleSayer;
import org.bukkit.inventory.ItemStack;

/**
 * Integration for the ItemsAdder custom items plugin.
 * Allows MrSellChests to identify ItemsAdder items by their namespaced ID
 * (e.g. {@code namespace:item_name}).
 *
 * ItemsAdder exposes its API through
 * dev.lone.itemsadder.api.CustomStack.
 *
 * @author graffs444 (https://github.com/graffs444)
 */
public class ItemsAdderIntegration {

    private boolean available = false;

    public ItemsAdderIntegration() {
        try {
            Class.forName("dev.lone.itemsadder.api.CustomStack");
            available = true;
            MrLibConsoleSayer.MrSay_Success("ItemsAdder integration initialized successfully!");
        } catch (ClassNotFoundException e) {
            MrLibConsoleSayer.MrSay_Info("ItemsAdder not found - skipping integration");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Returns the ItemsAdder namespaced ID (e.g. {@code mypack:ruby}) for the
     * given ItemStack, or null if it is not an ItemsAdder item.
     */
    public String getItemId(ItemStack item) {
        if (!available || item == null) return null;
        try {
            // CustomStack.byItemStack(ItemStack) -> CustomStack | null
            Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            java.lang.reflect.Method byItemStack = customStackClass.getMethod("byItemStack", ItemStack.class);
            Object customStack = byItemStack.invoke(null, item);
            if (customStack == null) return null;
            // CustomStack.getId() -> String (namespaced id, e.g. "mypack:ruby")
            java.lang.reflect.Method getId = customStack.getClass().getMethod("getId");
            Object result = getId.invoke(customStack);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns true if the given ItemStack is the ItemsAdder item identified by
     * {@code itemsAdderId} (namespaced, e.g. {@code mypack:ruby}).
     */
    public boolean matchesId(ItemStack item, String itemsAdderId) {
        String id = getItemId(item);
        return id != null && id.equalsIgnoreCase(itemsAdderId);
    }

    /**
     * Builds an ItemStack for the given ItemsAdder namespaced ID, or returns
     * null if the ID is not registered.
     */
    public ItemStack buildItem(String itemsAdderId) {
        if (!available) return null;
        try {
            Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            // CustomStack.getInstance(String) -> CustomStack | null
            java.lang.reflect.Method getInstance = customStackClass.getMethod("getInstance", String.class);
            Object customStack = getInstance.invoke(null, itemsAdderId);
            if (customStack == null) return null;
            // CustomStack.getItemStack() -> ItemStack
            java.lang.reflect.Method getItemStack = customStack.getClass().getMethod("getItemStack");
            Object result = getItemStack.invoke(customStack);
            return result instanceof ItemStack ? (ItemStack) result : null;
        } catch (Exception e) {
            return null;
        }
    }
}
