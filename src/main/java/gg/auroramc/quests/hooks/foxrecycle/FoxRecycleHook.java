package gg.auroramc.quests.hooks.foxrecycle;

import gg.auroramc.quests.AuroraQuests;
import gg.auroramc.quests.api.factory.ObjectiveFactory;
import gg.auroramc.quests.api.objective.ObjectiveType;
import gg.auroramc.quests.hooks.Hook;
import gg.auroramc.quests.hooks.foxrecycle.objective.RecycleItemObjective;

public class FoxRecycleHook implements Hook {
    @Override
    public void hook(AuroraQuests plugin) {
        ObjectiveFactory.registerObjective(ObjectiveType.RECYCLE_ITEM, RecycleItemObjective.class);

        AuroraQuests.logger().info("Hooked into FoxRecycle for RECYCLE_ITEM");
    }
}
