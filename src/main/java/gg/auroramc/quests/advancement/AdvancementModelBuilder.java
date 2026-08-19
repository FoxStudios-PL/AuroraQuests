package gg.auroramc.quests.advancement;

import gg.auroramc.aurora.api.config.premade.ItemConfig;
import gg.auroramc.quests.AuroraQuests;
import gg.auroramc.quests.advancement.AdvancementModel.QuestNode;
import gg.auroramc.quests.advancement.AdvancementModel.TabModel;
import gg.auroramc.quests.api.quest.QuestDefinition;
import gg.auroramc.quests.api.questpool.Pool;
import gg.auroramc.quests.api.questpool.PoolType;
import gg.auroramc.quests.config.Config;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Comparator;

/**
 * Turns the loaded pools + module config into the shared {@link AdvancementModel}.
 * Runs once per {@code /quests reload}; every config mistake (unknown tab, unknown or
 * cyclic parent, invalid background) degrades to a sane fallback with a console warning
 * instead of failing the load.
 */
final class AdvancementModelBuilder {
    private static final int MAX_STEPS = 100;

    private AdvancementModelBuilder() {
    }

    static AdvancementModel build(Iterable<Pool> pools, Config.AdvancementGuiConfig cfg) {
        Map<String, MutableTab> tabs = new LinkedHashMap<>();
        Map<String, MutableNode> nodes = new LinkedHashMap<>();

        // Declared tabs first so their config wins over pool auto tabs with the same id.
        if (cfg.getTabs() != null) {
            for (var entry : cfg.getTabs().entrySet()) {
                var id = sanitize(entry.getKey());
                var tc = entry.getValue();
                var tab = tabs.computeIfAbsent(id, MutableTab::new);
                tab.title = tc.getName() != null ? tc.getName() : id;
                tab.description = tc.getDescription() != null ? tc.getDescription() : "";
                tab.icon = tc.getIcon();
                tab.background = tc.getBackground();
                tab.index = tc.getIndex() != null ? tc.getIndex() : 0;
                tab.declared = true;
            }
        }

        // Pass 1: create every pool tab up front, so quests may reference any pool's
        // auto tab (not only their own) without a bogus unknown-tab warning.
        Map<String, String> poolTabIds = new HashMap<>();
        for (var pool : pools) {
            var def = pool.getDefinition();
            var poolAdv = def.getAdvancement();
            if (poolAdv != null && Boolean.FALSE.equals(poolAdv.getEnabled())) continue;

            var poolTabId = sanitize(poolAdv != null && poolAdv.getTab() != null ? poolAdv.getTab() : def.getId());
            poolTabIds.put(def.getId(), poolTabId);
            var poolTab = tabs.computeIfAbsent(poolTabId, MutableTab::new);
            if (!poolTab.declared) {
                // Auto tab: pool root section > pool display defaults.
                var root = poolAdv != null ? poolAdv.getRoot() : null;
                if (poolTab.title == null) {
                    var title = root != null && root.getTitle() != null ? root.getTitle() : "{name}";
                    poolTab.title = title.replace("{name}", def.getName() != null ? def.getName() : def.getId());
                }
                if (poolTab.description == null && root != null && root.getDescription() != null) {
                    poolTab.description = root.getDescription();
                }
                if (poolTab.icon == null) {
                    poolTab.icon = root != null && root.getIcon() != null
                            ? root.getIcon()
                            : (def.getMenuItem() != null ? def.getMenuItem().getItem() : null);
                }
                if (poolTab.background == null && root != null) {
                    poolTab.background = root.getBackground();
                }
            }
        }

        // Pass 2: create one node per quest.
        for (var pool : pools) {
            var def = pool.getDefinition();
            var poolTabId = poolTabIds.get(def.getId());
            if (poolTabId == null) continue;

            boolean rolled = def.getType() == PoolType.TIMED_RANDOM;

            for (var quest : def.getQuests().values()) {
                var adv = quest.getAdvancement();
                var tabId = poolTabId;
                if (adv != null && adv.getTab() != null) {
                    var wanted = sanitize(adv.getTab());
                    if (tabs.containsKey(wanted)) {
                        tabId = wanted;
                    } else {
                        warn("Quest " + def.getId() + "/" + quest.getId() + " references unknown advancement tab '"
                                + adv.getTab() + "', falling back to '" + poolTabId + "'.");
                    }
                }

                var node = new MutableNode();
                node.poolId = def.getId();
                node.questId = quest.getId();
                node.key = def.getId() + "/" + quest.getId();
                node.tabId = tabId;
                node.quest = quest;
                node.rolled = rolled;
                node.rawParent = adv != null ? adv.getParent() : null;
                node.frame = parseFrame(adv != null ? adv.getFrame() : null, node.key);
                node.hidden = adv != null && Boolean.TRUE.equals(adv.getHidden());
                node.steps = resolveSteps(quest, adv != null ? adv.getProgressSteps() : null, cfg.getProgressSteps());
                node.descriptionTemplate = adv != null ? adv.getDescription() : null;
                node.iconOverride = adv != null ? adv.getIcon() : null;
                if (adv != null && adv.getPosition() != null
                        && adv.getPosition().getX() != null && adv.getPosition().getY() != null) {
                    node.manualPos = new float[]{adv.getPosition().getX().floatValue(), adv.getPosition().getY().floatValue()};
                }
                nodes.put(node.key, node);
                tabs.get(tabId).nodeKeys.add(node.key);
            }
        }

        resolveParents(nodes);

        // Assemble the immutable model.
        Map<String, QuestNode> builtNodes = new LinkedHashMap<>();
        Map<String, List<QuestNode>> byPool = new HashMap<>();
        Map<String, TabModel> builtTabs = new LinkedHashMap<>();
        Map<String, List<QuestNode>> childrenBuffers = new HashMap<>();

        var orderedTabs = tabs.values().stream()
                .filter(t -> t.declared || !t.nodeKeys.isEmpty())
                .sorted(Comparator.comparingInt((MutableTab t) -> t.index).thenComparing(t -> t.id))
                .toList();

        // Sanitizing quest keys ("Épée" -> "_p_e") can merge two distinct quests into
        // the same advancement id; suffix duplicates so every node keeps a unique id.
        var usedIds = new HashSet<String>();

        for (var tab : orderedTabs) {
            // Parent-before-child order: iterate root nodes in insertion order, BFS down.
            List<MutableNode> ordered = orderByDepth(tab, nodes);
            boolean dynamic = ordered.stream().anyMatch(n -> n.rolled);

            List<QuestNode> tabNodes = new ArrayList<>(ordered.size());
            for (var mn : ordered) {
                var children = new ArrayList<QuestNode>();
                childrenBuffers.put(mn.key, children);
                var idPath = "quests/" + sanitize(mn.key);
                for (int i = 2; !usedIds.add(idPath); i++) {
                    idPath = "quests/" + sanitize(mn.key) + "_" + i;
                }
                var built = new QuestNode(
                        mn.key, mn.poolId, mn.questId, mn.tabId,
                        Identifier.fromNamespaceAndPath(AdvancementPacketFactory.NAMESPACE, idPath),
                        mn.parentKey, mn.frame, mn.hidden, mn.steps,
                        AdvancementPacketFactory.criteriaNames(mn.steps),
                        AdvancementRequirements.allOf(AdvancementPacketFactory.criteriaNames(mn.steps)),
                        mn.manualPos, mn.descriptionTemplate, generatedDescription(mn.quest),
                        mn.iconOverride, mn.rolled, children);
                builtNodes.put(built.getKey(), built);
                tabNodes.add(built);
                byPool.computeIfAbsent(mn.poolId, k -> new ArrayList<>()).add(built);
                if (mn.parentKey != null) {
                    childrenBuffers.get(mn.parentKey).add(built);
                }
            }

            Map<String, float[]> coords = dynamic ? Map.of() : staticLayout(tabNodes);

            builtTabs.put(tab.id, new TabModel(
                    tab.id,
                    Identifier.fromNamespaceAndPath(AdvancementPacketFactory.NAMESPACE, "tabs/" + tab.id),
                    tab.title != null ? tab.title : tab.id,
                    tab.description != null ? tab.description : "",
                    tab.icon,
                    background(tab.background, cfg.getDefaultBackground(), tab.id),
                    tab.index,
                    List.copyOf(tabNodes),
                    dynamic,
                    coords));
        }

        return new AdvancementModel(builtTabs, builtNodes, byPool);
    }

