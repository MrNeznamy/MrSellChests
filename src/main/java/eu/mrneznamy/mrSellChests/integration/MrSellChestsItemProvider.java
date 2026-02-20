package eu.mrneznamy.mrSellChests.integration;

import eu.mrneznamy.mrSellChests.MrSellChests;
import eu.mrneznamy.mrultimateshop.api.CustomItemProvider;
import java.lang.invoke.CallSite;
import java.util.HashSet;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

public class MrSellChestsItemProvider
implements CustomItemProvider {
    private final MrSellChests plugin;

    public MrSellChestsItemProvider(MrSellChests plugin) {
        this.plugin = plugin;
    }

    public String getPluginName() {
        return "MrSellChests";
    }

    public ItemStack getCustomItem(String itemId) {
        if (!this.hasCustomItem(itemId)) {
            return null;
        }
        if (itemId.startsWith("booster_")) {
            String boosterId = itemId.substring(8);
            return this.plugin.getSellChestManager().createBoosterItem(boosterId);
        }
        return this.plugin.getSellChestManager().createSellChestItem(itemId);
    }

    public boolean hasCustomItem(String itemName) {
        if (itemName.startsWith("booster_")) {
            String boosterId = itemName.substring(8);
            ConfigurationSection boostersSection = this.plugin.getBoostersConfig().getConfigurationSection("Boosters");
            return boostersSection != null && boostersSection.contains(boosterId);
        }
        ConfigurationSection sellChestsSection = this.plugin.getConfig().getConfigurationSection("SellChests");
        if (sellChestsSection == null) {
            return false;
        }
        return sellChestsSection.contains(itemName);
    }

    public String[] getAvailableItems() {
        ConfigurationSection boostersSection;
        HashSet<String> allItems = new HashSet<String>();
        ConfigurationSection sellChestsSection = this.plugin.getConfig().getConfigurationSection("SellChests");
        if (sellChestsSection != null) {
            allItems.addAll(sellChestsSection.getKeys(false));
        }
        if ((boostersSection = this.plugin.getBoostersConfig().getConfigurationSection("Boosters")) != null) {
            for (String boosterId : boostersSection.getKeys(false)) {
                allItems.add("booster_" + boosterId);
            }
        }
        return allItems.toArray(new String[0]);
    }
}

