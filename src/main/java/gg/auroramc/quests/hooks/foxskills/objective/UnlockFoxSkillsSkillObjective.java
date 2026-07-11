package gg.auroramc.quests.hooks.foxskills.objective;

import com.foxstudios.foxskills.api.events.PlayerWeaponSkillUnlockEvent;
import gg.auroramc.quests.api.objective.ObjectiveDefinition;
import gg.auroramc.quests.api.objective.StringTypedObjective;
import gg.auroramc.quests.api.profile.Profile;
import gg.auroramc.quests.api.quest.Quest;
import org.bukkit.event.EventPriority;

public class UnlockFoxSkillsSkillObjective extends StringTypedObjective {

    public UnlockFoxSkillsSkillObjective(Quest quest, ObjectiveDefinition definition, Profile.TaskDataWrapper data) {
        super(quest, definition, data);
    }

    @Override
    protected void activate() {
        onEvent(PlayerWeaponSkillUnlockEvent.class, this::handle, EventPriority.MONITOR);
    }

    public void handle(PlayerWeaponSkillUnlockEvent e) {
        // type = "<weaponId>:<skillId>" so the `types` filter can target e.g. "katana:dash"
        var weaponId = e.getWeaponId() != null ? e.getWeaponId() : "unknown";
        progress(1, meta(weaponId + ":" + e.getSkillId()));
    }
}
