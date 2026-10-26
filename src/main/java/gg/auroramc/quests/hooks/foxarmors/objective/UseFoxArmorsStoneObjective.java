package gg.auroramc.quests.hooks.foxarmors.objective;

import com.google.common.collect.Lists;
import com.ignitedev.foxarmors.api.event.ItemUpgradeEvent;
import gg.auroramc.quests.api.objective.ObjectiveDefinition;
import gg.auroramc.quests.api.objective.StringTypedObjective;
import gg.auroramc.quests.api.objective.filter.ObjectiveFilter;
import gg.auroramc.quests.api.objective.filter.StringFilter;
import gg.auroramc.quests.api.objective.filter.StringListFilter;
import gg.auroramc.quests.api.profile.Profile;
import gg.auroramc.quests.api.quest.Quest;
import org.bukkit.event.EventPriority;

import java.util.List;

public class UseFoxArmorsStoneObjective extends StringTypedObjective {
    // count: attempts (default) adds 1 per paid attempt, count: stones adds the stones it took.
    private final boolean countStones;

    public UseFoxArmorsStoneObjective(Quest quest, ObjectiveDefinition definition, Profile.TaskDataWrapper data) {
        super(quest, definition, data);
        this.countStones = definition.getArgs().getString("count", "attempts").equalsIgnoreCase("stones");
    }

    @Override
    protected void activate() {
        onEvent(ItemUpgradeEvent.class, this::handle, EventPriority.MONITOR);
    }

    public void handle(ItemUpgradeEvent e) {
        int amount = countStones ? e.getStonesUsed() : 1;
        if (amount <= 0) return;
        progress(amount, StringFilter.with(meta(e.getStoneId()), "outcome", e.getOutcome().name()));
    }

    @Override
    public List<ObjectiveFilter> getFilters() {
        return Lists.newArrayList(StringListFilter.stringListFilter(definition.getArgs(), "outcome"));
    }
}
