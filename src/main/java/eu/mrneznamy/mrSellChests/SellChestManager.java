package eu.mrneznamy.mrSellChests;

import eu.mrneznamy.mrSellChests.Commands;
import eu.mrneznamy.mrSellChests.MrSellChests;
import eu.mrneznamy.mrSellChests.database.SellChestsDatabaseManager;
import eu.mrneznamy.mrSellChests.api.MrSellChestsAPI;
import eu.mrneznamy.mrSellChests.api.PriceProvider;
import eu.mrneznamy.mrlibcore.gui.MrLibGUI;
import eu.mrneznamy.mrlibcore.gui.MrLibItemBuilder;
import eu.mrneznamy.mrlibcore.utils.MrLibColors;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import me.gypopo.economyshopgui.api.EconomyShopGUIHook;
import me.gypopo.economyshopgui.api.objects.SellPrice;
import net.brcdev.shopgui.ShopGuiPlusApi;
import eu.mrneznamy.mrSellChests.integration.StackerHandler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class SellChestManager {
    private final MrSellChests plugin;
    private final Map<String, Inventory> chestInventories;
    private final Map<String, BukkitTask> sellTasks;
    private BukkitTask sellTask;
    private final Map<String, Integer> sellIntervals;
    private final Map<Material, Double> itemPrices;
    private final Map<String, Long> lastSellTimes;
    private final Map<UUID, SellMessageData> playerSellData;
    private final Map<String, BukkitTask> collectorTasks;
    private BukkitTask sellMessageTask;
    private BukkitTask chargingTask;
    private final Map<String, String> chestTypes = new HashMap<String, String>();
    private final Map<String, FileConfiguration> configCache = new HashMap<String, FileConfiguration>();
    private StackerHandler roseStackerIntegration;

    public SellChestManager(MrSellChests plugin) {
        this.plugin = plugin;
        if (Bukkit.getPluginManager().isPluginEnabled("RoseStacker")) {
            try {
                this.roseStackerIntegration = (StackerHandler) Class.forName("eu.mrneznamy.mrSellChests.integration.RoseStackerIntegration").getConstructor().newInstance();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to hook into RoseStacker: " + e.getMessage());
            }
        }
        this.chestInventories = new HashMap<String, Inventory>();
        this.sellTasks = new HashMap<String, BukkitTask>();
        this.sellIntervals = new HashMap<String, Integer>();
        this.itemPrices = new HashMap<Material, Double>();
        this.lastSellTimes = new HashMap<String, Long>();
        this.playerSellData = new HashMap<UUID, SellMessageData>();
        this.collectorTasks = new HashMap<String, BukkitTask>();
        this.loadPrices();
        this.loadAllChests();
        this.startSellTasks();
        this.startSellMessageTask();
        this.startChargingTasks();
    }

    private BukkitTask createTask(Runnable runnable, long delay, long period, boolean async) {
        if (async) {
            try {
                Method asyncMethod = Bukkit.getScheduler().getClass().getMethod("runTaskTimerAsync", Plugin.class, Runnable.class, Long.TYPE, Long.TYPE);
                return (BukkitTask)asyncMethod.invoke((Object)Bukkit.getScheduler(), new Object[]{this.plugin, runnable, delay, period});
            }
            catch (Exception e) {
                return Bukkit.getScheduler().runTaskTimerAsynchronously((Plugin)this.plugin, runnable, delay, period);
            }
        }
        return Bukkit.getScheduler().runTaskTimer((Plugin)this.plugin, runnable, delay, period);
    }

    private void startSellMessageTask() {
        int interval = this.plugin.getConfig().getInt("MrSellChests.Sell-Message.Interval", 1);
        this.sellMessageTask = this.createTask(() -> {
            long currentTime = System.currentTimeMillis();
            for (Map.Entry<UUID, SellMessageData> entry : this.playerSellData.entrySet()) {
                UUID playerId = entry.getKey();
                SellMessageData data = entry.getValue();
                if (currentTime - data.lastReset < (long)(interval * 60 * 1000)) continue;
                Player player = Bukkit.getPlayer((UUID)playerId);
                if (player != null && data.totalEarned > 0.0) {
                    String message = this.plugin.getMessage("sell_chest_interval_message");
                    if (message == null) {
                        message = "&7Your sell chests earned &a$[MoneyEarned]&7 in the last &a[Interval] &7minutes!";
                    }
                    message = message.replace("[MoneyEarned]", String.format("%.2f", data.totalEarned)).replace("[Interval]", String.valueOf(interval));
                    this.plugin.sendMessage(player, message);
                }
                data.totalEarned = 0.0;
                data.lastReset = currentTime;
            }
        }, 1200L, 1200L, true);
    }

    private void loadPrices() {
        File pricesFile;
        YamlConfiguration prices;
        ConfigurationSection pricesSection;
        String provider = this.plugin.getConfig().getString("MrSellChests.SellPrices.Provider", "FILE");
        if (provider.equalsIgnoreCase("SHOPGUI+")) {
            if (Bukkit.getPluginManager().getPlugin("ShopGUIPlus") != null) {
                return;
            }
            this.plugin.getLogger().warning("ShopGUIPlus not found, falling back to file prices");
            provider = "FILE";
        }
        if (provider.equalsIgnoreCase("MrUltimateShop")) {
            if (Bukkit.getPluginManager().getPlugin("MrUltimateShop") != null) {
                return;
            }
            this.plugin.getLogger().warning("MrUltimateShop not found, falling back to file prices, you can download for free on repo.mrneznamy.eu");
            provider = "FILE";
        }
        if (provider.equalsIgnoreCase("EconomyShopGUI")) {
            if (Bukkit.getPluginManager().getPlugin("EconomyShopGUI") != null || Bukkit.getPluginManager().getPlugin("EconomyShopGUI-Premium") != null) {
                return;
            }
            this.plugin.getLogger().warning("EconomyShopGUI not found, falling back to file prices");
            provider = "FILE";
        }
        if (provider.equalsIgnoreCase("ExcellentShop")) {
            if (Bukkit.getPluginManager().getPlugin("ExcellentShop") != null) {
                return;
            }
            this.plugin.getLogger().warning("ExcellentShop not found, falling back to file prices");
            provider = "FILE";
        }
        if (provider.equalsIgnoreCase("FILE") && (pricesSection = (prices = YamlConfiguration.loadConfiguration((File)(pricesFile = new File(this.plugin.getDataFolder(), "sell-prices.yml")))).getConfigurationSection("sell-prices")) != null) {
            for (String materialName : pricesSection.getKeys(false)) {
                Material material = Material.matchMaterial((String)materialName);
                if (material == null) continue;
                double price = prices.getDouble("sell-prices." + materialName);
                this.itemPrices.put(material, price);
            }
        }
    }

    private double getItemPrice(ItemStack item, OfflinePlayer player) {
        MrSellChestsAPI api = MrSellChestsAPI.getInstance();
        if (api != null) {
            PriceProvider activeProvider = api.getActiveProvider();
            if (activeProvider != null && activeProvider.isAvailable()) {
                return activeProvider.getSellPrice(item, player);
            }
        }

        String provider = this.plugin.getConfig().getString("MrSellChests.SellPrices.Provider", "FILE");
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
            return 0.0;
        }
        if (provider.equalsIgnoreCase("SHOPGUI+") && Bukkit.getPluginManager().getPlugin("ShopGUIPlus") != null) {
            return ShopGuiPlusApi.getItemStackPriceSell((ItemStack)item) / (double)item.getAmount();
        }
        if (provider.equalsIgnoreCase("MrUltimateShop") && Bukkit.getPluginManager().getPlugin("MrUltimateShop") != null) {
            try {
                if (eu.mrneznamy.mrultimateshop.api.MrUltimateShopAPI.getInstance() != null) {
                    return eu.mrneznamy.mrultimateshop.api.MrUltimateShopAPI.getInstance().getItemStackPriceSell(item) / (double)item.getAmount();
                }
                return 0.0;
            }
            catch (Exception e) {
                this.plugin.getLogger().warning("Failed to get price from MrUltimateShop: " + e.getMessage());
                return 0.0;
            }
        }
        if (provider.equalsIgnoreCase("EconomyShopGUI") && (Bukkit.getPluginManager().getPlugin("EconomyShopGUI") != null || Bukkit.getPluginManager().getPlugin("EconomyShopGUI-Premium") != null)) {
            if (player == null) {
                return 0.0;
            }
            Optional optional = EconomyShopGUIHook.getSellPrice((OfflinePlayer)player, (ItemStack)item);
            if (optional.isPresent()) {
                SellPrice priceObj = (SellPrice)optional.get();
                try {
                    Method getPrices = priceObj.getClass().getMethod("getPrices", new Class[0]);
                    Map prices = (Map)getPrices.invoke((Object)priceObj, new Object[0]);
                    double price = 0.0;
                    if (prices.containsKey("VAULT")) {
                        price = (Double)prices.get("VAULT");
                    } else if (!prices.isEmpty()) {
                        price = (Double)prices.values().iterator().next();
                    }
                    return price > 0.0 ? price : 0.0;
                }
                catch (Exception e) {
                    e.printStackTrace();
                    return 0.0;
                }
            }
            return 0.0;
        }
        if (provider.equalsIgnoreCase("ExcellentShop") && Bukkit.getPluginManager().getPlugin("ExcellentShop") != null) {
            block21: {
                if (player == null || player.getPlayer() == null) {
                    return 0.0;
                }
                try {
                    Class<?> shopApiClass = Class.forName("su.nightexpress.nexshop.ShopAPI");
                    Method getVirtualShopMethod = shopApiClass.getMethod("getVirtualShop", new Class[0]);
                    Object virtualShopModule = getVirtualShopMethod.invoke(null, new Object[0]);
                    if (virtualShopModule == null) break block21;
                    Class<?> tradeTypeClass = Class.forName("su.nightexpress.nexshop.api.shop.type.TradeType");
                    Object sellType = Enum.valueOf((Class<Enum>)tradeTypeClass, "SELL");
                    Method getBestProductMethod = virtualShopModule.getClass().getMethod("getBestProductFor", ItemStack.class, tradeTypeClass, Player.class);
                    Object product = getBestProductMethod.invoke(virtualShopModule, item, sellType, player.getPlayer());
                    if (product == null) break block21;
                    try {
                        Method getPreparedMethod = product.getClass().getMethod("getPrepared", Player.class, tradeTypeClass, Boolean.TYPE);
                        Object preparedProduct = getPreparedMethod.invoke(product, player.getPlayer(), sellType, false);
                        if (preparedProduct != null) {
                            Method getPriceMethod = preparedProduct.getClass().getMethod("getPrice", new Class[0]);
                            double price = (Double)getPriceMethod.invoke(preparedProduct, new Object[0]);
                            Method getUnitAmountMethod = product.getClass().getMethod("getUnitAmount", new Class[0]);
                            int unitAmount = (Integer)getUnitAmountMethod.invoke(product, new Object[0]);
                            return price / (double)unitAmount;
                        }
                    }
                    catch (Exception e) {
                        this.plugin.getLogger().warning("ExcellentShop integration error: " + e.getMessage());
                        this.plugin.getLogger().warning("Available methods for " + product.getClass().getName() + ":");
                        for (Method m : product.getClass().getMethods()) {
                            this.plugin.getLogger().warning(m.getName() + " " + Arrays.toString(m.getParameterTypes()));
                        }
                        throw e;
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return 0.0;
        }
        Double price = this.itemPrices.get(item.getType());
        return price != null ? price : 0.0;
    }

    private void loadAllChests() {
        SellChestsDatabaseManager databaseManager = this.plugin.getDatabaseManager();
        Set<String> chestKeys = databaseManager.getAllChestKeys();
        for (BukkitTask task : this.collectorTasks.values()) {
            task.cancel();
        }
        this.collectorTasks.clear();
        for (String key : chestKeys) {
            try {
                boolean collectorEnabled;
                String chestType;
                String[] parts = key.split(":");
                if (parts.length != 4 || (chestType = databaseManager.getChestType(key)) == null) continue;
                FileConfiguration pluginConfig = this.plugin.getConfig();
                String chestConfigPath = "MrSellChests.SellChests." + chestType + ".Chest";
                int size = pluginConfig.getInt(chestConfigPath + ".Size", 9);
                int interval = pluginConfig.getInt(chestConfigPath + ".Interval", 10);
                String title = MrLibColors.colorize((String)pluginConfig.getString("MrSellChests.SettingsMenu.titleforsellinv", "&8Sell Inventory"));
                Inventory inv = Bukkit.createInventory(null, (int)size, (String)title);
                this.chestInventories.put(key, inv);
                this.sellIntervals.put(key, interval);
                if (databaseManager.hasInventoryData(key)) {
                    for (int i = 0; i < size; ++i) {
                        ItemStack item = databaseManager.getInventoryItem(key, i);
                        if (item == null) continue;
                        inv.setItem(i, item);
                    }
                }
                if (!(collectorEnabled = databaseManager.getChestCollectorEnabled(key)) || Bukkit.getWorld((String)parts[0]) == null) continue;
                Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> this.startCollectorTask(key, parts, chestType), 40L);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void startCollectorTask(String key, String[] locationParts, String chestType) {
        try {
            int collectorInterval;
            String chestConfigPath;
            FileConfiguration pluginConfig;
            BukkitTask existingTask = this.collectorTasks.remove(key);
            if (existingTask != null) {
                existingTask.cancel();
            }
            if ((pluginConfig = this.plugin.getConfig()).contains((chestConfigPath = "MrSellChests.SellChests." + chestType + ".Chest") + ".CollectTime")) {
                collectorInterval = pluginConfig.getInt(chestConfigPath + ".CollectTime");
            } else {
                int sellInterval = pluginConfig.getInt(chestConfigPath + ".Interval", 10);
                collectorInterval = sellInterval / 2;
            }
            Location loc = new Location(Bukkit.getWorld((String)locationParts[0]), Double.parseDouble(locationParts[1]), Double.parseDouble(locationParts[2]), Double.parseDouble(locationParts[3]));
            BukkitTask task = this.createTask(() -> {
                try {
                    if (!loc.getChunk().isLoaded()) {
                        return;
                    }
                    SellChestsDatabaseManager dbManager = this.plugin.getDatabaseManager();
                    if (!dbManager.chestExists(key) || dbManager.getChestType(key) == null || !dbManager.isCollectorEnabled(key)) {
                        BukkitTask currentTask = this.collectorTasks.remove(key);
                        if (currentTask != null) {
                            currentTask.cancel();
                        }
                        return;
                    }
                    for (Entity entity : Arrays.asList(loc.getChunk().getEntities())) {
                        if (!(entity instanceof Item)) continue;

                        Item itemEntity = (Item) entity;
                        ItemStack stack = itemEntity.getItemStack();

                        if (Commands.isItemBanned(stack, "COLLECT", this.plugin)) continue;

                        int originalAmount = stack.getAmount();
                        if (roseStackerIntegration != null) {
                            originalAmount = roseStackerIntegration.getItemStackSize(itemEntity);
                        }

                        int remaining = this.addItemToChestGetRemaining(key, stack, originalAmount);

                        if (remaining != originalAmount) {
                            if (remaining <= 0) {
                                itemEntity.remove();
                            } else {
                                if (roseStackerIntegration != null) {
                                    roseStackerIntegration.setItemStackSize(itemEntity, remaining);
                                } else {
                                    ItemStack newStack = stack.clone();
                                    newStack.setAmount(remaining);
                                    itemEntity.setItemStack(newStack);
                                }
                            }
                            this.saveChestInventory(key);
                        }
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }, 20L * (long)collectorInterval, Math.max(100L, 20L * (long)collectorInterval), false);
            this.collectorTasks.put(key, task);
            SellChestsDatabaseManager dbManager = this.plugin.getDatabaseManager();
            dbManager.setCollectorTaskId(key, task.getTaskId());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startSellTasks() {
        this.sellTask = this.createTask(() -> {
            long currentTime = System.currentTimeMillis();
            for (Map.Entry<String, Integer> entry : this.sellIntervals.entrySet()) {
                String key = entry.getKey();
                int interval = entry.getValue();
                Long lastSellTime = this.lastSellTimes.get(key);
                if (lastSellTime == null) {
                    lastSellTime = currentTime;
                    this.lastSellTimes.put(key, lastSellTime);
                }
                if (currentTime - lastSellTime < (long)interval * 1000L) continue;
                this.sellInventory(key);
            }
        }, 20L, 20L, false);
    }

    private void startChargingTasks() {
        this.chargingTask = this.createTask(() -> {
            SellChestsDatabaseManager dbManager = this.plugin.getDatabaseManager();
            for (String key : this.chestInventories.keySet()) {
                try {
                    int currentCharge;
                    String chestType = this.chestTypes.computeIfAbsent(key, k -> dbManager.getChestType((String)k));
                    if (!"Charging".equalsIgnoreCase(chestType) || (currentCharge = dbManager.getChestChargingMinutes(key)) <= 0) continue;
                    dbManager.setChestChargingMinutes(key, (int)((double)currentCharge - 0.016666666666666666));
                }
                catch (Exception exception) {}
            }
        }, 20L, 20L, true);
    }

    public Inventory getChestInventory(String key) {
        return this.chestInventories.get(key);
    }

    public boolean addItemToChest(String chestKey, ItemStack item) {
        return this.addItemToChestGetRemaining(chestKey, item, item.getAmount()) == 0;
    }

    public int addItemToChestGetRemaining(String chestKey, ItemStack item, int amount) {
        Inventory sellInv;
        double itemPrice;
        if (item == null) {
            return 0;
        }
        SellChestsDatabaseManager dbManager = this.plugin.getDatabaseManager();
        if (!dbManager.chestExists(chestKey) || dbManager.getChestType(chestKey) == null) {
            BukkitTask task = this.collectorTasks.remove(chestKey);
            if (task != null) {
                task.cancel();
            }
            return amount;
        }
        OfflinePlayer player = null;
        String ownerUUID = dbManager.getChestPlayerUuid(chestKey);
        if (ownerUUID != null) {
            try {
                player = Bukkit.getOfflinePlayer((UUID)UUID.fromString(ownerUUID));
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        if ((itemPrice = this.getItemPrice(item, player)) > 0.0) {
            Inventory sellInv2 = this.getChestInventory(chestKey);
            if (sellInv2 != null) {
                int remaining = this.addItemsToInventory(sellInv2, item, amount);
                if (amount - remaining > 0) {
                     // this.plugin.getLogger().info("Added " + (amount - remaining) + " of " + item.getType() + " to chest " + chestKey + ". Price: " + itemPrice);
                }
                return remaining;
            }
            return amount;
        }
        
        String trasherMode = dbManager.getChestTrasherMode(chestKey);
        if (trasherMode == null) {
            // this.plugin.getLogger().info("Item " + item.getType() + " has no price. Trasher mode: REMOVE (Default)");
            trasherMode = "REMOVE";
        } else {
            // this.plugin.getLogger().info("Item " + item.getType() + " has no price. Trasher mode: " + trasherMode);
        }
        if ("TRANSFER".equals(trasherMode)) {
            Inventory sellInv3;
            List<String> linkedChests = dbManager.getLinkedChests(chestKey);
            int currentRemaining = amount;
            if (!linkedChests.isEmpty()) {
                for (String chestLoc : linkedChests) {
                    Container container;
                    Location loc;
                    Block block;
                    String[] parts = chestLoc.split(",");
                    if (parts.length != 4 || !((block = (loc = new Location(Bukkit.getWorld((String)parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]))).getBlock()).getState() instanceof Container)) continue;
                    
                    container = (Container)block.getState();
                    currentRemaining = this.addItemsToInventory(container.getInventory(), item, currentRemaining);
                    if (currentRemaining == 0) return 0;
                }
            }
            if ((sellInv3 = this.getChestInventory(chestKey)) != null) {
                currentRemaining = this.addItemsToInventory(sellInv3, item, currentRemaining);
                if (currentRemaining > 0) {
                    this.addItemToDeleteQueue(chestKey, currentRemaining);
                }
            } else {
                this.addItemToDeleteQueue(chestKey, currentRemaining);
            }
            return 0;
        }
        if ("REMOVE".equals(trasherMode)) {
            this.addItemToDeleteQueue(chestKey, amount);
            return 0;
        }
        if ("KEEP".equals(trasherMode) && (sellInv = this.getChestInventory(chestKey)) != null) {
            return this.addItemsToInventory(sellInv, item, amount);
        }
        return amount;
    }

    private int addItemsToInventory(Inventory inv, ItemStack item, int amount) {
        int remaining = amount;
        int maxStackSize = item.getMaxStackSize();
        while (remaining > 0) {
            int chunk = Math.min(remaining, maxStackSize);
            ItemStack stack = item.clone();
            stack.setAmount(chunk);
            HashMap<Integer, ItemStack> leftovers = inv.addItem(stack);
            int failedToAdd = 0;
            if (!leftovers.isEmpty()) {
                failedToAdd = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
            }
            int added = chunk - failedToAdd;
            remaining -= added;
            if (failedToAdd > 0) {
                return remaining;
            }
        }
        return 0;
    }

    private void addItemToDeleteQueue(String key, int amount) {
        try {
            SellChestsDatabaseManager dbManager = this.plugin.getDatabaseManager();
            int currentPendingDeleted = dbManager.getPendingDeletedItems(key);
            dbManager.setPendingDeletedItems(key, currentPendingDeleted + amount);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveChestInventory(String key) {
        SellChestsDatabaseManager dbManager = this.plugin.getDatabaseManager();
        Inventory inv = this.chestInventories.get(key);
        if (inv == null) {
            return;
        }
        boolean hasChanges = false;
        for (int i = 0; i < inv.getSize(); ++i) {
            ItemStack item = inv.getItem(i);
            ItemStack currentItem = dbManager.getInventoryItem(key, i);
            if (item != null && item.getType() != Material.AIR) {
                if (item.equals((Object)currentItem)) continue;
                dbManager.setInventoryItem(key, i, item);
                hasChanges = true;
                continue;
            }
            if (currentItem == null) continue;
            dbManager.setInventoryItem(key, i, null);
            hasChanges = true;
        }
    }

    private void sellInventory(String key) {
        try {
            ItemStack item;
            int i;
            File linkedFile;
            int currentCharge;
            Inventory inv = this.chestInventories.get(key);
            if (inv == null) {
                return;
            }
            String[] parts = key.split(":");
            if (parts.length != 4) {
                return;
            }
            World world = Bukkit.getWorld((String)parts[0]);
            if (world == null) {
                return;
            }
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                return;
            }
            SellChestsDatabaseManager dbManager = this.plugin.getDatabaseManager();
            String chestType = this.chestTypes.computeIfAbsent(key, k -> dbManager.getChestType((String)k));
            if (chestType == null) {
                return;
            }
            if ("Charging".equalsIgnoreCase(chestType) && (currentCharge = dbManager.getChestChargingMinutes(key)) <= 0) {
                this.lastSellTimes.put(key, System.currentTimeMillis());
                return;
            }
            OfflinePlayer player = null;
            String ownerUUID = dbManager.getChestPlayerUuid(key);
            if (ownerUUID != null) {
                try {
                    player = Bukkit.getOfflinePlayer((UUID)UUID.fromString(ownerUUID));
                }
                catch (IllegalArgumentException e) {
                    return;
                }
            }
            double totalEarnings = 0.0;
            int itemsSold = 0;
            int itemsDeleted = 0;
            double booster = this.getTotalBooster(key);
            String trasherMode = dbManager.getChestTrasherMode(key);
            List<String> linkedChests = null;
            if ("TRANSFER".equals(trasherMode)) {
                linkedChests = dbManager.getLinkedChests(key);
            }
            for (i = 0; i < inv.getSize(); ++i) {
                double itemPrice;
                item = inv.getItem(i);
                if (item == null || item.getType() == Material.AIR || Commands.isItemBanned(item, "SELL", this.plugin) || !((itemPrice = this.getItemPrice(item, player)) > 0.0)) continue;

                totalEarnings += itemPrice * (double)item.getAmount() * booster;
                itemsSold += item.getAmount();
                inv.setItem(i, null);
            }
            if ("TRANSFER".equals(trasherMode) && linkedChests != null && !linkedChests.isEmpty()) {
                for (i = 0; i < inv.getSize(); ++i) {
                    item = inv.getItem(i);
                    if (item == null || item.getType() == Material.AIR) continue;
                    boolean transferred = false;
                    for (String chestLoc : linkedChests) {
                        try {
                            Location loc;
                            Block block;
                            World world2;
                            String[] parts2 = chestLoc.split(",");
                            if (parts2.length != 4 || (world2 = Bukkit.getWorld((String)parts2[0])) == null || !((block = (loc = new Location(world2, Double.parseDouble(parts2[1]), Double.parseDouble(parts2[2]), Double.parseDouble(parts2[3]))).getBlock()).getState() instanceof Container)) continue;
                            Container container = (Container)block.getState();
                            HashMap leftover = container.getInventory().addItem(new ItemStack[]{item.clone()});
                            if (!leftover.isEmpty()) continue;
                            inv.setItem(i, null);
                            transferred = true;
                            break;
                        }
                        catch (Exception e) {
                        }
                    }
                    if (transferred || !"REMOVE".equals(trasherMode)) continue;
                    itemsDeleted += item.getAmount();
                    inv.setItem(i, null);
                }
            } else if ("REMOVE".equals(trasherMode)) {
                for (i = 0; i < inv.getSize(); ++i) {
                    item = inv.getItem(i);
                    if (item == null || item.getType() == Material.AIR) continue;
                    itemsDeleted += item.getAmount();
                    inv.setItem(i, null);
                }
            }
            if (totalEarnings > 0.0 || itemsDeleted > 0) {
                dbManager.addItemsSold(key, itemsSold);
                dbManager.addDeletedItems(key, itemsDeleted);
                dbManager.addMoneyEarned(key, totalEarnings);
                if (player != null && totalEarnings > 0.0) {
                    Map<String, Double> invitedPlayers = dbManager.getChestInvitedPlayers(key);
                    double ownerShare = totalEarnings;
                    HashMap<UUID, Double> playerShares = new HashMap<UUID, Double>();
                    if (!invitedPlayers.isEmpty()) {
                        double totalInvitedShare = invitedPlayers.values().stream().mapToDouble(Double::doubleValue).sum();
                        ownerShare = totalEarnings * (1.0 - totalInvitedShare);
                        for (Map.Entry<String, Double> entry : invitedPlayers.entrySet()) {
                            try {
                                UUID invitedUUID = UUID.fromString(entry.getKey());
                                double playerShare = totalEarnings * entry.getValue();
                                playerShares.put(invitedUUID, playerShare);
                            }
                            catch (IllegalArgumentException invitedUUID) {}
                        }
                    }
                    this.plugin.getEconomy().deposit(player, ownerShare);
                    SellMessageData ownerMessageData = this.playerSellData.computeIfAbsent(player.getUniqueId(), k -> new SellMessageData());
                    ownerMessageData.totalEarned += ownerShare;
                    for (Map.Entry entry : playerShares.entrySet()) {
                        try {
                            Player invitedPlayer = Bukkit.getPlayer((UUID)((UUID)entry.getKey()));
                            if (invitedPlayer == null) continue;
                            this.plugin.getEconomy().deposit((OfflinePlayer)invitedPlayer, ((Double)entry.getValue()).doubleValue());
                            SellMessageData invitedMessageData = this.playerSellData.computeIfAbsent((UUID)entry.getKey(), k -> new SellMessageData());
                            invitedMessageData.totalEarned += ((Double)entry.getValue()).doubleValue();
                        }
                        catch (Exception exception) {}
                    }
                }
                this.saveChestInventory(key);
            }
            this.lastSellTimes.put(key, System.currentTimeMillis());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public BukkitTask getSellTask(String key) {
        return this.sellTasks.get(key);
    }

    public int getRemainingSeconds(String key) {
        Long lastSellTime;
        Integer interval = this.sellIntervals.get(key);
        if (interval == null) {
            try {
                String chestType = this.plugin.getDatabaseManager().getChestType(key);
                if (chestType == null) {
                    return 0;
                }
                int configInterval = this.plugin.getConfig().getInt("MrSellChests.SellChests." + chestType + ".Chest.Interval", 10);
                this.sellIntervals.put(key, configInterval);
                interval = configInterval;
            }
            catch (Exception e) {
                e.printStackTrace();
                return 0;
            }
        }
        if ((lastSellTime = this.lastSellTimes.get(key)) == null) {
            this.lastSellTimes.put(key, System.currentTimeMillis());
            return interval;
        }
        long currentTime = System.currentTimeMillis();
        long elapsedMillis = currentTime - lastSellTime;
        int elapsedSeconds = (int)(elapsedMillis / 1000L);
        int remainingSeconds = interval - elapsedSeconds % interval;
        return remainingSeconds;
    }

    public void removeChest(String key) {
        BukkitTask collectorTask;
        this.chestInventories.remove(key);
        BukkitTask task = this.sellTasks.remove(key);
        if (task != null) {
            task.cancel();
        }
        if ((collectorTask = this.collectorTasks.remove(key)) != null) {
            collectorTask.cancel();
        }
        this.sellIntervals.remove(key);
        this.lastSellTimes.remove(key);
    }

    public void openSettingsMenu(Player player, String chestKey) {
        FileConfiguration config = this.plugin.getConfig();
        String title = config.getString("MrSellChests.SettingsMenu.title", "&8Sell Chest Menu");
        int size = config.getInt("MrSellChests.SettingsMenu.size", 27);
        int rows = size / 9;
        MrLibGUI gui = new MrLibGUI(MrLibColors.colorize((String)title), rows);
        String trasherMode = this.plugin.getDatabaseManager().getChestTrasherMode(chestKey);
        String chestType = this.plugin.getDatabaseManager().getChestType(chestKey);
        if (config.getConfigurationSection("MrSellChests.SettingsMenu.Filters") != null) {
            for (String filterKey : config.getConfigurationSection("MrSellChests.SettingsMenu.Filters").getKeys(false)) {
                String[] slots;
                String path = "MrSellChests.SettingsMenu.Filters." + filterKey;
                if (!config.getBoolean(path + ".enabled", true)) continue;
                String materialStr = config.getString(path + ".material", "GRAY_STAINED_GLASS_PANE");
                Material material = Material.valueOf((String)materialStr.toUpperCase());
                String name = config.getString(path + ".name", " ");
                String slotStr = config.getString(path + ".slot", "");
                if (slotStr.isEmpty()) continue;
                for (String slot : slots = slotStr.split(",")) {
                    try {
                        int slotNum = Integer.parseInt(slot.trim());
                        if (slotNum < 0 || slotNum >= size) continue;
                        ItemStack filter = new MrLibItemBuilder(material).setName(name).build();
                        gui.setItem(slotNum, filter);
                    }
                    catch (NumberFormatException slotNum) {
                        // empty catch block
                    }
                }
            }
        }
        String ownerName = this.plugin.getDatabaseManager().getChestOwner(chestKey);
        Object booster = String.format("%.2f", this.getTotalBooster(chestKey));
        long boostTime = this.getBoostTimeLeft(chestKey);
        if (boostTime > 0L) {
            Object timeMsg = this.plugin.getMessage("boost_time_left");
            timeMsg = timeMsg != null ? ((String)timeMsg).replace("[Time]", String.valueOf(boostTime)) : "&7Boost time left: " + boostTime;
            booster = (String)booster + " " + (String)timeMsg;
        }
        int itemsSold = this.plugin.getDatabaseManager().getItemsSold(chestKey);
        int deletedItems = this.plugin.getDatabaseManager().getDeletedItems(chestKey);
        double moneyEarned = this.plugin.getDatabaseManager().getMoneyEarned(chestKey);
        boolean hologramEnabled = this.plugin.getDatabaseManager().getChestHologramEnabled(chestKey);
        boolean chunkCollectorEnabled = this.plugin.getDatabaseManager().getChestCollectorEnabled(chestKey);
        int currentChargeMinutes = this.plugin.getDatabaseManager().getChestChargingMinutes(chestKey);
        int maxChargeMinutes = this.plugin.getDatabaseManager().getChestChargingMaxMinutes(chestKey);
        double pricePerCharge = config.getDouble("MrSellChests.SellChests." + chestType + ".Chest.Charging.PriceForCharge", 10000.0);
        Map<String, Double> invitedPlayersMap = this.plugin.getDatabaseManager().getChestInvitedPlayers(chestKey);
        int maxPlayersCount = this.plugin.getDatabaseManager().getChestInvitePlayersMax(chestKey);
        List<String> linkedChests = this.plugin.getDatabaseManager().getLinkedChests(chestKey);
        int maxLinks = config.getInt("MrSellChests.SellChests." + chestType + ".Chest.MaxLinks", 1);
        if (config.getConfigurationSection("MrSellChests.SettingsMenu.items") != null) {
            boolean globalHologramsEnabled = config.getBoolean("MrSellChests.Holograms.Enabled", true);
            for (String itemKey : config.getConfigurationSection("MrSellChests.SettingsMenu.items").getKeys(false)) {
                String path = "MrSellChests.SettingsMenu.items." + itemKey;
                int slot = config.getInt(path + ".slot", 0);
                if (itemKey.equals("charge_chest") && !"Charging".equalsIgnoreCase(chestType)) continue;
                if (itemKey.equals("trasher")) {
                    String linkMaterialStr;
                    Material linkMaterial;
                    ItemStack linkItem;
                    ItemMeta linkMeta;
                    String linkPath;
                    int linkSlot;
                    String trasherPath = path + ".Items." + (trasherMode = trasherMode.toUpperCase());
                    String materialStr = config.getString(trasherPath + ".material", trasherMode.equals("REMOVE") ? "BARRIER" : "HOPPER");
                    Material trasherMaterial = Material.valueOf((String)materialStr.toUpperCase());
                    ItemStack trasher = new ItemStack(trasherMaterial);
                    ItemMeta trasherMeta = trasher.getItemMeta();
                    if (trasherMeta == null) continue;
                    trasherMeta.setDisplayName(MrLibColors.colorize((String)config.getString(trasherPath + ".name", "&eItem Trasher")));
                    List<String> trasherLore = config.getStringList(trasherPath + ".lore");
                    ArrayList<String> colorizedLore = new ArrayList<String>();
                    for (String line : trasherLore) {
                        colorizedLore.add(MrLibColors.colorize(line));
                    }
                    trasherMeta.setLore(colorizedLore);
                    trasher.setItemMeta(trasherMeta);
                    String finalChestKey = chestKey;
                    MrLibGUI finalGui = gui;
                    gui.setItem(slot, trasher, event -> {
                        Player p = (Player)event.getWhoClicked();
                        boolean isRightClick = event.isRightClick();
                        this.handleMenuClick(p, finalChestKey, "trasher", isRightClick, false, finalGui);
                    });
                    if (!trasherMode.equals("TRANSFER") || (linkSlot = config.getInt((linkPath = path + ".Items.TRANSFER.Link_Item") + ".slot", slot + 1)) >= size || (linkMeta = (linkItem = new ItemStack(linkMaterial = Material.valueOf((String)(linkMaterialStr = config.getString(linkPath + ".material", "HOPPER")).toUpperCase()))).getItemMeta()) == null) continue;
                    linkMeta.setDisplayName(MrLibColors.colorize((String)config.getString(linkPath + ".name", "&eLink Chest")));
                    List<String> linkLore = config.getStringList(linkPath + ".lore");
                    ArrayList<String> colorizedLinkLore = new ArrayList<String>();
                    for (String line : linkLore) {
                        line = line.replace("[LinkedChests]", String.valueOf(linkedChests.size())).replace("[MaxLinks]", String.valueOf(maxLinks));
                        colorizedLinkLore.add(MrLibColors.colorize(line));
                    }
                    linkMeta.setLore(colorizedLinkLore);
                    linkItem.setItemMeta(linkMeta);
                    String finalChestKey2 = chestKey;
                    MrLibGUI finalGui2 = gui;
                    gui.setItem(linkSlot, linkItem, event -> {
                        Player p = (Player)event.getWhoClicked();
                        boolean isRightClick = event.isRightClick();
                        this.handleMenuClick(p, finalChestKey2, "trasher", isRightClick, true, finalGui2);
                    });
                    continue;
                }
                Material material = Material.valueOf((String)config.getString(path + ".material", "STONE").toUpperCase());
                ItemStack item = new ItemStack(material);
                ItemMeta meta = item.getItemMeta();
                if (meta == null) continue;
                meta.setDisplayName(MrLibColors.colorize((String)config.getString(path + ".name", "&eItem")));
                List<String> lore = config.getStringList(path + ".lore");
                ArrayList<String> colorizedLore = new ArrayList<String>();
                for (String line : lore) {
                    String trasherModeDisplay = trasherMode;
                    if (itemKey.equals("trasher") && (trasherModeDisplay = this.plugin.getMessage("trasher_mode_" + trasherMode.toUpperCase())) == null) {
                        trasherModeDisplay = trasherMode;
                    }
                    line = line.replace("[PlayerName]", String.valueOf(ownerName)).replace("[Booster]", String.valueOf(booster)).replace("[ItemsSold]", String.valueOf(itemsSold)).replace("[DeletedItems]", String.valueOf(deletedItems)).replace("[MoneyEarned]", String.format("%.2f", moneyEarned)).replace("[Status]", itemKey.equals("toggle_hologram") ? (hologramEnabled ? this.plugin.getMessage("state_enabled") : this.plugin.getMessage("state_disabled")) : (itemKey.equals("chunk_collector") ? (chunkCollectorEnabled ? this.plugin.getMessage("state_enabled") : this.plugin.getMessage("state_disabled")) : String.valueOf(trasherModeDisplay))).replace("[CurrentPlayers]", String.valueOf(invitedPlayersMap.size())).replace("[MaxPlayers]", String.valueOf(maxPlayersCount)).replace("[CurrentCharge]", String.valueOf(currentChargeMinutes)).replace("[MaxCharge]", String.valueOf(maxChargeMinutes)).replace("[Pricepercharge]", String.format("%.2f", pricePerCharge));
                    colorizedLore.add(MrLibColors.colorize(line));
                }
                meta.setLore(colorizedLore);
                item.setItemMeta(meta);
                String finalChestKey3 = chestKey;
                String finalItemKey = itemKey;
                MrLibGUI finalGui3 = gui;
                gui.setItem(slot, item, event -> {
                    Player p = (Player)event.getWhoClicked();
                    boolean isRightClick = event.isRightClick();
                    this.handleMenuClick(p, finalChestKey3, finalItemKey, isRightClick, false, finalGui3);
                });
            }
        }
        gui.open(player);
    }

    private void handleMenuClick(Player player, String chestKey, String action, boolean isRightClick, boolean isLinkItem, MrLibGUI gui) {
        FileConfiguration config = this.plugin.getConfig();
        switch (action) {
            case "sell_inventory": {
                Inventory inv = this.getChestInventory(chestKey);
                if (inv != null) {
                    player.closeInventory();
                    player.openInventory(inv);
                    break;
                }
                this.plugin.sendMessage(player, this.plugin.getMessage("sell_inventory_not_available"));
                break;
            }
            case "trasher": {
                if (isLinkItem) {
                    this.handleLinkItem(player, chestKey, isRightClick);
                    break;
                }
                this.handleTrasherToggle(player, chestKey, gui);
                break;
            }
            case "toggle_hologram": {
                this.handleHologramToggle(player, chestKey, gui);
                break;
            }
            case "chunk_collector": {
                this.handleChunkCollectorToggle(player, chestKey, gui);
                break;
            }
            case "invite_players": {
                player.closeInventory();
                this.openInvitePlayersMenu(player, chestKey);
                break;
            }
            case "charge_chest": {
                String chestType = this.plugin.getDatabaseManager().getChestType(chestKey);
                if ("Charging".equalsIgnoreCase(chestType)) {
                    int maxCharge;
                    int currentCharge = this.plugin.getDatabaseManager().getChestChargingMinutes(chestKey);
                    if (currentCharge >= (maxCharge = this.plugin.getDatabaseManager().getChestChargingMaxMinutes(chestKey))) {
                        this.plugin.sendMessage(player, this.plugin.getMessage("chest_already_fully_charged"));
                        return;
                    }
                    double pricePerCharge = config.getDouble("MrSellChests.SellChests." + chestType + ".Chest.Charging.PriceForCharge", 10000.0);
                    int minutesPerCharge = config.getInt("MrSellChests.SellChests." + chestType + ".Chest.Charging.PerUpgrade", 100);
                    double playerBalance = this.plugin.getEconomy().getBalance(player);
                    if (playerBalance < pricePerCharge) {
                        String msg = this.plugin.getMessage("chest_charge_not_enough_money");
                        if (msg != null) {
                            msg = msg.replace("[Price]", String.format("%.2f", pricePerCharge));
                            this.plugin.sendMessage(player, msg);
                        }
                        return;
                    }
                    int minutesToAdd = Math.min(minutesPerCharge, maxCharge - currentCharge);
                    double cost = pricePerCharge * ((double)minutesToAdd / (double)minutesPerCharge);
                    this.plugin.getEconomy().withdraw((OfflinePlayer)player, cost);
                    this.plugin.getDatabaseManager().setChestChargingMinutes(chestKey, currentCharge + minutesToAdd);
                    String msg = this.plugin.getMessage("chest_charged_success");
                    if (msg == null) break;
                    msg = msg.replace("[Minutes]", String.valueOf(minutesToAdd)).replace("[Cost]", String.format("%.2f", cost));
                    this.plugin.sendMessage(player, msg);
                    break;
                }
                this.plugin.sendMessage(player, this.plugin.getMessage("chest_type_no_charging"));
            }
        }
    }

    private void handleLinkItem(Player player, String chestKey, boolean isRightClick) {
        String chestType = this.plugin.getDatabaseManager().getChestType(chestKey);
        int maxLinks = this.plugin.getConfig().getInt("MrSellChests.SellChests." + chestType + ".Chest.MaxLinks", 1);
        List<String> linkedChests = this.plugin.getDatabaseManager().getLinkedChests(chestKey);
        if (isRightClick) {
            this.plugin.getDatabaseManager().setLinkedChests(chestKey, new ArrayList<>());
            player.closeInventory();
            this.plugin.sendMessage(player, this.plugin.getMessage("chest_unlink_all"));
            this.openSettingsMenu(player, chestKey);
        } else {
            if (linkedChests.size() >= maxLinks) {
                player.closeInventory();
                this.plugin.sendMessage(player, this.plugin.getMessage("chest_link_max_disabled").replace("{max}", String.valueOf(maxLinks)));
                return;
            }
            this.plugin.getDatabaseManager().setChestLinking(chestKey, true);
            player.closeInventory();
            Object message = this.plugin.getMessage("chest_link_start");
            message = message != null ? ((String)message).replace("{current}", String.valueOf(linkedChests.size())).replace("{max}", String.valueOf(maxLinks)) : "&7Right-click to link chests. Left-click to cancel. Current: " + linkedChests.size() + "/" + maxLinks;
            this.plugin.sendMessage(player, (String)message);
        }
    }

    private void handleTrasherToggle(Player player, String chestKey, MrLibGUI gui) {
        String modeDisplay;
        String currentMode = this.plugin.getDatabaseManager().getChestTrasherMode(chestKey);
        if (currentMode == null) {
            currentMode = "REMOVE";
        }
        String newMode = "REMOVE".equals(currentMode = currentMode.toUpperCase()) ? "KEEP" : ("KEEP".equals(currentMode) ? "TRANSFER" : "REMOVE");
        this.plugin.getDatabaseManager().setChestTrasherMode(chestKey, newMode);
        String rawMessage = this.plugin.getMessage("trasher_mode_changed");
        if (rawMessage == null) {
            rawMessage = "&7Trasher mode changed to: {mode}";
        }
        if ((modeDisplay = this.plugin.getMessage("trasher_mode_" + newMode.toUpperCase())) == null) {
            modeDisplay = newMode;
        }
        String message = rawMessage.replace("{mode}", modeDisplay);
        this.plugin.sendMessage(player, message);
        player.closeInventory();
        this.openSettingsMenu(player, chestKey);
    }

    private void handleHologramToggle(Player player, String chestKey, MrLibGUI gui) {
        if (!this.plugin.getConfig().getBoolean("MrSellChests.Holograms.Enabled", true)) {
            return;
        }
        boolean currentHologram = this.plugin.getDatabaseManager().getChestHologramEnabled(chestKey);
        boolean newHologramState = !currentHologram;
        this.plugin.getDatabaseManager().setChestHologramEnabled(chestKey, newHologramState);
        if (!newHologramState) {
            String cleanKey = chestKey.endsWith(":") ? chestKey.substring(0, chestKey.length() - 1) : chestKey;
            String[] parts = cleanKey.split(":");
            if (parts.length == 4) {
                String holoName = "sellchest_" + parts[0] + "_" + parts[1] + "_" + parts[2] + "_" + parts[3];
                this.plugin.getHologramManager().deleteHologram(holoName);
            }
        } else {
            String cleanKey = chestKey.endsWith(":") ? chestKey.substring(0, chestKey.length() - 1) : chestKey;
            String[] parts = cleanKey.split(":");
            if (parts.length == 4) {
                List<String> hologramLines;
                String holoName = "sellchest_" + parts[0] + "_" + parts[1] + "_" + parts[2] + "_" + parts[3];
                String chestType = this.plugin.getDatabaseManager().getChestType(chestKey);
                if (chestType != null && !(hologramLines = this.plugin.getConfig().getStringList("MrSellChests.SellChests." + chestType + ".Hologram")).isEmpty()) {
                    Location chestLoc = new Location(Bukkit.getWorld((String)parts[0]), (double)Integer.parseInt(parts[1]) + 0.5, (double)Integer.parseInt(parts[2]) + 0.5, (double)Integer.parseInt(parts[3]) + 0.5);
                    boolean playerNearby = false;
                    if (chestLoc.getWorld() != null) {
                        for (Player p : chestLoc.getWorld().getPlayers()) {
                            if (!(p.getLocation().distance(chestLoc) <= 20.0)) continue;
                            playerNearby = true;
                            break;
                        }
                    }
                    if (playerNearby) {
                        double yOffset = "MrLibCore-TextDisplay".equals(this.plugin.getHologramManager().getActiveProviderName()) ? 4.5 : 1.0;
                        Location holoLoc = new Location(Bukkit.getWorld((String)parts[0]), (double)Integer.parseInt(parts[1]) + 0.5, (double)Integer.parseInt(parts[2]) + yOffset, (double)Integer.parseInt(parts[3]) + 0.5);
                        int interval = this.plugin.getConfig().getInt("MrSellChests.SellChests." + chestType + ".Chest.Interval", 10);
                        this.updateHologram(chestKey, holoLoc, hologramLines, interval);
                    }
                }
            }
        }
        player.closeInventory();
        this.openSettingsMenu(player, chestKey);
    }

    private void handleChunkCollectorToggle(Player player, String chestKey, MrLibGUI gui) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> this.handleChunkCollectorToggle(player, chestKey, gui));
            return;
        }
        boolean currentCollector = this.plugin.getDatabaseManager().getChestCollectorEnabled(chestKey);
        boolean newCollectorState = !currentCollector;
        this.plugin.getDatabaseManager().setChestCollectorEnabled(chestKey, newCollectorState);
        if (newCollectorState) {
            String chestType;
            String[] parts = chestKey.split(":");
            if (parts.length == 4 && (chestType = this.plugin.getDatabaseManager().getChestType(chestKey)) != null) {
                this.startCollectorTask(chestKey, parts, chestType);
            }
        } else {
            BukkitTask task = this.collectorTasks.remove(chestKey);
            if (task != null) {
                task.cancel();
            }
        }
        player.closeInventory();
        this.openSettingsMenu(player, chestKey);
    }

    public void registerChestInventory(String key, String chestType) {
        String chestConfigPath = "MrSellChests.SellChests." + chestType + ".Chest";
        FileConfiguration pluginConfig = this.getCachedConfig(chestType);
        this.chestTypes.put(key, chestType);
        int size = pluginConfig.getInt(chestConfigPath + ".Size", 9);
        int interval = pluginConfig.getInt(chestConfigPath + ".Interval", 10);
        String title = MrLibColors.colorize((String)pluginConfig.getString("MrSellChests.SettingsMenu.titleforsellinv", "&8Sell Inventory"));
        Inventory inv = Bukkit.createInventory(null, (int)size, (String)title);
        this.chestInventories.put(key, inv);
        this.sellIntervals.put(key, interval);
        this.lastSellTimes.put(key, System.currentTimeMillis());
    }

    public void shutdown() {
        for (BukkitTask bukkitTask : this.sellTasks.values()) {
            bukkitTask.cancel();
        }
        this.sellTasks.clear();
        for (BukkitTask bukkitTask : this.collectorTasks.values()) {
            bukkitTask.cancel();
        }
        this.collectorTasks.clear();
        this.lastSellTimes.clear();
        for (Map.Entry entry : this.chestInventories.entrySet()) {
            this.saveChestInventory((String)entry.getKey());
        }
        if (this.sellMessageTask != null) {
            this.sellMessageTask.cancel();
        }
        this.playerSellData.clear();
    }

    public void updateTrasherItem(Inventory menu, String chestKey, String trasherMode) {
        FileConfiguration config = this.plugin.getConfig();
        String path = "MrSellChests.SettingsMenu.items.trasher";
        int slot = config.getInt(path + ".slot", 0);
        String trasherPath = path + ".Items." + trasherMode;
        String materialStr = config.getString(trasherPath + ".material", trasherMode.equals("REMOVE") ? "BARRIER" : "HOPPER");
        Material trasherMaterial = Material.valueOf((String)materialStr.toUpperCase());
        ItemStack trasher = new ItemStack(trasherMaterial);
        ItemMeta trasherMeta = trasher.getItemMeta();
        if (trasherMeta != null) {
            trasherMeta.setDisplayName(MrLibColors.colorize((String)config.getString(trasherPath + ".name", "&eItem Trasher")));
            List<String> trasherLore = config.getStringList(trasherPath + ".lore");
            ArrayList<String> colorizedLore = new ArrayList<String>();
            for (String line : trasherLore) {
                colorizedLore.add(MrLibColors.colorize(line));
            }
            trasherMeta.setLore(colorizedLore);
            trasherMeta.getPersistentDataContainer().set(new NamespacedKey((Plugin)this.plugin, "chest_key"), PersistentDataType.STRING, chestKey);
            trasherMeta.getPersistentDataContainer().set(new NamespacedKey((Plugin)this.plugin, "menu_action"), PersistentDataType.STRING, "trasher");
            trasher.setItemMeta(trasherMeta);
            menu.setItem(slot, trasher);
            if (trasherMode.equals("TRANSFER")) {
                String chestType = this.plugin.getDatabaseManager().getChestType(chestKey);
                int maxLinks = config.getInt("MrSellChests.SellChests." + chestType + ".Chest.MaxLinks", 1);
                List<String> linkedChests = this.plugin.getDatabaseManager().getLinkedChests(chestKey);
                String linkPath = path + ".Items.TRANSFER.Link_Item";
                int linkSlot = config.getInt(linkPath + ".slot", slot + 1);
                String linkMaterialStr = config.getString(linkPath + ".material", "HOPPER");
                Material linkMaterial = Material.valueOf((String)linkMaterialStr.toUpperCase());
                ItemStack linkItem = new ItemStack(linkMaterial);
                ItemMeta linkMeta = linkItem.getItemMeta();
                if (linkMeta != null) {
                    linkMeta.setDisplayName(MrLibColors.colorize((String)config.getString(linkPath + ".name", "&eLink Chest")));
                    List<String> linkLore = config.getStringList(linkPath + ".lore");
                    ArrayList<String> colorizedLinkLore = new ArrayList<String>();
                    for (String line : linkLore) {
                        line = line.replace("[LinkedChests]", String.valueOf(linkedChests.size())).replace("[MaxLinks]", String.valueOf(maxLinks));
                        colorizedLinkLore.add(MrLibColors.colorize(line));
                    }
                    linkMeta.setLore(colorizedLinkLore);
                    linkMeta.getPersistentDataContainer().set(new NamespacedKey((Plugin)this.plugin, "chest_key"), PersistentDataType.STRING, chestKey);
                    linkMeta.getPersistentDataContainer().set(new NamespacedKey((Plugin)this.plugin, "menu_action"), PersistentDataType.STRING, "trasher");
                    linkMeta.getPersistentDataContainer().set(new NamespacedKey((Plugin)this.plugin, "Link_Item"), PersistentDataType.STRING, "true");
                    linkItem.setItemMeta(linkMeta);
                    menu.setItem(linkSlot, linkItem);
                }
            } else {
                int linkSlot = config.getInt(path + ".Items.TRANSFER.Link_Item.slot", slot + 1);
                menu.setItem(linkSlot, null);
            }
        }
    }

    public void updateHologram(String chestKey, Location location, List<String> lines, int interval) {
        String cleanKey = chestKey.endsWith(":") ? chestKey.substring(0, chestKey.length() - 1) : chestKey;
        String[] parts = cleanKey.split(":");
        if (parts.length != 4) return;
        
        String holoName = "sellchest_" + parts[0] + "_" + parts[1] + "_" + parts[2] + "_" + parts[3];
        
        this.plugin.getHologramManager().deleteHologram(holoName);
        
        List<String> formattedLines = new ArrayList<>();
        for (String line : lines) {
            formattedLines.add(line.replace("{time}", String.valueOf(interval)));
        }
        
        this.plugin.getHologramManager().createHologram(holoName, location, formattedLines);
    }

    public void updateHologramItem(Inventory menu, String chestKey, boolean enabled) {
        FileConfiguration config = this.plugin.getConfig();
        String path = "MrSellChests.SettingsMenu.items.toggle_hologram";
        int slot = config.getInt(path + ".slot", 0);
        Material material = Material.valueOf((String)config.getString(path + ".material", "STONE").toUpperCase());
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MrLibColors.colorize((String)config.getString(path + ".name", "&eToggle Hologram")));
            List<String> lore = config.getStringList(path + ".lore");
            ArrayList<String> colorizedLore = new ArrayList<String>();
            for (String line : lore) {
                line = line.replace("[Status]", enabled ? this.plugin.getMessage("state_enabled") : this.plugin.getMessage("state_disabled"));
                colorizedLore.add(MrLibColors.colorize(line));
            }
            meta.setLore(colorizedLore);
            meta.getPersistentDataContainer().set(new NamespacedKey((Plugin)this.plugin, "chest_key"), PersistentDataType.STRING, chestKey);
            meta.getPersistentDataContainer().set(new NamespacedKey((Plugin)this.plugin, "menu_action"), PersistentDataType.STRING, "toggle_hologram");
            item.setItemMeta(meta);
            menu.setItem(slot, item);
        }
    }

    public void updateCollectorItem(Inventory menu, String chestKey, boolean enabled) {
        if (enabled) {
            String[] parts;
            String chestType = this.plugin.getDatabaseManager().getChestType(chestKey);
            if (chestType != null && (parts = chestKey.split(":")).length == 4) {
                this.startCollectorTask(chestKey, parts, chestType);
            }
        } else {
            BukkitTask task = this.collectorTasks.remove(chestKey);
            if (task != null) {
                task.cancel();
            }
        }
        FileConfiguration config = this.plugin.getConfig();
        String path = "MrSellChests.SettingsMenu.items.chunk_collector";
        int slot = config.getInt(path + ".slot", 0);
        Material material = Material.valueOf((String)config.getString(path + ".material", "STONE").toUpperCase());
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MrLibColors.colorize((String)config.getString(path + ".name", "&eChunk Collector")));
            List<String> lore = config.getStringList(path + ".lore");
            ArrayList<String> colorizedLore = new ArrayList<String>();
            for (String line : lore) {
                line = line.replace("[Status]", enabled ? this.plugin.getMessage("state_enabled") : this.plugin.getMessage("state_disabled"));
                colorizedLore.add(MrLibColors.colorize(line));
            }
            meta.setLore(colorizedLore);
            meta.getPersistentDataContainer().set(new NamespacedKey((Plugin)this.plugin, "chest_key"), PersistentDataType.STRING, chestKey);
            meta.getPersistentDataContainer().set(new NamespacedKey((Plugin)this.plugin, "menu_action"), PersistentDataType.STRING, "chunk_collector");
            item.setItemMeta(meta);
            menu.setItem(slot, item);
        }
    }

    public ItemStack createSellChestItem(String chestType) {
        ItemStack item;
        ItemMeta meta;
        String path = "MrSellChests.SellChests." + chestType + ".";
        String materialName = this.plugin.getConfig().getString(path + "Chest.Material", "CHEST");
        Material material = Material.matchMaterial((String)materialName);
        if (material == null) {
            material = Material.CHEST;
        }
        if ((meta = (item = new ItemStack(material, 1)).getItemMeta()) != null) {
            meta.setDisplayName(MrLibColors.colorize((String)this.plugin.getConfig().getString(path + "Item.Name", "Sell Chest")));
            List<String> lore = this.plugin.getConfig().getStringList(path + "Item.Lore");
            if (!lore.isEmpty()) {
                ArrayList<String> colorizedLore = new ArrayList<String>();
                for (String line : lore) {
                    colorizedLore.add(MrLibColors.colorize(line));
                }
                meta.setLore(colorizedLore);
            }
            NamespacedKey key = new NamespacedKey((Plugin)this.plugin, "sellchest_type");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, chestType);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createBoosterItem(String boosterId) {
        ItemStack item;
        ItemMeta meta;
        String path = "Boosters." + boosterId + ".";
        String materialName = this.plugin.getBoostersConfig().getString(path + "Item.Material", "DIAMOND");
        Material material = Material.matchMaterial((String)materialName);
        if (material == null) {
            material = Material.DIAMOND;
        }
        if ((meta = (item = new ItemStack(material, 1)).getItemMeta()) != null) {
            String customModelData;
            boolean glowing;
            String name = this.plugin.getBoostersConfig().getString(path + "Item.Name", "&aBooster");
            meta.setDisplayName(MrLibColors.colorize((String)name));
            List<String> lore = this.plugin.getBoostersConfig().getStringList(path + "Item.Lore");
            if (!lore.isEmpty()) {
                ArrayList<String> colorizedLore = new ArrayList<String>();
                for (String line : lore) {
                    colorizedLore.add(MrLibColors.colorize(line));
                }
                meta.setLore(colorizedLore);
            }
            if (glowing = this.plugin.getBoostersConfig().getBoolean(path + "Item.Glowing", false)) {
                meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
                meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
            }
            if (!"NONE".equals(customModelData = this.plugin.getBoostersConfig().getString(path + "Item.CustomModelData", "NONE"))) {
                try {
                    meta.setCustomModelData(Integer.valueOf(Integer.parseInt(customModelData)));
                }
                catch (NumberFormatException colorizedLore) {
                    // empty catch block
                }
            }
            NamespacedKey key = new NamespacedKey((Plugin)this.plugin, "booster_id");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, boosterId);
            item.setItemMeta(meta);
        }
        return item;
    }

    private FileConfiguration getCachedConfig(String chestType) {
        return this.configCache.computeIfAbsent(chestType, k -> this.plugin.getConfig());
    }

    public void openInvitePlayersMenu(Player player, String chestKey) {
        try {
            FileConfiguration config = this.plugin.getInviteMenuConfig();
            if (config == null) {
                config = this.plugin.getConfig();
            }
            String menuPath = "MrSellChests.InvitePlayersMenu";
            String title = config.getString(menuPath + ".title", "&8Invite Players");
            int size = config.getInt(menuPath + ".size", 36);
            Inventory inv = Bukkit.createInventory(null, (int)size, (String)MrLibColors.colorize((String)title));
            if (config.isConfigurationSection(menuPath + ".Filters")) {
                for (String filterKey : config.getConfigurationSection(menuPath + ".Filters").getKeys(false)) {
                    String[] slots;
                    ItemStack filterItem;
                    ItemMeta filterMeta;
                    ConfigurationSection filterSection = config.getConfigurationSection(menuPath + ".Filters." + filterKey);
                    if (!filterSection.getBoolean("enabled", true)) continue;
                    String filterName = filterSection.getString("name", "&e");
                    Material filterMaterial = Material.matchMaterial((String)filterSection.getString("material", "BLACK_STAINED_GLASS_PANE"));
                    if (filterMaterial == null) {
                        filterMaterial = Material.BLACK_STAINED_GLASS_PANE;
                    }
                    if ((filterMeta = (filterItem = new ItemStack(filterMaterial)).getItemMeta()) != null) {
                        filterMeta.setDisplayName(MrLibColors.colorize((String)filterName));
                        filterItem.setItemMeta(filterMeta);
                    }
                    for (String slotStr : slots = filterSection.getString("slot", "").split(",")) {
                        try {
                            int slot = Integer.parseInt(slotStr.trim());
                            if (slot < 0 || slot >= size) continue;
                            inv.setItem(slot, filterItem);
                        }
                        catch (NumberFormatException slot) {
                            // empty catch block
                        }
                    }
                }
            }
            Map<String, Double> invitedPlayers = this.plugin.getDatabaseManager().getChestInvitedPlayers(chestKey);
            int maxPlayers = this.plugin.getDatabaseManager().getChestInvitePlayersMax(chestKey);
            int slot = 10;
            for (Map.Entry<String, Double> entry : invitedPlayers.entrySet()) {
                try {
                    String playerName;
                    UUID playerUUID = UUID.fromString(entry.getKey());
                    Player invitedPlayer = Bukkit.getPlayer((UUID)playerUUID);
                    String string = playerName = invitedPlayer != null ? invitedPlayer.getName() : "Unknown";
                    if (slot >= size || slot >= 25) break;
                    ItemStack playerItem = new ItemStack(Material.PLAYER_HEAD);
                    ItemMeta meta = playerItem.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(MrLibColors.colorize((String)("&c" + playerName)));
                        ArrayList<String> lore = new ArrayList<String>();
                        lore.add("");
                        lore.add("&7Profit share: &f" + String.format("%.0f", entry.getValue() * 100.0) + "%");
                        lore.add("&7Status: &cInvited");
                        lore.add("");
                        lore.add("&7Left click: &eEdit percentage");
                        lore.add("&7Right click: &cUninvite player");
                        for (int i = 0; i < lore.size(); ++i) {
                            lore.set(i, MrLibColors.colorize(lore.get(i)));
                        }
                        meta.setLore(lore);
                        NamespacedKey clickKey = new NamespacedKey((Plugin)this.plugin, "menu_action");
                        meta.getPersistentDataContainer().set(clickKey, PersistentDataType.STRING, "uninvite_player");
                        NamespacedKey chestKeyNS = new NamespacedKey((Plugin)this.plugin, "chest_key");
                        meta.getPersistentDataContainer().set(chestKeyNS, PersistentDataType.STRING, chestKey);
                        NamespacedKey playerUUIDKey = new NamespacedKey((Plugin)this.plugin, "player_uuid");
                        meta.getPersistentDataContainer().set(playerUUIDKey, PersistentDataType.STRING, entry.getKey());
                        playerItem.setItemMeta(meta);
                    }
                    inv.setItem(slot, playerItem);
                    if (++slot % 9 != 7) continue;
                    slot += 2;
                }
                catch (IllegalArgumentException e) {}
            }
            if (config.isConfigurationSection(menuPath + ".items")) {
                for (String itemKey : config.getConfigurationSection(menuPath + ".items").getKeys(false)) {
                    ItemStack item;
                    ItemMeta meta;
                    ConfigurationSection itemSection = config.getConfigurationSection(menuPath + ".items." + itemKey);
                    if (!itemSection.getBoolean("enabled", true)) continue;
                    int itemSlot = itemSection.getInt("slot", 0);
                    String itemName = itemSection.getString("name", "&eItem");
                    Material material = Material.matchMaterial((String)itemSection.getString("material", "STONE"));
                    if (material == null) {
                        material = Material.STONE;
                    }
                    if ((meta = (item = new ItemStack(material)).getItemMeta()) != null) {
                        String processedName = itemName.replace("[CurrentPlayers]", String.valueOf(invitedPlayers.size())).replace("[MaxPlayers]", String.valueOf(maxPlayers));
                        meta.setDisplayName(MrLibColors.colorize((String)processedName));
                        List<String> lore = itemSection.getStringList("lore");
                        if (!lore.isEmpty()) {
                            for (int i = 0; i < lore.size(); ++i) {
                                String line = lore.get(i).replace("[CurrentPlayers]", String.valueOf(invitedPlayers.size())).replace("[MaxPlayers]", String.valueOf(maxPlayers));
                                lore.set(i, MrLibColors.colorize(line));
                            }
                            meta.setLore(lore);
                        }
                        NamespacedKey clickKey = new NamespacedKey((Plugin)this.plugin, "menu_action");
                        meta.getPersistentDataContainer().set(clickKey, PersistentDataType.STRING, itemKey);
                        NamespacedKey chestKeyNS = new NamespacedKey((Plugin)this.plugin, "chest_key");
                        meta.getPersistentDataContainer().set(chestKeyNS, PersistentDataType.STRING, chestKey);
                        item.setItemMeta(meta);
                    }
                    inv.setItem(itemSlot, item);
                }
            }
            player.openInventory(inv);
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    public double getTotalBooster(String chestKey) {
        String uuid;
        String chestType = this.plugin.getDatabaseManager().getChestType(chestKey);
        double base = 1.0;
        if (chestType != null) {
            FileConfiguration config = this.getCachedConfig(chestType);
            base = config.getDouble("MrSellChests.SellChests." + chestType + ".Chest.Booster", 1.0);
        }
        double temp = 0.0;
        long now = System.currentTimeMillis();
        
        long until = this.plugin.getDatabaseManager().getChestTempBoostUntil(chestKey);
        if (until > now) {
             temp = this.plugin.getDatabaseManager().getChestTempBoostValue(chestKey);
             return base + temp;
        } else if (until > 0) {
             this.plugin.getDatabaseManager().setChestTempBoost(chestKey, 0.0, 0L);
        }

        if ((uuid = this.plugin.getDatabaseManager().getChestPlayerUuid(chestKey)) != null) {
            try {
                UUID playerUUID = UUID.fromString(uuid);
                double playerBoost = this.plugin.getDatabaseManager().getPlayerBoostValue(playerUUID);
                long playerUntil = this.plugin.getDatabaseManager().getPlayerBoostUntil(playerUUID);
                if (playerBoost > 0.0 && playerUntil > now) {
                    temp = playerBoost;
                } else if (playerBoost > 0.0) {
                    this.plugin.getDatabaseManager().removePlayerBoost(uuid);
                }
            }
            catch (IllegalArgumentException illegalArgumentException) {
                // empty catch block
            }
        }
        return base + temp;
    }

    public long getBoostTimeLeft(String chestKey) {
        String uuid;
        long now = System.currentTimeMillis();
        
        long until = this.plugin.getDatabaseManager().getChestTempBoostUntil(chestKey);
        if (until > now) {
             return (until - now) / 1000L;
        } else if (until > 0) {
             this.plugin.getDatabaseManager().setChestTempBoost(chestKey, 0.0, 0L);
        }
        
        if ((uuid = this.plugin.getDatabaseManager().getChestPlayerUuid(chestKey)) != null) {
            try {
                UUID playerUUID = UUID.fromString(uuid);
                long playerUntil = this.plugin.getDatabaseManager().getPlayerBoostUntil(playerUUID);
                if (playerUntil > now) {
                    return (playerUntil - now) / 1000L;
                }
                if (playerUntil > 0L) {
                    this.plugin.getDatabaseManager().removePlayerBoost(uuid);
                }
            }
            catch (IllegalArgumentException illegalArgumentException) {
                // empty catch block
            }
        }
        return 0L;
    }

    private static class SellMessageData {
        double totalEarned = 0.0;
        long lastReset = System.currentTimeMillis();

        private SellMessageData() {
        }
    }
}

