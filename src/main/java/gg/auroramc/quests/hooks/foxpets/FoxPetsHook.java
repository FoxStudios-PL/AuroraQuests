package gg.auroramc.quests.hooks.foxpets;

import gg.auroramc.quests.AuroraQuests;
import gg.auroramc.quests.api.factory.ObjectiveFactory;
import gg.auroramc.quests.api.objective.ObjectiveType;
import gg.auroramc.quests.hooks.Hook;
import gg.auroramc.quests.hooks.foxpets.objective.FeedPetObjective;
import gg.auroramc.quests.hooks.foxpets.objective.HatchPetEggObjective;

public class FoxPetsHook implements Hook {
    @Override
    public void hook(AuroraQuests plugin) {
        ObjectiveFactory.registerObjective(ObjectiveType.FEED_PET, FeedPetObjective.class);
        ObjectiveFactory.registerObjective(ObjectiveType.HATCH_PET_EGG, HatchPetEggObjective.class);

        AuroraQuests.logger().info("Hooked into FoxPets for FEED_PET, HATCH_PET_EGG");
    }
}
