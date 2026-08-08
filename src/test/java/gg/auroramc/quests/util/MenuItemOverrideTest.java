package gg.auroramc.quests.util;

import gg.auroramc.aurora.api.config.premade.ItemConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class MenuItemOverrideTest {
    private static ItemConfig baseItem() {
        var base = new ItemConfig();
        base.setMaterial("oak_log");
        base.setCustomModelData(0);
        base.setName("&6{name}");
        base.setLore(List.of("&7Base lore"));
        return base;
    }

    private static ItemConfig override(String material, Integer customModelData) {
        var override = new ItemConfig();
        override.setMaterial(material);
        override.setCustomModelData(customModelData);
        return override;
    }

    private static ConfigurationSection section(String... keys) {
        var root = new MemoryConfiguration();
        var section = root.createSection("completed-item");
        for (var key : keys) {
            section.set(key, 1);
        }
        return section;
    }

    @Test
    void returnsNullWhenNoOverrideIsConfigured() {
        assertNull(MenuItemOverride.apply(baseItem(), null, null));
    }

    @Test
    void overrideReplacesTheIcon() {
        var merged = MenuItemOverride.apply(baseItem(), override("PAPER", 880064), section("material", "custom-model-data"));

        assertEquals("PAPER", merged.getMaterial());
        assertEquals(880064, merged.getCustomModelData());
    }

    @Test
    void keysLeftOutOfTheOverrideAreInheritedFromTheBaseItem() {
        var merged = MenuItemOverride.apply(baseItem(), override("PAPER", 880064), section("material", "custom-model-data"));

        assertEquals("&6{name}", merged.getName());
        assertEquals(List.of("&7Base lore"), merged.getLore());
    }

    @Test
    void doesNotMutateTheBaseItem() {
        var base = baseItem();
        var merged = MenuItemOverride.apply(base, override("PAPER", 880064), section("material", "custom-model-data"));

        assertNotSame(base, merged);
        assertEquals("oak_log", base.getMaterial());
        assertEquals(0, base.getCustomModelData());
    }

    @Test
    void keepsTheBaseAmountWhenTheOverrideDoesNotDeclareOne() {
        var base = baseItem();
        base.setAmount(5);

        var merged = MenuItemOverride.apply(base, override("PAPER", 880064), section("material", "custom-model-data"));

        assertEquals(5, merged.getAmount());
    }

    @Test
    void honoursAnAmountDeclaredByTheOverride() {
        var base = baseItem();
        base.setAmount(5);

        var overrideItem = override("PAPER", 880064);
        overrideItem.setAmount(2);

        var merged = MenuItemOverride.apply(base, overrideItem, section("material", "custom-model-data", "amount"));

        assertEquals(2, merged.getAmount());
    }

    @Test
    void keepsTheBaseRefreshFlagWhenTheOverrideDoesNotDeclareOne() {
        var base = baseItem();
        base.setRefresh(true);

        var merged = MenuItemOverride.apply(base, override("PAPER", 880064), section("material", "custom-model-data"));

        assertEquals(true, merged.isRefresh());
    }

    @Test
    void fallsBackToTheOverrideWhenThereIsNoBaseItem() {
        var overrideItem = override("PAPER", 880064);

        assertSame(overrideItem, MenuItemOverride.apply(null, overrideItem, null));
    }
}
