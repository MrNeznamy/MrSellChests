package eu.mrneznamy.mrSellChests;

import eu.mrneznamy.mrSellChests.MrSellChests;
import eu.mrneznamy.mrlibcore.messages.MrLibMessage;
import eu.mrneznamy.mrlibcore.utils.MrLibColors;
import eu.mrneznamy.mrlibcore.utils.MrLibHelper;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.io.BukkitObjectOutputStream;

public class Commands
implements CommandExecutor {
    private final MrSellChests plugin;
    private FileConfiguration config;
    private MrLibMessage messageSystem;

    public Commands(MrSellChests plugin) {
        this.plugin = plugin;
        this.reloadConfigs();
    }

    public void reloadConfigs() {
        try {
            this.plugin.reloadConfiguration();
            this.config = this.plugin.getConfig();
            this.messageSystem = this.plugin.getMessageSystem();
        }
        catch (Exception e) {
            if (Bukkit.getConsoleSender() != null) {
                Bukkit.getConsoleSender().sendMessage("[MrSellChests] Reload failed: " + e.getMessage());
            }
            throw e;
        }
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                MrLibHelper.showPluginHelp((Player)((Player)sender), (String)"MrSellChests", (int)1);
            } else {
                sender.sendMessage(this.plugin.getMessage("command_usage"));
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("help")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(this.plugin.getMessage("help_players_only"));
                return true;
            }
            if (args.length == 1) {
                MrLibHelper.showPluginHelp((Player)((Player)sender), (String)"MrSellChests", (int)1);
            } else if (args.length == 2) {
                MrLibHelper.showPluginHelp((Player)((Player)sender), (String)args[1], (int)1);
            } else if (args.length == 3) {
                try {
                    int page = Integer.parseInt(args[2]);
                    MrLibHelper.showPluginHelp((Player)((Player)sender), (String)args[1], (int)page);
                }
                catch (NumberFormatException e) {
                    this.plugin.sendMessage((Player)sender, this.plugin.getMessage("invalid_page_number"));
                }
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("mrsellchests.reload")) {
                this.plugin.sendMessage((Player)sender, this.plugin.getMessage("no_permission"));
                return true;
            }
            try {
                this.reloadConfigs();
                this.plugin.reloadGuide();
                this.plugin.sendMessage((Player)sender, this.plugin.getMessage("reload_success"));
            }
            catch (Exception e) {
                String msg = this.plugin.getMessage("reload_failed", "&cReload failed: " + e.getMessage());
                if (sender instanceof Player) {
                    this.plugin.sendMessage((Player)sender, msg);
                }
                sender.sendMessage(msg);
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("give")) {
            Object msg;
            int amount;
            if (!sender.hasPermission("mrsellchests.give")) {
                this.plugin.sendMessage((Player)sender, this.plugin.getMessage("no_permission"));
                return true;
            }
            if (args.length < 4) {
                this.plugin.sendMessage((Player)sender, this.plugin.getMessage("give_usage"));
                return true;
            }
            Player target = Bukkit.getPlayer((String)args[1]);
            if (target == null) {
                this.plugin.sendMessage((Player)sender, this.plugin.getMessage("give_player_not_found"));
                return true;
            }
            String chestType = args[2];
            try {
                amount = Integer.parseInt(args[3]);
            }
            catch (NumberFormatException var9) {
                this.plugin.sendMessage((Player)sender, this.plugin.getMessage("give_invalid_amount"));
                return true;
            }
            if (!this.config.isConfigurationSection("MrSellChests.SellChests." + chestType) && !this.config.isConfigurationSection("SellChests." + chestType)) {
                this.plugin.sendMessage((Player)sender, this.plugin.getMessage("give_chest_type_not_found"));
                return true;
            }
            ItemStack item = this.createSellChestItem(chestType, amount);
            HashMap leftovers = target.getInventory().addItem(new ItemStack[]{item});
            if (!leftovers.isEmpty()) {
                for (Object obj : leftovers.values()) {
                    ItemStack left = (ItemStack)obj;
                    target.getWorld().dropItemNaturally(target.getLocation(), left);
                }
            }
            msg = (msg = this.plugin.getMessage("give_success")) != null ? ((String)msg).replace("{amount}", String.valueOf(amount)).replace("{chest}", chestType).replace("{player}", target.getName()) : "(!message!)&aD\u00e1no " + amount + "x " + chestType + " sell truhla hr\u00e1\u010di " + target.getName() + "!";
            this.plugin.sendMessage((Player)sender, (String)msg);
            return true;
        }
        if (args[0].equalsIgnoreCase("boost")) {
            long duration;
            double boost;
            if (!sender.hasPermission("mrsellchests.boost")) {
                this.plugin.sendMessage((Player)sender, this.plugin.getMessage("no_permission"));
                return true;
            }
            if (args.length >= 2 && args[1].equalsIgnoreCase("give")) {
                Object msg;
                HashMap leftovers;
                ItemStack voucher;
                ItemMeta meta;
                int amount;
                if (args.length < 5) {
                    this.plugin.sendMessage((Player)sender, this.plugin.getMessage("booster_give_usage"));
                    return true;
                }
                Player target = Bukkit.getPlayer((String)args[2]);
                if (target == null) {
                    this.plugin.sendMessage((Player)sender, this.plugin.getMessage("boost_player_not_found"));
                    return true;
                }
                String boosterId = args[3];
                try {
                    amount = Integer.parseInt(args[4]);
                }
                catch (NumberFormatException e) {
                    this.plugin.sendMessage((Player)sender, this.plugin.getMessage("booster_invalid_amount"));
                    return true;
                }
                File boostersFile = new File(this.plugin.getDataFolder(), "boosters.yml");
                if (!boostersFile.exists()) {
                    this.plugin.sendMessage((Player)sender, this.plugin.getMessage("boosters_yml_not_found"));
                    return true;
                }
                YamlConfiguration boosters = YamlConfiguration.loadConfiguration((File)boostersFile);
                if (!boosters.isConfigurationSection("Boosters." + boosterId)) {
                    this.plugin.sendMessage((Player)sender, this.plugin.getMessage("booster_not_found"));
                    return true;
                }
                ConfigurationSection booster = boosters.getConfigurationSection("Boosters." + boosterId);
                ConfigurationSection headSection = booster.getConfigurationSection("Head");
                ConfigurationSection itemSection = booster.getConfigurationSection("Item");
                if (itemSection == null) {
                    this.plugin.sendMessage((Player)sender, this.plugin.getMessage("booster_item_not_defined"));
                    return true;
                }
                String matName = itemSection.getString("Material", "PAPER");
                Material mat = Material.matchMaterial((String)matName);
                if (mat == null) {
                    mat = Material.PAPER;
                }
                if ((meta = (voucher = new ItemStack(mat, amount)).getItemMeta()) != null) {
                    Object voucherName;
                    if (itemSection.contains("CustomModelData") && !"NONE".equalsIgnoreCase(itemSection.getString("CustomModelData"))) {
                        try {
                            meta.setCustomModelData(Integer.valueOf(Integer.parseInt(itemSection.getString("CustomModelData"))));
                        }
                        catch (Exception exception) {
                            // empty catch block
                        }
                    }
                    if ((voucherName = this.plugin.getMessage("booster_voucher_name")) == null) {
                        voucherName = "&aBooster Voucher";
                    }
                    meta.setDisplayName(MrLibColors.colorize((String)itemSection.getString("Name", (String)voucherName)));
                    List<String> lore = itemSection.getStringList("Lore");
                    if (!lore.isEmpty()) {
                        for (int i = 0; i < lore.size(); ++i) {
                            lore.set(i, MrLibColors.colorize(lore.get(i)));
                        }
                        meta.setLore(lore);
                    }
                    if (itemSection.getBoolean("Glowing", false)) {
                        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                        meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
                    }
                    NamespacedKey boosterKey = new NamespacedKey((Plugin)this.plugin, "booster_voucher");
                    meta.getPersistentDataContainer().set(boosterKey, PersistentDataType.STRING, boosterId);
                    boolean stackable = headSection != null && headSection.getBoolean("Stackable", false);
                    NamespacedKey stackableKey = new NamespacedKey((Plugin)this.plugin, "booster_stackable");
                    meta.getPersistentDataContainer().set(stackableKey, PersistentDataType.INTEGER, stackable ? 1 : 0);
                    voucher.setItemMeta(meta);
                }
                if (!(leftovers = target.getInventory().addItem(new ItemStack[]{voucher})).isEmpty()) {
                    for (Object obj : leftovers.values()) {
                        ItemStack left = (ItemStack)obj;
                        target.getWorld().dropItemNaturally(target.getLocation(), left);
                    }
                }
                msg = (msg = this.plugin.getMessage("booster_give_success")) != null ? ((String)msg).replace("{amount}", String.valueOf(amount)).replace("{booster}", boosterId).replace("{player}", target.getName()) : "(!message!)&aGiven " + amount + "x booster voucher (" + boosterId + ") to " + target.getName() + "!";
                this.plugin.sendMessage((Player)sender, (String)msg);
                return true;
            }
            if (args.length < 4) {
                this.plugin.sendMessage((Player)sender, this.plugin.getMessage("boost_usage"));
                return true;
            }
            Player target = Bukkit.getPlayer((String)args[1]);
            if (target == null) {
                this.plugin.sendMessage((Player)sender, this.plugin.getMessage("boost_player_not_found"));
                return true;
            }
            try {
                boost = Double.parseDouble(args[2]);
                duration = Long.parseLong(args[3]);
            }
            catch (NumberFormatException e) {
                this.plugin.sendMessage((Player)sender, this.plugin.getMessage("boost_invalid"));
                return true;
            }
            long until = System.currentTimeMillis() + duration * 1000L;
            String playerUuid = target.getUniqueId().toString();
            this.plugin.getDatabaseManager().setPlayerBoost(playerUuid, boost, until);
            Object msg = this.plugin.getMessage("boost_set");
            msg = msg != null ? ((String)msg).replace("{boost}", String.valueOf(boost)).replace("{duration}", String.valueOf(duration)).replace("{count}", "all").replace("{player}", target.getName()) : "(!message!)&aSet temporary boost " + boost + "x for " + duration + "s to all chests of " + target.getName() + ".";
            this.plugin.sendMessage((Player)sender, (String)msg);
            if (target.isOnline()) {
                Object msg2 = this.plugin.getMessage("boost_received");
                msg2 = msg2 != null ? ((String)msg2).replace("{boost}", String.valueOf(boost)).replace("{duration}", String.valueOf(duration)) : "(!message!)&aYou received a temporary sell chest boost: " + boost + "x for " + duration + "s!";
                this.plugin.sendMessage(target, (String)msg2);
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("banned")) {
            if (!sender.hasPermission("mrsellchests.banned")) {
                this.plugin.sendMessage((Player)sender, this.plugin.getMessage("no_permission"));
                return true;
            }
            if (!(sender instanceof Player)) {
                this.plugin.sendMessage((Player)sender, this.plugin.getMessage("banned_item_usage"));
                return true;
            }
            Player player = (Player)sender;
            if (args.length < 3) {
                this.plugin.sendMessage(player, this.plugin.getMessage("banned_item_usage"));
                return true;
            }
            String category = args[1].toUpperCase();
            String action = args[2].toLowerCase();
            if (!(category.equals("SELL") || category.equals("COLLECT") || category.equals("INV"))) {
                this.plugin.sendMessage(player, this.plugin.getMessage("banned_item_invalid_category"));
                return true;
            }
            if (!action.equals("add") && !action.equals("remove")) {
                this.plugin.sendMessage(player, this.plugin.getMessage("banned_item_invalid_action"));
                return true;
            }
            ItemStack itemInHand = player.getInventory().getItemInMainHand();
            if (itemInHand == null || itemInHand.getType() == Material.AIR) {
                this.plugin.sendMessage(player, this.plugin.getMessage("banned_item_no_item"));
                return true;
            }
            return this.handleBannedItemCommand(player, category, action, itemInHand);
        }
        this.plugin.sendMessage((Player)sender, this.plugin.getMessage("unknown_subcommand"));
        return true;
    }

    private ItemStack createSellChestItem(String chestType, int amount) {
        ItemStack item;
        ItemMeta meta;
        String basePath = "MrSellChests.SellChests." + chestType;
        if (!this.config.isConfigurationSection(basePath)) {
            basePath = "SellChests." + chestType;
        }
        String path = basePath + ".Item.";
        String materialName = this.config.getString(basePath + ".Chest.Material", "CHEST");
        Material material = Material.matchMaterial((String)materialName);
        if (material == null) {
            material = Material.CHEST;
        }
        if ((meta = (item = new ItemStack(material, amount)).getItemMeta()) != null) {
            meta.setDisplayName(MrLibColors.colorize((String)this.config.getString(path + "Name", "Sell Chest")));
            List<String> lore = this.config.getStringList(path + "Lore");
            if (!lore.isEmpty()) {
                for (int i = 0; i < lore.size(); ++i) {
                    lore.set(i, MrLibColors.colorize(lore.get(i)));
                }
                meta.setLore(lore);
            }
            NamespacedKey key = new NamespacedKey((Plugin)this.plugin, "sellchest_type");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, chestType);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean handleBannedItemCommand(Player player, String category, String action, ItemStack item) {
        File bannedItemsFile = new File(this.plugin.getDataFolder(), "banned-items.yml");
        YamlConfiguration bannedConfig = YamlConfiguration.loadConfiguration((File)bannedItemsFile);
        String itemKey = this.generateItemKey(item);
        String path = this.getConfigPath(bannedConfig, category, itemKey);
        if (action.equals("add")) {
            if (bannedConfig.contains(path)) {
                Object msg = this.plugin.getMessage("banned_item_already_banned");
                msg = msg != null ? ((String)msg).replace("{category}", category) : "\u00a7cThis item is already banned for " + category + "!";
                this.plugin.sendMessage(player, (String)msg);
                return true;
            }
            bannedConfig.set(path + ".material", (Object)item.getType().name());
            bannedConfig.set(path + ".amount", (Object)item.getAmount());
            if (item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta.hasDisplayName()) {
                    bannedConfig.set(path + ".display_name", (Object)meta.getDisplayName());
                }
                if (meta.hasLore()) {
                    bannedConfig.set(path + ".lore", (Object)meta.getLore());
                }
                if (meta.hasCustomModelData()) {
                    bannedConfig.set(path + ".custom_model_data", (Object)meta.getCustomModelData());
                }
                if (meta.hasEnchants()) {
                    bannedConfig.set(path + ".enchantments", (Object)meta.getEnchants());
                }
                String nbtData = this.serializeItemToBase64(item);
                bannedConfig.set(path + ".nbt_data", (Object)nbtData);
            }
            try {
                bannedConfig.save(bannedItemsFile);
                this.plugin.reloadBannedItemsCache();
                Object msg = this.plugin.getMessage("banned_item_add_success");
                msg = msg != null ? ((String)msg).replace("{category}", category) : "\u00a7aItem successfully banned for " + category + "!";
                this.plugin.sendMessage(player, (String)msg);
                return true;
            }
            catch (Exception e) {
                Object msg = this.plugin.getMessage("banned_item_save_error");
                msg = msg != null ? ((String)msg).replace("{error}", e.getMessage()) : "\u00a7cFailed to save banned item: " + e.getMessage();
                this.plugin.sendMessage(player, (String)msg);
                return true;
            }
        }
        if (action.equals("remove")) {
            if (!bannedConfig.contains(path)) {
                Object msg = this.plugin.getMessage("banned_item_not_banned");
                msg = msg != null ? ((String)msg).replace("{category}", category) : "\u00a7cThis item is not banned for " + category + "!";
                this.plugin.sendMessage(player, (String)msg);
                return true;
            }
            bannedConfig.set(path, null);
            try {
                bannedConfig.save(bannedItemsFile);
                this.plugin.reloadBannedItemsCache();
                Object msg = this.plugin.getMessage("banned_item_remove_success");
                msg = msg != null ? ((String)msg).replace("{category}", category) : "\u00a7aItem successfully unbanned for " + category + "!";
                this.plugin.sendMessage(player, (String)msg);
                return true;
            }
            catch (Exception e) {
                Object msg = this.plugin.getMessage("banned_item_save_error");
                msg = msg != null ? ((String)msg).replace("{error}", e.getMessage()) : "\u00a7cFailed to save banned items: " + e.getMessage();
                this.plugin.sendMessage(player, (String)msg);
                return true;
            }
        }
        return true;
    }

    private String generateItemKey(ItemStack item) {
        StringBuilder key = new StringBuilder();
        key.append(item.getType().name());
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta.hasCustomModelData()) {
                key.append("_CMD").append(meta.getCustomModelData());
            }
            if (meta.hasDisplayName()) {
                key.append("_NAME").append(meta.getDisplayName().hashCode());
            }
            if (meta.hasLore()) {
                key.append("_LORE").append(meta.getLore().hashCode());
            }
            if (meta.hasEnchants()) {
                key.append("_ENCH").append(meta.getEnchants().hashCode());
            }
        }
        return key.toString().replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private String serializeItemToBase64(ItemStack item) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream((OutputStream)outputStream);
            dataOutput.writeObject((Object)item);
            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        }
        catch (Exception e) {
            return "";
        }
    }

    public static boolean isItemBanned(ItemStack item, String category, MrSellChests plugin) {
        YamlConfiguration bannedConfig = plugin.getBannedItemsConfig();
        if (bannedConfig == null) {
            return false;
        }
        ConfigurationSection categorySection = Commands.getCategorySection(bannedConfig, category);
        if (categorySection == null) {
            return false;
        }
        for (String itemKey : categorySection.getKeys(false)) {
            String currentNbtData;
            ConfigurationSection itemSection = categorySection.getConfigurationSection(itemKey);
            if (itemSection == null) continue;
            String storedNbtData = itemSection.getString("nbt_data");
            if (storedNbtData != null && !storedNbtData.isEmpty() && storedNbtData.equals(currentNbtData = Commands.serializeItemToBase64Static(item))) {
                return true;
            }
            String material = itemSection.getString("material");
            if (material == null || !material.equals(item.getType().name())) continue;
            if (itemSection.contains("custom_model_data")) {
                int storedCmd = itemSection.getInt("custom_model_data");
                if (!item.hasItemMeta() || !item.getItemMeta().hasCustomModelData() || storedCmd != item.getItemMeta().getCustomModelData()) continue;
                return true;
            }
            return true;
        }
        return false;
    }

    private String getConfigPath(YamlConfiguration config, String category, String itemKey) {
        if (config.isConfigurationSection("MrSellChests")) {
            String newFormatKey = Commands.mapCategoryToNewFormat(category);
            return "MrSellChests." + newFormatKey + "." + itemKey;
        }
        return category + "." + itemKey;
    }

    private static ConfigurationSection getCategorySection(YamlConfiguration config, String category) {
        String newFormatKey;
        ConfigurationSection mrSellChestsSection;
        if (config.isConfigurationSection("MrSellChests") && (mrSellChestsSection = config.getConfigurationSection("MrSellChests")).isConfigurationSection(newFormatKey = Commands.mapCategoryToNewFormat(category))) {
            return mrSellChestsSection.getConfigurationSection(newFormatKey);
        }
        if (config.isConfigurationSection(category)) {
            return config.getConfigurationSection(category);
        }
        return null;
    }

    private static String mapCategoryToNewFormat(String category) {
        switch (category.toUpperCase()) {
            case "SELL": {
                return "Banned-Sell-Items";
            }
            case "COLLECT": {
                return "Banned-Chunk-Collect-Items";
            }
            case "INV": {
                return "Banned-Inventory-Items";
            }
        }
        return category;
    }

    private static String mapCategoryToOldFormat(String category) {
        switch (category) {
            case "Banned-Sell-Items": {
                return "SELL";
            }
            case "Banned-Chunk-Collect-Items": {
                return "COLLECT";
            }
            case "Banned-Inventory-Items": {
                return "INV";
            }
        }
        return category;
    }

    private static String serializeItemToBase64Static(ItemStack item) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream((OutputStream)outputStream);
            dataOutput.writeObject((Object)item);
            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        }
        catch (Exception e) {
            return "";
        }
    }
}

