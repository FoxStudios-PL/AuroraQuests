package gg.auroramc.quests.objective;

import gg.auroramc.aurora.api.item.TypeId;
import gg.auroramc.quests.AuroraQuests;
import gg.auroramc.quests.api.objective.ObjectiveDefinition;
import gg.auroramc.quests.api.objective.ObjectiveType;
import gg.auroramc.quests.api.objective.TypedObjective;
import gg.auroramc.quests.api.profile.Profile;
import gg.auroramc.quests.api.quest.Quest;
import org.bukkit.Material;
import org.bukkit.entity.Cow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Goat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.Map;

public class MilkingObjective extends TypedObjective {

    public MilkingObjective(Quest quest, ObjectiveDefinition definition, Profile.TaskDataWrapper data) {
        super(quest, definition, data);
    }

    @Override
    protected void activate() {
        // Since 26.x Paper only fires the PlayerInteractAtEntityEvent subclass for entity
        // right-clicks, so subclasses must be accepted or no milking is ever counted.
        onEvent(PlayerInteractEntityEvent.class, this::onMilk, EventPriority.MONITOR, true, true);
    }

    public void onMilk(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Cow || event.getRightClicked() instanceof Goat)) {
            return;
        }

        // The item of the hand that interacted: counts an off-hand bucket, and never counts
        // the same click twice when the event also fires for the other hand.
        var item = event.getPlayer().getInventory().getItem(event.getHand());
        if (item == null || item.getType() != Material.BUCKET) {
            return;
        }

        progress(1, meta(event.getRightClicked() instanceof Cow ? TypeId.from(EntityType.COW) : TypeId.from(EntityType.GOAT)));
    }
}
