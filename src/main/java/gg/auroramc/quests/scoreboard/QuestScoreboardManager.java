package gg.auroramc.quests.scoreboard;

import gg.auroramc.quests.AuroraQuests;
import gg.auroramc.quests.api.profile.Profile;
import gg.auroramc.quests.api.quest.Quest;
import gg.auroramc.quests.config.Config;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shows a configurable sidebar to players who are tracking a quest.
 * <p>
 * Performance: only players with an active sidebar own a (per-player, Folia-safe)
 * refresh task; players without a tracked quest cost nothing. Each tick re-reads
 * the current step, so progress and external placeholders stay live, and track /
 * untrack toggles trigger an immediate refresh.
 * <p>
 * When BetterHud popup hiding is enabled (see {@link Config.ScoreboardConfig}), the
 * per-player task polls fast (every {@link #POPUP_POLL_INTERVAL} ticks) so the
 * sidebar disappears/returns almost instantly with the popup, while the (relatively
 * costly) content render is still throttled to {@code refresh-interval} ticks via a
 * per-player counter. With the feature off the task keeps the plain
 * {@code refresh-interval} cadence, so nothing changes for servers without BetterHud.
 */
public class QuestScoreboardManager {
    // Poll cadence (ticks) used only while popup suppression can apply, so hide/restore
    // tracks the popup with no perceptible lag. Content rendering stays throttled to
    // refresh-interval regardless (see renderCycles), keeping each poll cheap.
    private static final long POPUP_POLL_INTERVAL = 2L;

    private final AuroraQuests plugin;
    private final Map<UUID, QuestSidebar> sidebars = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask> tasks = new ConcurrentHashMap<>();
    // Per-player poll countdown until the next full content render (Folia: only ever
    // touched on the player's own region thread, so plain reads/writes are safe).
    private final Map<UUID, Integer> renderCountdown = new ConcurrentHashMap<>();

    public QuestScoreboardManager(AuroraQuests plugin) {
        this.plugin = plugin;
    }

    private Config.ScoreboardConfig config() {
        var cfg = plugin.getConfigManager().getConfig();
        return cfg == null ? null : cfg.getScoreboard();
    }

    private boolean enabled() {
        var c = config();
        return c != null && Boolean.TRUE.equals(c.getEnabled());
    }

    public void onJoin(Player player) {
        if (!enabled()) return;
        startTask(player);
    }

    public void onQuit(Player player) {
        stopTask(player);
        renderCountdown.remove(player.getUniqueId());
        QuestSidebar sidebar = sidebars.remove(player.getUniqueId());
        if (sidebar != null) sidebar.destroy();
    }

    /** Immediate refresh after a track / untrack toggle (shows, updates or hides). */
    public void refresh(Player player) {
        if (player == null || !enabled()) return;
        if (tasks.containsKey(player.getUniqueId())) {
            // Force a content render on this immediate tick so a newly tracked quest
            // shows at once instead of waiting for the next scheduled render.
            renderCountdown.put(player.getUniqueId(), 0);
            player.getScheduler().run(plugin, t -> tick(player), null);
        } else {
            startTask(player);
        }
    }

    public void reload() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            stopTask(player);
            hide(player);
        }
        renderCountdown.clear();
        if (enabled()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                startTask(player);
            }
        }
    }

    public void shutdown() {
        for (ScheduledTask task : tasks.values()) {
            task.cancel();
        }
        tasks.clear();
        renderCountdown.clear();
        for (QuestSidebar sidebar : sidebars.values()) {
            sidebar.destroy();
        }
        sidebars.clear();
    }

    private void startTask(Player player) {
        stopTask(player);
        var cfg = config();
        if (cfg == null) return;
        // Runs on the player's region thread (Folia-safe); first tick after 1 tick.
        ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, t -> tick(player), null, 1L, pollInterval());
        if (task != null) {
            tasks.put(player.getUniqueId(), task);
        }
    }

    private void stopTask(Player player) {
        ScheduledTask task = tasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }

    /** Refresh interval in ticks, clamped to a sane minimum (default 20). */
    private int effectiveRefreshInterval() {
        var cfg = config();
        int interval = (cfg == null || cfg.getRefreshInterval() == null) ? 20 : cfg.getRefreshInterval();
        return Math.max(1, interval);
    }

    /**
     * How often (ticks) the per-player task fires. Fast while popup suppression can
     * apply (so hide/restore is near-instant), otherwise the plain refresh cadence so
     * servers without the feature keep exactly their previous behaviour and cost.
     */
    private long pollInterval() {
        int refresh = effectiveRefreshInterval();
        return popupSuppressionPossible() ? Math.min(POPUP_POLL_INTERVAL, refresh) : refresh;
    }

    /** Number of poll cycles between full content renders (>= 1). */
    private int renderCycles() {
        return Math.max(1, (int) Math.ceil(effectiveRefreshInterval() / (double) pollInterval()));
    }

    private void tick(Player player) {
        UUID id = player.getUniqueId();
        if (!player.isOnline()) {
            stopTask(player);
            renderCountdown.remove(id);
            hide(player);
            return;
        }
        if (!enabled()) {
            hide(player);
            return;
        }

        // Popup guard: while a watched BetterHud popup is active, drop the sidebar so
        // the popup (drawn underneath it) is visible. hide() restores the main
        // scoreboard; the first popup-free tick rebuilds the sidebar from scratch.
        if (isPopupSuppressing(player)) {
            hide(player);
            return;
        }

        Profile profile = plugin.getProfileManager().getProfile(player);
        if (profile == null) {
            hide(player);
            return;
        }

        Quest quest = resolveTracked(profile);
        if (quest == null) {
            hide(player);
            return;
        }

        QuestSidebar sidebar = sidebars.get(id);
        // Throttle the (relatively costly) content render to refresh-interval. A missing
        // sidebar -- first show, or return from a popup -- always renders immediately, so
        // hiding/restoring stays instant while steady-state fast polling stays cheap.
        if (sidebar != null && !renderDue(id)) {
            return;
        }

        var cfg = config();
        var title = ScoreboardRenderer.renderTitle(player, cfg.getTitle());
        var lines = ScoreboardRenderer.renderLines(player, quest, cfg.getLines());

        if (sidebar == null) {
            sidebar = new QuestSidebar(player, title);
            sidebars.put(id, sidebar);
        }
        sidebar.update(title, lines);
        renderCountdown.put(id, renderCycles());
    }

    /**
     * Decrements the player's render clock; returns {@code true} when a full render is
     * due (the caller then renders and resets the clock via {@link #renderCycles()}).
     */
    private boolean renderDue(UUID id) {
        int remaining = renderCountdown.getOrDefault(id, 0) - 1;
        if (remaining > 0) {
            renderCountdown.put(id, remaining);
            return false;
        }
        return true;
    }

    private Quest resolveTracked(Profile profile) {
        var data = profile.getData();
        if (data == null || !data.hasTrackedQuest()) return null;
        var pool = profile.getQuestPool(data.getTrackedPoolId());
        if (pool == null) return null;
        return pool.getQuest(data.getTrackedQuestId());
    }

    /**
     * Whether popup suppression can currently apply at all: the feature is on, at least
     * one group is watched, and BetterHud is installed and enabled. Used to decide the
     * poll cadence. Deliberately references no BetterHud class (only the Bukkit plugin
     * manager), so nothing links BetterHud types when it is absent.
     */
    private boolean popupSuppressionPossible() {
        var cfg = config();
        if (cfg == null || !Boolean.TRUE.equals(cfg.getHideDuringPopups())) return false;
        List<String> groups = cfg.getHideDuringPopupGroups();
        if (groups == null || groups.isEmpty()) return false;
        return Bukkit.getPluginManager().isPluginEnabled("BetterHud");
    }

    /**
     * Whether the sidebar should be hidden right now because a watched BetterHud popup
     * group is active for the player. The {@code isPluginEnabled("BetterHud")} gate is
     * evaluated <em>before</em> {@link BetterHudPopupHook} is referenced, so the bridge
     * class (and its {@code kr.toxicity.hud.*} imports) is never loaded when BetterHud
     * is absent -- no {@code NoClassDefFoundError} is possible.
     */
    private boolean isPopupSuppressing(Player player) {
        var cfg = config();
        if (cfg == null || !Boolean.TRUE.equals(cfg.getHideDuringPopups())) return false;
        List<String> groups = cfg.getHideDuringPopupGroups();
        if (groups == null || groups.isEmpty()) return false;
        if (!Bukkit.getPluginManager().isPluginEnabled("BetterHud")) return false;
        return BetterHudPopupHook.isSuppressing(player, groups);
    }

    private void hide(Player player) {
        QuestSidebar sidebar = sidebars.remove(player.getUniqueId());
        if (sidebar != null) sidebar.destroy();
    }
}
