package gg.auroramc.quests.scoreboard;

import gg.auroramc.quests.AuroraQuests;
import gg.auroramc.quests.api.profile.Profile;
import gg.auroramc.quests.api.quest.Quest;
import gg.auroramc.quests.config.Config;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

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
 */
public class QuestScoreboardManager {
    private final AuroraQuests plugin;
    private final Map<UUID, QuestSidebar> sidebars = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask> tasks = new ConcurrentHashMap<>();

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
        QuestSidebar sidebar = sidebars.remove(player.getUniqueId());
        if (sidebar != null) sidebar.destroy();
    }

    /** Immediate refresh after a track / untrack toggle (shows, updates or hides). */
    public void refresh(Player player) {
        if (player == null || !enabled()) return;
        if (tasks.containsKey(player.getUniqueId())) {
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
        for (QuestSidebar sidebar : sidebars.values()) {
            sidebar.destroy();
        }
        sidebars.clear();
    }

    private void startTask(Player player) {
        stopTask(player);
        var cfg = config();
        if (cfg == null) return;
        int interval = Math.max(1, cfg.getRefreshInterval() == null ? 20 : cfg.getRefreshInterval());
        // Runs on the player's region thread (Folia-safe); first tick after 1 tick.
        ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, t -> tick(player), null, 1L, interval);
        if (task != null) {
            tasks.put(player.getUniqueId(), task);
        }
    }

    private void stopTask(Player player) {
        ScheduledTask task = tasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }

    private void tick(Player player) {
        if (!player.isOnline()) {
            stopTask(player);
            hide(player);
            return;
        }
        if (!enabled()) {
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

        var cfg = config();
        var title = ScoreboardRenderer.renderTitle(player, cfg.getTitle());
        var lines = ScoreboardRenderer.renderLines(player, quest, cfg.getLines());

        QuestSidebar sidebar = sidebars.get(player.getUniqueId());
        if (sidebar == null) {
            sidebar = new QuestSidebar(player, title);
            sidebars.put(player.getUniqueId(), sidebar);
        }
        sidebar.update(title, lines);
    }

    private Quest resolveTracked(Profile profile) {
        var data = profile.getData();
        if (data == null || !data.hasTrackedQuest()) return null;
        var pool = profile.getQuestPool(data.getTrackedPoolId());
        if (pool == null) return null;
        return pool.getQuest(data.getTrackedQuestId());
    }

    private void hide(Player player) {
        QuestSidebar sidebar = sidebars.remove(player.getUniqueId());
        if (sidebar != null) sidebar.destroy();
    }
}
