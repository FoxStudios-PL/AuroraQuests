package gg.auroramc.quests.hooks.foxskills.objective;

import com.foxstudios.foxskills.api.events.PlayerWeaponLevelUpEvent;
import gg.auroramc.quests.api.objective.ObjectiveDefinition;
import gg.auroramc.quests.api.objective.StringTypedObjective;
import gg.auroramc.quests.api.profile.Profile;
import gg.auroramc.quests.api.quest.Quest;
import org.bukkit.event.EventPriority;

public class ReachFoxSkillsWeaponLevelObjective extends StringTypedObjective {

    public ReachFoxSkillsWeaponLevelObjective(Quest quest, ObjectiveDefinition definition, Profile.TaskDataWrapper data) {
        super(quest, definition, data);
    }

    @Override
    protected void activate() {
        onEvent(PlayerWeaponLevelUpEvent.class, this::handle, EventPriority.MONITOR);
    }

    public void handle(PlayerWeaponLevelUpEvent e) {
        // type = weaponId so the `types` filter can target e.g. "katana"
        var weaponId = e.getWeaponId() != null ? e.getWeaponId() : "unknown";
        if (!passesFilters(meta(weaponId))) return;

        // reach semantics: jump to the level reached, never decrease
        // (another weapon leveling below the best one must not lower progress)
        if (e.getNewLevel() > getProgress()) {
            setProgress(e.getNewLevel());
        }
    }
}
