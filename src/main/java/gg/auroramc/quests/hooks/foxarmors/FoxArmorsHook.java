package gg.auroramc.quests.hooks.foxarmors;

import gg.auroramc.quests.AuroraQuests;
import gg.auroramc.quests.api.factory.ObjectiveFactory;
import gg.auroramc.quests.api.objective.ObjectiveType;
import gg.auroramc.quests.hooks.Hook;
import gg.auroramc.quests.hooks.foxarmors.objective.ApplyFoxArmorsRuneObjective;
import gg.auroramc.quests.hooks.foxarmors.objective.UseFoxArmorsStoneObjective;

public class FoxArmorsHook implements Hook {
    @Override
    public void hook(AuroraQuests plugin) {
        ObjectiveFactory.registerObjective(ObjectiveType.APPLY_FOXARMORS_RUNE, ApplyFoxArmorsRuneObjective.class);
        ObjectiveFactory.registerObjective(ObjectiveType.USE_FOXARMORS_STONE, UseFoxArmorsStoneObjective.class);

        AuroraQuests.logger().info("Hooked into FoxArmors for APPLY_FOXARMORS_RUNE, USE_FOXARMORS_STONE");
    }
}
