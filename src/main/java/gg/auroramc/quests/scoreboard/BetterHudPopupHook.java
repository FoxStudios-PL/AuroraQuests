package gg.auroramc.quests.scoreboard;

import kr.toxicity.hud.api.BetterHudAPI;
import kr.toxicity.hud.api.player.HudPlayer;
import org.bukkit.entity.Player;

import java.util.Collection;

/**
 * Isolated bridge to BetterHud's popup API.
 * <p>
 * Every {@code kr.toxicity.hud.*} import lives in this class and nowhere else, so
 * the rest of the plugin never triggers class loading of BetterHud types. This
 * class is only ever <em>referenced</em> from
 * {@link QuestScoreboardManager#isPopupSuppressing(Player)} after a
 * {@code Bukkit.getPluginManager().isPluginEnabled("BetterHud")} check has passed,
 * so its BetterHud symbols are only linked when the plugin is actually present.
 * On top of that guard every access is wrapped in a {@code catch (Throwable)}
 * fallback, so a missing, disabled or API-incompatible BetterHud simply disables
 * the feature (returns {@code false}) without any error or
 * {@code NoClassDefFoundError}.
 * <p>
 * API pinned to BetterHud 1.14.1
 * ({@code io.github.toxicity188:BetterHud-standard-api:1.14.1}).
 */
final class BetterHudPopupHook {

    private BetterHudPopupHook() {
    }

    /**
     * Returns {@code true} if the player currently has at least one active popup whose
     * group name is contained in {@code groups}.
     * <p>
     * BetterHud stores an entry keyed by popup <b>group name</b> in
     * {@link HudPlayer#getPopupGroupIteratorMap()} for exactly as long as the popup is
     * showing (it is removed when the popup ends), so this reflects the live state.
     * <p>
     * Never throws. Returns {@code false} on any error. Callers MUST gate this behind
     * an {@code isPluginEnabled("BetterHud")} check so the BetterHud types referenced
     * here are only linked when the plugin is present.
     *
     * @param player the player to inspect
     * @param groups the watched popup group names (must be non-empty for a positive result)
     * @return whether a watched popup group is currently active for the player
     */
    static boolean isSuppressing(Player player, Collection<String> groups) {
        try {
            HudPlayer hudPlayer = BetterHudAPI.inst()
                    .getPlayerManager()
                    .getHudPlayer(player.getUniqueId());
            if (hudPlayer == null) return false;
            // Keys are popup group names; the map is empty when no popup is showing.
            var activeGroups = hudPlayer.getPopupGroupIteratorMap().keySet();
            if (activeGroups.isEmpty()) return false;
            for (String group : groups) {
                if (group != null && activeGroups.contains(group)) return true;
            }
            return false;
        } catch (Throwable ignored) {
            // BetterHud absent, disabled mid-call, or a different API shape -> feature off.
            return false;
        }
    }
}
