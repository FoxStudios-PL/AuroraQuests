package gg.auroramc.quests.api.quest;

import gg.auroramc.aurora.api.config.premade.ItemConfig;
import gg.auroramc.aurora.api.reward.Reward;
import gg.auroramc.quests.api.objective.ObjectiveDefinition;
import gg.auroramc.quests.config.Config;
import gg.auroramc.quests.config.advancement.QuestAdvancementConfig;
import gg.auroramc.quests.config.quest.QuestConfig;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.LinkedHashMap;

@Builder
@Getter
public class QuestDefinition {
    private final String id;
    private final String name;
    private final String chapter;
    private final String difficulty;
    /**
     * Normalized (lowercase, trimmed) activity tags of this quest, used by pools with
     * {@code avoid-duplicate-tags} to keep two quests of the same activity out of one roll.
     * Never {@code null}: a quest without a {@code tags:} section simply has none.
     */
    private final List<String> tags;
    private final ItemConfig menuItem;
    /**
     * Icon shown while the quest is unlocked and not completed yet, {@code null} when the
     * quest doesn't override it. Already merged over {@link #menuItem}.
     */
    private final ItemConfig inProgressMenuItem;
    /**
     * Icon shown once the quest is completed, {@code null} when the quest doesn't override
     * it. Already merged over {@link #menuItem}.
     */
    private final ItemConfig completedMenuItem;
    /**
     * Icon shown while the quest is locked, {@code null} when the quest doesn't override
     * it. Already merged over {@link #menuItem}.
     */
    private final ItemConfig lockedMenuItem;
    private final List<String> lockedLore;
    private final List<String> completedLore;
    private final List<String> uncompletedLore;
    private final LinkedHashMap<String, ObjectiveDefinition> tasks;
    private final LinkedHashMap<String, Reward> rewards;
    private final QuestRequirement requirements;
    private final QuestConfig.LevelUpMessage questCompleteMessage;
    private final QuestConfig.LevelUpSound questCompleteSound;
    private final boolean linearObjectives;
    private final String lockedObjectiveLore;
    private final List<String> onTrack;
    private final List<String> onUntrack;
    /** Raw {@code advancement:} section of the quest file, {@code null} when absent. */
    private final QuestAdvancementConfig advancement;
    /** Per-quest {@code objective-list:} override for {@code {tasks}}, {@code null} when absent. */
    private final Config.ObjectiveListConfig objectiveList;

    /** Never {@code null}, so callers can iterate without a guard. */
    public List<String> getTags() {
        return tags == null ? List.of() : tags;
    }
}
