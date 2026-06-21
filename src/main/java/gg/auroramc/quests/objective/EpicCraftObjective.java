package gg.auroramc.quests.objective;

import com.google.common.collect.Lists;
import gg.auroramc.quests.api.event.objective.PlayerEpicCraftEvent;
import gg.auroramc.quests.api.objective.ObjectiveDefinition;
import gg.auroramc.quests.api.objective.StringTypedObjective;
import gg.auroramc.quests.api.objective.filter.ObjectiveFilter;
import gg.auroramc.quests.api.objective.filter.StringFilter;
import gg.auroramc.quests.api.profile.Profile;
import gg.auroramc.quests.api.quest.Quest;
import org.bukkit.event.EventPriority;

import java.util.List;

public class EpicCraftObjective extends StringTypedObjective {

    public EpicCraftObjective(Quest quest, ObjectiveDefinition definition, Profile.TaskDataWrapper data) {
        super(quest, definition, data);
    }

    @Override
    protected void activate() {
        onEvent(PlayerEpicCraftEvent.class, this::handle, EventPriority.MONITOR);
    }

    public void handle(PlayerEpicCraftEvent event) {
        // "type" = the unique crafting id (Crafting#getName), matched against the configured "types" list.
        // Optional secondary "category" filter, mirroring CompleteDungeonObjective's "difficulty".
        progress(1, StringFilter.with(meta(event.getCraftId()), "category", event.getCategory()));
    }

    @Override
    public List<ObjectiveFilter> getFilters() {
        return Lists.newArrayList(StringFilter.stringFilter(definition.getArgs(), "category"));
    }
}
