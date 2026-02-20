package eu.mrneznamy.mrSellChests.api;

import eu.mrneznamy.mrSellChests.MrSellChests;
import java.util.HashMap;
import java.util.Map;

public class MrSellChestsAPIImpl extends MrSellChestsAPI {

    private final MrSellChests plugin;
    private final Map<String, PriceProvider> providers = new HashMap<>();

    public MrSellChestsAPIImpl(MrSellChests plugin) {
        this.plugin = plugin;
    }

    @Override
    public void registerPriceProvider(PriceProvider provider) {
        if (provider == null) return;
        providers.put(provider.getName(), provider);
        plugin.getLogger().info("Registered price provider: " + provider.getName());
    }

    @Override
    public void unregisterPriceProvider(String name) {
        if (name == null) return;
        providers.remove(name);
        plugin.getLogger().info("Unregistered price provider: " + name);
    }

    @Override
    public PriceProvider getPriceProvider(String name) {
        return providers.get(name);
    }

    @Override
    public PriceProvider getActiveProvider() {
        String providerName = plugin.getConfig().getString("MrSellChests.SellPrices.Provider", "FILE");
        PriceProvider provider = providers.get(providerName);
        if (provider == null) {
            return providers.get("FILE");
        }
        return provider;
    }
}
