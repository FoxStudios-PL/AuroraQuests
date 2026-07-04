package gg.auroramc.quests.objective;

import gg.auroramc.quests.api.event.objective.PlayerOpenMenuEvent;
import gg.auroramc.quests.api.objective.ObjectiveDefinition;
import gg.auroramc.quests.api.objective.StringTypedObjective;
import gg.auroramc.quests.api.profile.Profile;
import gg.auroramc.quests.api.quest.Quest;
import org.bukkit.event.EventPriority;

public class OpenMenuObjective extends StringTypedObjective {

    public OpenMenuObjective(Quest quest, ObjectiveDefinition definition, Profile.TaskDataWrapper data) {
        super(quest, definition, data);
    }

    @Override
    protected void activate() {
        onEvent(PlayerOpenMenuEvent.class, this::handle, EventPriority.MONITOR);
    }

    public void handle(PlayerOpenMenuEvent event) {
        // "type" = the menu id, matched against the configured "types" list
        // (empty types = any menu).
        progress(1, meta(event.getMenuName()));
    }
}
