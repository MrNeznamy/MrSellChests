package eu.mrneznamy.mrSellChests.integration;

import org.bukkit.entity.Item;

public interface StackerHandler {
    int getItemStackSize(Item item);
    void setItemStackSize(Item item, int size);
}
