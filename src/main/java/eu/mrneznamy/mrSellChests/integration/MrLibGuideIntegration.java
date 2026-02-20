package eu.mrneznamy.mrSellChests.integration;

import eu.mrneznamy.mrSellChests.MrSellChests;
import eu.mrneznamy.mrlibcore.MrLibVersionDetector;
import eu.mrneznamy.mrlibcore.guide.GuideCategory;
import eu.mrneznamy.mrlibcore.guide.GuideItem;
import eu.mrneznamy.mrlibcore.guide.MrLibGuideManager;
import eu.mrneznamy.mrlibcore.utils.MrLibColors;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public class MrLibGuideIntegration {
    private final MrSellChests plugin;

    public MrLibGuideIntegration(MrSellChests plugin) {
        this.plugin = plugin;
    }

    public void register() {
        if (!MrLibVersionDetector.isAtLeast((int)21)) {
            return;
        }
        MrLibGuideManager manager = MrLibGuideManager.getInstance();
        GuideCategory mainCategory = new GuideCategory("MrSellChests", "&aMrSellChests", new ItemStack(Material.CHEST));
        GuideCategory sellChestsCategory = new GuideCategory("sellchests", "&aSell Chests", new ItemStack(Material.TRAPPED_CHEST));
        this.addSellChests(sellChestsCategory);
        mainCategory.addSubCategory(sellChestsCategory);
        GuideCategory boostersCategory = new GuideCategory("boosters", "&aBoosters", new ItemStack(Material.EXPERIENCE_BOTTLE));
        this.addBoosters(boostersCategory);
        mainCategory.addSubCategory(boostersCategory);
        manager.registerPluginCategory("MrSellChests", mainCategory);
    }

    public void unregister() {
        if (!MrLibVersionDetector.isAtLeast((int)21)) {
            return;
        }
        MrLibGuideManager.getInstance().unregisterPluginCategory("MrSellChests");
    }

    private void addSellChests(GuideCategory category) {
        FileConfiguration config = this.plugin.getConfig();
        ConfigurationSection sellChestsSection = config.getConfigurationSection("MrSellChests.SellChests");
        if (sellChestsSection == null) {
            sellChestsSection = config.getConfigurationSection("SellChests");
        }
        if (sellChestsSection != null) {
            for (String chestType : sellChestsSection.getKeys(false)) {
                ItemStack chestItem = this.createSellChestItem(chestType);
                if (chestItem == null) continue;
                category.addItem(new GuideItem("sellchest_" + chestType, chestItem));
            }
        }
    }

    private ItemStack createSellChestItem(String chestType) {
        ItemStack item;
        ItemMeta meta;
        String basePath;
        FileConfiguration config = this.plugin.getConfig();
        if (!config.isConfigurationSection(basePath = "MrSellChests.SellChests." + chestType)) {
            basePath = "SellChests." + chestType;
        }
        String path = basePath + ".Item.";
        String materialName = config.getString(basePath + ".Chest.Material", "CHEST");
        Material material = Material.matchMaterial((String)materialName);
        if (material == null) {
            material = Material.CHEST;
        }
        if ((meta = (item = new ItemStack(material)).getItemMeta()) != null) {
            meta.setDisplayName(MrLibColors.colorize((String)config.getString(path + "Name", "Sell Chest")));
            List<String> lore = config.getStringList(path + "Lore");
            ArrayList<String> coloredLore = new ArrayList<String>();
            for (String line : lore) {
                coloredLore.add(MrLibColors.colorize((String)line));
            }
            meta.setLore(coloredLore);
            NamespacedKey key = new NamespacedKey((Plugin)this.plugin, "sellchest_type");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, chestType);
            item.setItemMeta(meta);
            return item;
        }
        return null;
    }

    private void addBoosters(GuideCategory category) {
        File boostersFile = new File(this.plugin.getDataFolder(), "boosters.yml");
        if (!boostersFile.exists()) {
            return;
        }
        YamlConfiguration boostersConfig = YamlConfiguration.loadConfiguration((File)boostersFile);
        ConfigurationSection boostersSection = boostersConfig.getConfigurationSection("Boosters");
        if (boostersSection != null) {
            for (String boosterId : boostersSection.getKeys(false)) {
                ItemStack boosterItem = this.createBoosterItem((FileConfiguration)boostersConfig, boosterId);
                if (boosterItem == null) continue;
                category.addItem(new GuideItem("booster_" + boosterId, boosterItem));
            }
        }
    }

    private ItemStack createBoosterItem(FileConfiguration config, String boosterId) {
        ItemStack item;
        ItemMeta meta;
        ConfigurationSection booster = config.getConfigurationSection("Boosters." + boosterId);
        if (booster == null) {
            return null;
        }
        ConfigurationSection headSection = booster.getConfigurationSection("Head");
        ConfigurationSection itemSection = booster.getConfigurationSection("Item");
        if (itemSection == null) {
            return null;
        }
        String matName = itemSection.getString("Material", "PAPER");
        Material mat = Material.matchMaterial((String)matName);
        if (mat == null) {
            mat = Material.PAPER;
        }
        if ((meta = (item = new ItemStack(mat)).getItemMeta()) != null) {
            String voucherName;
            if (itemSection.contains("CustomModelData") && !"NONE".equalsIgnoreCase(itemSection.getString("CustomModelData"))) {
                try {
                    meta.setCustomModelData(Integer.valueOf(Integer.parseInt(itemSection.getString("CustomModelData"))));
                }
                catch (NumberFormatException numberFormatException) {
                    // empty catch block
                }
            }
            if ((voucherName = this.plugin.getMessageFromConfig("booster_voucher_name")) == null) {
                voucherName = "&aBooster Voucher";
            }
            meta.setDisplayName(MrLibColors.colorize((String)itemSection.getString("Name", voucherName)));
            List<String> lore = itemSection.getStringList("Lore");
            ArrayList<String> coloredLore = new ArrayList<String>();
            for (String line : lore) {
                coloredLore.add(MrLibColors.colorize((String)line));
            }
            meta.setLore(coloredLore);
            if (itemSection.getBoolean("Glowing", false)) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
            }
            NamespacedKey boosterKey = new NamespacedKey((Plugin)this.plugin, "booster_voucher");
            meta.getPersistentDataContainer().set(boosterKey, PersistentDataType.STRING, boosterId);
            boolean stackable = headSection != null && headSection.getBoolean("Stackable", false);
            NamespacedKey stackableKey = new NamespacedKey((Plugin)this.plugin, "booster_stackable");
            meta.getPersistentDataContainer().set(stackableKey, PersistentDataType.INTEGER, stackable ? 1 : 0);
            item.setItemMeta(meta);
            return item;
        }
        return null;
    }
}

