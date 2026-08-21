package gg.auroramc.quests.advancement;

import gg.auroramc.aurora.api.menu.ItemBuilder;
import gg.auroramc.aurora.api.message.Text;
import gg.auroramc.quests.AuroraQuests;
import gg.auroramc.quests.advancement.AdvancementModel.QuestNode;
import gg.auroramc.quests.advancement.AdvancementModel.TabModel;
import gg.auroramc.quests.api.event.QuestCompletedEvent;
import gg.auroramc.quests.api.event.QuestPoolReRolledEvent;
import gg.auroramc.quests.api.profile.Profile;
import gg.auroramc.quests.api.quest.Quest;
import gg.auroramc.quests.config.Config;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.resources.Identifier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Mirrors quests into the vanilla advancement screen, per player.
 * <p>
 * <b>Performance model (built for 250+ online players):</b>
 * <ul>
 *   <li>All player-independent data (tabs, trees, layout, NMS requirement objects,
 *       description templates) lives in one shared immutable {@link AdvancementModel},
 *       rebuilt only on {@code /quests reload}.</li>
 *   <li>No polling: quest progress marks the quest dirty in an O(1) concurrent set.
 *       Idle players cost strictly nothing.</li>
 *   <li>A single async flusher drains dirty players every {@code flush-interval-ticks};
 *       each player gets at most one packet per window, built on their own region
 *       thread (Folia-safe) and containing only the entries that actually changed.
 *       A pure progress tick is a few dozen bytes on the wire.</li>
 *   <li>Structural changes (join, quest completed, pool rerolled, config reload) fall
 *       back to a full tab resync for that player only — a few kilobytes.</li>
 * </ul>
 * The module is fully optional: when {@code advancement-gui.enabled} is false nothing
 * is registered beyond no-op event guards, and it can be toggled live via reload.
 */
public class AdvancementGuiManager implements Listener {
    /** Requirements/criteria of tab roots (always completed, single criterion). */
    private static final AdvancementRequirements ROOT_REQUIREMENTS =
            AdvancementRequirements.allOf(AdvancementPacketFactory.criteriaNames(1));

    private final AuroraQuests plugin;
    private final Map<UUID, PlayerAdvancementView> views = new ConcurrentHashMap<>();

    private volatile AdvancementModel model = AdvancementModel.empty();
    private volatile boolean active = false;
    private volatile boolean hideVanillaTabs = false;
    private volatile boolean completionToast = true;
    private volatile boolean commandHint = false;
    private volatile boolean selectQuestTab = true;
    private ScheduledTask flushTask;

    public AdvancementGuiManager(AuroraQuests plugin) {
        this.plugin = plugin;
    }

    public boolean isActive() {
        return active;
    }

    /** Whether {@code /quests} should send the "press L" hint instead of the menu. */
    public boolean isCommandHint() {
        return active && commandHint;
    }

    // ------------------------------------------------------------------ lifecycle

