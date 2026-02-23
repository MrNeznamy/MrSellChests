package eu.mrneznamy.mrSellChests.integration;

import eu.mrneznamy.mrSellChests.MrSellChests;
import eu.mrneznamy.mrlibcore.utils.MrLibConsoleSayer;
import org.bukkit.inventory.ItemStack;

/**
 * Central handler that aggregates Nexo, Oraxen and ItemsAdder integrations.
 *
 * <p>Usage in price lookups / ban checks:</p>
 * <pre>
 *   String id = plugin.getCustomItemIntegration().getCustomItemId(item);
 *   // id will be prefixed:  "nexo:my_item", "oraxen:my_item", "itemsadder:ns:my_item"
 * </pre>
 *
 * <p>In config files operators can refer to custom items with the prefixed syntax:
 * <ul>
 *   <li>{@code nexo:<id>}</li>
 *   <li>{@code oraxen:<id>}</li>
 *   <li>{@code itemsadder:<namespace>:<id>}</li>
 * </ul>
 *
 * @author graffs444 (https://github.com/graffs444)
 */
public class CustomItemIntegration {

    /** Prefix used in configuration values to address Nexo items. */
    public static final String NEXO_PREFIX        = "nexo:";
    /** Prefix used in configuration values to address Oraxen items. */
    public static final String ORAXEN_PREFIX      = "oraxen:";
    /** Prefix used in configuration values to address ItemsAdder items. */
    public static final String ITEMSADDER_PREFIX  = "itemsadder:";

    private final NexoIntegration       nexo;
    private final OraxenIntegration     oraxen;
    private final ItemsAdderIntegration itemsAdder;

    public CustomItemIntegration(MrSellChests plugin) {
        nexo       = new NexoIntegration();
        oraxen     = new OraxenIntegration();
        itemsAdder = new ItemsAdderIntegration();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the prefixed custom-item ID for the given ItemStack, or
     * {@code null} if the item is not a custom item from any supported plugin.
     *
     * <ul>
     *   <li>Nexo items  → {@code "nexo:<nexoId>"}</li>
     *   <li>Oraxen items → {@code "oraxen:<oraxenId>"}</li>
     *   <li>ItemsAdder items → {@code "itemsadder:<namespace>:<name>"}</li>
     * </ul>
     */
    public String getCustomItemId(ItemStack item) {
        if (item == null) return null;

        if (nexo.isAvailable()) {
            String id = nexo.getItemId(item);
            if (id != null) return NEXO_PREFIX + id;
        }

        if (oraxen.isAvailable()) {
            String id = oraxen.getItemId(item);
            if (id != null) return ORAXEN_PREFIX + id;
        }

        if (itemsAdder.isAvailable()) {
            String id = itemsAdder.getItemId(item);
            if (id != null) return ITEMSADDER_PREFIX + id;
        }

        return null;
    }

    /**
     * Returns {@code true} if the ItemStack matches the supplied config value.
     *
     * <p>The config value must start with one of the recognised prefixes
     * ({@code nexo:}, {@code oraxen:}, {@code itemsadder:}).  Comparison is
     * case-insensitive.</p>
     */
    public boolean matchesConfigId(ItemStack item, String configId) {
        if (item == null || configId == null || configId.isEmpty()) return false;
        String lower = configId.toLowerCase();

        if (lower.startsWith(NEXO_PREFIX) && nexo.isAvailable()) {
            return nexo.matchesId(item, configId.substring(NEXO_PREFIX.length()));
        }
        if (lower.startsWith(ORAXEN_PREFIX) && oraxen.isAvailable()) {
            return oraxen.matchesId(item, configId.substring(ORAXEN_PREFIX.length()));
        }
        if (lower.startsWith(ITEMSADDER_PREFIX) && itemsAdder.isAvailable()) {
            return itemsAdder.matchesId(item, configId.substring(ITEMSADDER_PREFIX.length()));
        }
        return false;
    }

    /**
     * Returns {@code true} if {@code configId} uses one of the recognised
     * custom-item prefixes (regardless of whether the integration is available).
     */
    public static boolean isCustomItemId(String configId) {
        if (configId == null) return false;
        String lower = configId.toLowerCase();
        return lower.startsWith(NEXO_PREFIX)
            || lower.startsWith(ORAXEN_PREFIX)
            || lower.startsWith(ITEMSADDER_PREFIX);
    }

    /**
     * Attempts to build an ItemStack from a prefixed config ID.
     *
     * @return the ItemStack, or {@code null} if not found / plugin unavailable.
     */
    public ItemStack buildItem(String configId) {
        if (configId == null || configId.isEmpty()) return null;
        String lower = configId.toLowerCase();

        if (lower.startsWith(NEXO_PREFIX)) {
            return nexo.buildItem(configId.substring(NEXO_PREFIX.length()));
        }
        if (lower.startsWith(ORAXEN_PREFIX)) {
            return oraxen.buildItem(configId.substring(ORAXEN_PREFIX.length()));
        }
        if (lower.startsWith(ITEMSADDER_PREFIX)) {
            return itemsAdder.buildItem(configId.substring(ITEMSADDER_PREFIX.length()));
        }
        return null;
    }

    // Convenience accessors
    public NexoIntegration       getNexo()       { return nexo; }
    public OraxenIntegration     getOraxen()     { return oraxen; }
    public ItemsAdderIntegration getItemsAdder() { return itemsAdder; }

    public boolean isAnyAvailable() {
        return nexo.isAvailable() || oraxen.isAvailable() || itemsAdder.isAvailable();
    }
}
