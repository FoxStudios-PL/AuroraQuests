package gg.auroramc.quests.parser;

import gg.auroramc.aurora.api.reward.NumberReward;
import gg.auroramc.aurora.api.reward.Reward;
import gg.auroramc.aurora.api.reward.RewardFactory;
import gg.auroramc.quests.AuroraQuests;
import gg.auroramc.quests.api.quest.QuestDefinition;
import gg.auroramc.quests.api.quest.QuestRequirement;
import gg.auroramc.quests.api.objective.ObjectiveDefinition;
import gg.auroramc.quests.config.quest.QuestConfig;
import gg.auroramc.quests.config.quest.StartRequirementConfig;
import gg.auroramc.quests.reward.ScaledReward;
import gg.auroramc.quests.util.MenuItemOverride;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.LinkedHashMap;

public class QuestParser {
    public static QuestDefinition parse(QuestConfig config, RewardFactory rewardFactory) {
        var context = (config.getPoolConfig() != null ? config.getPoolConfig().getId() + "/" : "") + config.getId();
        return QuestDefinition.builder()
                .id(config.getId())
                .name(config.getName())
                .chapter(config.getChapter())
                .difficulty(config.getDifficulty())
                .requirements(parseRequirement(config.getStartRequirements()))
                .rewards(parseRewards(config.getRewards(), rewardFactory, context))
                .tasks(parseTasks(config, rewardFactory))
                .menuItem(config.getMenuItem())
                .inProgressMenuItem(MenuItemOverride.apply(config.getMenuItem(), config.getInProgressItem(),
                        rawSection(config, "in-progress-item")))
                .completedMenuItem(MenuItemOverride.apply(config.getMenuItem(), config.getCompletedItem(),
                        rawSection(config, "completed-item")))
                .lockedMenuItem(MenuItemOverride.apply(config.getMenuItem(), config.getLockedItem(),
                        rawSection(config, "locked-item")))
                .completedLore(config.getCompletedLore())
                .lockedLore(config.getLockedLore())
                .uncompletedLore(config.getUncompletedLore())
                .questCompleteMessage(config.getQuestCompleteMessage())
                .questCompleteSound(config.getQuestCompleteSound())
                .linearObjectives(config.getLinearObjectives() != null && config.getLinearObjectives())
                .lockedObjectiveLore(config.getLockedObjectiveLore())
                .onTrack(config.getOnTrack())
                .onUntrack(config.getOnUntrack())
                .advancement(config.getAdvancement())
                .objectiveList(config.getObjectiveList())
                .build();
    }

    private static ConfigurationSection rawSection(QuestConfig config, String key) {
        var raw = config.getRawConfig();
        return raw != null ? raw.getConfigurationSection(key) : null;
    }

    public static QuestRequirement parseRequirement(StartRequirementConfig config) {
        if (config == null) {
            return new QuestRequirement(false, false, null, null);
        }
        return new QuestRequirement(config.isAlwaysShowInMenu(), config.isNeedsManualUnlock(), config.getQuests(), config.getPermissions());
    }

    /**
     * @param context "pool/quest" (or "pool/quest task x") used in scale error messages
     * @throws IllegalArgumentException when a reward references an unknown or non-numeric
     *                                  {@code scale} — the caller refuses the whole quest
     *                                  at load time rather than silently paying zero
     */
    public static LinkedHashMap<String, Reward> parseRewards(ConfigurationSection config, RewardFactory factory, String context) {
        if (config == null) return new LinkedHashMap<>(); //allow zero quest rewards

        LinkedHashMap<String, Reward> rewards = new LinkedHashMap<>();

        for (String key : config.getKeys(false)) {
            var section = config.getConfigurationSection(key);
            var scaleId = section != null ? section.getString("scale") : null;

            if (scaleId == null) {
                var reward = factory.createReward(section);
                reward.ifPresent(value -> rewards.put(key, value));
                continue;
            }

            var scaled = createScaledReward(key, section, scaleId, factory, context);
            if (scaled != null) {
                rewards.put(key, scaled);
            }
        }

        return rewards;
    }

    /**
     * Builds a {@link ScaledReward}: the underlying reward is created from a copy of the
     * section whose {@code amount} was replaced by a formula reading the scale token, so
     * every {@link NumberReward} subtype resolves the scaled value with zero per-type code.
     */
    private static Reward createScaledReward(String key, ConfigurationSection section, String scaleId,
                                             RewardFactory factory, String context) {
        var configManager = AuroraQuests.getInstance().getConfigManager();
        var scale = configManager != null ? configManager.getRewardScales().get(scaleId) : null;
        if (scale == null) {
            throw new IllegalArgumentException("reward '" + key + "' references reward-scale '" + scaleId
                    + "' which does not exist in config.yml (reward-scales)");
        }

        if (section.contains("amount") || section.contains("formula")) {
            AuroraQuests.logger().warning("[reward-scales] Reward '" + key + "' of " + context
                    + " declares both 'scale' and 'amount'/'formula'; 'scale: " + scaleId + "' wins.");
        }

        // Shallow copy is enough: reward init/factory only read top-level keys.
        var copy = new MemoryConfiguration();
        for (var k : section.getKeys(false)) {
            copy.set(k, section.get(k));
        }
        copy.set("amount", null);
        copy.set("formula", ScaledReward.VALUE_TOKEN);

        var delegate = factory.createReward(copy);
        if (delegate.isEmpty()) {
            // Unknown reward type: same silent-skip behaviour as unscaled rewards.
            return null;
        }
        if (!(delegate.get() instanceof NumberReward numberReward)) {
            throw new IllegalArgumentException("reward '" + key + "' has 'scale: " + scaleId + "' but its type '"
                    + section.getString("type") + "' is not a numeric reward (scale needs an amount to replace)");
        }

        var scaled = new ScaledReward(scaleId, numberReward);
        scaled.init(section);
        return scaled;
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

        var context = (config.getPoolConfig() != null ? config.getPoolConfig().getId() + "/" : "") + config.getId();

        for (String key : order) {
            var taskConfig = map.get(key);
            if (taskConfig != null) {
                tasks.put(key, ObjectiveParser.parse(key, taskConfig, rewardFactory, context));
            }
        }

        // Safety net: include any tasks not present in the raw order (should not happen).
        for (var entry : map.entrySet()) {
            if (!tasks.containsKey(entry.getKey())) {
                tasks.put(entry.getKey(), ObjectiveParser.parse(entry.getKey(), entry.getValue(), rewardFactory, context));
            }
        }

        return tasks;
    }
}
