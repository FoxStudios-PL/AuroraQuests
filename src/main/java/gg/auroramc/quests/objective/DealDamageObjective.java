package gg.auroramc.quests.objective;

import gg.auroramc.aurora.api.AuroraAPI;
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
        progress(event.getFinalDamage(), meta(id));
    }

    // Counts damage dealt by player-cast MythicMobs skills (bridged via the MythicMobs hook).
    // Matches the player by UUID since the bridged reference may differ from the live profile player.
    public void handleSkillDamage(PlayerDealDamageEvent event) {
        if (!event.getPlayer().getUniqueId().equals(data.profile().getPlayer().getUniqueId())) return;
        if (event.getTarget() instanceof Player && !countPlayerDamage) {
            return;
        }
        var id = AuroraAPI.getEntityManager().resolveId(event.getTarget());
        progress(event.getDamage(), meta(id));
    }
}
