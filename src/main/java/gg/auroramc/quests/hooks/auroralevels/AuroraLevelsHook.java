package gg.auroramc.quests.hooks.auroralevels;

import gg.auroramc.aurora.api.util.NamespacedId;
import gg.auroramc.levels.api.event.PlayerLevelUpEvent;
import gg.auroramc.quests.AuroraQuests;
import gg.auroramc.quests.api.factory.ObjectiveFactory;
import gg.auroramc.quests.api.objective.ObjectiveType;
import gg.auroramc.quests.hooks.Hook;
import gg.auroramc.quests.hooks.auroralevels.objective.GainAuroraLevelObjective;
import gg.auroramc.quests.hooks.auroralevels.objective.GainAuroraXpObjective;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class AuroraLevelsHook implements Hook, Listener {
    @Override
    public void hook(AuroraQuests plugin) {
        plugin.getPoolManager().getRewardFactory()
                .registerRewardType(NamespacedId.fromDefault("levels_xp"), AuroraLevelsReward.class);

        ObjectiveFactory.registerObjective(ObjectiveType.GAIN_AURORA_XP, GainAuroraXpObjective.class);
        ObjectiveFactory.registerObjective(ObjectiveType.GAIN_AURORA_LEVEL, GainAuroraLevelObjective.class);

        AuroraQuests.logger().info("Hooked into AuroraLevels for GAIN_AURORA_XP and GAIN_AURORA_LEVEL objective and for levels_xp reward");
    }

    /**
     * A level-up can move the player into another reward-scale tier: refresh their
     * advancement screen so the reward lines show the new amounts without a reconnect.
     * The chest menu needs nothing (its lore is rebuilt on every open).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLevelUp(PlayerLevelUpEvent event) {
        var plugin = AuroraQuests.getInstance();
        var configManager = plugin.getConfigManager();
        if (configManager == null || !configManager.getRewardScales().hasAuroraLevelSource()) return;

        var advancementGui = plugin.getAdvancementGuiManager();
        if (advancementGui != null) {
            advancementGui.refresh(event.getPlayer());
        }
    }
}
