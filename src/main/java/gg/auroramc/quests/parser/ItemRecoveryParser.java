package gg.auroramc.quests.parser;

import gg.auroramc.quests.AuroraQuests;
import gg.auroramc.quests.api.factory.ObjectiveFactory;
import gg.auroramc.quests.api.objective.ObjectiveDefinition;
import gg.auroramc.quests.api.quest.ItemRecoveryDefinition;
import gg.auroramc.quests.config.quest.ItemRecoveryConfig;
import gg.auroramc.quests.config.quest.QuestConfig;
import gg.auroramc.quests.util.ItemMatcher;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * Parses the {@code item-recovery:} section of a quest file. An invalid entry is
 * reported in the console and skipped; it never prevents the quest from loading.
 */
public class ItemRecoveryParser {
    public static final double DEFAULT_CHANCE = 1.0;
    public static final int DEFAULT_COOLDOWN_SECONDS = 5;

    public static List<ItemRecoveryDefinition> parse(QuestConfig config, Set<String> taskIds, String context) {
        var map = config.getItemRecovery();
        if (map == null || map.isEmpty()) return List.of();

        // Same as tasks: keep the YAML order (Aurora maps sections into a HashMap).
        var rawSection = config.getRawConfig() != null ? config.getRawConfig().getConfigurationSection("item-recovery") : null;
        var order = rawSection != null ? rawSection.getKeys(false) : map.keySet();

        var result = new ArrayList<ItemRecoveryDefinition>(map.size());
        for (var key : order) {
            var entry = map.get(key);
            if (entry == null) continue;
            var rawEntry = rawSection != null ? rawSection.getConfigurationSection(key) : null;
            var definition = parseEntry(key, entry, rawEntry, taskIds, context);
            if (definition != null) result.add(definition);
        }
        return result;
    }

    private static ItemRecoveryDefinition parseEntry(String id, ItemRecoveryConfig config, ConfigurationSection raw,
                                                     Set<String> taskIds, String context) {
        var where = "item-recovery '" + id + "' of quest " + context;

        var after = blankToNull(config.getAfter());
        if (after != null && !taskIds.contains(after)) {
            return skip(where, "'after: " + after + "' is not a task of this quest");
        }
        var until = blankToNull(config.getUntil());
        if (until != null && !taskIds.contains(until)) {
            return skip(where, "'until: " + until + "' is not a task of this quest");
        }
        if (after != null && after.equals(until)) {
            return skip(where, "'after' and 'until' are the same task, the window can never open");
        }

        var trigger = config.getTrigger();
        var triggerTask = trigger != null ? blankToNull(trigger.getTask()) : null;
        if (triggerTask == null) {
            return skip(where, "'trigger.task' is missing");
        }
        if (!ObjectiveFactory.isRegistered(triggerTask)) {
            return skip(where, "'trigger.task: " + triggerTask + "' is not a known task type");
        }

        var item = new ItemMatcher(config.getItem());
        if (!item.isValid()) {
            return skip(where, "'item' has no valid criteria (material/name/lore/custom-model-data)");
        }

        if (config.getOnRecover() == null || config.getOnRecover().isEmpty()) {
            return skip(where, "'on-recover' is empty, there is nothing to give back");
        }

        var chance = config.getChance() != null ? config.getChance() : DEFAULT_CHANCE;
        if (chance < 0 || chance > 1) {
            AuroraQuests.logger().warning("[item-recovery] " + where + ": 'chance: " + chance
                    + "' is outside 0.0-1.0, clamped.");
            chance = Math.max(0, Math.min(1, chance));
        }

        var cooldown = config.getCooldownSeconds() != null ? config.getCooldownSeconds() : DEFAULT_COOLDOWN_SECONDS;
        if (cooldown < 0) cooldown = 0;

        var triggerDefinition = ObjectiveDefinition.builder()
                .id("item-recovery:" + id)
                .task(triggerTask)
                .display("")
                .description(List.of())
                .args(triggerArgs(raw))
                .filters(ObjectiveParser.parseFilters(trigger.getFilters()))
                .onProgress(List.of())
                .onComplete(List.of())
                .rewards(new LinkedHashMap<>())
                .build();

        return ItemRecoveryDefinition.builder()
                .id(id)
                .after(after)
                .until(until)
                .item(item)
                .trigger(triggerDefinition)
                .chance(chance)
                .cooldownMillis(cooldown * 1000L)
                .onRecover(List.copyOf(config.getOnRecover()))
                .build();
    }

    /**
     * Task arguments of the trigger: written directly under {@code trigger:}
     * ({@code types}, {@code mode}, ...), or under {@code trigger.args:} like a task.
     */
    private static ConfigurationSection triggerArgs(ConfigurationSection raw) {
        var trigger = raw != null ? raw.getConfigurationSection("trigger") : null;
        if (trigger == null) return new MemoryConfiguration();
        var args = trigger.getConfigurationSection("args");
        return args != null ? args : trigger;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ItemRecoveryDefinition skip(String where, String reason) {
        AuroraQuests.logger().warning("[item-recovery] " + where + " ignored: " + reason + ".");
        return null;
    }
}
