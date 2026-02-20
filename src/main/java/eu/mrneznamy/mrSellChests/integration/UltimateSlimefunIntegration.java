package eu.mrneznamy.mrSellChests.integration;

import eu.mrneznamy.mrSellChests.MrSellChests;
import eu.mrneznamy.mrlibcore.utils.MrLibConsoleSayer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

public class UltimateSlimefunIntegration {
    private final MrSellChests plugin;
    private boolean isUltimateSlimefunAvailable = false;
    private Object slimefunAPI;
    private Object itemAPI;
    private Object sellChestsCategory;

    public UltimateSlimefunIntegration(MrSellChests plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {
        try {
            if (!this.plugin.getServer().getPluginManager().isPluginEnabled("MrUltimateSlimefun")) {
                MrLibConsoleSayer.MrSay_Info((String)"MrUltimateSlimefun not found - skipping integration");
                return false;
            }
            Class<?> slimefunAPIClass = Class.forName("eu.mrneznamy.mrultimateslimefun.api.SlimefunAPI");
            Class<?> itemAPIClass = Class.forName("eu.mrneznamy.mrultimateslimefun.api.ItemAPI");
            Class<?> slimefunItemClass = Class.forName("eu.mrneznamy.mrultimateslimefun.models.SlimefunItem");
            Class<?> itemCategoryClass = Class.forName("eu.mrneznamy.mrultimateslimefun.models.ItemCategory");
            Class<?> recipeTypeClass = Class.forName("eu.mrneznamy.mrultimateslimefun.models.RecipeType");
            this.itemAPI = slimefunAPIClass.getMethod("getItemAPI", new Class[0]).invoke(null, new Object[0]);
            this.createSellChestsCategory(itemCategoryClass);
            this.registerSellChestItems(slimefunItemClass, recipeTypeClass);
            this.isUltimateSlimefunAvailable = true;
            MrLibConsoleSayer.MrSay_Success((String)"Ultimate Slimefun integration initialized successfully!");
            return true;
        }
        catch (Exception e) {
            MrLibConsoleSayer.MrSay_Error((String)("Failed to initialize Ultimate Slimefun integration: " + e.getMessage()));
            return false;
        }
    }

    private void createSellChestsCategory(Class<?> itemCategoryClass) throws Exception {
        this.sellChestsCategory = itemCategoryClass.getConstructor(String.class, String.class, ItemStack.class, Integer.TYPE).newInstance("sell_chests", "\u00a76\u00a7lSell Chests", this.createCategoryDisplayItem(), 50);
        this.itemAPI.getClass().getMethod("registerCategory", itemCategoryClass).invoke(this.itemAPI, this.sellChestsCategory);
    }

    private ItemStack createCategoryDisplayItem() {
        ItemStack item = new ItemStack(Material.CHEST);
        item.getItemMeta().setDisplayName("\u00a76\u00a7lSell Chests");
        ArrayList<String> lore = new ArrayList<String>();
        lore.add("\u00a77Automatic selling chests");
        lore.add("\u00a77that sell items at intervals");
        lore.add("");
        lore.add("\u00a7eClick to browse sell chests!");
        item.getItemMeta().setLore(lore);
        return item;
    }

    private void registerSellChestItems(Class<?> slimefunItemClass, Class<?> recipeTypeClass) throws Exception {
        ConfigurationSection sellChestsSection = this.plugin.getConfig().getConfigurationSection("MrSellChests.SellChests");
        if (sellChestsSection == null) {
            MrLibConsoleSayer.MrSay_Warning((String)"No sell chests found in configuration!");
            return;
        }
        Set<String> chestTypes = sellChestsSection.getKeys(false);
        for (String chestType : chestTypes) {
            try {
                this.registerSellChestItem(chestType, slimefunItemClass, recipeTypeClass);
            }
            catch (Exception e) {
                MrLibConsoleSayer.MrSay_Error((String)("Failed to register sell chest type '" + chestType + "': " + e.getMessage()));
            }
        }
    }

    private void registerSellChestItem(String chestType, Class<?> slimefunItemClass, Class<?> recipeTypeClass) throws Exception {
        Material material;
        String configPath = "MrSellChests.SellChests." + chestType;
        ConfigurationSection chestConfig = this.plugin.getConfig().getConfigurationSection(configPath);
        if (chestConfig == null) {
            return;
        }
        String materialName = chestConfig.getString("Chest.Material", "CHEST");
        String displayName = chestConfig.getString("Item.Name", "\u00a76" + chestType + " Sell Chest");
        List lore = chestConfig.getStringList("Item.Lore");
        int interval = chestConfig.getInt("Chest.Interval", 10);
        double booster = chestConfig.getDouble("Chest.Booster", 1.0);
        int size = chestConfig.getInt("Chest.Size", 9);
        try {
            material = Material.valueOf((String)materialName.toUpperCase());
        }
        catch (IllegalArgumentException e) {
            material = Material.CHEST;
        }
        ItemStack displayItem = new ItemStack(material);
        displayItem.getItemMeta().setDisplayName(displayName);
        ArrayList<String> enhancedLore = new ArrayList<String>(lore);
        enhancedLore.add("");
        enhancedLore.add("\u00a77\u00a7lSell Chest Properties:");
        enhancedLore.add("\u00a77\u2022 Interval: \u00a7e" + interval + " seconds");
        enhancedLore.add("\u00a77\u2022 Booster: \u00a7e" + booster + "x");
        enhancedLore.add("\u00a77\u2022 Size: \u00a7e" + size + " slots");
        enhancedLore.add("");
        enhancedLore.add("\u00a76\u00a7lClick to get this sell chest!");
        displayItem.getItemMeta().setLore(enhancedLore);
        ItemStack[] recipe = this.createSellChestRecipe(material);
        Object recipeType = recipeTypeClass.getField("ENHANCED_CRAFTING_TABLE").get(null);
        Object slimefunItem = slimefunItemClass.getConstructor(String.class, String.class, ItemStack.class, Object.class, ItemStack[].class).newInstance("mrsellchests_" + chestType.toLowerCase(), "sell_chests", displayItem, recipeType, recipe);
        this.setSlimefunItemAction(slimefunItem, chestType);
        this.itemAPI.getClass().getMethod("registerItem", slimefunItemClass).invoke(this.itemAPI, slimefunItem);
        MrLibConsoleSayer.MrSay_Info((String)("Registered sell chest '" + chestType + "' in Ultimate Slimefun menu"));
    }

    private ItemStack[] createSellChestRecipe(Material chestMaterial) {
        ItemStack[] recipe = new ItemStack[]{new ItemStack(Material.IRON_INGOT), new ItemStack(Material.REDSTONE), new ItemStack(Material.IRON_INGOT), new ItemStack(Material.REDSTONE), new ItemStack(chestMaterial), new ItemStack(Material.REDSTONE), new ItemStack(Material.IRON_INGOT), new ItemStack(Material.REDSTONE), new ItemStack(Material.IRON_INGOT)};
        return recipe;
    }

    private void setSlimefunItemAction(Object slimefunItem, String chestType) throws Exception {
    }

    public boolean isAvailable() {
        return this.isUltimateSlimefunAvailable;
    }

    public void disable() {
        if (this.isUltimateSlimefunAvailable) {
            MrLibConsoleSayer.MrSay_Info((String)"Ultimate Slimefun integration disabled");
        }
    }
}

