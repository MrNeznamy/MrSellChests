package eu.mrneznamy.mrSellChests;

import eu.mrneznamy.mrSellChests.Commands;
import eu.mrneznamy.mrSellChests.MrSellChests;
import eu.mrneznamy.mrSellChests.database.SellChestsDatabaseManager;
import eu.mrneznamy.mrlibcore.utils.MrLibColors;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class SellChestListener
implements Listener {
    private final MrSellChests plugin;
    private final SellChestsDatabaseManager dbManager;
    private final Map<UUID, String> linkingPlayers;
    private BukkitTask hologramUpdateTask;
    private final Map<String, List<String>> hologramCache = new HashMap<String, List<String>>();
    private final Object hologramLock = new Object();
    private final Map<String, Boolean> hologramVisibility = new HashMap<String, Boolean>();
    private final Map<String, Boolean> hologramManuallyDisabled = new HashMap<String, Boolean>();
    private static final double HOLOGRAM_VIEW_DISTANCE = 20.0;
    private final Map<UUID, String> inviteChestKey = new HashMap<UUID, String>();
    private final Map<UUID, String> invitePlayerName = new HashMap<UUID, String>();
    private final Map<UUID, SellChestInviteStep> inviteStep = new HashMap<UUID, SellChestInviteStep>();
    private final Map<UUID, String> editChestKey = new HashMap<UUID, String>();
    private final Map<UUID, String> editPlayerUUID = new HashMap<UUID, String>();
    private final Map<UUID, SellChestEditStep> editStep = new HashMap<UUID, SellChestEditStep>();

    public SellChestListener(MrSellChests plugin) {
        this.plugin = plugin;
        this.dbManager = plugin.getDatabaseManager();
        this.linkingPlayers = new HashMap<UUID, String>();
    }

    public void startHologramUpdateTask() {
        if (this.hologramUpdateTask != null) {
            this.hologramUpdateTask.cancel();
            this.hologramUpdateTask = null;
        }
        if (this.plugin.getConfig().getBoolean("MrSellChests.Holograms.Enabled", true)) {
            int updateInterval = this.plugin.getConfig().getInt("MrSellChests.Holograms.UpdateInterval", 20);
            this.hologramUpdateTask = Bukkit.getScheduler().runTaskTimer((Plugin)this.plugin, () -> this.updateAllHolograms(), 0L, (long)updateInterval);
        }
    }

    private void removeHologram(String holoName) {
        Object object;
        Object object2 = object = this.hologramLock;
        synchronized (object2) {
            this.plugin.getHologramManager().deleteHologram(holoName);
            this.hologramCache.remove(holoName);
        }
    }

    private void cleanNearbyTextDisplays(Location chestLoc) {
        if (chestLoc == null || chestLoc.getWorld() == null) {
            return;
        }
        for (Entity entity : chestLoc.getWorld().getNearbyEntities(chestLoc, 8.0, 8.0, 8.0)) {
            if (!entity.getType().name().equals("TEXT_DISPLAY")) continue;
            entity.remove();
        }
    }

    private void clearHologramCache() {
        Object object;
        Object object2 = object = this.hologramLock;
        synchronized (object2) {
            this.hologramCache.clear();
        }
    }

    private void updateAllHolograms() {
        Object object;
        if (!this.plugin.getConfig().getBoolean("MrSellChests.Holograms.Enabled", true)) {
            return;
        }
        Object object2 = object = this.hologramLock;
        synchronized (object2) {
            try {
                for (String key : this.dbManager.getAllChestKeys()) {
                    try {
                        List<String> cachedLines;
                        String chestType;
                        String cleanKey = key.endsWith(":") ? key.substring(0, key.length() - 1) : key;
                        String[] parts = cleanKey.split(":");
                        if (parts.length != 4) continue;
                        String holoName = "sellchest_" + parts[0] + "_" + parts[1] + "_" + parts[2] + "_" + parts[3];
                        if (!this.dbManager.getChestHologramEnabled(key)) {
                            this.hologramManuallyDisabled.put(holoName, true);
                            if (!this.plugin.getHologramManager().hologramExists(holoName)) continue;
                            this.removeHologram(holoName);
                            this.hologramVisibility.put(holoName, false);
                            continue;
                        }
                        this.hologramManuallyDisabled.remove(holoName);
                        int x = Integer.parseInt(parts[1]);
                        int y = Integer.parseInt(parts[2]);
                        int z = Integer.parseInt(parts[3]);
                        Location chestLoc = new Location(this.plugin.getServer().getWorld(parts[0]), (double)x + 0.5, (double)y + 0.5, (double)z + 0.5);
                        boolean playerNearby = false;
                        if (chestLoc.getWorld() != null) {
                            for (Player player : chestLoc.getWorld().getPlayers()) {
                                if (!(player.getLocation().distance(chestLoc) <= 20.0)) continue;
                                playerNearby = true;
                                break;
                            }
                        }
                        Boolean currentlyVisible = this.hologramVisibility.get(holoName);
                        boolean hologramExists = this.plugin.getHologramManager().hologramExists(holoName);
                        if (!playerNearby && hologramExists) {
                            this.removeHologram(holoName);
                            this.hologramVisibility.put(holoName, false);
                            continue;
                        }
                        if (!playerNearby || (chestType = this.dbManager.getChestType(key)) == null) continue;
                        List<String> hologramLines = this.plugin.getConfig().getStringList("MrSellChests.SellChests." + chestType + ".Hologram");
                        if (hologramLines.isEmpty()) {
                            hologramLines = this.plugin.getConfig().getStringList("SellChests." + chestType + ".Hologram");
                        }
                        if (hologramLines.isEmpty()) continue;
                        String ownerName = this.dbManager.getChestOwner(key);
                        if (ownerName == null) {
                            ownerName = "Unknown";
                        }
                        double boosterValue = this.plugin.getSellChestManager().getTotalBooster(key);
                        long boostTime = this.plugin.getSellChestManager().getBoostTimeLeft(key);
                        String booster = String.format("%.2f", boosterValue);
                        if (boostTime > 0L) {
                            String timeMsg = this.plugin.getMessage("boost_time_left");
                            timeMsg = timeMsg != null ? timeMsg.replace("[Time]", String.valueOf(boostTime)) : "&7Boost time left: " + boostTime;
                            booster = booster + "x " + timeMsg;
                        }
                        int itemsSold = this.dbManager.getItemsSold(key);
                        int deletedItems = this.dbManager.getDeletedItems(key);
                        double moneyEarned = this.dbManager.getMoneyEarned(key);
                        int remainingSeconds = this.plugin.getSellChestManager().getRemainingSeconds(key);
                        int chargedMinutes = this.dbManager.getChestChargingMinutes(key);
                        ArrayList<String> updatedLines = new ArrayList<String>();
                        for (String line : hologramLines) {
                            String updatedLine = line.replace("[PlayerName]", ownerName).replace("[Booster]", booster).replace("[ItemsSold]", String.valueOf(itemsSold)).replace("[DeletedItems]", String.valueOf(deletedItems)).replace("[MoneyEarned]", String.format("%.2f", moneyEarned)).replace("[Interval]", String.valueOf(remainingSeconds)).replace("[Remaining]", String.valueOf(remainingSeconds)).replace("[ChargedFor]", String.valueOf(chargedMinutes));
                            updatedLines.add(MrLibColors.colorize(updatedLine));
                        }
                        if (playerNearby && !hologramExists) {
                            this.hologramVisibility.put(holoName, true);
                            this.cleanNearbyTextDisplays(chestLoc);
                            try {
                                double yOffset = "MrLibCore-TextDisplay".equals(this.plugin.getHologramManager().getActiveProviderName()) ? 4.5 : 1.0;
                                double d = yOffset;
                                Location holoLoc = new Location(this.plugin.getServer().getWorld(parts[0]), (double)x + 0.5, (double)y + yOffset, (double)z + 0.5);
                                if (holoLoc.getWorld() == null) continue;
                                this.plugin.getHologramManager().createHologram(holoName, holoLoc, updatedLines);
                                Object object3 = object = this.hologramLock;
                                synchronized (object3) {
                                    this.hologramCache.put(holoName, new ArrayList(updatedLines));
                                }
                            }
                            catch (Exception yOffset) {
                                // empty catch block
                            }
                        }
                        boolean needsUpdate = (cachedLines = this.hologramCache.get(holoName)) == null || !cachedLines.equals(updatedLines);
                        boolean bl = needsUpdate;
                        if (!needsUpdate) continue;
                        this.hologramCache.put(holoName, new ArrayList(updatedLines));
                        if (this.plugin.getHologramManager().hologramExists(holoName)) {
                            this.plugin.getHologramManager().updateHologram(holoName, updatedLines);
                            continue;
                        }
                        this.cleanNearbyTextDisplays(chestLoc);
                        try {
                            double yOffset = "MrLibCore-TextDisplay".equals(this.plugin.getHologramManager().getActiveProviderName()) ? 4.5 : 1.0;
                            double d = yOffset;
                            Location holoLoc = new Location(this.plugin.getServer().getWorld(parts[0]), (double)x + 0.5, (double)y + yOffset, (double)z + 0.5);
                            if (holoLoc.getWorld() == null) continue;
                            this.plugin.getHologramManager().createHologram(holoName, holoLoc, updatedLines);
                        }
                        catch (Exception exception) {
                        }
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if ("MrLibCore-Entity".equals(this.plugin.getHologramManager().getActiveProviderName())) {
                    this.plugin.getHologramManager().forceUpdateAllEntityHolograms();
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemMeta meta;
        ItemStack item = event.getItemInHand();
        if (item != null && item.getType() != Material.AIR && (meta = item.getItemMeta()) != null) {
            NamespacedKey key = new NamespacedKey((Plugin)this.plugin, "sellchest_type");
            PersistentDataContainer container = meta.getPersistentDataContainer();
            if (container.has(key, PersistentDataType.STRING)) {
                String basePath;
                String chestType = (String)container.get(key, PersistentDataType.STRING);
                FileConfiguration config = this.plugin.getConfig();
                if (!config.isConfigurationSection(basePath = "MrSellChests.SellChests." + chestType)) {
                    basePath = "SellChests." + chestType;
                }
                if (config.isConfigurationSection(basePath)) {
                    Player player = event.getPlayer();
                    if (!player.hasPermission("mrsellchests.place." + chestType)) {
                        event.setCancelled(true);
                        this.plugin.sendMessage(player, this.plugin.getMessage("no_permission_place"));
                    } else {
                        List<String> hologramLines;
                        int playerLimit = 0;
                        for (int i = 100; i >= 1; --i) {
                            if (!player.hasPermission("mrsellchests.limit." + i)) continue;
                            playerLimit = i;
                            break;
                        }
                        Block block = event.getBlockPlaced();
                        File dataFile = new File(this.plugin.getDataFolder(), "data.yml");
                        YamlConfiguration data = YamlConfiguration.loadConfiguration((File)dataFile);
                        for (String existingKey : data.getKeys(false)) {
                            String[] parts = existingKey.split(":");
                            if (parts.length != 4) continue;
                            String world = parts[0];
                            int x = Integer.parseInt(parts[1]);
                            int y = Integer.parseInt(parts[2]);
                            int z = Integer.parseInt(parts[3]);
                            if (!world.equals(block.getWorld().getName()) || x >> 4 != block.getX() >> 4 || z >> 4 != block.getZ() >> 4) continue;
                            event.setCancelled(true);
                            this.plugin.sendMessage(event.getPlayer(), this.plugin.getMessage("chest_same_chunk"));
                            return;
                        }
                        this.saveSellChest(block, chestType, player.getName());
                        String chestPath = basePath + ".Chest";
                        String type = config.getString(chestPath + ".Type");
                        if ("TIME".equals(type) || "CHARGING".equals(type)) {
                            String var10000 = block.getWorld().getName();
                            String chestKey = var10000 + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
                            this.plugin.getSellChestManager().registerChestInventory(chestKey, chestType);
                        }
                        if (this.plugin.getConfig().getBoolean("MrSellChests.Holograms.Enabled", true) && !(hologramLines = this.plugin.getConfig().getStringList(basePath + ".Hologram")).isEmpty()) {
                            ArrayList<String> updatedLines = new ArrayList<String>();
                            int interval = this.plugin.getConfig().getInt(basePath + ".Chest.Interval", 10);
                            for (String line : hologramLines) {
                                line = line.replace("[PlayerName]", player.getName()).replace("[Booster]", "1.0").replace("[ItemsSold]", "0").replace("[DeletedItems]", "0").replace("[MoneyEarned]", "0.00").replace("[Interval]", String.valueOf(interval)).replace("[Remaining]", String.valueOf(interval));
                                updatedLines.add(MrLibColors.colorize((String)line));
                            }
                            String var40 = block.getWorld().getName();
                            String holoName = "sellchest_" + var40 + "_" + block.getX() + "_" + block.getY() + "_" + block.getZ();
                            this.cleanNearbyTextDisplays(block.getLocation().add(0.5, 0.5, 0.5));
                            double yOffset = "MrLibCore-TextDisplay".equals(this.plugin.getHologramManager().getActiveProviderName()) ? 4.5 : 1.0;
                            this.plugin.getHologramManager().createHologram(holoName, block.getLocation().add(0.5, yOffset, 0.5), updatedLines);
                        }
                        this.plugin.sendMessage(player, this.plugin.getMessage("chest_created"));
                    }
                }
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        String key = loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
        try {
            if (this.dbManager.chestExists(key)) {
                Player player = event.getPlayer();
                String ownerUUID = this.dbManager.getChestPlayerUuid(key);
                if (!player.getUniqueId().toString().equals(ownerUUID) && !player.hasPermission("mrsellchests.destroy.others")) {
                    event.setCancelled(true);
                    this.plugin.sendMessage(player, this.plugin.getMessage("no_chest_destroy"));
                } else {
                    String chestType = this.dbManager.getChestType(key);
                    if (chestType != null) {
                        event.setDropItems(false);
                        ItemStack sellChest = this.plugin.getSellChestManager().createSellChestItem(chestType);
                        loc.getWorld().dropItemNaturally(loc, sellChest);
                        String holoName = "sellchest_" + loc.getWorld().getName() + "_" + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
                        this.plugin.getHologramManager().deleteHologram(holoName);
                        this.plugin.getSellChestManager().removeChest(key);
                        this.dbManager.deleteChest(key);
                        this.plugin.sendMessage(player, this.plugin.getMessage("chest_destroyed"));
                    }
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        long until = 0L;
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            NamespacedKey boosterKey = new NamespacedKey((Plugin)this.plugin, "booster_voucher");
            if (meta.getPersistentDataContainer().has(boosterKey, PersistentDataType.STRING)) {
                ConfigurationSection booster;
                String boosterId = (String)meta.getPersistentDataContainer().get(boosterKey, PersistentDataType.STRING);
                NamespacedKey stackableKey = new NamespacedKey((Plugin)this.plugin, "booster_stackable");
                boolean stackable = meta.getPersistentDataContainer().has(stackableKey, PersistentDataType.INTEGER) && (Integer)meta.getPersistentDataContainer().get(stackableKey, PersistentDataType.INTEGER) == 1;
                boolean bl = stackable;
                FileConfiguration boostersConfig = this.dbManager.getBoostersYaml();

                if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) && boostersConfig != null && boostersConfig.isConfigurationSection("Boosters." + boosterId) && (booster = boostersConfig.getConfigurationSection("Boosters." + boosterId + ".Head")) != null) {
                    double boost = booster.getDouble("Boost", 1.0);
                    long duration = booster.getLong("Duration", 60L);
                    long now = System.currentTimeMillis();
                    String playerUuid = player.getUniqueId().toString();
                    boolean hasActive = false;
                    double currentBoost = this.dbManager.getPlayerBoostValue(player.getUniqueId());
                    long currentUntil = this.dbManager.getPlayerBoostUntil(player.getUniqueId());
                    if (currentBoost > 0.0 && currentUntil > now) {
                        hasActive = true;
                        currentBoost = (double)Math.round(currentBoost * 100.0) / 100.0;
                    }
                    double roundedBoost = (double)Math.round(boost * 100.0) / 100.0;
                    if (stackable) {
                        if (hasActive) {
                            if (!(Math.abs(currentBoost - roundedBoost) < 1.0E-4)) {
                                String msg = this.plugin.getMessage("booster_already_active");
                                this.plugin.sendMessage(player, msg);
                                event.setCancelled(true);
                                return;
                            }
                            long newUntil = currentUntil + duration * 1000L;
                            this.dbManager.setPlayerBoost(playerUuid, roundedBoost, newUntil);
                        } else {
                            until = now + duration * 1000L;
                            this.dbManager.setPlayerBoost(playerUuid, roundedBoost, until);
                        }
                    } else {
                        if (hasActive && currentUntil > now) {
                            String msg = this.plugin.getMessage("booster_already_active");
                            this.plugin.sendMessage(player, msg);
                            event.setCancelled(true);
                            return;
                        }
                        until = now + duration * 1000L;
                        this.dbManager.setPlayerBoost(playerUuid, roundedBoost, until);
                    }
                    if (item.getAmount() > 1) {
                        item.setAmount(item.getAmount() - 1);
                    } else {
                        player.getInventory().setItem(event.getHand() == EquipmentSlot.HAND ? player.getInventory().getHeldItemSlot() : player.getInventory().getHeldItemSlot(), (ItemStack)null);
                    }
                    Object msg = this.plugin.getMessage("booster_activated");
                    msg = msg != null ? ((String)msg).replace("{boost}", String.valueOf(boost)).replace("{duration}", String.valueOf(duration)) : "&aBooster activated! Boost: " + boost + "x for " + duration + " seconds!";
                    this.plugin.sendMessage(player, (String)msg);
                    event.setCancelled(true);
                    return;
                }
            }
        }
        for (String existingKey : this.dbManager.getAllChestKeys()) {
            if (!this.dbManager.getChestLinking(existingKey)) continue;
            event.setCancelled(true);
            if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) {
                if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
                    Object message;
                    Block block = event.getClickedBlock();
                    String var10000 = block.getWorld().getName();
                    String clickedKey = var10000 + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
                    if (this.dbManager.chestExists(clickedKey)) {
                        this.plugin.sendMessage(player, this.plugin.getMessage("chest_link_sell_chest"));
                        return;
                    }
                    if (!(block.getState() instanceof Container)) {
                        this.plugin.sendMessage(player, this.plugin.getMessage("chest_link_invalid"));
                        return;
                    }
                    String chestType = this.dbManager.getChestType(existingKey);
                    int maxLinks = this.plugin.getConfig().getInt("MrSellChests.SellChests." + chestType + ".Chest.MaxLinks", 1);
                    List<String> linkedChests = this.dbManager.getLinkedChests(existingKey);
                    if (linkedChests == null) linkedChests = new ArrayList<>();
                    
                    if (linkedChests.size() >= maxLinks) {
                        Object message2 = this.plugin.getMessage("chest_link_max");
                        message2 = message2 != null ? ((String)message2).replace("{current}", String.valueOf(linkedChests.size())).replace("{max}", String.valueOf(maxLinks)) : "&cYou have reached the maximum number of linked chests! Current: " + linkedChests.size() + ", Max: " + maxLinks;
                        this.plugin.sendMessage(player, (String)message2);
                        this.dbManager.setChestLinking(existingKey, false);
                        return;
                    }
                    var10000 = block.getWorld().getName();
                    String chestLoc = var10000 + "," + block.getX() + "," + block.getY() + "," + block.getZ();
                    if (!linkedChests.contains(chestLoc)) {
                        linkedChests.add(chestLoc);
                        this.dbManager.setLinkedChests(existingKey, linkedChests);
                    }
                    message = (message = this.plugin.getMessage("chest_link_success")) != null ? ((String)message).replace("{current}", String.valueOf(linkedChests.size())).replace("{max}", String.valueOf(maxLinks)) : "&aChest linked successfully! Current links: " + linkedChests.size() + "/" + maxLinks;
                    this.plugin.sendMessage(player, (String)message);
                    this.dbManager.setChestLinking(existingKey, false);
                    return;
                }
                return;
            }
            this.dbManager.setChestLinking(existingKey, false);
            this.plugin.sendMessage(player, this.plugin.getMessage("chest_link_cancelled"));
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Block block = event.getClickedBlock();
            String key = block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
            try {
                String chestType;
                if (this.dbManager.chestExists(key) && (chestType = this.dbManager.getChestType(key)) != null && chestType != null) {
                    String ownerUUID;
                    String playerUUID = player.getUniqueId().toString();
                    if (!playerUUID.equals(ownerUUID = this.dbManager.getChestPlayerUuid(key)) && !player.hasPermission("mrsellchests.open.others")) {
                        event.setCancelled(true);
                        this.plugin.sendMessage(player, this.plugin.getMessage("no_chest_access"));
                        return;
                    }
                    if (event.getPlayer().isSneaking()) {
                        ItemStack itemInHand = player.getInventory().getItemInMainHand();
                        if (itemInHand != null && itemInHand.getType() == Material.HOPPER) {
                            return;
                        }
                        event.setCancelled(true);
                        Inventory inv = this.plugin.getSellChestManager().getChestInventory(key);
                        if (inv != null) {
                            player.openInventory(inv);
                        }
                    } else {
                        event.setCancelled(true);
                        this.plugin.getSellChestManager().openSettingsMenu(player, key);
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void purgeStraySellChestHolograms() {
        try {
            boolean removed = false;
            for (String key : this.dbManager.getAllChestKeys()) {
                String cleanKey = key.endsWith(":") ? key.substring(0, key.length() - 1) : key;
                String[] parts = cleanKey.split(":");
                if (parts.length != 4) continue;
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);
                Location chestLoc = new Location(Bukkit.getWorld((String)parts[0]), (double)x + 0.5, (double)y + 0.5, (double)z + 0.5);
                if (chestLoc.getWorld() == null) continue;
                for (Entity entity : chestLoc.getWorld().getNearbyEntities(chestLoc, 8.0, 8.0, 8.0)) {
                    if (!entity.getType().name().equals("TEXT_DISPLAY")) continue;
                    entity.remove();
                }
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    private void saveSellChest(Block block, String chestType, String owner) {
        try {
            String key = block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
            Player placer = Bukkit.getPlayerExact((String)owner);
            String playerUUID = placer != null ? placer.getUniqueId().toString() : null;
            this.dbManager.createChest(key, owner, playerUUID, block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), chestType);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void removeAllSellChestHolograms() {
        for (String key : this.dbManager.getAllChestKeys()) {
            String cleanKey = key.endsWith(":") ? key.substring(0, key.length() - 1) : key;
            String[] parts = cleanKey.split(":");
            if (parts.length != 4) continue;
            String holoName = "sellchest_" + parts[0] + "_" + parts[1] + "_" + parts[2] + "_" + parts[3];
            this.plugin.getHologramManager().deleteHologram(holoName);
        }
        this.clearHologramCache();
        this.purgeStraySellChestHolograms();
    }

    public void restoreAllSellChestHolograms() {
        if (!this.plugin.getConfig().getBoolean("MrSellChests.Holograms.Enabled", true)) {
            return;
        }
        for (String key : this.dbManager.getAllChestKeys()) {
            try {
                Object object;
                String chestType;
                String[] parts;
                if (!this.dbManager.getChestHologramEnabled(key) || (parts = key.split(":")).length != 4 || (chestType = this.dbManager.getChestType(key)) == null) continue;
                List<String> hologramLines = this.plugin.getConfig().getStringList("MrSellChests.SellChests." + chestType + ".Hologram");
                if (hologramLines.isEmpty()) {
                    hologramLines = this.plugin.getConfig().getStringList("SellChests." + chestType + ".Hologram");
                }
                if (hologramLines.isEmpty()) continue;
                String ownerName = this.dbManager.getChestOwner(key);
                if (ownerName == null) {
                    ownerName = "Unknown";
                }
                double boosterValue = this.plugin.getSellChestManager().getTotalBooster(key);
                long boostTime = this.plugin.getSellChestManager().getBoostTimeLeft(key);
                String booster = String.format("%.2f", boosterValue);
                if (boostTime > 0L) {
                    String timeMsg = this.plugin.getMessage("boost_time_left");
                    timeMsg = timeMsg != null ? timeMsg.replace("[Time]", String.valueOf(boostTime)) : "&7Boost time left: " + boostTime;
                    booster = booster + " " + timeMsg;
                }
                int itemsSold = this.dbManager.getItemsSold(key);
                int deletedItems = this.dbManager.getDeletedItems(key);
                double moneyEarned = this.dbManager.getMoneyEarned(key);
                int remainingSeconds = this.plugin.getSellChestManager().getRemainingSeconds(key);
                int chargedMinutes = this.dbManager.getChestChargingMinutes(key);
                ArrayList<String> updatedLines = new ArrayList<String>();
                for (String line : hologramLines) {
                    String updatedLine = line.replace("[PlayerName]", ownerName).replace("[Booster]", booster).replace("[ItemsSold]", String.valueOf(itemsSold)).replace("[DeletedItems]", String.valueOf(deletedItems)).replace("[MoneyEarned]", String.format("%.2f", moneyEarned)).replace("[Interval]", String.valueOf(remainingSeconds)).replace("[Remaining]", String.valueOf(remainingSeconds)).replace("[ChargedFor]", String.valueOf(chargedMinutes));
                    updatedLines.add(MrLibColors.colorize(updatedLine));
                }
                String holoName = "sellchest_" + parts[0] + "_" + parts[1] + "_" + parts[2] + "_" + parts[3];
                Location worldLoc = null;
                Location chestLoc = null;
                try {
                    chestLoc = new Location(Bukkit.getWorld((String)parts[0]), Double.parseDouble(parts[1]) + 0.5, Double.parseDouble(parts[2]) + 0.5, Double.parseDouble(parts[3]) + 0.5);
                    worldLoc = new Location(Bukkit.getWorld((String)parts[0]), Double.parseDouble(parts[1]) + 0.5, Double.parseDouble(parts[2]) + ("MrLibCore-TextDisplay".equals(this.plugin.getHologramManager().getActiveProviderName()) ? 2.5 : 4.5), Double.parseDouble(parts[3]) + 0.5);
                }
                catch (Exception exception) {
                    // empty catch block
                }
                if (worldLoc == null || worldLoc.getWorld() == null) continue;
                this.cleanNearbyTextDisplays(chestLoc);
                this.plugin.getHologramManager().createHologram(holoName, worldLoc, updatedLines);
                Object object2 = object = this.hologramLock;
                synchronized (object2) {
                    this.hologramCache.put(holoName, new ArrayList(updatedLines));
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void restartHologramUpdateTask() {
        if (this.hologramUpdateTask != null) {
            this.hologramUpdateTask.cancel();
            this.hologramUpdateTask = null;
        }
        this.clearHologramCache();
        this.startHologramUpdateTask();
    }

    public void updateAllSellChestHolograms() {
        this.removeAllSellChestHolograms();
        this.clearHologramCache();
        Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> this.restoreAllSellChestHolograms(), 10L);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player)event.getWhoClicked();
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null) {
            return;
        }
        String sellTitle = MrLibColors.colorize((String)this.plugin.getMessage("SettingsMenu.titleforsellinv"));
        if (clickedInventory.getViewers().size() > 0 && player.getOpenInventory().getTitle().equals(sellTitle)) {
            ItemStack cursorItem = event.getCursor();
            ItemStack clickedItem = event.getCurrentItem();
            switch (event.getAction()) {
                case PLACE_ALL: 
                case PLACE_ONE: 
                case PLACE_SOME: {
                    if (cursorItem == null || cursorItem.getType() == Material.AIR || !Commands.isItemBanned(cursorItem, "INV", this.plugin)) break;
                    event.setCancelled(true);
                    this.plugin.sendMessage(player, this.plugin.getMessage("banned_item_inventory"));
                    return;
                }
                case SWAP_WITH_CURSOR: {
                    if (cursorItem == null || cursorItem.getType() == Material.AIR || !Commands.isItemBanned(cursorItem, "INV", this.plugin)) break;
                    event.setCancelled(true);
                    this.plugin.sendMessage(player, this.plugin.getMessage("banned_item_inventory"));
                    return;
                }
                case HOTBAR_SWAP: 
                case HOTBAR_MOVE_AND_READD: {
                    ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
                    if (hotbarItem == null || hotbarItem.getType() == Material.AIR || !Commands.isItemBanned(hotbarItem, "INV", this.plugin)) break;
                    event.setCancelled(true);
                    this.plugin.sendMessage(player, this.plugin.getMessage("banned_item_inventory"));
                    return;
                }
                case MOVE_TO_OTHER_INVENTORY: {
                    if (event.getClickedInventory() == clickedInventory || clickedItem == null || clickedItem.getType() == Material.AIR || !Commands.isItemBanned(clickedItem, "INV", this.plugin)) break;
                    event.setCancelled(true);
                    this.plugin.sendMessage(player, this.plugin.getMessage("banned_item_inventory"));
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        Inventory source = event.getSource();
        Inventory destination = event.getDestination();
        String sellTitle = MrLibColors.colorize((String)this.plugin.getMessage("SettingsMenu.titleforsellinv"));
        if (destination.getHolder() instanceof Container) {
            Container destContainer = (Container)destination.getHolder();
            Location destLoc = destContainer.getLocation();
            String destKey = destLoc.getWorld().getName() + ":" + destLoc.getBlockX() + ":" + destLoc.getBlockY() + ":" + destLoc.getBlockZ();
            File dataFile = new File(this.plugin.getDataFolder(), "data.yml");
            YamlConfiguration data = YamlConfiguration.loadConfiguration((File)dataFile);
            if (data.contains(destKey)) {
                ItemStack itemToMove = event.getItem();
                if (itemToMove != null) {
                    if (Commands.isItemBanned(itemToMove, "INV", this.plugin)) {
                        event.setCancelled(true);
                        return;
                    }
                    boolean added = this.plugin.getSellChestManager().addItemToChest(destKey, itemToMove.clone());
                    if (added) {
                        Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
                            ItemStack[] destContents = destination.getContents();
                            for (int i = 0; i < destContents.length; ++i) {
                                ItemStack stackInDest = destContents[i];
                                if (stackInDest == null || !stackInDest.isSimilar(itemToMove)) continue;
                                if (stackInDest.getAmount() > itemToMove.getAmount()) {
                                    stackInDest.setAmount(stackInDest.getAmount() - itemToMove.getAmount());
                                    break;
                                }
                                destination.setItem(i, null);
                                break;
                            }
                        });
                        return;
                    }
                    event.setCancelled(true);
                    return;
                }
                event.setCancelled(true);
                return;
            }
            if (source.getHolder() == null) {
                String viewTitle = event.getSource().getViewers().isEmpty() ? "" : ((HumanEntity)event.getSource().getViewers().get(0)).getOpenInventory().getTitle();
                String string = viewTitle;
                if (viewTitle.equals(sellTitle)) {
                    event.setCancelled(true);
                    return;
                }
            }
            for (String key : this.dbManager.getAllChestKeys()) {
                String destLocStr;
                List<String> linkedChests;
                String trasherMode = this.dbManager.getChestTrasherMode(key);
                if (!"TRANSFER".equalsIgnoreCase(trasherMode) || (linkedChests = this.dbManager.getLinkedChests(key)) == null || linkedChests.isEmpty() || !linkedChests.contains(destLocStr = destLoc.getWorld().getName() + "," + destLoc.getX() + "," + destLoc.getY() + "," + destLoc.getZ())) continue;
                String viewTitle = event.getSource().getViewers().isEmpty() ? "" : ((HumanEntity)event.getSource().getViewers().get(0)).getOpenInventory().getTitle();
                String string = viewTitle;
                if (!viewTitle.equals(sellTitle)) {
                    event.setCancelled(true);
                }
                return;
            }
        }
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            String key = block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
            if (!this.dbManager.chestExists(key)) continue;
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            String key = block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
            if (!this.dbManager.chestExists(key)) continue;
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler
    public void onBlockFromTo(BlockFromToEvent event) {
        Block block = event.getBlock();
        String key = block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
        if (this.dbManager.chestExists(key)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        ArrayList<Block> blocksToRemove = new ArrayList<Block>();
        for (Block block : event.blockList()) {
            String key = block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
            if (!this.dbManager.chestExists(key)) continue;
            blocksToRemove.add(block);
        }
        event.blockList().removeAll(blocksToRemove);
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        ArrayList<Block> blocksToRemove = new ArrayList<Block>();
        for (Block block : event.blockList()) {
            String key = block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
            if (!this.dbManager.chestExists(key)) continue;
            blocksToRemove.add(block);
        }
        event.blockList().removeAll(blocksToRemove);
    }

    @EventHandler
    public void onInvitePlayersMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player)event.getWhoClicked();
        FileConfiguration inviteConfig = this.plugin.getInviteMenuConfig();
        if (inviteConfig == null) {
            inviteConfig = this.plugin.getConfig();
        }
        String inviteTitle = MrLibColors.colorize((String)inviteConfig.getString("MrSellChests.InvitePlayersMenu.title", "&8Invite Players"));
        if (event.getView().getTitle().equals(inviteTitle)) {
            ItemMeta meta;
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.getType() != Material.AIR && (meta = clicked.getItemMeta()) != null) {
                String chestKey = (String)meta.getPersistentDataContainer().get(new NamespacedKey((Plugin)this.plugin, "chest_key"), PersistentDataType.STRING);
                String action = (String)meta.getPersistentDataContainer().get(new NamespacedKey((Plugin)this.plugin, "menu_action"), PersistentDataType.STRING);
                if (chestKey != null && action != null) {
                    switch (action) {
                        case "invite_player": {
                            player.closeInventory();
                            this.startInviteProcess(player, chestKey);
                            break;
                        }
                        case "uninvite_player": {
                            String playerUUID;
                            if (event.isRightClick()) {
                                String playerUUID2 = (String)meta.getPersistentDataContainer().get(new NamespacedKey((Plugin)this.plugin, "player_uuid"), PersistentDataType.STRING);
                                if (playerUUID2 == null) break;
                                this.handlePlayerUninvite(player, chestKey, playerUUID2);
                                break;
                            }
                            if (!event.isLeftClick() || (playerUUID = (String)meta.getPersistentDataContainer().get(new NamespacedKey((Plugin)this.plugin, "player_uuid"), PersistentDataType.STRING)) == null) break;
                            this.startEditPercentageProcess(player, chestKey, playerUUID);
                            break;
                        }
                        case "back_button": {
                            player.closeInventory();
                            this.plugin.getSellChestManager().openSettingsMenu(player, chestKey);
                        }
                    }
                }
            }
        }
    }

    private void handlePlayerUninvite(Player player, String chestKey, String targetPlayerUUID) {
        try {
            String ownerUUID = this.dbManager.getChestPlayerUuid(chestKey);
            if (!player.getUniqueId().toString().equals(ownerUUID)) {
                this.plugin.sendMessage(player, this.plugin.getMessage("invite_no_chest_owner"));
                return;
            }
            Player targetPlayer = null;
            try {
                targetPlayer = Bukkit.getPlayer((UUID)UUID.fromString(targetPlayerUUID));
            }
            catch (IllegalArgumentException illegalArgumentException) {
                // empty catch block
            }
            this.dbManager.removeChestInvitedPlayer(chestKey, targetPlayerUUID);
            if (targetPlayer != null) {
                Object ownerMessage = this.plugin.getMessage("invite_uninvite_success_online");
                ownerMessage = ownerMessage != null ? ((String)ownerMessage).replace("{player}", targetPlayer.getName()) : "&aSuccessfully uninvited &f" + targetPlayer.getName() + " &afrom this chest!";
                this.plugin.sendMessage(player, (String)ownerMessage);
                Object targetMessage = this.plugin.getMessage("invite_uninvite_target");
                targetMessage = targetMessage != null ? ((String)targetMessage).replace("{owner}", player.getName()) : "&cYou have been uninvited from &f" + player.getName() + "&c's sell chest!";
                this.plugin.sendMessage(targetPlayer, (String)targetMessage);
            } else {
                this.plugin.sendMessage(player, this.plugin.getMessage("invite_uninvite_success_offline"));
            }
            Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> this.plugin.getSellChestManager().openInvitePlayersMenu(player, chestKey));
        }
        catch (Exception e) {
            this.plugin.sendMessage(player, this.plugin.getMessage("invite_uninvite_failed"));
        }
    }

    private void startInviteProcess(Player player, String chestKey) {
        try {
            String ownerUUID = this.dbManager.getChestPlayerUuid(chestKey);
            if (!player.getUniqueId().toString().equals(ownerUUID)) {
                this.plugin.sendMessage(player, this.plugin.getMessage("invite_no_chest_owner"));
                return;
            }
            if (this.inviteStep.containsKey(player.getUniqueId())) {
                this.plugin.sendMessage(player, this.plugin.getMessage("invite_already_in_process"));
                return;
            }
            Map<String, Double> invitedPlayers = this.dbManager.getChestInvitedPlayers(chestKey);
            int maxPlayers = this.dbManager.getChestInvitePlayersMax(chestKey);
            if (invitedPlayers.size() >= maxPlayers) {
                Object msg = this.plugin.getMessage("invite_max_players_reached");
                msg = msg != null ? ((String)msg).replace("{max}", String.valueOf(maxPlayers)) : "&cYou have reached the maximum number of invited players! (" + maxPlayers + ")";
                this.plugin.sendMessage(player, (String)msg);
                return;
            }
            this.inviteChestKey.put(player.getUniqueId(), chestKey);
            this.inviteStep.put(player.getUniqueId(), SellChestInviteStep.WAITING_FOR_PLAYER_NAME);
            this.plugin.sendMessage(player, this.plugin.getMessage("invite_process_start"));
            this.plugin.sendMessage(player, this.plugin.getMessage("invite_enter_player_name"));
            this.plugin.sendMessage(player, this.plugin.getMessage("invite_player_must_be_online"));
            this.plugin.sendMessage(player, this.plugin.getMessage("invite_type_cancel"));
        }
        catch (Exception e) {
            this.plugin.sendMessage(player, this.plugin.getMessage("invite_failed_start"));
        }
    }

    private void startEditPercentageProcess(Player player, String chestKey, String targetPlayerUUID) {
        try {
            String ownerUUID = this.dbManager.getChestPlayerUuid(chestKey);
            if (!player.getUniqueId().toString().equals(ownerUUID)) {
                this.plugin.sendMessage(player, this.plugin.getMessage("edit_no_chest_owner"));
                return;
            }
            if (this.editStep.containsKey(player.getUniqueId())) {
                this.plugin.sendMessage(player, this.plugin.getMessage("edit_already_in_process"));
                return;
            }
            Map<String, Double> invitedPlayers = this.dbManager.getChestInvitedPlayers(chestKey);
            if (!invitedPlayers.containsKey(targetPlayerUUID)) {
                this.plugin.sendMessage(player, this.plugin.getMessage("edit_player_no_longer_invited"));
                return;
            }
            double currentPercentage = invitedPlayers.get(targetPlayerUUID) * 100.0;
            String playerName = "Unknown";
            try {
                Player targetPlayer = Bukkit.getPlayer((UUID)UUID.fromString(targetPlayerUUID));
                playerName = targetPlayer != null ? targetPlayer.getName() : "Offline Player";
            }
            catch (Exception e) {
                playerName = "Unknown";
            }
            this.editChestKey.put(player.getUniqueId(), chestKey);
            this.editPlayerUUID.put(player.getUniqueId(), targetPlayerUUID);
            this.editStep.put(player.getUniqueId(), SellChestEditStep.WAITING_FOR_NEW_PERCENTAGE);
            this.plugin.sendMessage(player, this.plugin.getMessage("edit_process_start"));
            Object msg1 = this.plugin.getMessage("edit_current_player");
            msg1 = msg1 != null ? ((String)msg1).replace("{player}", playerName) : "&7Player: &f" + playerName;
            this.plugin.sendMessage(player, (String)msg1);
            Object msg2 = this.plugin.getMessage("edit_current_percentage");
            msg2 = msg2 != null ? ((String)msg2).replace("{percentage}", String.format("%.0f", currentPercentage)) : "&7Current percentage: &f" + String.format("%.0f", currentPercentage) + "%";
            this.plugin.sendMessage(player, (String)msg2);
            this.plugin.sendMessage(player, this.plugin.getMessage("edit_enter_new_percentage"));
            this.plugin.sendMessage(player, this.plugin.getMessage("edit_type_cancel"));
        }
        catch (Exception e) {
            this.plugin.sendMessage(player, this.plugin.getMessage("edit_failed_start"));
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        if (this.editStep.containsKey(playerUUID)) {
            this.handleEditPercentageChat(event);
            return;
        }
        if (!this.inviteStep.containsKey(playerUUID)) {
            return;
        }
        event.setCancelled(true);
        SellChestInviteStep currentStep = this.inviteStep.get(playerUUID);
        String chestKey = this.inviteChestKey.get(playerUUID);
        if (chestKey == null) {
            this.cleanupInviteState(playerUUID);
            this.plugin.sendMessage(player, this.plugin.getMessage("invite_cancelled_missing_data"));
            return;
        }
        String message = event.getMessage().trim();
        switch (currentStep) {
            case WAITING_FOR_PLAYER_NAME: {
                if (message.equalsIgnoreCase("cancel")) {
                    this.cleanupInviteState(playerUUID);
                    this.plugin.sendMessage(player, this.plugin.getMessage("invite_cancelled"));
                    return;
                }
                Player targetPlayer = Bukkit.getPlayerExact((String)message);
                if (targetPlayer == null) {
                    Object msg = this.plugin.getMessage("invite_player_not_online");
                    msg = msg != null ? ((String)msg).replace("{player}", message) : "&cPlayer '" + message + "' is not online or doesn't exist!";
                    this.plugin.sendMessage(player, (String)msg);
                    this.plugin.sendMessage(player, this.plugin.getMessage("invite_type_cancel"));
                    return;
                }
                if (targetPlayer.getUniqueId().equals(playerUUID)) {
                    this.plugin.sendMessage(player, this.plugin.getMessage("invite_cannot_invite_self"));
                    this.plugin.sendMessage(player, this.plugin.getMessage("invite_type_cancel"));
                    return;
                }
                this.invitePlayerName.put(playerUUID, message);
                this.inviteStep.put(playerUUID, SellChestInviteStep.WAITING_FOR_PROFIT_PERCENTAGE);
                Object msg1 = this.plugin.getMessage("invite_player_found");
                msg1 = msg1 != null ? ((String)msg1).replace("{player}", message) : "&aPlayer '" + message + "' found!";
                this.plugin.sendMessage(player, (String)msg1);
                this.plugin.sendMessage(player, this.plugin.getMessage("invite_enter_percentage"));
                this.plugin.sendMessage(player, this.plugin.getMessage("invite_percentage_example"));
                this.plugin.sendMessage(player, this.plugin.getMessage("invite_type_cancel"));
                break;
            }
            case WAITING_FOR_PROFIT_PERCENTAGE: {
                if (message.equalsIgnoreCase("cancel")) {
                    this.cleanupInviteState(playerUUID);
                    this.plugin.sendMessage(player, this.plugin.getMessage("invite_cancelled"));
                    return;
                }
                try {
                    double percentage = Double.parseDouble(message);
                    if (percentage < 0.0 || percentage > 100.0) {
                        this.plugin.sendMessage(player, this.plugin.getMessage("invite_percentage_invalid"));
                        this.plugin.sendMessage(player, this.plugin.getMessage("invite_type_cancel"));
                        return;
                    }
                    String targetPlayerName = this.invitePlayerName.get(playerUUID);
                    Player targetPlayer = Bukkit.getPlayerExact((String)targetPlayerName);
                    if (targetPlayer == null) {
                        this.cleanupInviteState(playerUUID);
                        Object msg = this.plugin.getMessage("invite_player_offline");
                        msg = msg != null ? ((String)msg).replace("{player}", targetPlayerName) : "&cPlayer '" + targetPlayerName + "' is no longer online!";
                        this.plugin.sendMessage(player, (String)msg);
                        return;
                    }
                    String ownerUUID = this.dbManager.getChestPlayerUuid(chestKey);
                    if (!playerUUID.toString().equals(ownerUUID)) {
                        this.cleanupInviteState(playerUUID);
                        this.plugin.sendMessage(player, this.plugin.getMessage("invite_no_longer_owner"));
                        return;
                    }
                    Map<String, Double> invitedPlayers = this.dbManager.getChestInvitedPlayers(chestKey);
                    int maxPlayers = this.dbManager.getChestInvitePlayersMax(chestKey);
                    if (invitedPlayers.size() >= maxPlayers) {
                        this.cleanupInviteState(playerUUID);
                        Object msg = this.plugin.getMessage("invite_max_players_reached");
                        msg = msg != null ? ((String)msg).replace("{max}", String.valueOf(maxPlayers)) : "&cYou have reached the maximum number of invited players! (" + maxPlayers + ")";
                        this.plugin.sendMessage(player, (String)msg);
                        return;
                    }
                    if (invitedPlayers.containsKey(targetPlayer.getUniqueId().toString())) {
                        this.cleanupInviteState(playerUUID);
                        Object msg = this.plugin.getMessage("invite_player_already_invited");
                        msg = msg != null ? ((String)msg).replace("{player}", targetPlayerName) : "&cPlayer '" + targetPlayerName + "' is already invited to this chest!";
                        this.plugin.sendMessage(player, (String)msg);
                        return;
                    }
                    double profitShare = percentage / 100.0;
                    this.dbManager.addChestInvitedPlayer(chestKey, targetPlayer.getUniqueId().toString(), targetPlayerName, profitShare);
                    Object msg1 = this.plugin.getMessage("invite_success_owner");
                    msg1 = msg1 != null ? ((String)msg1).replace("{player}", targetPlayerName).replace("{percentage}", String.valueOf((int)percentage)) : "&aSuccessfully invited &f" + targetPlayerName + " &ato this chest! They will receive " + (int)percentage + "% of profits.";
                    this.plugin.sendMessage(player, (String)msg1);
                    Object msg2 = this.plugin.getMessage("invite_success_target");
                    msg2 = msg2 != null ? ((String)msg2).replace("{inviter}", player.getName()).replace("{percentage}", String.valueOf((int)percentage)) : "&aYou have been invited to &f" + player.getName() + "&a's sell chest! You will receive " + (int)percentage + "% of profits.";
                    targetPlayer.sendMessage((String)msg2);
                    this.cleanupInviteState(playerUUID);
                    Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> this.plugin.getSellChestManager().openInvitePlayersMenu(player, chestKey));
                    break;
                }
                catch (NumberFormatException e) {
                    this.plugin.sendMessage(player, this.plugin.getMessage("invite_percentage_invalid"));
                    this.plugin.sendMessage(player, this.plugin.getMessage("invite_type_cancel"));
                    break;
                }
                catch (Exception e) {
                    this.cleanupInviteState(playerUUID);
                    Object msg = this.plugin.getMessage("invite_failed");
                    msg = msg != null ? ((String)msg).replace("{error}", e.getMessage()) : "&cFailed to invite player: " + e.getMessage();
                    this.plugin.sendMessage(player, (String)msg);
                }
            }
        }
    }

    private void handleEditPercentageChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        event.setCancelled(true);
        SellChestEditStep currentStep = this.editStep.get(playerUUID);
        String chestKey = this.editChestKey.get(playerUUID);
        String targetPlayerUUID = this.editPlayerUUID.get(playerUUID);
        if (chestKey == null || targetPlayerUUID == null) {
            this.cleanupEditState(playerUUID);
            this.plugin.sendMessage(player, this.plugin.getMessage("edit_cancelled_missing_data"));
            return;
        }
        String message = event.getMessage().trim();
        switch (currentStep) {
            case WAITING_FOR_NEW_PERCENTAGE: {
                if (message.equalsIgnoreCase("cancel")) {
                    this.cleanupEditState(playerUUID);
                    this.plugin.sendMessage(player, this.plugin.getMessage("edit_cancelled"));
                    return;
                }
                try {
                    double percentage = Double.parseDouble(message);
                    if (percentage < 0.0 || percentage > 100.0) {
                        this.plugin.sendMessage(player, this.plugin.getMessage("edit_percentage_invalid"));
                        this.plugin.sendMessage(player, this.plugin.getMessage("edit_type_cancel"));
                        return;
                    }
                    String ownerUUID = this.dbManager.getChestPlayerUuid(chestKey);
                    if (!playerUUID.toString().equals(ownerUUID)) {
                        this.cleanupEditState(playerUUID);
                        this.plugin.sendMessage(player, this.plugin.getMessage("edit_no_longer_owner"));
                        return;
                    }
                    Map<String, Double> invitedPlayers = this.dbManager.getChestInvitedPlayers(chestKey);
                    if (!invitedPlayers.containsKey(targetPlayerUUID)) {
                        this.cleanupEditState(playerUUID);
                        this.plugin.sendMessage(player, this.plugin.getMessage("edit_player_no_longer_invited"));
                        return;
                    }
                    double profitShare = percentage / 100.0;
                    this.dbManager.updateChestInvitedPlayerProfitShare(chestKey, targetPlayerUUID, profitShare);
                    String playerName = "Unknown";
                    try {
                        Player targetPlayer = Bukkit.getPlayer((UUID)UUID.fromString(targetPlayerUUID));
                        if (targetPlayer != null) {
                            playerName = targetPlayer.getName();
                        }
                    }
                    catch (Exception e) {
                        playerName = "Unknown";
                    }
                    Object msg = this.plugin.getMessage("edit_success");
                    msg = msg != null ? ((String)msg).replace("{player}", playerName).replace("{percentage}", String.valueOf((int)percentage)) : "&aSuccessfully updated &f" + playerName + "&a's profit share to " + (int)percentage + "%!";
                    this.plugin.sendMessage(player, (String)msg);
                    try {
                        Player targetPlayer = Bukkit.getPlayer((UUID)UUID.fromString(targetPlayerUUID));
                        if (targetPlayer != null) {
                            Object msg2 = this.plugin.getMessage("edit_notification");
                            msg2 = msg2 != null ? ((String)msg2).replace("{owner}", player.getName()).replace("{percentage}", String.valueOf((int)percentage)) : "&7Your profit share for &f" + player.getName() + "&7's sell chest has been updated to " + (int)percentage + "%!";
                            targetPlayer.sendMessage((String)msg2);
                        }
                    }
                    catch (Exception exception) {
                        // empty catch block
                    }
                    this.cleanupEditState(playerUUID);
                    Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> this.plugin.getSellChestManager().openInvitePlayersMenu(player, chestKey));
                    break;
                }
                catch (NumberFormatException e) {
                    this.plugin.sendMessage(player, this.plugin.getMessage("edit_percentage_invalid"));
                    this.plugin.sendMessage(player, this.plugin.getMessage("edit_type_cancel"));
                    break;
                }
                catch (Exception e) {
                    this.cleanupEditState(playerUUID);
                    Object msg = this.plugin.getMessage("edit_failed");
                    msg = msg != null ? ((String)msg).replace("{error}", e.getMessage()) : "&cFailed to update percentage: " + e.getMessage();
                    this.plugin.sendMessage(player, (String)msg);
                }
            }
        }
    }

    private void cleanupInviteState(UUID playerUUID) {
        this.inviteChestKey.remove(playerUUID);
        this.invitePlayerName.remove(playerUUID);
        this.inviteStep.remove(playerUUID);
    }

    private void cleanupEditState(UUID playerUUID) {
        this.editChestKey.remove(playerUUID);
        this.editPlayerUUID.remove(playerUUID);
        this.editStep.remove(playerUUID);
    }
}