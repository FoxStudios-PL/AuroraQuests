package gg.auroramc.quests.objective;

import gg.auroramc.quests.AuroraQuests;
import gg.auroramc.quests.api.event.objective.PlayerDeliverItemEvent;
import gg.auroramc.quests.api.objective.Objective;
import gg.auroramc.quests.api.objective.ObjectiveDefinition;
import gg.auroramc.quests.api.profile.Profile;
import gg.auroramc.quests.api.quest.Quest;
import gg.auroramc.quests.util.ItemMatcher;
import org.bukkit.event.EventPriority;

/**
 * All-or-nothing item turn-in, triggered by {@code /quests deliver}: when the event
 * arrives, either the player holds the whole remaining amount of the configured item
 * (matched on material / name / lore / custom model data) and it is taken from the
 * inventory in one go — completing the objective — or nothing happens at all.
 */
public class DeliverItemObjective extends Objective {
    private final ItemMatcher matcher;

    public DeliverItemObjective(Quest quest, ObjectiveDefinition definition, Profile.TaskDataWrapper data) {
        super(quest, definition, data);
        this.matcher = new ItemMatcher(definition.getArgs().getConfigurationSection("item"));
        if (!matcher.isValid()) {
            AuroraQuests.logger().warning("DELIVER_ITEM task " + definition.getId() + " of quest " + quest.getId()
                    + " has no valid 'item' section (material/name/lore/custom-model-data), it will never match any item!");
        }
    }

    @Override
    protected void activate() {
        onEvent(PlayerDeliverItemEvent.class, this::handle, EventPriority.MONITOR);
    }

    private void handle(PlayerDeliverItemEvent event) {
        if (event.getQuest() != quest) return;
        if (event.getObjectiveId() != null && !event.getObjectiveId().equals(getId())) return;
        if (isCompleted()) return;

        var remaining = remainingAmount();
        if (remaining <= 0) return;
        if (!passesFilters(meta())) return;

        if (!matcher.take(data.profile().getPlayer(), remaining)) {
            event.markMissing(getId());
            return;
        }

        setProgress(target);
        event.markDelivered(getId());
    }

    /**
     * Whether the player's inventory currently holds the (remaining) required items —
     * an already completed objective counts as satisfied. Backs the
     * {@code %aurora_quests_has_items_...%} placeholder.
     */
    public boolean hasRequiredItems() {
        if (isCompleted()) return true;
        var remaining = remainingAmount();
        return remaining <= 0 || matcher.count(data.profile().getPlayer()) >= remaining;
    }

    private int remainingAmount() {
        return (int) Math.ceil(target - data.getProgress());
    }
}
