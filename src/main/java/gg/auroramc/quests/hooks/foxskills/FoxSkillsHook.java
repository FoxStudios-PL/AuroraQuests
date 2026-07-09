package gg.auroramc.quests.hooks.foxskills;

import gg.auroramc.quests.AuroraQuests;
import gg.auroramc.quests.api.factory.ObjectiveFactory;
import gg.auroramc.quests.api.objective.ObjectiveType;
import gg.auroramc.quests.hooks.Hook;
import gg.auroramc.quests.hooks.foxskills.objective.UnlockFoxSkillsSkillObjective;

public class FoxSkillsHook implements Hook {
    @Override
    public void hook(AuroraQuests plugin) {
        ObjectiveFactory.registerObjective(ObjectiveType.UNLOCK_FOXSKILLS_SKILL, UnlockFoxSkillsSkillObjective.class);

        AuroraQuests.logger().info("Hooked into FoxSkills for UNLOCK_FOXSKILLS_SKILL objective");
    }
}
