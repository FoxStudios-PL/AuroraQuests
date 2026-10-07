package gg.auroramc.quests.hooks.foxskills.objective;

import com.foxstudios.foxskills.FoxSkills;
import com.foxstudios.foxskills.api.events.PlayerWeaponLevelUpEvent;
import gg.auroramc.quests.AuroraQuests;
import gg.auroramc.quests.api.objective.ObjectiveDefinition;
import gg.auroramc.quests.api.objective.StringTypedObjective;
import gg.auroramc.quests.api.profile.Profile;
import gg.auroramc.quests.api.quest.Quest;
import org.bukkit.Bukkit;
import org.bukkit.event.EventPriority;

public class ReachFoxSkillsWeaponLevelObjective extends StringTypedObjective {
    // Logged once: FoxSkills' item readers are public methods of its managers, not of its
    // API interface, so a FoxSkills update could rename them.
    private static volatile boolean readerMissingLogged = false;

    public ReachFoxSkillsWeaponLevelObjective(Quest quest, ObjectiveDefinition definition, Profile.TaskDataWrapper data) {
        super(quest, definition, data);
    }

    @Override
    protected void activate() {
        onEvent(PlayerWeaponLevelUpEvent.class, this::handle, EventPriority.MONITOR);

        // activate() runs exactly when the step becomes active: quest start, previous linear
        // step completed (also through /quests complete), /quests unlock, and the re-start of
        // every active step on login. The level-up event alone would make a weapon already
        // past `amount` wait for its next level, so read the weapons once. One tick later and
        // on the player's own thread: the quest has recorded this step as active by then, and
        // activate() can run off the main thread (profile load, unlock task).
        if (isCheckOnStartEnabled()) {
            syncDelayed(this::checkHeldWeapons, 1);
        }
    }

    public void handle(PlayerWeaponLevelUpEvent e) {
        // type = weaponId so the `types` filter can target e.g. "katana"
        var weaponId = e.getWeaponId() != null ? e.getWeaponId() : "unknown";
        if (!passesFilters(meta(weaponId))) return;

        reach(e.getNewLevel());
    }

    // reach semantics: jump to the level reached, never decrease
    // (another weapon leveling below the best one must not lower progress)
    private void reach(int level) {
        if (level > getProgress()) {
            setProgress(level);
        }
    }

    private void checkHeldWeapons() {
        // Only the active, not yet completed step. A dispose() since the scheduling (step
        // completed by a level-up, quest reset, logout, reload) already cancelled this task;
        // this guard is the same one progress() relies on against a double completion.
        if (!started || isCompleted()) return;

        if (!(Bukkit.getPluginManager().getPlugin("FoxSkills") instanceof FoxSkills foxSkills) || !foxSkills.isEnabled()) {
            return;
        }

        try {
            var weapons = foxSkills.getWeaponManager();
            var leveling = foxSkills.getWeaponLevelingManager();
            if (weapons == null || leveling == null) return;

            int best = 0;
            // Hotbar (main hand) + main inventory + armor + off hand.
            for (var item : data.profile().getPlayer().getInventory().getContents()) {
                // null for an empty slot or anything that is not a class weapon. Checked first:
                // getWeaponLevel() answers 1 for any item without a stored level.
                var weaponId = weapons.getWeaponId(item);
                if (weaponId == null) continue;

                // The stored level: the same value PlayerWeaponLevelUpEvent carries as new level.
                int level = leveling.getWeaponLevel(item);
                if (level > best && passesFilters(meta(weaponId))) {
                    best = level;
                }
            }

            // No class weapon: progress is left untouched (still shows 0).
            if (best > 0) {
                reach(best);
            }
        } catch (LinkageError e) {
            if (!readerMissingLogged) {
                readerMissingLogged = true;
                AuroraQuests.logger().warning("This FoxSkills version doesn't expose the weapon level readers used by "
                        + "REACH_FOXSKILLS_WEAPON_LEVEL's check-on-start (" + e + "). Steps keep progressing on level-ups only.");
            }
        }
    }

    private static boolean isCheckOnStartEnabled() {
        var objectives = AuroraQuests.getInstance().getConfigManager().getConfig().getObjectives();
        if (objectives == null || objectives.getReachFoxskillsWeaponLevel() == null) return true;
        return !Boolean.FALSE.equals(objectives.getReachFoxskillsWeaponLevel().getCheckOnStart());
    }
}
