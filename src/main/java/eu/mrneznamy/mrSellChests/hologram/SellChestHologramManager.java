package eu.mrneznamy.mrSellChests.hologram;

import eu.mrneznamy.mrSellChests.MrSellChests;
import eu.mrneznamy.mrlibcore.holograms.MrLibHologramManager;
import eu.mrneznamy.mrlibcore.utils.MrLibColors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;

public class SellChestHologramManager {
    private final MrSellChests plugin;
    private final Map<String, List<String>> lastContent = new HashMap<String, List<String>>();
    private final MrLibHologramManager hologramManager;

    public SellChestHologramManager(MrSellChests plugin) {
        this.plugin = plugin;
        this.hologramManager = MrLibHologramManager.getInstance();
    }

    public void createHologram(String key, Location base, List<String> lines) {
        this.showLines(key, base, lines);
    }

    public void deleteHologram(String key) {
        this.hologramManager.deleteHologram(key);
        this.lastContent.remove(key);
    }

    public void showLines(String key, Location base, List<String> lines) {
        if (base == null || base.getWorld() == null || lines == null || lines.isEmpty()) {
            return;
        }
        List<String> lastLines = this.lastContent.get(key);
        if (lastLines != null && lastLines.equals(lines)) {
            return;
        }
        ArrayList<String> colorized = new ArrayList<String>();
        for (String raw : lines) {
            if (raw == null || raw.trim().isEmpty()) continue;
            colorized.add(MrLibColors.colorize((String)raw));
        }
        this.lastContent.put(key, new ArrayList(colorized));
        if (this.hologramManager.hologramExists(key)) {
            this.hologramManager.updateHologram(key, colorized);
        } else {
            this.hologramManager.createHologram(key, base, colorized);
        }
    }

    public void remove(String key) {
        this.hologramManager.deleteHologram(key);
        this.lastContent.remove(key);
    }

    public void removeAll() {
        for (String key : new ArrayList<String>(this.lastContent.keySet())) {
            this.hologramManager.deleteHologram(key);
        }
        this.lastContent.clear();
    }

    public boolean exists(String key) {
        return this.hologramManager.hologramExists(key);
    }

    public void cleanup() {
        this.hologramManager.cleanupAllHolograms();
        this.lastContent.clear();
    }
}

