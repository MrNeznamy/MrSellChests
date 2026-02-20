package eu.mrneznamy.mrSellChests.integration;

import eu.mrneznamy.mrSellChests.MrSellChests;
import java.lang.invoke.CallSite;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

public class MrSellChestsItemProviderWrapper {
    private final MrSellChests plugin;

    public MrSellChestsItemProviderWrapper(MrSellChests plugin) {
        this.plugin = plugin;
    }

    public Object createProvider() {
        try {
            Class<?> customItemProviderClass = Class.forName("eu.mrneznamy.mrultimateshop.api.CustomItemProvider");
            return Proxy.newProxyInstance(customItemProviderClass.getClassLoader(), new Class[]{customItemProviderClass}, (InvocationHandler)new CustomItemProviderHandler());
        }
        catch (ClassNotFoundException e) {
            return null;
        }
    }

    private ItemStack getCustomItem(String itemId) {
        if (!this.hasCustomItem(itemId)) {
            return null;
        }
        if (itemId.startsWith("booster_")) {
            String boosterId = itemId.substring(8);
            return this.plugin.getSellChestManager().createBoosterItem(boosterId);
        }
        return this.plugin.getSellChestManager().createSellChestItem(itemId);
    }

    private boolean hasCustomItem(String itemName) {
        if (itemName.startsWith("booster_")) {
            String boosterId = itemName.substring(8);
            ConfigurationSection boostersSection = this.plugin.getBoostersConfig().getConfigurationSection("Boosters");
            return boostersSection != null && boostersSection.contains(boosterId);
        }
        ConfigurationSection chestsSection = this.plugin.getConfig().getConfigurationSection("SellChests");
        return chestsSection != null && chestsSection.contains(itemName);
    }

    private String[] getAvailableItems() {
        ConfigurationSection boostersSection;
        ArrayList<String> items = new ArrayList<String>();
        ConfigurationSection chestsSection = this.plugin.getConfig().getConfigurationSection("SellChests");
        if (chestsSection != null) {
            items.addAll(chestsSection.getKeys(false));
        }
        if ((boostersSection = this.plugin.getBoostersConfig().getConfigurationSection("Boosters")) != null) {
            for (String boosterId : boostersSection.getKeys(false)) {
                items.add("booster_" + boosterId);
            }
        }
        return items.toArray(new String[0]);
    }

    private String getCustomItemDisplayName(String itemId) {
        if (itemId.startsWith("booster_")) {
            String boosterId = itemId.substring(8);
            ConfigurationSection boosterSection = this.plugin.getBoostersConfig().getConfigurationSection("Boosters." + boosterId);
            if (boosterSection != null) {
                return boosterSection.getString("name", "Booster " + boosterId);
            }
        } else {
            ConfigurationSection chestSection = this.plugin.getConfig().getConfigurationSection("MrSellChests.SellChests." + itemId);
            if (chestSection != null) {
                return chestSection.getString("Item.Name", "Sell Chest " + itemId);
            }
        }
        return itemId;
    }

    private class CustomItemProviderHandler
    implements InvocationHandler {
        private CustomItemProviderHandler() {
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName;
            switch (methodName = method.getName()) {
                case "getPluginName": {
                    return "MrSellChests";
                }
                case "getCustomItem": {
                    if (args.length > 0 && args[0] instanceof String) {
                        return MrSellChestsItemProviderWrapper.this.getCustomItem((String)args[0]);
                    }
                    return null;
                }
                case "hasCustomItem": {
                    if (args.length > 0 && args[0] instanceof String) {
                        return MrSellChestsItemProviderWrapper.this.hasCustomItem((String)args[0]);
                    }
                    return false;
                }
                case "getAvailableItems": {
                    return MrSellChestsItemProviderWrapper.this.getAvailableItems();
                }
                case "getCustomItemDisplayName": {
                    if (args.length > 0 && args[0] instanceof String) {
                        return MrSellChestsItemProviderWrapper.this.getCustomItemDisplayName((String)args[0]);
                    }
                    return null;
                }
                case "isAvailable": {
                    return true;
                }
            }
            return null;
        }
    }
}