    /** (Re)builds the shared model from the loaded pools and applies config changes. */
    public void reload() {
        var cfg = config();
        boolean wasActive = active;
        boolean wasHide = hideVanillaTabs;

        active = cfg != null && Boolean.TRUE.equals(cfg.getEnabled());
        hideVanillaTabs = active && Boolean.TRUE.equals(cfg.getHideVanillaTabs());
        completionToast = cfg == null || !Boolean.FALSE.equals(cfg.getCompletionToast());
        commandHint = cfg != null && "message".equalsIgnoreCase(cfg.getCommandBehavior());
        selectQuestTab = cfg == null || !Boolean.FALSE.equals(cfg.getSelectQuestTab());

        if (active) {
            model = AdvancementModelBuilder.build(plugin.getPoolManager().getPools(), cfg);
            AuroraQuests.logger().info("Advancement GUI enabled: " + model.getTabs().size() + " tab(s), "
                    + model.getNodes().size() + " quest(s) mirrored into the vanilla progress screen.");
        } else {
            model = AdvancementModel.empty();
        }

        restartFlushTask(cfg);

        if (wasActive && !active) {
            // Turned off live: cleanly remove our tabs (and restore vanilla ones if we hid them).
            for (var view : views.values()) {
                var player = Bukkit.getPlayer(view.playerId);
                if (player == null) continue;
                boolean restoreVanilla = wasHide;
                player.getScheduler().run(plugin, t -> clearClient(player, view, restoreVanilla), null);
            }
            views.clear();
            return;
        }

        if (active) {
            if (wasHide && !hideVanillaTabs) {
                // Vanilla tabs were hidden and should come back: replay the vanilla sync.
                for (var player : Bukkit.getOnlinePlayers()) {
                    player.getScheduler().run(plugin, t -> AdvancementPacketFactory.resendVanillaAdvancements(player), null);
                }
            }
            for (var player : Bukkit.getOnlinePlayers()) {
                if (plugin.getProfileManager().getProfile(player) != null) {
                    onJoin(player);
                }
            }
            for (var view : views.values()) {
                view.forgetClient = wasHide && !hideVanillaTabs || !wasActive;
                view.fullResync = true;
            }
        }
    }

    public void onJoin(Player player) {
        if (!active) return;
        views.computeIfAbsent(player.getUniqueId(), PlayerAdvancementView::new);
    }

    public void onQuit(Player player) {
        views.remove(player.getUniqueId());
    }

    public void shutdown() {
        if (flushTask != null && !flushTask.isCancelled()) {
            flushTask.cancel();
            flushTask = null;
        }
        views.clear();
    }

