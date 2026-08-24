package gg.auroramc.quests.parser;

import gg.auroramc.aurora.api.reward.RewardFactory;
import gg.auroramc.quests.AuroraQuests;
import gg.auroramc.quests.api.quest.QuestDefinition;
import gg.auroramc.quests.api.questpool.Pool;
import gg.auroramc.quests.api.questpool.PoolConfig;
import gg.auroramc.quests.api.questpool.PoolDefinition;
import gg.auroramc.quests.api.questpool.PoolType;
import gg.auroramc.quests.config.quest.QuestConfig;

import java.util.LinkedHashMap;
import java.util.Map;

public class PoolParser {
    public static Pool parse(PoolConfig config, RewardFactory rewardFactory) {
        var definition = PoolDefinition.builder()
                .id(config.getId())
                .name(config.getName())
                .difficulties(config.getDifficulties())
                .resetFrequency(config.getResetFrequency())
                .rerollOnCompletion(config.getRerollOnCompletion())
                .menu(config.getMenu())
                .menuItem(config.getMenuItem())
                .requirement(QuestParser.parseRequirement(config.getUnlockRequirements()))
                .leveling(config.getLeveling())
                .type(PoolType.fromString(config.getType()))
                .advancement(config.getAdvancement())
                .quests(parseQuests(config.getQuests(), rewardFactory))
                .build();

        return new Pool(definition, rewardFactory);
    }

    private static LinkedHashMap<String, QuestDefinition> parseQuests(Map<String, QuestConfig> map, RewardFactory rewardFactory) {
        LinkedHashMap<String, QuestDefinition> quests = new LinkedHashMap<>();

        for(var quest : map.values()) {
            try {
                quests.put(quest.getId(), QuestParser.parse(quest, rewardFactory));
            } catch (IllegalArgumentException e) {
                // Invalid reward-scale reference (or similar hard config error): refuse
                // the quest at load time instead of silently paying zero later.
                var poolId = quest.getPoolConfig() != null ? quest.getPoolConfig().getId() : "?";
                AuroraQuests.logger().severe("Quest not loaded: quest_pools/" + poolId + "/quests/"
                        + quest.getId() + ".yml - " + e.getMessage());
            }
        }

        return quests;
    }
}
