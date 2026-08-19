package gg.auroramc.quests.advancement;

import gg.auroramc.aurora.api.config.premade.ItemConfig;
import lombok.Getter;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable, player-independent view of every quest shown in the advancement GUI.
 * <p>
 * Built once per {@code /quests reload} by {@link AdvancementModelBuilder} and shared by
 * all players: tab structure, parent links, grid layout, NMS requirement objects and
 * description templates are computed exactly once, so per-player work is reduced to
 * rendering the few quests whose state actually changed.
 */
@Getter
public final class AdvancementModel {
    /** Tabs keyed by id, iteration order = display order (index, then id). */
    private final Map<String, TabModel> tabs;
    /** Every displayed quest keyed by {@code "<poolId>/<questId>"}. */
    private final Map<String, QuestNode> nodes;
    /** Nodes grouped by pool id (fast pool-scoped lookups on reroll/completion). */
    private final Map<String, List<QuestNode>> nodesByPool;

    AdvancementModel(Map<String, TabModel> tabs, Map<String, QuestNode> nodes, Map<String, List<QuestNode>> nodesByPool) {
        this.tabs = tabs;
        this.nodes = nodes;
        this.nodesByPool = nodesByPool;
    }

    public static AdvancementModel empty() {
        return new AdvancementModel(Map.of(), Map.of(), Map.of());
    }

    public boolean isEmpty() {
        return tabs.isEmpty();
    }

    /** One advancement tab (a quest category). */
    @Getter
    public static final class TabModel {
        private final String id;
        private final Identifier rootId;
        private final String title;
        private final String description;
        /** Icon of the root advancement; resolved per player when the tab is (re)sent. */
        private final @Nullable ItemConfig icon;
        private final Optional<ClientAsset.ResourceTexture> background;
        private final int index;
        /** Quests of this tab, parents always before their children. */
        private final List<QuestNode> nodes;
        /**
         * True when the tab contains quests of a timed-random pool: the visible subset
         * then differs per player/roll, so the grid is recomputed per sync instead of
         * using {@link #staticCoords} (small sets, negligible cost).
         */
        private final boolean dynamicLayout;
        /** Precomputed grid coordinates by node key ({@code ""} = root) for static tabs. */
        private final Map<String, float[]> staticCoords;

        TabModel(String id, Identifier rootId, String title, String description, @Nullable ItemConfig icon,
                 Optional<ClientAsset.ResourceTexture> background, int index, List<QuestNode> nodes,
                 boolean dynamicLayout, Map<String, float[]> staticCoords) {
            this.id = id;
            this.rootId = rootId;
            this.title = title;
            this.description = description;
            this.icon = icon;
            this.background = background;
            this.index = index;
            this.nodes = nodes;
            this.dynamicLayout = dynamicLayout;
            this.staticCoords = staticCoords;
        }
    }

    /** One quest as displayed in the advancement screen. */
    @Getter
    public static final class QuestNode {
        private final String key;
        private final String poolId;
        private final String questId;
        private final String tabId;
        private final Identifier id;
        /** Key of the parent quest node, {@code null} when attached to the tab root. */
        private final @Nullable String parentKey;
        private final AdvancementType frame;
        /** Invisible until the quest can be started (per-quest {@code hidden: true}). */
        private final boolean hidden;
        /** Steps of the native x/y counter (1 = plain advancement without fraction). */
        private final int steps;
        /** Criterion names, one per step; shared requirements object built from them. */
        private final List<String> criteria;
        private final AdvancementRequirements requirements;
        /** Manual {x, y} position override from the quest config, {@code null} = auto. */
        private final float @Nullable [] manualPos;
        /** Custom description template ({@code advancement.description}), or null. */
        private final @Nullable List<String> descriptionTemplate;
        /** Generated fallback template (task lines + reward lines). */
        private final List<String> generatedDescription;
        /** Per-quest icon override ({@code advancement.icon}), or null. */
        private final @Nullable ItemConfig iconOverride;
        /** Whether the owning pool is timed-random (visibility = current roll). */
        private final boolean rolledPool;
        /** Direct children, used to re-send whole subtrees on display updates. */
        private final List<QuestNode> children;

        QuestNode(String key, String poolId, String questId, String tabId, Identifier id,
                  @Nullable String parentKey, AdvancementType frame, boolean hidden, int steps,
                  List<String> criteria, AdvancementRequirements requirements, float @Nullable [] manualPos,
                  @Nullable List<String> descriptionTemplate, List<String> generatedDescription,
                  @Nullable ItemConfig iconOverride, boolean rolledPool, List<QuestNode> children) {
            this.key = key;
            this.poolId = poolId;
            this.questId = questId;
            this.tabId = tabId;
            this.id = id;
            this.parentKey = parentKey;
            this.frame = frame;
            this.hidden = hidden;
            this.steps = steps;
            this.criteria = criteria;
            this.requirements = requirements;
            this.manualPos = manualPos;
            this.descriptionTemplate = descriptionTemplate;
            this.generatedDescription = generatedDescription;
            this.iconOverride = iconOverride;
            this.rolledPool = rolledPool;
            this.children = children;
        }
    }
}
