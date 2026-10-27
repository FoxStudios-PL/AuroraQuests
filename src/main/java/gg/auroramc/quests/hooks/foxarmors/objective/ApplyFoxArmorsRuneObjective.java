package gg.auroramc.quests.hooks.foxarmors.objective;

import com.ignitedev.foxarmors.api.event.RuneApplyEvent;
import gg.auroramc.quests.api.objective.ObjectiveDefinition;
import gg.auroramc.quests.api.objective.StringTypedObjective;
import gg.auroramc.quests.api.profile.Profile;
import gg.auroramc.quests.api.quest.Quest;
import org.bukkit.event.EventPriority;

public class ApplyFoxArmorsRuneObjective extends StringTypedObjective {

    public ApplyFoxArmorsRuneObjective(Quest quest, ObjectiveDefinition definition, Profile.TaskDataWrapper data) {
        super(quest, definition, data);
    }

    @Override
    protected void activate() {
        onEvent(RuneApplyEvent.class, this::handle, EventPriority.MONITOR);
    }

    public void handle(RuneApplyEvent e) {
        progress(1, meta(e.getRuneId()));
    }
}
