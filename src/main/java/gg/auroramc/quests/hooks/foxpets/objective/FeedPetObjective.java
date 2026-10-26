package gg.auroramc.quests.hooks.foxpets.objective;

import com.google.common.collect.Lists;
import fr.foxpets.api.event.PetFeedEvent;
import gg.auroramc.quests.api.objective.ObjectiveDefinition;
import gg.auroramc.quests.api.objective.StringTypedObjective;
import gg.auroramc.quests.api.objective.filter.ObjectiveFilter;
import gg.auroramc.quests.api.objective.filter.StringFilter;
import gg.auroramc.quests.api.objective.filter.StringListFilter;
import gg.auroramc.quests.api.profile.Profile;
import gg.auroramc.quests.api.quest.Quest;
import org.bukkit.event.EventPriority;

import java.util.List;

public class FeedPetObjective extends StringTypedObjective {

    public FeedPetObjective(Quest quest, ObjectiveDefinition definition, Profile.TaskDataWrapper data) {
        super(quest, definition, data);
    }

    @Override
    protected void activate() {
        onEvent(PetFeedEvent.class, this::handle, EventPriority.MONITOR);
    }

    public void handle(PetFeedEvent e) {
        // A meal given to an egg has no pet yet: `types` then matches the egg type instead.
        var type = e.getPetId() != null ? e.getPetId() : e.getEggType();
        progress(1, StringFilter.with(meta(type != null ? type : "unknown"), "kind", e.getKind().name()));
    }

    @Override
    public List<ObjectiveFilter> getFilters() {
        return Lists.newArrayList(StringListFilter.stringListFilter(definition.getArgs(), "kind"));
    }
}
