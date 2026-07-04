package gg.auroramc.quests.objective;

import gg.auroramc.quests.api.event.objective.PlayerPlaceFurnitureEvent;
import gg.auroramc.quests.api.objective.ObjectiveDefinition;
import gg.auroramc.quests.api.objective.StringTypedObjective;
import gg.auroramc.quests.api.profile.Profile;
import gg.auroramc.quests.api.quest.Quest;
import org.bukkit.event.EventPriority;

public class PlaceFurnitureObjective extends StringTypedObjective {

    public PlaceFurnitureObjective(Quest quest, ObjectiveDefinition definition, Profile.TaskDataWrapper data) {
        super(quest, definition, data);
    }

    @Override
    protected void activate() {
        onEvent(PlayerPlaceFurnitureEvent.class, this::handle, EventPriority.MONITOR);
    }

    public void handle(PlayerPlaceFurnitureEvent event) {
        // "type" = the furniture id, matched against the configured "types" list
        // (empty types = any furniture).
        progress(1, meta(event.getFurnitureId()));
    }
}
