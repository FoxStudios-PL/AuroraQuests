package gg.auroramc.quests.api.quest;

import gg.auroramc.aurora.api.config.premade.ItemConfig;
import gg.auroramc.aurora.api.reward.Reward;
import gg.auroramc.quests.api.objective.ObjectiveDefinition;
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
}
