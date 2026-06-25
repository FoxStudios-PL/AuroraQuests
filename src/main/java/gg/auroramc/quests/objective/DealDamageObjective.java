package gg.auroramc.quests.objective;

import gg.auroramc.aurora.api.AuroraAPI;
import gg.auroramc.quests.AuroraQuests;
import gg.auroramc.quests.api.event.objective.PlayerDealDamageEvent;
import gg.auroramc.quests.api.objective.ObjectiveDefinition;
import gg.auroramc.quests.api.objective.TypedObjective;
import gg.auroramc.quests.api.profile.Profile;
import gg.auroramc.quests.api.quest.Quest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class DealDamageObjective extends TypedObjective {
    private final boolean countPlayerDamage;
    private final boolean countSkillDamage;

    public DealDamageObjective(Quest quest, ObjectiveDefinition definition, Profile.TaskDataWrapper data) {
        super(quest, definition, data);
        this.countPlayerDamage = definition.getArgs().getBoolean("count-player-damage", false);
        this.countSkillDamage = definition.getArgs().getBoolean("count-skill-damage", false);
    }

    @Override
    protected void activate() {
        AuroraQuests.logger().info("[AQ-DEBUG] DEAL_DAMAGE activate: quest=" + quest.getId()
                + " objective=" + definition.getId()
                + " countSkillDamage=" + countSkillDamage
                + " configTypes=" + definition.getArgs().getStringList("types")
                + " amount=" + target);
        onEvent(EntityDamageByEntityEvent.class, this::handle, EventPriority.MONITOR);
        if (countSkillDamage) {
            onEvent(PlayerDealDamageEvent.class, this::handleSkillDamage, EventPriority.MONITOR);
        }
    }

    public void handle(EntityDamageByEntityEvent event) {
        var damager = event.getDamager();
        if (!(damager instanceof Player player) || player != data.profile().getPlayer()) return;

        if (event.getEntity() instanceof Player && !countPlayerDamage) {
            return;
        }

        var id = AuroraAPI.getEntityManager().resolveId(event.getEntity());
        AuroraQuests.logger().info("[AQ-DEBUG] DEAL_DAMAGE melee handle: target=" + event.getEntity().getType()
                + " resolvedId=" + id + " finalDamage=" + event.getFinalDamage()
                + " passesFilters=" + passesFilters(meta(id)) + " completed=" + isCompleted());
        progress(event.getFinalDamage(), meta(id));
    }

    // Counts damage dealt by player-cast MythicMobs skills (bridged via the MythicMobs hook).
    public void handleSkillDamage(PlayerDealDamageEvent event) {
        boolean mine = event.getPlayer().getUniqueId().equals(data.profile().getPlayer().getUniqueId());
        AuroraQuests.logger().info("[AQ-DEBUG] DEAL_DAMAGE skill handle: eventPlayer=" + event.getPlayer().getName()
                + " mine=" + mine + " target=" + event.getTarget().getType() + " damage=" + event.getDamage());
        if (!mine) return;
        if (event.getTarget() instanceof Player && !countPlayerDamage) {
            return;
        }
        var id = AuroraAPI.getEntityManager().resolveId(event.getTarget());
        AuroraQuests.logger().info("[AQ-DEBUG] DEAL_DAMAGE skill progress: resolvedId=" + id
                + " configTypes=" + definition.getArgs().getStringList("types")
                + " passesFilters=" + passesFilters(meta(id)) + " completed=" + isCompleted());
        progress(event.getDamage(), meta(id));
    }
}
