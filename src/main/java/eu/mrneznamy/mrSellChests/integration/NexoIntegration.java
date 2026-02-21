package eu.mrneznamy.mrSellChests.integration;

import eu.mrneznamy.mrlibcore.utils.MrLibConsoleSayer;
import org.bukkit.inventory.ItemStack;

/**
 * Integration for the Nexo custom items plugin.
 * Allows MrSellChests to identify Nexo items by their string ID.
 *
 * Nexo replaces Oraxen and provides the same functionality via
 * com.nexomc.nexo.api.NexoItems.
 *
 * @author graffs444 (https://github.com/graffs444)
 */
public class NexoIntegration {

    private boolean available = false;

    public NexoIntegration() {
        try {
            Class.forName("com.nexomc.nexo.api.NexoItems");
            available = true;
            MrLibConsoleSayer.MrSay_Success("Nexo integration initialized successfully!");
        } catch (ClassNotFoundException e) {
            MrLibConsoleSayer.MrSay_Info("Nexo not found - skipping integration");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Returns the Nexo item ID for the given ItemStack, or null if it is not a
     * Nexo item.
     */
    public String getItemId(ItemStack item) {
        if (!available || item == null) return null;
        try {
            // com.nexomc.nexo.api.NexoItems.idFromItem(ItemStack) -> String | null
            Class<?> nexoItemsClass = Class.forName("com.nexomc.nexo.api.NexoItems");
            java.lang.reflect.Method idFromItem = nexoItemsClass.getMethod("idFromItem", ItemStack.class);
            Object result = idFromItem.invoke(null, item);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns true if the given ItemStack is the Nexo item identified by
     * {@code nexoId}.
     */
    public boolean matchesId(ItemStack item, String nexoId) {
        String id = getItemId(item);
        return id != null && id.equalsIgnoreCase(nexoId);
    }

    /**
     * Builds an ItemStack for the given Nexo item ID, or returns null if the
     * ID is not registered.
     */
    public ItemStack buildItem(String nexoId) {
        if (!available) return null;
        try {
            Class<?> nexoItemsClass = Class.forName("com.nexomc.nexo.api.NexoItems");
            // NexoItems.itemFromId(String) -> NexoItem | null
            java.lang.reflect.Method itemFromId = nexoItemsClass.getMethod("itemFromId", String.class);
            Object nexoItem = itemFromId.invoke(null, nexoId);
            if (nexoItem == null) return null;
            // NexoItem.build() -> ItemStack
            java.lang.reflect.Method build = nexoItem.getClass().getMethod("build");
            Object result = build.invoke(nexoItem);
            return result instanceof ItemStack ? (ItemStack) result : null;
        } catch (Exception e) {
            return null;
        }
    }
}
