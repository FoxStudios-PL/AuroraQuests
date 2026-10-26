package gg.auroramc.quests.hooks.foxpets.objective;

import com.google.common.collect.Lists;
import fr.foxpets.api.event.PetHatchEvent;
import gg.auroramc.quests.api.objective.ObjectiveDefinition;
import gg.auroramc.quests.api.objective.StringTypedObjective;
import gg.auroramc.quests.api.objective.filter.ObjectiveFilter;
import gg.auroramc.quests.api.objective.filter.StringFilter;
import gg.auroramc.quests.api.objective.filter.StringListFilter;
import gg.auroramc.quests.api.profile.Profile;
import gg.auroramc.quests.api.quest.Quest;
import org.bukkit.event.EventPriority;

import java.util.List;

public class HatchPetEggObjective extends StringTypedObjective {

    public HatchPetEggObjective(Quest quest, ObjectiveDefinition definition, Profile.TaskDataWrapper data) {
        super(quest, definition, data);
    }

    @Override
    protected void activate() {
        onEvent(PetHatchEvent.class, this::handle, EventPriority.MONITOR);
    }

    public void handle(PetHatchEvent e) {
        progress(1, StringFilter.with(meta(e.getEggType()), "shiny", String.valueOf(e.isShiny())));
    }

    @Override
    public List<ObjectiveFilter> getFilters() {
        return Lists.newArrayList(StringListFilter.stringListFilter(definition.getArgs(), "shiny"));
    }
}