    /** Computes the shared grid for a tab whose visible set never changes. */
    static Map<String, float[]> staticLayout(List<QuestNode> tabNodes) {
        var entries = new ArrayList<AdvancementLayout.Entry>(tabNodes.size());
        for (var n : tabNodes) {
            entries.add(new AdvancementLayout.Entry(n.getKey(), n.getParentKey()));
        }
        var coords = AdvancementLayout.layout(entries);
        for (var n : tabNodes) {
            if (n.getManualPos() != null) {
                coords.put(n.getKey(), n.getManualPos());
            }
        }
        return coords;
    }

    private static void resolveParents(Map<String, MutableNode> nodes) {
        for (var node : nodes.values()) {
            if (node.rawParent == null) continue;
            var ref = node.rawParent.contains("/") ? node.rawParent : node.poolId + "/" + node.rawParent;
            var parent = nodes.get(ref);
            if (parent == null) {
                warn("Quest " + node.key + " has unknown advancement parent '" + node.rawParent + "', attaching to the tab root.");
            } else if (!parent.tabId.equals(node.tabId)) {
                warn("Quest " + node.key + " and its advancement parent '" + ref + "' are in different tabs, attaching to the tab root.");
            } else {
                node.parentKey = ref;
            }
        }

        // Break parent cycles (a -> b -> a): detach the first node found on a cycle.
        for (var node : nodes.values()) {
            var seen = new HashSet<String>();
            var cur = node;
            while (cur.parentKey != null) {
                if (!seen.add(cur.key)) {
                    warn("Advancement parent cycle detected at quest " + cur.key + ", attaching it to the tab root.");
                    cur.parentKey = null;
                    break;
                }
                cur = nodes.get(cur.parentKey);
            }
        }
    }

