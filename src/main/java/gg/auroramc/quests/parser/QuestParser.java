package gg.auroramc.quests.parser;

import gg.auroramc.aurora.api.reward.Reward;
import gg.auroramc.aurora.api.reward.RewardFactory;
import gg.auroramc.quests.api.quest.QuestDefinition;
import gg.auroramc.quests.api.quest.QuestRequirement;
import gg.auroramc.quests.api.objective.ObjectiveDefinition;
import gg.auroramc.quests.config.quest.QuestConfig;
import gg.auroramc.quests.config.quest.StartRequirementConfig;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;

public class QuestParser {
    public static QuestDefinition parse(QuestConfig config, RewardFactory rewardFactory) {
        return QuestDefinition.builder()
                .id(config.getId())
                .name(config.getName())
                .difficulty(config.getDifficulty())
                .requirements(parseRequirement(config.getStartRequirements()))
                .rewards(parseRewards(config.getRewards(), rewardFactory))
                .tasks(parseTasks(config, rewardFactory))
                .menuItem(config.getMenuItem())
                .completedLore(config.getCompletedLore())
                .lockedLore(config.getLockedLore())
                .uncompletedLore(config.getUncompletedLore())
                .questCompleteMessage(config.getQuestCompleteMessage())
                .questCompleteSound(config.getQuestCompleteSound())
                .linearObjectives(config.getLinearObjectives() != null && config.getLinearObjectives())
                .lockedObjectiveLore(config.getLockedObjectiveLore())
                .onTrack(config.getOnTrack())
                .onUntrack(config.getOnUntrack())
                .build();
    }

    public static QuestRequirement parseRequirement(StartRequirementConfig config) {
        if (config == null) {
            return new QuestRequirement(false, false, null, null);
        }
        return new QuestRequirement(config.isAlwaysShowInMenu(), config.isNeedsManualUnlock(), config.getQuests(), config.getPermissions());
    }

    public static LinkedHashMap<String, Reward> parseRewards(ConfigurationSection config, RewardFactory factory) {
        if (config == null) return new LinkedHashMap<>(); //allow zero quest rewards

        LinkedHashMap<String, Reward> rewards = new LinkedHashMap<>();

        for (String key : config.getKeys(false)) {
            var reward = factory.createReward(config.getConfigurationSection(key));
            reward.ifPresent(value -> rewards.put(key, value));
        }

        return rewards;
    }

    private static LinkedHashMap<String, ObjectiveDefinition> parseTasks(QuestConfig config, RewardFactory rewardFactory) {
        LinkedHashMap<String, ObjectiveDefinition> tasks = new LinkedHashMap<>();

        var map = config.getTasks();
        if (map == null || map.isEmpty()) return tasks;

        // Aurora deserializes Map config fields into a HashMap, which loses the YAML
        // declaration order. Recover the real order from the raw config (getKeys
        // preserves it), so task display AND linear-objectives progression follow
        // the order the tasks are written in.
        var rawSection = config.getRawConfig() != null ? config.getRawConfig().getConfigurationSection("tasks") : null;
        var order = rawSection != null ? rawSection.getKeys(false) : map.keySet();

        for (String key : order) {
            var taskConfig = map.get(key);
            if (taskConfig != null) {
                tasks.put(key, ObjectiveParser.parse(key, taskConfig, rewardFactory));
            }
        }

        // Safety net: include any tasks not present in the raw order (should not happen).
        for (var entry : map.entrySet()) {
            if (!tasks.containsKey(entry.getKey())) {
                tasks.put(entry.getKey(), ObjectiveParser.parse(entry.getKey(), entry.getValue(), rewardFactory));
            }
        }

        return tasks;
    }
}
