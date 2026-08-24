package gg.auroramc.quests.reward;

import gg.auroramc.levels.api.AuroraLevelsProvider;
import org.bukkit.entity.Player;

/**
 * Only bridge between reward scales and AuroraLevels. Kept in its own class so the
 * {@code gg.auroramc.levels} types are never linked unless AuroraLevels is enabled
 * (callers gate on {@code isPluginEnabled} first — no {@code NoClassDefFoundError}).
 * The level lives in memory ({@code LevelData}), so this is safe on the player's thread.
 */
final class AuroraLevelsSource {
    private AuroraLevelsSource() {
    }

    static double level(Player player) {
        return AuroraLevelsProvider.getLeveler().getUserData(player).getLevel();
    }
}
