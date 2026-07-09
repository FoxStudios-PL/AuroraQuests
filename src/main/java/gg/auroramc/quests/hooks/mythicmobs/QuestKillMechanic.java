package gg.auroramc.quests.hooks.mythicmobs;

import gg.auroramc.aurora.api.item.TypeId;
import gg.auroramc.quests.api.event.objective.PlayerKillMobEvent;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Custom MythicMobs mechanic that credits a mob "kill" to the targeted player without
 * the mob dying: {@code questkill{type=<internal_name>;amount=<n>} @trigger}.
 *
 * Meant for harvest-node style mobs (custom ores, crops) whose lifecycle is
 * remove/respawn instead of death, so MythicMobDeathEvent never fires for them.
 * Fires the same PlayerKillMobEvent as the death listener, so KILL_MOB and
 * KILL_LEVELLED_MOB objectives (types, filters, multipliers) behave identically.
 *
 * - type/t/mob/m: mythic mob internal name to credit; defaults to the casting mob's own type
 * - amount/a: kills to credit, defaults to 1
 */
public class QuestKillMechanic implements ITargetedEntitySkill {
    private final String type;
    private final int amount;

    public QuestKillMechanic(MythicLineConfig config) {
        this.type = config.getString(new String[]{"type", "t", "mob", "m"}, null);
        this.amount = config.getInteger(new String[]{"amount", "a"}, 1);
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        if (!(target.getBukkitEntity() instanceof Player player)) return SkillResult.INVALID_TARGET;

        var mobName = type;
        double level = 0;

        if (data.getCaster() instanceof ActiveMob mob) {
            if (mobName == null) mobName = mob.getType().getInternalName();
            level = mob.getLevel();
        }

        // No explicit type and the caster isn't a mythic mob (e.g. cast from an item)
        if (mobName == null) return SkillResult.INVALID_CONFIG;

        var typeId = new TypeId("mythicmobs", mobName);

        if (level > 1) {
            Bukkit.getPluginManager().callEvent(new PlayerKillMobEvent(player, typeId, amount, level));
        } else {
            Bukkit.getPluginManager().callEvent(new PlayerKillMobEvent(player, typeId, amount));
        }

        return SkillResult.SUCCESS;
    }
}