    private void restartFlushTask(Config.AdvancementGuiConfig cfg) {
        if (flushTask != null && !flushTask.isCancelled()) {
            flushTask.cancel();
            flushTask = null;
        }
        if (!active) return;
        long intervalMs = Math.max(1, cfg.getFlushIntervalTicks() != null ? cfg.getFlushIntervalTicks() : 10) * 50L;
        flushTask = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> flushAll(), intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    // ------------------------------------------------------------------ dirt marking

    /**
     * Requests a full resync of one player's quest screen on the next flush window.
     * Used by admin actions whose effect is structural but fires no quest event
     * (e.g. {@code /quests unlock}), so state flips show without a reconnect.
     */
    public void refresh(Player player) {
        if (!active || player == null) return;
        var view = views.get(player.getUniqueId());
        if (view != null) view.fullResync = true;
    }

    /**
     * O(1) hot-path hook called from quest task progress/completion. Safe from any
     * thread; a no-op while the module is off or the quest isn't displayed.
     */
    public void markProgressDirty(Player player, String poolId, String questId) {
        if (!active || player == null) return;
        var view = views.get(player.getUniqueId());
        if (view == null) return;
        var key = poolId + "/" + questId;
        if (model.getNodes().containsKey(key)) {
            view.dirty.add(key);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuestCompleted(QuestCompletedEvent event) {
        if (!active) return;
        // Completions can unlock other quests, reveal hidden ones or trigger
        // reroll-on-completion: resync the whole view (rare event, cheap packet).
        var view = views.get(event.getPlayer().getUniqueId());
        if (view != null) view.fullResync = true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPoolReRolled(QuestPoolReRolledEvent event) {
        if (!active) return;
        var view = views.get(event.getPlayer().getUniqueId());
        if (view != null) view.fullResync = true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerResourcesReloaded(ServerResourcesReloadedEvent event) {
        if (!active) return;
        // A datapack reload makes vanilla send reset packets: clients forgot our tabs.
        for (var view : views.values()) {
            view.forgetClient = true;
            view.fullResync = true;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVanillaAdvancementDone(PlayerAdvancementDoneEvent event) {
        if (!active || !hideVanillaTabs) return;
        // The vanilla system just re-sent an advancement to this client; re-clear.
        var view = views.get(event.getPlayer().getUniqueId());
        if (view != null) view.fullResync = true;
    }

    // ------------------------------------------------------------------ flushing

    private void flushAll() {
        if (!active) return;
        for (var view : views.values()) {
            if (!view.fullResync && view.dirty.isEmpty()) continue;
            if (!view.flushScheduled.compareAndSet(false, true)) continue;

            var player = Bukkit.getPlayer(view.playerId);
            if (player == null) {
                views.remove(view.playerId);
                view.flushScheduled.set(false);
                continue;
            }
            // Render + send on the player's own region thread (Folia-safe; PlaceholderAPI
            // and quest data are only touched there). Netty accepts sends from any thread.
            player.getScheduler().run(plugin, t -> {
                try {
                    flush(player, view);
                } catch (Exception e) {
                    AuroraQuests.logger().warning("[advancement-gui] Failed to sync advancements for "
                            + player.getName() + ": " + e);
                } finally {
                    view.flushScheduled.set(false);
                }
            }, () -> view.flushScheduled.set(false));
        }
    }

    private void flush(Player player, PlayerAdvancementView view) {
        if (!active || !player.isOnline()) return;
        var profile = plugin.getProfileManager().getProfile(player);
        if (profile == null) return; // keep flags, retry next window

        if (view.forgetClient) {
            view.sent.clear();
            view.sentRoots.clear();
            view.tabPreselected = false;
            view.forgetClient = false;
            view.fullResync = true;
        }

        if (view.fullResync) {
            view.fullResync = false;
            view.dirty.clear();
            fullSync(player, profile, view);
        } else {
            var keys = new ArrayList<>(view.dirty);
            view.dirty.removeAll(keys);
            incrementalSync(player, profile, view, keys);
        }
    }

    /** Rebuilds and re-sends the player's complete quest screen (one or two packets). */
    private void fullSync(Player player, Profile profile, PlayerAdvancementView view) {
        var m = model;
        var added = new LinkedHashMap<Identifier, AdvancementHolder>();
        var progress = new HashMap<Identifier, AdvancementProgress>();
        var newSent = new HashMap<String, PlayerAdvancementView.SentState>();
        var newRoots = new HashMap<String, Identifier>();
        // hide-vanilla resyncs use reset=true, which suppresses client toasts; fresh
        // completions are then promoted by a tiny follow-up packet so the toast pops.
        var toastFollowUp = new HashMap<Identifier, AdvancementProgress>();

        for (var tab : m.getTabs().values()) {
            var visible = new ArrayList<QuestNode>(tab.getNodes().size());
            for (var node : tab.getNodes()) {
                if (isVisible(profile, node)) visible.add(node);
            }

            Map<String, float[]> coords = tab.isDynamicLayout() ? dynamicCoords(visible) : tab.getStaticCoords();

            float[] rootPos = coords.getOrDefault("", new float[]{0f, 0f});
            added.put(tab.getRootId(), AdvancementPacketFactory.holder(
                    tab.getRootId(), null,
                    component(player, tab.getTitle()),
                    component(player, tab.getDescription()),
                    resolveIcon(player, tab.getIcon()),
                    net.minecraft.advancements.AdvancementType.TASK,
                    tab.getBackground(), false, rootPos[0], rootPos[1],
                    ROOT_REQUIREMENTS));
            progress.put(tab.getRootId(), AdvancementPacketFactory.progress(
                    ROOT_REQUIREMENTS, AdvancementPacketFactory.criteriaNames(1), 1));
            newRoots.put(tab.getId(), tab.getRootId());

            var visibleKeys = new HashSet<String>(visible.size());
            for (var node : visible) visibleKeys.add(node.getKey());

            for (var node : visible) {
                var state = renderState(player, profile, node);
                if (state == null) continue;
                var prev = view.sent.get(node.getKey());
                boolean toast = completionToast && state.done() && prev != null && !prev.done();

                float[] pos = coords.getOrDefault(node.getKey(),
                        node.getManualPos() != null ? node.getManualPos() : new float[]{1f, 0f});
                Identifier parentId = node.getParentKey() != null && visibleKeys.contains(node.getParentKey())
                        ? m.getNodes().get(node.getParentKey()).getId()
                        : tab.getRootId();

                added.put(node.getId(), AdvancementPacketFactory.holder(
                        node.getId(), parentId, state.title(), state.description(), state.icon(),
                        node.getFrame(), java.util.Optional.empty(), toast, pos[0], pos[1],
                        node.getRequirements()));

                var full = AdvancementPacketFactory.progress(node.getRequirements(), node.getCriteria(), state.granted());
                if (toast && hideVanillaTabs) {
                    // Withhold the last criterion from the reset packet, grant it right after.
                    progress.put(node.getId(), AdvancementPacketFactory.progress(
                            node.getRequirements(), node.getCriteria(), Math.max(0, state.granted() - 1)));
                    toastFollowUp.put(node.getId(), full);
                } else {
                    progress.put(node.getId(), full);
                }

                newSent.put(node.getKey(), new PlayerAdvancementView.SentState(
                        node.getId(), state.granted(), state.done(), state.displayHash(), pos[0], pos[1]));
            }
        }

        Set<Identifier> removed;
        if (hideVanillaTabs) {
            removed = Set.of(); // reset=true wipes the whole screen first
        } else {
            // Only remove what the client actually knows: previously sent entries are
            // removed (and re-added in the same packet when still current) so their
            // display refreshes; brand-new ids are added without a bogus removal.
            removed = new HashSet<>();
            for (var old : view.sent.values()) removed.add(old.id());
            removed.addAll(view.sentRoots.values());
        }

        AdvancementPacketFactory.send(player, hideVanillaTabs, added.values(), removed, progress);
        if (!toastFollowUp.isEmpty()) {
            AdvancementPacketFactory.send(player, false, List.of(), Set.of(), toastFollowUp);
        }

        view.sent.clear();
        view.sent.putAll(newSent);
        view.sentRoots.clear();
        view.sentRoots.putAll(newRoots);

        // Once per session: pre-select the first quest tab so the progress screen opens
        // straight on the quests when the player presses L (no packet can OPEN the
        // client-side screen, but the client remembers the selected tab). Sent after the
        // sync packet, so the client already knows the root id. The player's own tab
        // clicks are respected afterwards.
        if (selectQuestTab && !view.tabPreselected) {
            var firstTab = m.getTabs().values().stream().findFirst();
            if (firstTab.isPresent()) {
                AdvancementPacketFactory.selectTab(player, firstTab.get().getRootId());
                view.tabPreselected = true;
            }
        }
    }

    /** Sends only what changed for the given quest keys (usually a single quest). */
    private void incrementalSync(Player player, Profile profile, PlayerAdvancementView view, List<String> keys) {
        var m = model;
        var added = new LinkedHashMap<Identifier, AdvancementHolder>();
        var removed = new HashSet<Identifier>();
        var progress = new HashMap<Identifier, AdvancementProgress>();

        for (var key : keys) {
            var node = m.getNodes().get(key);
            if (node == null) continue;
            var prev = view.sent.get(key);
            if (prev == null || !isVisible(profile, node)) {
                // Newly visible / newly hidden quests are structural: rebuild everything.
                view.fullResync = false;
                fullSync(player, profile, view);
                return;
            }

            var state = renderState(player, profile, node);
            if (state == null) continue;

            if (state.displayHash() == prev.displayHash()) {
                if (state.granted() != prev.granted()) {
                    progress.put(node.getId(), AdvancementPacketFactory.progress(
                            node.getRequirements(), node.getCriteria(), state.granted()));
                    view.sent.put(key, new PlayerAdvancementView.SentState(
                            node.getId(), state.granted(), state.done(), prev.displayHash(), prev.x(), prev.y()));
                }
                continue;
            }

            // Display changed: the entry must be re-sent. The client drops a node's whole
            // subtree on removal, so its visible descendants ride along in the same packet.
            boolean toast = completionToast && state.done() && !prev.done();
            reAddSubtree(m, player, profile, view, node, toast, added, removed, progress);
        }

        AdvancementPacketFactory.send(player, false, added.values(), removed, progress);
    }

    private void reAddSubtree(AdvancementModel m, Player player, Profile profile, PlayerAdvancementView view,
                              QuestNode node, boolean toastRoot, Map<Identifier, AdvancementHolder> added,
                              Set<Identifier> removed, Map<Identifier, AdvancementProgress> progress) {
        var queue = new ArrayList<QuestNode>();
        queue.add(node);
        boolean first = true;

        while (!queue.isEmpty()) {
            var current = queue.removeFirst();
            var prev = view.sent.get(current.getKey());
            if (prev == null) continue; // not visible for this player
            var state = renderState(player, profile, current);
            if (state == null) continue;

            boolean toast = first && toastRoot;
            first = false;

            var tab = m.getTabs().get(current.getTabId());
            Identifier parentId;
            if (current.getParentKey() != null && view.sent.containsKey(current.getParentKey())) {
                parentId = view.sent.get(current.getParentKey()).id();
            } else {
                parentId = tab != null ? tab.getRootId() : null;
            }

            removed.add(prev.id());
            added.put(current.getId(), AdvancementPacketFactory.holder(
                    current.getId(), parentId, state.title(), state.description(), state.icon(),
                    current.getFrame(), java.util.Optional.empty(), toast, prev.x(), prev.y(),
                    current.getRequirements()));
            progress.put(current.getId(), AdvancementPacketFactory.progress(
                    current.getRequirements(), current.getCriteria(), state.granted()));
            view.sent.put(current.getKey(), new PlayerAdvancementView.SentState(
                    current.getId(), state.granted(), state.done(), state.displayHash(), prev.x(), prev.y()));

            queue.addAll(current.getChildren());
        }
    }

    private void clearClient(Player player, PlayerAdvancementView view, boolean restoreVanilla) {
        var ids = new HashSet<Identifier>();
        for (var s : view.sent.values()) ids.add(s.id());
        ids.addAll(view.sentRoots.values());
        AdvancementPacketFactory.send(player, false, List.of(), ids, Map.of());
        if (restoreVanilla) {
            AdvancementPacketFactory.resendVanillaAdvancements(player);
        }
    }

    // ------------------------------------------------------------------ rendering

    private record RenderState(int granted, boolean done, int displayHash,
                               Component title, Component description, ItemStack icon) {
    }

    private RenderState renderState(Player player, Profile profile, QuestNode node) {
        var pool = profile.getQuestPool(node.getPoolId());
        if (pool == null) return null;
        var quest = pool.getQuest(node.getQuestId());
        if (quest == null) return null;

        boolean done = quest.isCompleted();
        boolean locked = !done && !quest.isUnlocked();

        var placeholders = quest.getPlaceholders();
        var def = quest.getDefinition();
        var titleRaw = def.getName() != null ? def.getName() : node.getQuestId();

        // Three-state tooltip text, mirroring PoolMenu's completed/locked/uncompleted
        // branching — but here the state lore REPLACES the description (an advancement
        // tooltip has no item lore to append to). Empty lists count as absent.
        List<String> template;
        if (locked) {
            if (hasLines(def.getLockedLore())) {
                template = def.getLockedLore();
            } else {
                var fallback = plugin.getConfigManager().getMessageConfig(player).getAdvancementLockedDescription();
                template = fallback == null || fallback.isBlank() ? List.of() : List.of(fallback);
            }
        } else {
            var stateLore = done ? def.getCompletedLore() : def.getUncompletedLore();
            if (hasLines(stateLore)) {
                template = stateLore;
            } else if (hasLines(node.getDescriptionTemplate())) {
                template = node.getDescriptionTemplate();
            } else {
                template = node.getGeneratedDescription();
            }
        }

        var lines = new ArrayList<String>(template.size());
        for (var line : template) {
            lines.add(Text.fillPlaceholders(player, line, placeholders));
        }

        int granted;
        if (done) {
            granted = node.getSteps();
        } else if (locked) {
            granted = 0;
        } else {
            granted = (int) Math.floor(progressFraction(quest) * node.getSteps());
            granted = Math.clamp(granted, 0, node.getSteps() - 1);
        }

        int displayHash = Objects.hash(titleRaw, lines, done, locked);

        var title = component(player, titleRaw);
        var description = joinLines(lines);
        var icon = resolveIcon(player, iconConfig(node, def, done, locked));

        return new RenderState(granted, done, displayHash, title, description, icon);
    }

    private double progressFraction(Quest quest) {
        double total = 0;
        double current = 0;
        for (var objective : quest.getObjectives()) {
            var target = Math.max(1e-9, objective.getTarget());
            total += target;
            current += Math.min(objective.getProgress(), target);
        }
        return total <= 0 ? 0 : current / total;
    }

    private static boolean hasLines(List<String> lines) {
        return lines != null && !lines.isEmpty();
    }

    private gg.auroramc.aurora.api.config.premade.ItemConfig iconConfig(
            QuestNode node, gg.auroramc.quests.api.quest.QuestDefinition def, boolean done, boolean locked) {
        if (node.getIconOverride() != null) return node.getIconOverride();
        if (done && def.getCompletedMenuItem() != null) return def.getCompletedMenuItem();
        if (locked && def.getLockedMenuItem() != null) return def.getLockedMenuItem();
        if (!done && !locked && def.getInProgressMenuItem() != null) return def.getInProgressMenuItem();
        return def.getMenuItem();
    }

    private ItemStack resolveIcon(Player player, gg.auroramc.aurora.api.config.premade.ItemConfig config) {
        if (config == null) return null;
        try {
            return ItemBuilder.of(config).toItemStack(player);
        } catch (Exception e) {
            AuroraQuests.logger().debug("[advancement-gui] Failed to build icon: " + e);
            return null;
        }
    }

    /** Placeholder-free text render (single line). */
    private Component component(Player player, String raw) {
        if (raw == null || raw.isEmpty()) return Component.empty();
        return Text.component(player, raw);
    }

    /** Joins pre-filled lines into one description component. */
    private Component joinLines(List<String> lines) {
        if (lines.isEmpty()) return Component.empty();
        var builder = Component.text();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) builder.appendNewline();
            builder.append(Text.component(lines.get(i)));
        }
        return builder.build();
    }

    // ------------------------------------------------------------------ visibility & layout

    private boolean isVisible(Profile profile, QuestNode node) {
        var pool = profile.getQuestPool(node.getPoolId());
        if (pool == null) return false;
        var quest = pool.getQuest(node.getQuestId());
        if (quest == null) return false;

        if (node.isRolledPool()) {
            // Timed-random pools: only the player's current roll (completed ones of the
            // roll stay visible until the next reroll, like in the chest menu).
            return pool.isRolledQuest(quest);
        }
        if (quest.isCompleted()) return true;
        if (node.isHidden()) return quest.isUnlocked();
        return true;
    }

    /** Per-player grid for tabs whose visible set depends on the player's roll. */
    private Map<String, float[]> dynamicCoords(List<QuestNode> visible) {
        var keys = new HashSet<String>(visible.size());
        for (var node : visible) keys.add(node.getKey());
        var entries = new ArrayList<AdvancementLayout.Entry>(visible.size());
        for (var node : visible) {
            var parent = node.getParentKey() != null && keys.contains(node.getParentKey()) ? node.getParentKey() : null;
            entries.add(new AdvancementLayout.Entry(node.getKey(), parent));
        }
        var coords = AdvancementLayout.layout(entries);
        for (var node : visible) {
            if (node.getManualPos() != null) coords.put(node.getKey(), node.getManualPos());
        }
        return coords;
    }

    private Config.AdvancementGuiConfig config() {
        var cfg = plugin.getConfigManager().getConfig();
        return cfg == null ? null : cfg.getAdvancementGui();
    }
}
