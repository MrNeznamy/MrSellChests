package eu.mrneznamy.mrSellChests.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import eu.mrneznamy.mrSellChests.MrSellChests;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class SellChestsDatabaseManager {
    private final MrSellChests plugin;
    private final String storageType;
    private HikariDataSource dataSource;
    private final File dataFile;
    private YamlConfiguration dataYaml;
    private final File boostersFile;
    private YamlConfiguration boostersYaml;
    private final File linkedChestsFile;
    private YamlConfiguration linkedChestsYaml;
    private Map<String, Boolean> chestLinking = new HashMap<>();

    public SellChestsDatabaseManager(MrSellChests plugin) {
        this.plugin = plugin;
        this.storageType = plugin.getConfig().getString("Database.Type", "yml");
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        this.boostersFile = new File(plugin.getDataFolder(), "boosters.yml");
        this.linkedChestsFile = new File(plugin.getDataFolder(), "linked_chests.yml");

        if (this.storageType.equalsIgnoreCase("mysql") || this.storageType.equalsIgnoreCase("sqlite")) {
            if (initializeDatabase()) {
                createTables();
            } else {
                loadYAMLFiles();
            }
        } else {
            loadYAMLFiles();
        }
    }

    private void loadYAMLFiles() {
        if (!this.dataFile.exists()) {
            this.dataYaml = new YamlConfiguration();
        } else {
            try {
                this.dataYaml = YamlConfiguration.loadConfiguration(this.dataFile);
            } catch (Exception e) {
                File backupFile = new File(this.plugin.getDataFolder(), "data.yml.backup");
                try {
                    if (this.dataFile.exists()) {
                        Files.copy(this.dataFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (Exception ignored) {}
                this.dataYaml = new YamlConfiguration();
                try {
                    this.dataYaml.save(this.dataFile);
                } catch (Exception ignored) {}
            }
        }
        if (!this.boostersFile.exists()) {
            this.boostersYaml = new YamlConfiguration();
        } else {
            try {
                this.boostersYaml = YamlConfiguration.loadConfiguration(this.boostersFile);
            } catch (Exception e) {
                this.boostersYaml = new YamlConfiguration();
            }
        }
        if (!this.linkedChestsFile.exists()) {
            this.linkedChestsYaml = new YamlConfiguration();
        } else {
            try {
                this.linkedChestsYaml = YamlConfiguration.loadConfiguration(this.linkedChestsFile);
            } catch (Exception e) {
                this.linkedChestsYaml = new YamlConfiguration();
            }
        }
    }

    private boolean initializeDatabase() {
        HikariConfig config = new HikariConfig();
        FileConfiguration fileConfig = plugin.getConfig();

        if (storageType.equalsIgnoreCase("mysql")) {
            config.setJdbcUrl("jdbc:mysql://" + fileConfig.getString("Database.MySQL.Host") + ":" +
                    fileConfig.getInt("Database.MySQL.Port") + "/" +
                    fileConfig.getString("Database.MySQL.Database"));
            config.setUsername(fileConfig.getString("Database.MySQL.Username"));
            config.setPassword(fileConfig.getString("Database.MySQL.Password"));

            // HikariCP Settings
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(10);
            config.setMaxLifetime(1800000); // 30 minutes
            config.setKeepaliveTime(300000); // 5 minutes
            config.setConnectionTimeout(5000); // 5 seconds
            config.setPoolName("MrSellChests-Pool");

            // MySQL Performance Optimizations
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");
            config.addDataSourceProperty("useLocalSessionState", "true");
            config.addDataSourceProperty("rewriteBatchedStatements", "true");
            config.addDataSourceProperty("cacheResultSetMetadata", "true");
            config.addDataSourceProperty("cacheServerConfiguration", "true");
            config.addDataSourceProperty("elideSetAutoCommits", "true");
            config.addDataSourceProperty("maintainTimeStats", "false");
        } else if (storageType.equalsIgnoreCase("sqlite")) {
            String fileName = fileConfig.getString("Database.SQLite.FileName", "sellchests.db");
            config.setJdbcUrl("jdbc:sqlite:" + new File(plugin.getDataFolder(), fileName).getAbsolutePath());
            config.setMaximumPoolSize(1);
        } else {
            return false;
        }

        try {
            this.dataSource = new HikariDataSource(config);
            plugin.getLogger().info("MrSellChests connected to " + storageType + " database!");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to " + storageType + "! Using YAML instead.");
            e.printStackTrace();
            return false;
        }
    }

    private void createTables() {
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("CREATE TABLE IF NOT EXISTS sell_chests (" +
                    "chest_key VARCHAR(255) PRIMARY KEY, " +
                    "owner VARCHAR(100) NOT NULL, " +
                    "player_uuid VARCHAR(36), " +
                    "world VARCHAR(100) NOT NULL, " +
                    "x INTEGER NOT NULL, " +
                    "y INTEGER NOT NULL, " +
                    "z INTEGER NOT NULL, " +
                    "type VARCHAR(50) NOT NULL, " +
                    "hologram_enabled BOOLEAN DEFAULT TRUE, " +
                    "chunk_collector_enabled BOOLEAN DEFAULT FALSE, " +
                    "chunk_loader_enabled BOOLEAN DEFAULT FALSE, " +
                    "trasher_mode VARCHAR(20) DEFAULT 'REMOVE', " +
                    "linked_chests TEXT, " +
                    "invite_players_max INTEGER DEFAULT 1, " +
                    "charging_minutes INTEGER DEFAULT 0, " +
                    "charging_max_minutes INTEGER DEFAULT 1000, " +
                    "boost_temp_value DOUBLE DEFAULT 0.0, " +
                    "boost_temp_until BIGINT DEFAULT 0)")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("CREATE TABLE IF NOT EXISTS chest_statistics (" +
                    "chest_key VARCHAR(255) PRIMARY KEY, " +
                    "items_sold INTEGER DEFAULT 0, " +
                    "deleted_items INTEGER DEFAULT 0, " +
                    "money_earned DOUBLE DEFAULT 0.0)")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("CREATE TABLE IF NOT EXISTS boosters (" +
                    "owner_uuid VARCHAR(36) NOT NULL, " +
                    "booster_id VARCHAR(100) NOT NULL, " +
                    "expiration_time BIGINT NOT NULL, " +
                    "PRIMARY KEY (owner_uuid, booster_id))")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("CREATE TABLE IF NOT EXISTS player_active_boosts (" +
                    "player_uuid VARCHAR(36) PRIMARY KEY, " +
                    "boost_value DOUBLE NOT NULL, " +
                    "expiration_time BIGINT NOT NULL)")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("CREATE TABLE IF NOT EXISTS chest_inventory (" +
                    "chest_key VARCHAR(255) NOT NULL, " +
                    "slot INTEGER NOT NULL, " +
                    "item_data TEXT, " +
                    "PRIMARY KEY (chest_key, slot))")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("CREATE TABLE IF NOT EXISTS chest_invited_players (" +
                    "chest_key VARCHAR(255) NOT NULL, " +
                    "player_uuid VARCHAR(36) NOT NULL, " +
                    "player_name VARCHAR(100) NOT NULL, " +
                    "profit_percentage DOUBLE DEFAULT 0.0, " +
                    "PRIMARY KEY (chest_key, player_uuid))")) {
                ps.executeUpdate();
            }
            plugin.getLogger().info("MrSellChests database tables created/verified!");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean isUsingDatabase() {
        return dataSource != null && !dataSource.isClosed();
    }

    public YamlConfiguration getDataYaml() {
        return this.dataYaml;
    }

    public YamlConfiguration getBoostersYaml() {
        return this.boostersYaml;
    }

    public void saveDataYaml() {
        if (!isUsingDatabase() && this.dataYaml != null) {
            try {
                this.dataYaml.save(this.dataFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void saveBoostersYaml() {
        if (!isUsingDatabase() && this.boostersYaml != null) {
            try {
                this.boostersYaml.save(this.boostersFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
        saveDataYaml();
        saveBoostersYaml();
    }

    public Set<String> getAllChestKeys() {
        if (isUsingDatabase()) {
            Set<String> result = new HashSet<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT chest_key FROM sell_chests");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString("chest_key"));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return result;
        }
        return this.dataYaml.getKeys(false);
    }

    public CompletableFuture<Set<String>> getAllChestKeysAsync() {
        return CompletableFuture.supplyAsync(this::getAllChestKeys);
    }

    public String getChestType(String chestKey) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT type FROM sell_chests WHERE chest_key = ?")) {
                ps.setString(1, chestKey);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("type");
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }
        return this.dataYaml.getString(chestKey + ".type");
    }

    public CompletableFuture<String> getChestTypeAsync(String chestKey) {
        return CompletableFuture.supplyAsync(() -> getChestType(chestKey));
    }

    public boolean getChestCollectorEnabled(String chestKey) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT chunk_collector_enabled FROM sell_chests WHERE chest_key = ?")) {
                ps.setString(1, chestKey);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getBoolean("chunk_collector_enabled");
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return false;
        }
        return this.dataYaml.getBoolean(chestKey + ".chunk_collector_enabled", false);
    }

    public CompletableFuture<Boolean> getChestCollectorEnabledAsync(String chestKey) {
        return CompletableFuture.supplyAsync(() -> getChestCollectorEnabled(chestKey));
    }

    public void setChestCollectorEnabled(String chestKey, boolean enabled) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE sell_chests SET chunk_collector_enabled = ? WHERE chest_key = ?")) {
                ps.setBoolean(1, enabled);
                ps.setString(2, chestKey);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            this.dataYaml.set(chestKey + ".chunk_collector_enabled", enabled);
            this.saveDataYaml();
        }
    }

    public CompletableFuture<Void> setChestCollectorEnabledAsync(String chestKey, boolean enabled) {
        return CompletableFuture.runAsync(() -> setChestCollectorEnabled(chestKey, enabled));
    }

    public boolean getChestChunkLoaderEnabled(String chestKey) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT chunk_loader_enabled FROM sell_chests WHERE chest_key = ?")) {
                ps.setString(1, chestKey);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getBoolean("chunk_loader_enabled");
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return false;
        }
        return this.dataYaml.getBoolean(chestKey + ".chunk_loader_enabled", false);
    }

    public CompletableFuture<Boolean> getChestChunkLoaderEnabledAsync(String chestKey) {
        return CompletableFuture.supplyAsync(() -> getChestChunkLoaderEnabled(chestKey));
    }

    public void setChestChunkLoaderEnabled(String chestKey, boolean enabled) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE sell_chests SET chunk_loader_enabled = ? WHERE chest_key = ?")) {
                ps.setBoolean(1, enabled);
                ps.setString(2, chestKey);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            this.dataYaml.set(chestKey + ".chunk_loader_enabled", enabled);
            this.saveDataYaml();
        }
    }

    public CompletableFuture<Void> setChestChunkLoaderEnabledAsync(String chestKey, boolean enabled) {
        return CompletableFuture.runAsync(() -> setChestChunkLoaderEnabled(chestKey, enabled));
    }

    public int getChestInvitePlayersMax(String chestKey) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT invite_players_max FROM sell_chests WHERE chest_key = ?")) {
                ps.setString(1, chestKey);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("invite_players_max");
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return 1;
        }
        return this.dataYaml.getInt(chestKey + ".invite_players_max", 1);
    }

    public CompletableFuture<Integer> getChestInvitePlayersMaxAsync(String chestKey) {
        return CompletableFuture.supplyAsync(() -> getChestInvitePlayersMax(chestKey));
    }

    public void setChestInvitePlayersMax(String chestKey, int max) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE sell_chests SET invite_players_max = ? WHERE chest_key = ?")) {
                ps.setInt(1, max);
                ps.setString(2, chestKey);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            this.dataYaml.set(chestKey + ".invite_players_max", max);
            this.saveDataYaml();
        }
    }

    public CompletableFuture<Void> setChestInvitePlayersMaxAsync(String chestKey, int max) {
        return CompletableFuture.runAsync(() -> setChestInvitePlayersMax(chestKey, max));
    }

    public Map<String, Double> getChestInvitedPlayers(String chestKey) {
        Map<String, Double> invitedPlayers = new HashMap<>();
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT player_uuid, profit_percentage FROM chest_invited_players WHERE chest_key = ?")) {
                ps.setString(1, chestKey);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        invitedPlayers.put(rs.getString("player_uuid"), rs.getDouble("profit_percentage"));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            ConfigurationSection invitedSection = this.dataYaml.getConfigurationSection(chestKey + ".invited_players");
            if (invitedSection != null) {
                for (String uuid : invitedSection.getKeys(false)) {
                    invitedPlayers.put(uuid, invitedSection.getDouble(uuid + ".profit_percentage", 0.0));
                }
            }
        }
        return invitedPlayers;
    }

    public CompletableFuture<Map<String, Double>> getChestInvitedPlayersAsync(String chestKey) {
        return CompletableFuture.supplyAsync(() -> getChestInvitedPlayers(chestKey));
    }

    public void addChestInvitedPlayer(String chestKey, String playerUuid, String playerName, double profitPercentage) {
        if (isUsingDatabase()) {
            String sql = storageType.equalsIgnoreCase("mysql") ?
                    "INSERT INTO chest_invited_players (chest_key, player_uuid, player_name, profit_percentage) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE player_name = ?, profit_percentage = ?" :
                    "INSERT INTO chest_invited_players (chest_key, player_uuid, player_name, profit_percentage) VALUES (?, ?, ?, ?) ON CONFLICT(chest_key, player_uuid) DO UPDATE SET player_name = ?, profit_percentage = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, chestKey);
                ps.setString(2, playerUuid);
                ps.setString(3, playerName);
                ps.setDouble(4, profitPercentage);
                ps.setString(5, playerName);
                ps.setDouble(6, profitPercentage);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            this.dataYaml.set(chestKey + ".invited_players." + playerUuid + ".name", playerName);
            this.dataYaml.set(chestKey + ".invited_players." + playerUuid + ".profit_percentage", profitPercentage);
            this.saveDataYaml();
        }
    }

    public CompletableFuture<Void> addChestInvitedPlayerAsync(String chestKey, String playerUuid, String playerName, double profitPercentage) {
        return CompletableFuture.runAsync(() -> addChestInvitedPlayer(chestKey, playerUuid, playerName, profitPercentage));
    }

    public void updateChestInvitedPlayerProfitShare(String chestKey, String playerUUID, double profitShare) {
        if (isUsingDatabase()) {
            String sql = "UPDATE chest_invited_players SET profit_percentage = ? WHERE chest_key = ? AND player_uuid = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setDouble(1, profitShare);
                ps.setString(2, chestKey);
                ps.setString(3, playerUUID);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            this.dataYaml.set(chestKey + ".invited_players." + playerUUID + ".profit_percentage", profitShare);
            this.saveDataYaml();
        }
    }

    public void removeChestInvitedPlayer(String chestKey, String playerUuid) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM chest_invited_players WHERE chest_key = ? AND player_uuid = ?")) {
                ps.setString(1, chestKey);
                ps.setString(2, playerUuid);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            this.dataYaml.set(chestKey + ".invited_players." + playerUuid, null);
            this.saveDataYaml();
        }
    }

    public CompletableFuture<Void> removeChestInvitedPlayerAsync(String chestKey, String playerUuid) {
        return CompletableFuture.runAsync(() -> removeChestInvitedPlayer(chestKey, playerUuid));
    }

    public void clearChestInvitedPlayers(String chestKey) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM chest_invited_players WHERE chest_key = ?")) {
                ps.setString(1, chestKey);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            this.dataYaml.set(chestKey + ".invited_players", null);
            this.saveDataYaml();
        }
    }

    public CompletableFuture<Void> clearChestInvitedPlayersAsync(String chestKey) {
        return CompletableFuture.runAsync(() -> clearChestInvitedPlayers(chestKey));
    }

    public int getChestChargingMinutes(String chestKey) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT charging_minutes FROM sell_chests WHERE chest_key = ?")) {
                ps.setString(1, chestKey);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("charging_minutes");
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return 0;
        }
        return this.dataYaml.getInt(chestKey + ".charging_minutes", 0);
    }

    public CompletableFuture<Integer> getChestChargingMinutesAsync(String chestKey) {
        return CompletableFuture.supplyAsync(() -> getChestChargingMinutes(chestKey));
    }

    public void setChestChargingMinutes(String chestKey, int minutes) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE sell_chests SET charging_minutes = ? WHERE chest_key = ?")) {
                ps.setInt(1, minutes);
                ps.setString(2, chestKey);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            this.dataYaml.set(chestKey + ".charging_minutes", minutes);
            this.saveDataYaml();
        }
    }

    public CompletableFuture<Void> setChestChargingMinutesAsync(String chestKey, int minutes) {
        return CompletableFuture.runAsync(() -> setChestChargingMinutes(chestKey, minutes));
    }

    public int getChestChargingMaxMinutes(String chestKey) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT charging_max_minutes FROM sell_chests WHERE chest_key = ?")) {
                ps.setString(1, chestKey);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("charging_max_minutes");
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return 1000;
        }
        return this.dataYaml.getInt(chestKey + ".charging_max_minutes", 1000);
    }

    public CompletableFuture<Integer> getChestChargingMaxMinutesAsync(String chestKey) {
        return CompletableFuture.supplyAsync(() -> getChestChargingMaxMinutes(chestKey));
    }

    public void setChestChargingMaxMinutes(String chestKey, int maxMinutes) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE sell_chests SET charging_max_minutes = ? WHERE chest_key = ?")) {
                ps.setInt(1, maxMinutes);
                ps.setString(2, chestKey);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            this.dataYaml.set(chestKey + ".charging_max_minutes", maxMinutes);
            this.saveDataYaml();
        }
    }

    public CompletableFuture<Void> setChestChargingMaxMinutesAsync(String chestKey, int maxMinutes) {
        return CompletableFuture.runAsync(() -> setChestChargingMaxMinutes(chestKey, maxMinutes));
    }

    public boolean chestExists(String chestKey) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM sell_chests WHERE chest_key = ?")) {
                ps.setString(1, chestKey);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return false;
        }
        return this.dataYaml.contains(chestKey);
    }

    public CompletableFuture<Boolean> chestExistsAsync(String chestKey) {
        return CompletableFuture.supplyAsync(() -> chestExists(chestKey));
    }

    public void createChest(String chestKey, String owner, String playerUuid, String world, int x, int y, int z, String type) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT INTO sell_chests (chest_key, owner, player_uuid, world, x, y, z, type) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, chestKey);
                ps.setString(2, owner);
                ps.setString(3, playerUuid);
                ps.setString(4, world);
                ps.setInt(5, x);
                ps.setInt(6, y);
                ps.setInt(7, z);
                ps.setString(8, type);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            this.dataYaml.set(chestKey + ".owner", owner);
            this.dataYaml.set(chestKey + ".player_uuid", playerUuid);
            this.dataYaml.set(chestKey + ".world", world);
            this.dataYaml.set(chestKey + ".x", x);
            this.dataYaml.set(chestKey + ".y", y);
            this.dataYaml.set(chestKey + ".z", z);
            this.dataYaml.set(chestKey + ".type", type);
            this.saveDataYaml();
        }
    }

    public CompletableFuture<Void> createChestAsync(String chestKey, String owner, String playerUuid, String world, int x, int y, int z, String type) {
        return CompletableFuture.runAsync(() -> createChest(chestKey, owner, playerUuid, world, x, y, z, type));
    }

    public void deleteChest(String chestKey) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM sell_chests WHERE chest_key = ?")) {
                    ps.setString(1, chestKey);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM chest_statistics WHERE chest_key = ?")) {
                    ps.setString(1, chestKey);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM chest_inventory WHERE chest_key = ?")) {
                    ps.setString(1, chestKey);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM chest_invited_players WHERE chest_key = ?")) {
                    ps.setString(1, chestKey);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            this.dataYaml.set(chestKey, null);
            this.saveDataYaml();
        }
    }

    public CompletableFuture<Void> deleteChestAsync(String chestKey) {
        return CompletableFuture.runAsync(() -> deleteChest(chestKey));
    }

    public String getChestOwner(String chestKey) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT owner FROM sell_chests WHERE chest_key = ?")) {
                ps.setString(1, chestKey);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("owner");
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }
        return this.dataYaml.getString(chestKey + ".owner");
    }

    public CompletableFuture<String> getChestOwnerAsync(String chestKey) {
        return CompletableFuture.supplyAsync(() -> getChestOwner(chestKey));
    }

    public String getChestPlayerUuid(String chestKey) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT player_uuid FROM sell_chests WHERE chest_key = ?")) {
                ps.setString(1, chestKey);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("player_uuid");
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }
        return this.dataYaml.getString(chestKey + ".player_uuid");
    }

    public CompletableFuture<String> getChestPlayerUuidAsync(String chestKey) {
        return CompletableFuture.supplyAsync(() -> getChestPlayerUuid(chestKey));
    }

    public boolean getChestHologramEnabled(String chestKey) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT hologram_enabled FROM sell_chests WHERE chest_key = ?")) {
                ps.setString(1, chestKey);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getBoolean("hologram_enabled");
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return true;
        }
        return this.dataYaml.getBoolean(chestKey + ".hologram_enabled", true);
    }

    public CompletableFuture<Boolean> getChestHologramEnabledAsync(String chestKey) {
        return CompletableFuture.supplyAsync(() -> getChestHologramEnabled(chestKey));
    }

    public void setChestHologramEnabled(String chestKey, boolean enabled) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE sell_chests SET hologram_enabled = ? WHERE chest_key = ?")) {
                ps.setBoolean(1, enabled);
                ps.setString(2, chestKey);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            this.dataYaml.set(chestKey + ".hologram_enabled", enabled);
            this.saveDataYaml();
        }
    }

    public CompletableFuture<Void> setChestHologramEnabledAsync(String chestKey, boolean enabled) {
        return CompletableFuture.runAsync(() -> setChestHologramEnabled(chestKey, enabled));
    }

    public String getChestTrasherMode(String chestKey) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT trasher_mode FROM sell_chests WHERE chest_key = ?")) {
                ps.setString(1, chestKey);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("trasher_mode");
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return "REMOVE";
        }
        return this.dataYaml.getString(chestKey + ".trasher_mode", "REMOVE");
    }

    public CompletableFuture<String> getChestTrasherModeAsync(String chestKey) {
        return CompletableFuture.supplyAsync(() -> getChestTrasherMode(chestKey));
    }

    public void setChestTrasherMode(String chestKey, String mode) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE sell_chests SET trasher_mode = ? WHERE chest_key = ?")) {
                ps.setString(1, mode);
                ps.setString(2, chestKey);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            this.dataYaml.set(chestKey + ".trasher_mode", mode);
            this.saveDataYaml();
        }
    }

    public CompletableFuture<Void> setChestTrasherModeAsync(String chestKey, String mode) {
        return CompletableFuture.runAsync(() -> setChestTrasherMode(chestKey, mode));
    }

    public int getItemsSold(String chestKey) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT items_sold FROM chest_statistics WHERE chest_key = ?")) {
                ps.setString(1, chestKey);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("items_sold");
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return 0;
        }
        return this.dataYaml.getInt(chestKey + ".items_sold", 0);
    }

    public CompletableFuture<Integer> getItemsSoldAsync(String chestKey) {
        return CompletableFuture.supplyAsync(() -> getItemsSold(chestKey));
    }

    public void setItemsSold(String chestKey, int count) {
        if (isUsingDatabase()) {
            String sql = storageType.equalsIgnoreCase("mysql") ?
                    "INSERT INTO chest_statistics (chest_key, items_sold) VALUES (?, ?) ON DUPLICATE KEY UPDATE items_sold = ?" :
                    "INSERT INTO chest_statistics (chest_key, items_sold) VALUES (?, ?) ON CONFLICT(chest_key) DO UPDATE SET items_sold = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, chestKey);
                ps.setInt(2, count);
                ps.setInt(3, count);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            this.dataYaml.set(chestKey + ".items_sold", count);
            this.saveDataYaml();
        }
    }

    public void addItemsSold(String chestKey, int amount) {
        if (isUsingDatabase()) {
            String sql = storageType.equalsIgnoreCase("mysql") ?
                    "INSERT INTO chest_statistics (chest_key, items_sold) VALUES (?, ?) ON DUPLICATE KEY UPDATE items_sold = items_sold + ?" :
                    "INSERT INTO chest_statistics (chest_key, items_sold) VALUES (?, ?) ON CONFLICT(chest_key) DO UPDATE SET items_sold = items_sold + ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, chestKey);
                ps.setInt(2, amount);
                ps.setInt(3, amount);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            int current = this.dataYaml.getInt(chestKey + ".items_sold", 0);
            this.dataYaml.set(chestKey + ".items_sold", current + amount);
            this.saveDataYaml();
        }
    }

    public CompletableFuture<Void> setItemsSoldAsync(String chestKey, int count) {
        return CompletableFuture.runAsync(() -> setItemsSold(chestKey, count));
    }

    public int getDeletedItems(String chestKey) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT deleted_items FROM chest_statistics WHERE chest_key = ?")) {
                ps.setString(1, chestKey);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("deleted_items");
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return 0;
        }
        return this.dataYaml.getInt(chestKey + ".deleted_items", 0);
    }

    public CompletableFuture<Integer> getDeletedItemsAsync(String chestKey) {
        return CompletableFuture.supplyAsync(() -> getDeletedItems(chestKey));
    }

    public void setDeletedItems(String chestKey, int count) {
        if (isUsingDatabase()) {
            String sql = storageType.equalsIgnoreCase("mysql") ?
                    "INSERT INTO chest_statistics (chest_key, deleted_items) VALUES (?, ?) ON DUPLICATE KEY UPDATE deleted_items = ?" :
                    "INSERT INTO chest_statistics (chest_key, deleted_items) VALUES (?, ?) ON CONFLICT(chest_key) DO UPDATE SET deleted_items = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, chestKey);
                ps.setInt(2, count);
                ps.setInt(3, count);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            this.dataYaml.set(chestKey + ".deleted_items", count);
            this.saveDataYaml();
        }
    }

    public void addDeletedItems(String chestKey, int amount) {
        if (isUsingDatabase()) {
            String sql = storageType.equalsIgnoreCase("mysql") ?
                    "INSERT INTO chest_statistics (chest_key, deleted_items) VALUES (?, ?) ON DUPLICATE KEY UPDATE deleted_items = deleted_items + ?" :
                    "INSERT INTO chest_statistics (chest_key, deleted_items) VALUES (?, ?) ON CONFLICT(chest_key) DO UPDATE SET deleted_items = deleted_items + ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, chestKey);
                ps.setInt(2, amount);
                ps.setInt(3, amount);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            int current = this.dataYaml.getInt(chestKey + ".deleted_items", 0);
            this.dataYaml.set(chestKey + ".deleted_items", current + amount);
            this.saveDataYaml();
        }
    }

    public CompletableFuture<Void> setDeletedItemsAsync(String chestKey, int count) {
        return CompletableFuture.runAsync(() -> setDeletedItems(chestKey, count));
    }

    public CompletableFuture<Void> addDeletedItemsAsync(String chestKey, int amount) {
        return CompletableFuture.runAsync(() -> addDeletedItems(chestKey, amount));
    }

    public double getMoneyEarned(String chestKey) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT money_earned FROM chest_statistics WHERE chest_key = ?")) {
                ps.setString(1, chestKey);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getDouble("money_earned");
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return 0.0;
        }
        return this.dataYaml.getDouble(chestKey + ".money_earned", 0.0);
    }

    public CompletableFuture<Double> getMoneyEarnedAsync(String chestKey) {
        return CompletableFuture.supplyAsync(() -> getMoneyEarned(chestKey));
    }

    public void setMoneyEarned(String chestKey, double amount) {
        if (isUsingDatabase()) {
            String sql = storageType.equalsIgnoreCase("mysql") ?
                    "INSERT INTO chest_statistics (chest_key, money_earned) VALUES (?, ?) ON DUPLICATE KEY UPDATE money_earned = ?" :
                    "INSERT INTO chest_statistics (chest_key, money_earned) VALUES (?, ?) ON CONFLICT(chest_key) DO UPDATE SET money_earned = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, chestKey);
                ps.setDouble(2, amount);
                ps.setDouble(3, amount);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            this.dataYaml.set(chestKey + ".money_earned", amount);
            this.saveDataYaml();
        }
    }

    public void addMoneyEarned(String chestKey, double amount) {
        if (isUsingDatabase()) {
            String sql = storageType.equalsIgnoreCase("mysql") ?
                    "INSERT INTO chest_statistics (chest_key, money_earned) VALUES (?, ?) ON DUPLICATE KEY UPDATE money_earned = money_earned + ?" :
                    "INSERT INTO chest_statistics (chest_key, money_earned) VALUES (?, ?) ON CONFLICT(chest_key) DO UPDATE SET money_earned = money_earned + ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, chestKey);
                ps.setDouble(2, amount);
                ps.setDouble(3, amount);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            double current = this.dataYaml.getDouble(chestKey + ".money_earned", 0.0);
            this.dataYaml.set(chestKey + ".money_earned", current + amount);
            this.saveDataYaml();
        }
    }

    public CompletableFuture<Void> setMoneyEarnedAsync(String chestKey, double amount) {
        return CompletableFuture.runAsync(() -> setMoneyEarned(chestKey, amount));
    }

    public CompletableFuture<Void> addMoneyEarnedAsync(String chestKey, double amount) {
        return CompletableFuture.runAsync(() -> addMoneyEarned(chestKey, amount));
    }

    public ItemStack getInventoryItem(String chestKey, int slot) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT item_data FROM chest_inventory WHERE chest_key = ? AND slot = ?")) {
                ps.setString(1, chestKey);
                ps.setInt(2, slot);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String data = rs.getString("item_data");
                        if (data != null && !data.isEmpty()) {
                            return deserializeItem(data);
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }
        return this.dataYaml.getItemStack(chestKey + ".inventory." + slot);
    }

    public CompletableFuture<ItemStack> getInventoryItemAsync(String chestKey, int slot) {
        return CompletableFuture.supplyAsync(() -> getInventoryItem(chestKey, slot));
    }

    public void setInventoryItem(String chestKey, int slot, ItemStack item) {
        if (isUsingDatabase()) {
            if (item != null) {
                String itemData = serializeItem(item);
                String sql = storageType.equalsIgnoreCase("mysql") ?
                        "INSERT INTO chest_inventory (chest_key, slot, item_data) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE item_data = ?" :
                        "INSERT INTO chest_inventory (chest_key, slot, item_data) VALUES (?, ?, ?) ON CONFLICT(chest_key, slot) DO UPDATE SET item_data = ?";
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, chestKey);
                    ps.setInt(2, slot);
                    ps.setString(3, itemData);
                    ps.setString(4, itemData);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            } else {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement ps = conn.prepareStatement("DELETE FROM chest_inventory WHERE chest_key = ? AND slot = ?")) {
                    ps.setString(1, chestKey);
                    ps.setInt(2, slot);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        } else {
            this.dataYaml.set(chestKey + ".inventory." + slot, item);
            this.saveDataYaml();
        }
    }

    public CompletableFuture<Void> setInventoryItemAsync(String chestKey, int slot, ItemStack item) {
        return CompletableFuture.runAsync(() -> setInventoryItem(chestKey, slot, item));
    }

    public boolean hasInventoryData(String chestKey) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM chest_inventory WHERE chest_key = ?")) {
                ps.setString(1, chestKey);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return false;
        }
        return this.dataYaml.contains(chestKey + ".inventory");
    }

    public CompletableFuture<Boolean> hasInventoryDataAsync(String chestKey) {
        return CompletableFuture.supplyAsync(() -> hasInventoryData(chestKey));
    }

    public void clearInventory(String chestKey) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM chest_inventory WHERE chest_key = ?")) {
                ps.setString(1, chestKey);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            this.dataYaml.set(chestKey + ".inventory", null);
            this.saveDataYaml();
        }
    }

    public CompletableFuture<Void> clearInventoryAsync(String chestKey) {
        return CompletableFuture.runAsync(() -> clearInventory(chestKey));
    }

    public boolean hasBooster(String playerUuid, String boosterId) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM boosters WHERE owner_uuid = ? AND booster_id = ? AND expiration_time > ?")) {
                ps.setString(1, playerUuid);
                ps.setString(2, boosterId);
                ps.setLong(3, System.currentTimeMillis());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return false;
        }
        ConfigurationSection boosters = this.boostersYaml.getConfigurationSection("Boosters");
        if (boosters != null) {
            for (String key : boosters.getKeys(false)) {
                ConfigurationSection booster = boosters.getConfigurationSection(key);
                if (booster == null || !playerUuid.equals(booster.getString("owner_uuid")) ||
                        !boosterId.equals(booster.getString("booster_id")) ||
                        booster.getLong("expiration_time") <= System.currentTimeMillis()) continue;
                return true;
            }
        }
        return false;
    }

    public CompletableFuture<Boolean> hasBoosterAsync(String playerUuid, String boosterId) {
        return CompletableFuture.supplyAsync(() -> hasBooster(playerUuid, boosterId));
    }

    public void addBooster(String playerUuid, String boosterId, long expirationTime) {
        if (isUsingDatabase()) {
            String sql = storageType.equalsIgnoreCase("mysql") ?
                    "INSERT INTO boosters (owner_uuid, booster_id, expiration_time) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE expiration_time = ?" :
                    "INSERT INTO boosters (owner_uuid, booster_id, expiration_time) VALUES (?, ?, ?) ON CONFLICT(owner_uuid, booster_id) DO UPDATE SET expiration_time = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid);
                ps.setString(2, boosterId);
                ps.setLong(3, expirationTime);
                ps.setLong(4, expirationTime);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            String key = "Boosters." + UUID.randomUUID().toString();
            this.boostersYaml.set(key + ".owner_uuid", playerUuid);
            this.boostersYaml.set(key + ".booster_id", boosterId);
            this.boostersYaml.set(key + ".expiration_time", expirationTime);
            this.saveBoostersYaml();
        }
    }

    public CompletableFuture<Void> addBoosterAsync(String playerUuid, String boosterId, long expirationTime) {
        return CompletableFuture.runAsync(() -> addBooster(playerUuid, boosterId, expirationTime));
    }

    public void removeExpiredBoosters() {
        long currentTime = System.currentTimeMillis();
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM boosters WHERE expiration_time <= ?")) {
                ps.setLong(1, currentTime);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            ConfigurationSection boosters = this.boostersYaml.getConfigurationSection("Boosters");
            if (boosters != null) {
                List<String> toRemove = new ArrayList<>();
                for (String key : boosters.getKeys(false)) {
                    ConfigurationSection booster = boosters.getConfigurationSection(key);
                    if (booster == null || booster.getLong("expiration_time") > currentTime) continue;
                    toRemove.add(key);
                }
                for (String key : toRemove) {
                    this.boostersYaml.set("Boosters." + key, null);
                }
                if (!toRemove.isEmpty()) {
                    this.saveBoostersYaml();
                }
            }
        }
    }

    public CompletableFuture<Void> removeExpiredBoostersAsync() {
        return CompletableFuture.runAsync(this::removeExpiredBoosters);
    }

    public boolean isCollectorEnabled(String chestKey) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT chunk_collector_enabled FROM sell_chests WHERE chest_key = ?")) {
                ps.setString(1, chestKey);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getBoolean("chunk_collector_enabled");
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return false;
        }
        return this.dataYaml.getBoolean(chestKey + ".chunk_collector_enabled", false);
    }

    public CompletableFuture<Boolean> isCollectorEnabledAsync(String chestKey) {
        return CompletableFuture.supplyAsync(() -> isCollectorEnabled(chestKey));
    }

    // This method seems missing in original but needed for consistency if used
    public List<String> getLinkedChests(String chestKey) {
         if (isUsingDatabase()) {
            List<String> linkedChests = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT linked_chests FROM sell_chests WHERE chest_key = ?")) {
                ps.setString(1, chestKey);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String linked = rs.getString("linked_chests");
                        if (linked != null && !linked.isEmpty()) {
                            String[] parts = linked.split(";");
                            Collections.addAll(linkedChests, parts);
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return linkedChests;
        }
        return this.linkedChestsYaml.getStringList(chestKey + ".linked_chests");
    }

    public boolean getChestLinking(String chestKey) {
        return this.chestLinking.getOrDefault(chestKey, false);
    }

    public void setChestLinking(String chestKey, boolean isLinking) {
        if (isLinking) {
            this.chestLinking.put(chestKey, true);
        } else {
            this.chestLinking.remove(chestKey);
        }
    }

    public void setLinkedChests(String chestKey, List<String> linkedChests) {
        if (isUsingDatabase()) {
            String linked = String.join(";", linkedChests);
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE sell_chests SET linked_chests = ? WHERE chest_key = ?")) {
                ps.setString(1, linked);
                ps.setString(2, chestKey);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            this.linkedChestsYaml.set(chestKey + ".linked_chests", linkedChests);
            try {
                this.linkedChestsYaml.save(this.linkedChestsFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private Map<String, Integer> collectorTaskIds = new HashMap<>();
    
    public void setCollectorTaskId(String key, int taskId) {
        collectorTaskIds.put(key, taskId);
    }

    public int getCollectorTaskId(String key) {
        return collectorTaskIds.getOrDefault(key, -1);
    }

    public void shutdown() {
        if (this.dataSource != null && !this.dataSource.isClosed()) {
            this.dataSource.close();
        }
    }

    public boolean isChestInChunk(String world, int chunkX, int chunkZ) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM sell_chests WHERE world = ? AND (x >> 4) = ? AND (z >> 4) = ?")) {
                ps.setString(1, world);
                ps.setInt(2, chunkX);
                ps.setInt(3, chunkZ);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return false;
        } else {
            for (String existingKey : this.dataYaml.getKeys(false)) {
                String[] parts = existingKey.split(":");
                if (parts.length != 4) continue;
                String w = parts[0];
                int x = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[3]);
                if (!w.equals(world) || x >> 4 != chunkX || z >> 4 != chunkZ) continue;
                return true;
            }
            return false;
        }
    }

    public CompletableFuture<Boolean> isChestInChunkAsync(String world, int chunkX, int chunkZ) {
        return CompletableFuture.supplyAsync(() -> isChestInChunk(world, chunkX, chunkZ));
    }

    
    public int getPendingDeletedItems(String chestKey) {
         return pendingDeletedItems.getOrDefault(chestKey, 0);
    }
    
    private Map<String, Integer> pendingDeletedItems = new HashMap<>();

    public void setPendingDeletedItems(String chestKey, int amount) {
        pendingDeletedItems.put(chestKey, amount);
    }

    private String serializeItem(ItemStack item) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(item);
            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private ItemStack deserializeItem(String data) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return item;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void setPlayerBoost(String playerUuid, double value, long expirationTime) {
        if (isUsingDatabase()) {
            String sql = storageType.equalsIgnoreCase("mysql") ?
                    "INSERT INTO player_active_boosts (player_uuid, boost_value, expiration_time) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE boost_value = ?, expiration_time = ?" :
                    "INSERT INTO player_active_boosts (player_uuid, boost_value, expiration_time) VALUES (?, ?, ?) ON CONFLICT(player_uuid) DO UPDATE SET boost_value = ?, expiration_time = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid);
                ps.setDouble(2, value);
                ps.setLong(3, expirationTime);
                ps.setDouble(4, value);
                ps.setLong(5, expirationTime);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            this.dataYaml.set("PlayerBoosts." + playerUuid + ".value", value);
            this.dataYaml.set("PlayerBoosts." + playerUuid + ".until", expirationTime);
            this.saveDataYaml();
        }
    }

    public double getPlayerBoostValue(UUID playerUuid) {
        String uuid = playerUuid.toString();
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT boost_value FROM player_active_boosts WHERE player_uuid = ?")) {
                ps.setString(1, uuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getDouble("boost_value");
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return 0.0;
        }
        return this.dataYaml.getDouble("PlayerBoosts." + uuid + ".value", 0.0);
    }

    public long getPlayerBoostUntil(UUID playerUuid) {
        String uuid = playerUuid.toString();
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT expiration_time FROM player_active_boosts WHERE player_uuid = ?")) {
                ps.setString(1, uuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong("expiration_time");
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return 0L;
        }
        return this.dataYaml.getLong("PlayerBoosts." + uuid + ".until", 0L);
    }

    public void removePlayerBoost(String playerUuid) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM player_active_boosts WHERE player_uuid = ?")) {
                ps.setString(1, playerUuid);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            this.dataYaml.set("PlayerBoosts." + playerUuid, null);
            this.saveDataYaml();
        }
    }

    public double getChestTempBoostValue(String chestKey) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT boost_temp_value FROM sell_chests WHERE chest_key = ?")) {
                ps.setString(1, chestKey);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getDouble("boost_temp_value");
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return 0.0;
        }
        return this.dataYaml.getDouble(chestKey + ".boost_temp.value", 0.0);
    }

    public long getChestTempBoostUntil(String chestKey) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT boost_temp_until FROM sell_chests WHERE chest_key = ?")) {
                ps.setString(1, chestKey);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong("boost_temp_until");
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return 0L;
        }
        return this.dataYaml.getLong(chestKey + ".boost_temp.until", 0L);
    }

    public void setChestTempBoost(String chestKey, double value, long until) {
        if (isUsingDatabase()) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE sell_chests SET boost_temp_value = ?, boost_temp_until = ? WHERE chest_key = ?")) {
                ps.setDouble(1, value);
                ps.setLong(2, until);
                ps.setString(3, chestKey);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            this.dataYaml.set(chestKey + ".boost_temp.value", value);
            this.dataYaml.set(chestKey + ".boost_temp.until", until);
            this.saveDataYaml();
        }
    }

    public CompletableFuture<Void> setChestTempBoostAsync(String chestKey, double value, long until) {
        return CompletableFuture.runAsync(() -> setChestTempBoost(chestKey, value, until));
    }
}
