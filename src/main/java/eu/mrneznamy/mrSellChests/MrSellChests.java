package eu.mrneznamy.mrSellChests;

import eu.mrneznamy.mrSellChests.Commands;
import eu.mrneznamy.mrSellChests.SellChestListener;
import eu.mrneznamy.mrSellChests.SellChestManager;
import eu.mrneznamy.mrSellChests.TabComplete;
import eu.mrneznamy.mrSellChests.database.SellChestsDatabaseManager;
import eu.mrneznamy.mrSellChests.integration.MrLibGuideIntegration;
import eu.mrneznamy.mrSellChests.integration.MrSellChestsItemProviderWrapper;
import eu.mrneznamy.mrlibcore.MrLibRegisterPlugin;
import eu.mrneznamy.mrlibcore.economy.MrLibVaultManager;
import eu.mrneznamy.mrlibcore.holograms.MrLibHologramManager;
import eu.mrneznamy.mrlibcore.messages.MrLibMessage;
import eu.mrneznamy.mrlibcore.utils.MrLibColors;
import eu.mrneznamy.mrlibcore.utils.MrLibConsoleSayer;
import eu.mrneznamy.mrlibcore.utils.MrLibHelper;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import eu.mrneznamy.mrSellChests.api.MrSellChestsAPI;
import eu.mrneznamy.mrSellChests.api.MrSellChestsAPIImpl;

