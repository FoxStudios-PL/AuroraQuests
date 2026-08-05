package gg.auroramc.quests.menu;

import gg.auroramc.aurora.api.config.premade.ItemConfig;
import gg.auroramc.aurora.api.menu.ItemBuilder;
import gg.auroramc.quests.AuroraQuests;
import org.bukkit.inventory.ItemStack;

/**
 * Drop-in replacements for the {@link ItemBuilder} factories used by the quest menus.
 * <p>
 * Every builder created here has the vanilla tooltip sections (attack damage, attack
 * speed, potion effects, enchantments, ...) hidden, so a menu item only shows the name
 * and lore configured for it. Controlled by {@code menus.hide-vanilla-tooltips}.
 */
public final class MenuItems {
    private MenuItems() {
    }

    public static ItemBuilder of(ItemConfig config) {
        return hideVanillaTooltips(ItemBuilder.of(config));
    }

    public static ItemBuilder close(ItemConfig config) {
        return hideVanillaTooltips(ItemBuilder.close(config));
    }

    public static ItemBuilder back(ItemConfig config) {
        return hideVanillaTooltips(ItemBuilder.back(config));
    }

    public static ItemBuilder item(ItemStack item) {
        return hideVanillaTooltips(ItemBuilder.item(item));
    }

    /**
     * The flags are added to the builder's own copy of the item config, so nothing is
     * written back to the loaded configuration and items configured with explicit flags
     * keep them.
     */
    public static ItemBuilder hideVanillaTooltips(ItemBuilder builder) {
        var configManager = AuroraQuests.getInstance().getConfigManager();
        if (configManager == null || configManager.getConfig() == null) return builder;

        for (var flag : configManager.getConfig().getMenuItemFlags()) {
            builder.flag(flag);
        }

        return builder;
    }
}
