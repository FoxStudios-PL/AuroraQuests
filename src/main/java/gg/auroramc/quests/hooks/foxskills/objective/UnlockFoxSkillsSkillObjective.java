package gg.auroramc.quests.hooks.foxskills.objective;

import com.foxstudios.foxskills.FoxSkills;
import com.foxstudios.foxskills.api.events.PlayerWeaponSkillUnlockEvent;
import gg.auroramc.quests.AuroraQuests;
import gg.auroramc.quests.api.objective.ObjectiveDefinition;
import gg.auroramc.quests.api.objective.StringTypedObjective;
import gg.auroramc.quests.api.profile.Profile;
import gg.auroramc.quests.api.quest.Quest;
import org.bukkit.Bukkit;
import org.bukkit.event.EventPriority;

import java.util.HashSet;
import java.util.Set;

public class UnlockFoxSkillsSkillObjective extends StringTypedObjective {
    // Logged once: FoxSkills' unlock readers are public methods of its managers and models,
    // not of its API interface, so a FoxSkills update could rename them.
    private static volatile boolean readerMissingLogged = false;

    // "<weaponId>:<skillId>" pairs already credited while this step is active, either by the
    // read on activation or by an unlock event. A pair is never credited twice, so progress
    // can't exceed the number of distinct skills actually unlocked.
    private final Set<String> credited = new HashSet<>();

    public UnlockFoxSkillsSkillObjective(Quest quest, ObjectiveDefinition definition, Profile.TaskDataWrapper data) {
        super(quest, definition, data);
    }

    @Override
    protected void activate() {
        credited.clear();
        onEvent(PlayerWeaponSkillUnlockEvent.class, this::handle, EventPriority.MONITOR);

        // activate() runs exactly when the step becomes active: quest start, previous linear
        // step completed (also through /quests complete), /quests unlock, and the re-start of
        // every active step on login. FoxSkills only fires its unlock event once per weapon +
        // skill (it refuses to unlock an already-unlocked skill), so a skill unlocked before
        // the step opened would never count: read the weapons once. One tick later and on the
        // player's own thread, like REACH_FOXSKILLS_WEAPON_LEVEL: the quest has recorded this
        // step as active by then, and activate() can run off the main thread.
        syncDelayed(this::checkUnlockedSkills, 1);
        // Read again 2 s later: on a network, the inventory (and the weapons in it) can be
        // restored by a sync plugin a moment after the join. Harmless when the first read
        // already completed the step (the task is cancelled with dispose()) or found nothing
        // new (the read never adds to the progress).
        syncDelayed(this::checkUnlockedSkills, 40);
    }

    public void handle(PlayerWeaponSkillUnlockEvent e) {
        // type = "<weaponId>:<skillId>" so the `types` filter can target e.g. "katana:dash"
        var weaponId = e.getWeaponId() != null ? e.getWeaponId() : "unknown";
        var key = weaponId + ":" + e.getSkillId();
        AuroraQuests.logger().debug("UNLOCK_FOXSKILLS_SKILL " + debugId() + ": unlock event " + key
                + (credited.contains(key) ? " (already credited, ignored)" : ""));
        // Already counted by the read on activation (e.g. the same skill unlocked on a second
        // copy of the weapon): no second credit.
        if (credited.contains(key)) return;

        var meta = meta(key);
        if (!passesFilters(meta)) return;

        credited.add(key);
        progress(1, meta);
    }

    private String debugId() {
        return data.profile().getPlayer().getName() + " " + data.poolId() + "/" + data.questId() + "/" + data.taskId();
    }

    private void checkUnlockedSkills() {
        // Only the active, not yet completed step. A dispose() since the scheduling (step
        // completed, quest reset, logout, reload) already cancelled this task; this guard is
        // the same one progress() relies on against a double completion.
        if (!started || isCompleted()) return;

        if (!(Bukkit.getPluginManager().getPlugin("FoxSkills") instanceof FoxSkills foxSkills) || !foxSkills.isEnabled()) {
            return;
        }

        try {
            var weapons = foxSkills.getWeaponManager();
            var unlocks = foxSkills.getSkillUnlockManager();
            if (weapons == null || unlocks == null) return;

            double found = 0;
            Set<String> seen = new HashSet<>();
            // Hotbar (main hand) + main inventory + armor + off hand. FoxSkills stores the
            // unlocked skills on the weapon item itself.
            for (var item : data.profile().getPlayer().getInventory().getContents()) {
                // null for an empty slot or anything that is not a class weapon.
                var weaponId = weapons.getWeaponId(item);
                if (weaponId == null) continue;
                var weapon = weapons.getWeapon(item);
                if (weapon == null || weapon.getSkills() == null) continue;

                for (var entry : weapon.getSkills().entrySet()) {
                    var skill = entry.getValue();
                    if (skill == null) continue;
                    // Skills given from the start (basic attack, passive) were never unlocked
                    // by the player: counting them would complete the step for free.
                    if (skill.isUnlockedByDefault()) continue;

                    var skillId = skill.getId() != null ? skill.getId() : entry.getKey();
                    if (!unlocks.isUnlocked(item, skillId)) continue;

                    var key = weaponId + ":" + skillId;
                    // Same skill on a second copy of the weapon: one skill, counted once.
                    if (!seen.add(key)) continue;

                    var meta = meta(key);
                    if (!passesFilters(meta)) continue;

                    credited.add(key);
                    found += applyMultipliers(1, meta);
                }
            }

            // Reach semantics: `found` is everything the player has unlocked right now,
            // including skills an unlock event already credited (this session or an earlier
            // one), so it replaces the progress only when higher and never adds to it.
            AuroraQuests.logger().debug("UNLOCK_FOXSKILLS_SKILL " + debugId() + ": read " + seen.size()
                    + " unlocked skill(s) " + seen + ", counted " + found + ", progress " + getProgress() + "/" + target);
            if (found > getProgress()) {
                setProgress(found);
            }
        } catch (LinkageError e) {
            if (!readerMissingLogged) {
                readerMissingLogged = true;
                AuroraQuests.logger().warning("This FoxSkills version doesn't expose the skill unlock readers used by "
                        + "UNLOCK_FOXSKILLS_SKILL's check on activation (" + e + "). Steps keep progressing on unlocks only.");
            }
        }
    }
}
