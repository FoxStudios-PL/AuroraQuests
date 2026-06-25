package gg.auroramc.quests.hooks.mythicmobs;

import gg.auroramc.aurora.api.AuroraAPI;
import gg.auroramc.aurora.api.item.TypeId;
import gg.auroramc.quests.AuroraQuests;
import gg.auroramc.quests.api.event.objective.PlayerDealDamageEvent;
import gg.auroramc.quests.api.event.objective.PlayerKillMobEvent;
import gg.auroramc.quests.api.event.objective.PlayerLootEvent;
import io.lumine.mythic.bukkit.events.MythicDamageEvent;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class MythicMobListener implements Listener {
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMythicMobDeath(MythicMobDeathEvent e) {
        if (!(e.getKiller() instanceof Player player)) return;

        var mobName = e.getMob().getType().getInternalName();
        var drops = e.getDrops();
        var typeId = new TypeId("mythicmobs", mobName);

        if (e.getMob().getLevel() > 1) {
            Bukkit.getPluginManager().callEvent(new PlayerKillMobEvent(player, typeId, 1, e.getMob().getLevel()));
        } else {
            Bukkit.getPluginManager().callEvent(new PlayerKillMobEvent(player, typeId, 1));
        }

        for (var drop : drops) {
            var dropId = AuroraAPI.getItemManager().resolveId(drop);
            Bukkit.getPluginManager().callEvent(new PlayerLootEvent(player, dropId, drop.getAmount(), PlayerLootEvent.Source.ENTITY));
        }
    }

    // Bridges player-cast MythicMobs skill damage into DEAL_DAMAGE objectives (count-skill-damage).
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMythicDamage(MythicDamageEvent e) {
        var casterEntity = e.getCaster().getEntity().getBukkitEntity();
        if (!(casterEntity instanceof Player player)) return;
        if (e.getTarget() == null) return;
        var target = e.getTarget().getBukkitEntity();
        if (target == null) return;

        AuroraQuests.logger().info("[AQ-DEBUG] MythicDamage fired: caster=" + player.getName()
                + " target=" + target.getType() + " damage=" + e.getDamage());

        Bukkit.getPluginManager().callEvent(new PlayerDealDamageEvent(player, target, e.getDamage()));
    }
}