public class MrSellChests
extends JavaPlugin {
    private MrLibMessage messageSystem;
    private SellChestListener sellChestListener;
    private SellChestManager sellChestManager;
    private SellChestsDatabaseManager databaseManager;
    private String prefix;
    private YamlConfiguration boostersConfig;
    private MrLibGuideIntegration mrLibGuideIntegration;
    private MrSellChestsItemProviderWrapper itemProviderWrapper;
    private Object itemProvider;
    private File messagesFile;
    private FileConfiguration messagesConfig;
    private YamlConfiguration bannedItemsConfig;
    private File bannedItemsFile;
    private File inviteMenuFile;
    private FileConfiguration inviteMenuConfig;

    public void onEnable() {
        if (this.getServer().getPluginManager().getPlugin("MrLibCore") == null) {
            this.getLogger().severe("========================================");
            this.getLogger().severe("MrSellChests requires MrLibCore to run!");
            this.getLogger().severe("Please download MrLibCore from:");
            this.getLogger().severe("https://modrinth.com/plugin/mrlibcore");
            this.getLogger().severe("========================================");
            this.getServer().getPluginManager().disablePlugin((Plugin)this);
            return;
        }
        MrLibRegisterPlugin.register((JavaPlugin)this, (String)"mrsellchests", (boolean)true);
        MrSellChestsAPI.setInstance(new MrSellChestsAPIImpl(this));
        if (!this.getDataFolder().exists()) {
            this.getDataFolder().mkdir();
        }
        this.saveDefaultMessagesFile();
        this.reloadInviteMenuConfig();
        this.downloadBannedItemsFile();
        this.loadBannedItemsCache();
        this.databaseManager = new SellChestsDatabaseManager(this);
        this.messageSystem = new MrLibMessage((JavaPlugin)this);
        this.prefix = MrLibColors.colorize((String)this.messagesConfig.getString("MrSellChests.Prefix", "&8[&aMrSellChests&8]"));
        if (!this.setupEconomy()) {
            String vaultMessage = this.messagesConfig.getString("MrSellChests.vault_not_found", "&cDisabled due to no Vault dependency found!");
            this.getLogger().severe(MrLibColors.colorize((String)vaultMessage));
            this.getServer().getPluginManager().disablePlugin((Plugin)this);
            return;
        }
        this.sellChestManager = new SellChestManager(this);
        this.getCommand("msc").setExecutor((CommandExecutor)new Commands(this));
        this.getCommand("msc").setTabCompleter((TabCompleter)new TabComplete(this));
        this.sellChestListener = new SellChestListener(this);
        this.getServer().getPluginManager().registerEvents((Listener)this.sellChestListener, (Plugin)this);
        File boostersFile = new File(this.getDataFolder(), "boosters.yml");
        if (!boostersFile.exists()) {
            this.saveResource("boosters.yml", false);
        }
        this.boostersConfig = YamlConfiguration.loadConfiguration((File)boostersFile);
        if (this.getServer().getPluginManager().isPluginEnabled("MrLibCore")) {
            this.mrLibGuideIntegration = new MrLibGuideIntegration(this);
            this.mrLibGuideIntegration.register();
        }
        String hologramProvider = "MrLibCore-TextDisplay";
        if (!this.getHologramManager().setProvider(hologramProvider)) {
            MrLibConsoleSayer.MrSay_Warning((String)"Failed to set TextDisplay provider, using default");
        } else {
            MrLibConsoleSayer.MrSay_Success((String)("Hologram Provider: " + hologramProvider));
        }
        this.sellChestListener.removeAllSellChestHolograms();
        Bukkit.getScheduler().runTaskLater((Plugin)this, () -> this.sellChestListener.startHologramUpdateTask(), 20L);
        this.registerCustomItems();
        Bukkit.getScheduler().runTask((Plugin)this, this::registerHelpCommands);
    }

    private boolean setupEconomy() {
        if (this.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        return MrLibVaultManager.getInstance().isEnabled();
    }

    public MrLibVaultManager getEconomy() {
        return MrLibVaultManager.getInstance();
    }

    public MrLibHologramManager getHologramManager() {
        return MrLibHologramManager.getInstance();
    }

    public MrLibMessage getMessageSystem() {
        return this.messageSystem;
    }

    public void sendMessage(Player player, String message) {
        if (message == null || ((String)message).isEmpty()) {
            return;
        }
        if (((String)message).contains("(!NOPREFIX!)")) {
            message = ((String)message).replace("(!NOPREFIX!)", "");
            this.messageSystem.send(player, (String)message);
        } else {
            String prefix = this.getPrefix();
            message = ((String)message).contains("(!message!)") ? ((String)message).replace("(!message!)", "(!message!)" + prefix + " ") : "(!message!)" + prefix + " " + (String)message;
            this.messageSystem.send(player, (String)message);
        }
    }

    public String getPrefix() {
        String prefixFromConfig;
        if (this.messagesConfig != null && (prefixFromConfig = this.messagesConfig.getString("MrSellChests.Prefix")) != null) {
            return prefixFromConfig;
        }
        return this.prefix;
    }

    public String getMessage(String path) {
        File messagesFile = new File(this.getDataFolder(), "messages.yml");
        if (messagesFile.exists()) {
            YamlConfiguration messages = YamlConfiguration.loadConfiguration((File)messagesFile);
            String message = messages.getString("MrSellChests." + path);
            if (message != null) {
                if (message.contains("(!message!)")) {
                    message = message.replace("(!message!)", "");
                }
                return MrLibColors.colorize((String)message);
            }
            message = messages.getString(path);
            if (message != null) {
                if (message.contains("(!message!)")) {
                    message = message.replace("(!message!)", "");
                }
                return MrLibColors.colorize((String)message);
            }
        }
        return this.prefix + " &cMessage not found: " + path;
    }

    public String getMessage(String path, String defaultValue) {
        File messagesFile = new File(this.getDataFolder(), "messages.yml");
        if (messagesFile.exists()) {
            YamlConfiguration messages = YamlConfiguration.loadConfiguration((File)messagesFile);
            String message = messages.getString("MrSellChests." + path);
            if (message != null) {
                if (message.contains("(!message!)")) {
                    message = message.replace("(!message!)", "");
                }
                return MrLibColors.colorize((String)message);
            }
            message = messages.getString(path);
            if (message != null) {
                if (message.contains("(!message!)")) {
                    message = message.replace("(!message!)", "");
                }
                return MrLibColors.colorize((String)message);
            }
        }
        if (defaultValue.contains("(!message!)")) {
            defaultValue = defaultValue.replace("(!message!)", "");
        }
        return this.prefix + " " + MrLibColors.colorize((String)defaultValue);
    }

    private void saveDefaultMessagesFile() {
        this.messagesFile = new File(this.getDataFolder(), "messages.yml");
        this.messagesConfig = YamlConfiguration.loadConfiguration((File)this.messagesFile);
    }

    public FileConfiguration getMessagesConfig() {
        return this.messagesConfig;
    }

    public FileConfiguration getInviteMenuConfig() {
        return this.inviteMenuConfig;
    }

    public String getMessageFromConfig(String path) {
        if (this.messagesConfig != null) {
            String message = this.messagesConfig.getString("MrSellChests." + path);
            if (message == null) {
                message = this.messagesConfig.getString(path);
            }
            return message;
        }
        return null;
    }

    public FileConfiguration getMessages() {
        return this.getConfig();
    }

    private void registerHelpCommands() {
        ArrayList<MrLibHelper.CommandInfo> commands = new ArrayList<MrLibHelper.CommandInfo>();
        commands.add(new MrLibHelper.CommandInfo("/msc give <player> <chest> <amount>", "Give sell chest to player", "/msc give Player123 Basic 1"));
        commands.add(new MrLibHelper.CommandInfo("/msc boost <player> <boost> <duration>", "Give temporary boost to player", "/msc boost Player123 2.0 300"));
        commands.add(new MrLibHelper.CommandInfo("/msc reload", "Reload plugin configuration", "/msc reload"));
        commands.add(new MrLibHelper.CommandInfo("/msc banned <COLLECT/SELL/INV> <add/remove>", "Add or remove item from banned list (Item in your hand)", "/msc banned COLLECT add"));
        MrLibHelper.registerPluginCommands((JavaPlugin)this, (String)"MrSellChests", commands);
    }

    public SellChestManager getSellChestManager() {
        return this.sellChestManager;
    }

    public SellChestsDatabaseManager getDatabaseManager() {
        return this.databaseManager;
    }

    public SellChestListener getSellChestListener() {
        return this.sellChestListener;
    }

    public void reloadConfiguration() {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask((Plugin)this, this::reloadConfiguration);
            return;
        }
        try {
            this.reloadConfig();
            this.reloadBannedItemsCache();
            this.reloadInviteMenuConfig();
            if (this.sellChestManager != null) {
                this.sellChestManager.shutdown();
            }
            this.sellChestManager = new SellChestManager(this);
            if (this.sellChestListener != null) {
                this.sellChestListener.restartHologramUpdateTask();
                this.sellChestListener.updateAllSellChestHolograms();
            }
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void onDisable() {
        if (this.sellChestListener != null) {
            this.sellChestListener.removeAllSellChestHolograms();
        }
        if (this.mrLibGuideIntegration != null) {
            this.mrLibGuideIntegration.unregister();
        }
        if (this.sellChestManager != null) {
            this.sellChestManager.shutdown();
        }
        if (this.databaseManager != null) {
            this.databaseManager.shutdown();
        }
        if (this.itemProvider != null && Bukkit.getPluginManager().getPlugin("MrUltimateShop") != null) {
            try {
                Class<?> apiClass = Class.forName("eu.mrneznamy.mrultimateshop.api.MrUltimateShopAPI");
                Method unregisterMethod = apiClass.getMethod("unregisterItemProvider", String.class);
                unregisterMethod.invoke(null, "MrSellChests");
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
    }

    private void registerCustomItems() {
        Bukkit.getScheduler().runTaskLater((Plugin)this, () -> {
            if (Bukkit.getPluginManager().getPlugin("MrUltimateShop") != null) {
                try {
                    Class<?> apiClass = Class.forName("eu.mrneznamy.mrultimateshop.api.MrUltimateShopAPI");
                    
                    Method getInstanceMethod = apiClass.getMethod("getInstance");
                    Object apiInstance = getInstanceMethod.invoke(null);
                    
                    if (apiInstance != null) {
                        this.itemProviderWrapper = new MrSellChestsItemProviderWrapper(this);
                        this.itemProvider = this.itemProviderWrapper.createProvider();
                        
                        if (this.itemProvider != null) {
                             Method registerMethod = apiClass.getMethod("registerItemProvider", Class.forName("eu.mrneznamy.mrultimateshop.api.CustomItemProvider"));
                             registerMethod.invoke(apiInstance, this.itemProvider);
                             
                             MrLibConsoleSayer.MrSay_Success("Successfully registered custom items with MrUltimateShopAPI!");
                        }
                    } else {
                        this.getLogger().warning("MrUltimateShopAPI instance is null! Is MrUltimateShop enabled?");
                    }
                }
                catch (Exception exception) {
                    this.getLogger().warning("Failed to register with MrUltimateShopAPI: " + exception.getMessage());
                    exception.printStackTrace();
                }
            }
        }, 40L); 
    }

    public YamlConfiguration getBoostersConfig() {
        return this.boostersConfig;
    }

    public void reloadGuide() {
        if (this.mrLibGuideIntegration != null) {
            this.mrLibGuideIntegration.unregister();
            this.mrLibGuideIntegration.register();
        }
    }

    private void loadBannedItemsCache() {
        this.bannedItemsFile = new File(this.getDataFolder(), "banned-items.yml");
        this.bannedItemsConfig = this.bannedItemsFile.exists() ? YamlConfiguration.loadConfiguration((File)this.bannedItemsFile) : new YamlConfiguration();
    }

    public YamlConfiguration getBannedItemsConfig() {
        return this.bannedItemsConfig;
    }

    public void reloadBannedItemsCache() {
        this.loadBannedItemsCache();
    }

    public void reloadInviteMenuConfig() {
        this.inviteMenuFile = new File(this.getDataFolder(), "invitemenu.yml");
        this.inviteMenuConfig = this.inviteMenuFile != null && this.inviteMenuFile.exists() ? YamlConfiguration.loadConfiguration((File)this.inviteMenuFile) : null;
    }

    private void downloadBannedItemsFile() {
        block17: {
            File bannedItemsFile = new File(this.getDataFolder(), "banned-items.yml");
            boolean fileExisted = bannedItemsFile.exists();
            try {
                HttpURLConnection connection;
                block16: {
                    String downloadUrl = "https://fastdl/configs/mrsellchests/banned-items.yml";
                    URL url = new URL(downloadUrl);
                    connection = (HttpURLConnection)url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(10000);
                    int responseCode = connection.getResponseCode();
                    if (responseCode == 200) {
                        try (InputStream inputStream = connection.getInputStream();
                             FileOutputStream outputStream = new FileOutputStream(bannedItemsFile);){
                            int bytesRead;
                            byte[] buffer = new byte[1024];
                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, bytesRead);
                            }
                            break block16;
                        }
                    }
                    if (!fileExisted) {
                        this.createDefaultBannedItemsFile(bannedItemsFile);
                    }
                }
                connection.disconnect();
            }
            catch (IOException e) {
                if (fileExisted) break block17;
                this.createDefaultBannedItemsFile(bannedItemsFile);
            }
        }
    }

    private void createDefaultBannedItemsFile(File bannedItemsFile) {
        try {
            YamlConfiguration defaultConfig = new YamlConfiguration();
            defaultConfig.createSection("SELL");
            defaultConfig.createSection("COLLECT");
            defaultConfig.createSection("INV");
            defaultConfig.setComments("SELL", List.of("Items banned from being sold"));
            defaultConfig.setComments("COLLECT", List.of("Items banned from being collected by chunk collector"));
            defaultConfig.setComments("INV", List.of("Items banned from being placed in sell chest inventory"));
            defaultConfig.save(bannedItemsFile);
        }
        catch (IOException iOException) {
            // empty catch block
        }
    }
}

