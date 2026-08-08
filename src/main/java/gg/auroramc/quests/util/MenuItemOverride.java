package gg.auroramc.quests.util;

import gg.auroramc.aurora.api.config.premade.ItemConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

/**
 * Applies a per-status icon override ({@code in-progress-item}, {@code completed-item}) on
 * top of a quest's base {@code menu-item}.
 * <p>
 * The override follows the same rules as every other item override in the plugin
 * ({@link ItemConfig#merge(ItemConfig)}): a key written in the override wins, a key left
 * out is inherited from the base item. So an override that only sets {@code material} and
 * {@code custom-model-data} swaps the icon and keeps the quest's name, lore and flags.
 */
public final class MenuItemOverride {
    private MenuItemOverride() {
    }

    /**
     * @param base        the quest's {@code menu-item}
     * @param override    the status override, {@code null} when the quest doesn't define one
     * @param rawOverride the override's raw YAML section, used to tell a key that was really
     *                    written from one the deserializer filled with a default
     * @return {@code null} when there is no override, otherwise the effective item to display
     */
    public static @Nullable ItemConfig apply(@Nullable ItemConfig base, @Nullable ItemConfig override,
                                             @Nullable ConfigurationSection rawOverride) {
        if (override == null) return null;
        if (base == null) return override;

        var merged = base.merge(override);

        // ItemConfig#merge copies amount and refresh unconditionally, and the deserializer
        // hands every override a non-null amount (1) and refresh (false) even when the keys
        // are absent. Only honour them when the override really declares them, so swapping
        // the material can never reset the base item's stack size or refresh behaviour.
        if (rawOverride == null || !rawOverride.contains("amount")) {
            merged.setAmount(base.getAmount());
        }
        if (rawOverride == null || !rawOverride.contains("refresh")) {
            merged.setRefresh(base.isRefresh());
        }

        return merged;
    }
}
