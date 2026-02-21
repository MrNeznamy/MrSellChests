package eu.mrneznamy.mrSellChests.integration;

import dev.rosewood.rosestacker.api.RoseStackerAPI;
import dev.rosewood.rosestacker.stack.StackedItem;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

public class RoseStackerIntegration implements StackerHandler {

    private final RoseStackerAPI api;

    public RoseStackerIntegration() {
        this.api = RoseStackerAPI.getInstance();
    }

    public int getItemStackSize(Item item) {
        if (api == null) return item.getItemStack().getAmount();
        
        StackedItem stackedItem = api.getStackedItem(item);
        if (stackedItem != null) {
            return stackedItem.getStackSize();
        }
        return item.getItemStack().getAmount();
    }

    public void setItemStackSize(Item item, int size) {
        if (api == null) {
            ItemStack stack = item.getItemStack();
            stack.setAmount(size);
            item.setItemStack(stack);
            return;
        }

        StackedItem stackedItem = api.getStackedItem(item);
        if (stackedItem != null) {
            if (size <= 0) {
                item.remove();
            } else {
                stackedItem.setStackSize(size);
            }
        } else {
            if (size <= 0) {
                item.remove();
            } else {
                ItemStack stack = item.getItemStack();
                stack.setAmount(size);
                item.setItemStack(stack);
            }
        }
    }
}