    private static List<MutableNode> orderByDepth(MutableTab tab, Map<String, MutableNode> nodes) {
        Map<String, List<MutableNode>> children = new LinkedHashMap<>();
        List<MutableNode> roots = new ArrayList<>();
        for (var key : tab.nodeKeys) {
            var node = nodes.get(key);
            if (node.parentKey == null) {
                roots.add(node);
            } else {
                children.computeIfAbsent(node.parentKey, k -> new ArrayList<>()).add(node);
            }
        }
        List<MutableNode> ordered = new ArrayList<>(tab.nodeKeys.size());
        var queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            var node = queue.poll();
            ordered.add(node);
            var kids = children.remove(node.key);
            if (kids != null) queue.addAll(kids);
        }
        // Orphans of a broken chain (should not happen after cycle-breaking): keep them anyway.
        for (var kids : children.values()) {
            for (var kid : kids) {
                warn("Quest " + kid.key + " lost its advancement parent chain, attaching to the tab root.");
                kid.parentKey = null;
                ordered.add(kid);
            }
        }
        return ordered;
    }

    /**
     * Default tooltip description: one line per task (same {@code display} strings as
     * the chest menu) followed by the reward displays. Filled with the quest's
     * placeholders at render time, so counters stay live.
     */
    private static List<String> generatedDescription(QuestDefinition quest) {
        var lines = new ArrayList<String>();
        for (var taskId : quest.getTasks().keySet()) {
            lines.add("{task_" + taskId + "}");
        }
        if (!quest.getRewards().isEmpty()) {
            if (!lines.isEmpty()) lines.add("");
            for (var rewardId : quest.getRewards().keySet()) {
                lines.add("{reward_" + rewardId + "}");
            }
        }
        return List.copyOf(lines);
    }

    private static int resolveSteps(QuestDefinition quest, Integer questSteps, Integer defaultSteps) {
        int steps = questSteps != null ? questSteps : (defaultSteps != null ? defaultSteps : 10);
        double total = 0;
        for (var task : quest.getTasks().values()) {
            total += Math.max(1, task.getArgs().getDouble("amount", 1));
        }
        steps = (int) Math.min(steps, Math.max(1, Math.ceil(total)));
        return Math.clamp(steps, 1, MAX_STEPS);
    }

    private static AdvancementType parseFrame(String raw, String key) {
        if (raw == null || raw.isBlank()) return AdvancementType.TASK;
        try {
            return AdvancementType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            warn("Quest " + key + " has unknown advancement frame '" + raw + "' (use task, goal or challenge), using task.");
            return AdvancementType.TASK;
        }
    }

    private static Optional<ClientAsset.ResourceTexture> background(String raw, String fallback, String tabId) {
        var value = raw != null && !raw.isBlank() ? raw : fallback;
        if (value == null || value.isBlank()) return Optional.empty();
        var id = Identifier.tryParse(value.trim());
        if (id == null) {
            warn("Tab '" + tabId + "' has an invalid background id '" + value + "', using none.");
            return Optional.empty();
        }
        return Optional.of(new ClientAsset.ResourceTexture(id));
    }

    /** Advancement ids only allow [a-z0-9/._-]; anything else becomes '_'. */
    private static String sanitize(String raw) {
        var sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = Character.toLowerCase(raw.charAt(i));
            sb.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '/' || c == '.' || c == '_' || c == '-' ? c : '_');
        }
        return sb.toString();
    }

    private static void warn(String message) {
        AuroraQuests.logger().warning("[advancement-gui] " + message);
    }

    private static final class MutableTab {
        final String id;
        String title;
        String description;
        ItemConfig icon;
        String background;
        int index = 0;
        boolean declared = false;
        final List<String> nodeKeys = new ArrayList<>();

        MutableTab(String id) {
            this.id = id;
        }
    }

    private static final class MutableNode {
        String key;
        String poolId;
        String questId;
        String tabId;
        QuestDefinition quest;
        String rawParent;
        String parentKey;
        AdvancementType frame;
        boolean hidden;
        boolean rolled;
        int steps;
        float[] manualPos;
        List<String> descriptionTemplate;
        ItemConfig iconOverride;
    }
}
