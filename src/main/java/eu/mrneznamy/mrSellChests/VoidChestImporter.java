package eu.mrneznamy.mrSellChests;

import eu.mrneznamy.mrlibcore.utils.MrLibColors;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class VoidChestImporter {

    private final MrSellChests plugin;

    public VoidChestImporter(MrSellChests plugin) {
        this.plugin = plugin;
    }

    public void importVoidChests(CommandSender sender) {
        File voidChestFolder = new File("plugins/VoidChest/voidchest");
        if (!voidChestFolder.exists() || !voidChestFolder.isDirectory()) {
            sender.sendMessage(MrLibColors.colorize("&cFolder plugins/VoidChest/voidchest not found!"));
            return;
        }

        File[] files = voidChestFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            sender.sendMessage(MrLibColors.colorize("&cNo .yml files found in plugins/VoidChest/voidchest!"));
            return;
        }

        FileConfiguration config = plugin.getConfig();
        ConfigurationSection sellChestsSection = config.getConfigurationSection("MrSellChests.SellChests");
        if (sellChestsSection == null) {
            sellChestsSection = config.createSection("MrSellChests.SellChests");
        }

        int count = 0;
        for (File file : files) {
            try {
                String fileName = file.getName().replace(".yml", "");
                
                String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                content = content.replaceAll("hologram:\\s*(true|false)", "hologram_enabled: $1");
                
                YamlConfiguration voidChestConfig = YamlConfiguration.loadConfiguration(new StringReader(content));

                String material = voidChestConfig.getString("Mechanics.block", "CHEST");
                int interval = voidChestConfig.getInt("Options.sell interval", 10);
                int collectTime = interval / 2;
                double booster = voidChestConfig.getDouble("Mechanics.booster", 1.0);
                
                boolean chargeEnabled = voidChestConfig.getBoolean("Options.charge.enabled", false);
                if (!chargeEnabled && voidChestConfig.contains("Options.charge") && voidChestConfig.isBoolean("Options.charge")) {
                     chargeEnabled = voidChestConfig.getBoolean("Options.charge");
                }
                
                int maxLinks = voidChestConfig.getInt("Options.links", 3);
                
                double chargePrice = voidChestConfig.getDouble("Options.charge.price", 0.0);
                int renewalTimeSeconds = voidChestConfig.getInt("Options.charge.renewal time", 3600);
                int maxTimeSeconds = voidChestConfig.getInt("Options.charge.max time", 86400);
                
                int maxTimeMinutes = (int) Math.ceil(maxTimeSeconds / 60.0);
                
                int perUpgradeSeconds = renewalTimeSeconds;
                
                String itemName = voidChestConfig.getString("Mechanics.item.name");
                if (itemName == null) {
                    itemName = voidChestConfig.getString("Options.item.name", "&eSell Chest");
                }
                
                List<String> itemLore = voidChestConfig.getStringList("Mechanics.item.lore");
                if (itemLore.isEmpty()) {
                    itemLore = voidChestConfig.getStringList("Options.item.lore");
                }
                
                List<String> newItemLore = new ArrayList<>();
                for (String line : itemLore) {
                    newItemLore.add(replacePlaceholders(line));
                }
                
                List<String> hologramLines = voidChestConfig.getStringList("Mechanics.hologram.text");
                if (hologramLines.isEmpty()) {
                    hologramLines = voidChestConfig.getStringList("Options.hologram.text");
                }
                
                List<String> newHologramLines = new ArrayList<>();
                for (String line : hologramLines) {
                    newHologramLines.add(replacePlaceholders(line));
                }

                String path = "MrSellChests.SellChests." + fileName;
                
                config.set(path + ".Chest.Size", 27);
                config.set(path + ".Chest.Interval", interval);
                config.set(path + ".Chest.CollectTime", collectTime);
                config.set(path + ".Chest.Material", material);
                config.set(path + ".Chest.Booster", booster);
                config.set(path + ".Chest.MaxLinks", maxLinks);
                config.set(path + ".Chest.InvitePlayers", 0); 
                config.set(path + ".Chest.ChunkLoader", false); 
                
                config.set(path + ".Chest.Charging.Enabled", chargeEnabled);
                if (chargeEnabled) {
                    config.set(path + ".Chest.Type", "CHARGING"); // Set Type explicitly as requested
                    config.set(path + ".Chest.Charging.PriceForCharge", chargePrice);
                    config.set(path + ".Chest.Charging.PerUpgrade", perUpgradeSeconds > 0 ? perUpgradeSeconds : 60);
                    config.set(path + ".Chest.Charging.MaxMinutes", maxTimeMinutes > 0 ? maxTimeMinutes : 1440);
                } else {
                    config.set(path + ".Chest.Type", "TIME"); // Default to TIME or SELL if not charging? Or just leave it?
                    // User showed "Type: TIME" in config, so it's a valid type.
                    // If we don't know, maybe default to "SELL" or "TIME".
                    // Let's set it to "TIME" for now if not charging, or just omit if unknown.
                    // Actually, if chargeEnabled is false, we probably shouldn't force a type unless we know it.
                    // But to be safe for non-charging chests to NOT be treated as charging, setting something else helps.
                    config.set(path + ".Chest.Type", "TIME");
                }
                
                config.set(path + ".Item.Name", itemName);
                config.set(path + ".Item.Lore", newItemLore);
                config.set(path + ".Item.Material", material);
                
                config.set(path + ".Hologram", newHologramLines);
                
                count++;
                sender.sendMessage(MrLibColors.colorize("&aImported " + fileName));
            } catch (Exception e) {
                sender.sendMessage(MrLibColors.colorize("&cFailed to import " + file.getName() + ": " + e.getMessage()));
                e.printStackTrace();
            }
        }

        plugin.saveConfig();
        
        if (sender instanceof org.bukkit.entity.Player) {
            ((org.bukkit.entity.Player) sender).performCommand("msc reload");
        } else {
            Bukkit.dispatchCommand(sender, "msc reload");
        }
        
        sender.sendMessage(MrLibColors.colorize("&aSuccessfully imported " + count + " VoidChests!"));
    }

    private String replacePlaceholders(String line) {
        if (line == null) return "";
        return line.replace("%owner%", "[PlayerName]")
                   .replace("%booster%", "[Booster]")
                   .replace("%money%", "[MoneyEarned]")
                   .replace("%items_sold%", "[ItemsSold]")
                   .replace("%items_purged%", "[DeletedItems]")
                   .replace("%items_stored%", "0") 
                   .replace("%timeleft%", "[Remaining]")
                   .replace("%charge_hologram%", "[ChargedFor]");
    }
}
