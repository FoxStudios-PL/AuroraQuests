package gg.auroramc.quests.config.advancement;

import gg.auroramc.aurora.api.config.premade.ItemConfig;
import lombok.Getter;

import java.util.List;

/**
 * Per-quest {@code advancement:} section. Everything is optional: without it the quest
 * still shows up in the advancement GUI with sensible defaults (pool tab, attached to
 * the tab root, task frame, icon taken from the quest's menu item).
 */
@Getter
public class QuestAdvancementConfig {
    /** Tab (category) id this quest is displayed in. Defaults to the pool's tab. */
    private String tab;
    /**
     * Quest this one is visually connected to: {@code "<questId>"} for a quest of the
     * same pool or {@code "<poolId>/<questId>"} across pools. Must resolve to a quest
     * shown in the same tab. Omit to attach the quest directly to the tab root.
     */
    private String parent;
    /** Frame style: {@code task} (default), {@code goal} or {@code challenge}. */
    private String frame;
    /** When true, the quest is completely invisible while it cannot be started yet. */
    private Boolean hidden;
    /** Overrides {@code advancement-gui.progress-steps} for this quest (1 = no fraction). */
    private Integer progressSteps;
    /**
     * Custom description lines. Supports the same placeholders as the menu lore
     * ({@code {task_<id>}}, {@code {reward_<id>}}, {@code {name}}, ...). Omit to
     * generate one from the task display lines and the reward displays.
     */
    private List<String> description;
    /** Icon override; falls back to the status menu items, then {@code menu-item}. */
    private ItemConfig icon;
    /** Manual grid position override; omit for automatic layout. */
    private PositionConfig position;

    @Getter
    public static class PositionConfig {
        /** Column (0 = tab root column); fractional values are allowed. */
        private Double x;
        /** Row; fractional values are allowed. */
        private Double y;
    }
}
