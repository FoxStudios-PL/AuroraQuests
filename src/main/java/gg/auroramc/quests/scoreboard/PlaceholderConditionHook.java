package gg.auroramc.quests.scoreboard;

import gg.auroramc.quests.config.Config;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Isolated bridge to PlaceholderAPI for {@code scoreboard.hide-when}.
 * <p>
 * Every {@code me.clip.*} import lives in this class only. It is referenced from
 * {@link QuestScoreboardManager} exclusively after an
 * {@code isPluginEnabled("PlaceholderAPI")} check, so PlaceholderAPI types are only
 * linked when the plugin is present; every call is additionally wrapped in a
 * {@code catch (Throwable)} so a missing or incompatible PlaceholderAPI simply turns
 * the feature off (returns {@code false}).
 */
final class PlaceholderConditionHook {

    private PlaceholderConditionHook() {
    }

    /**
     * Returns {@code true} if at least one condition matches for the player: the
     * placeholder, once resolved, equals the expected value (trimmed, case-insensitive).
     * Never throws.
     */
    static boolean anyMatches(Player player, List<Config.HideCondition> conditions) {
        try {
            for (Config.HideCondition condition : conditions) {
                String resolved = PlaceholderAPI.setPlaceholders(player, condition.placeholder());
                if (resolved != null && resolved.trim().equalsIgnoreCase(condition.equalsValue())) {
                    return true;
                }
            }
            return false;
        } catch (Throwable ignored) {
            // PlaceholderAPI absent, disabled mid-call, or an expansion threw -> not hidden.
            return false;
        }
    }
}
