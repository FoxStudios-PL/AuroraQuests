package gg.auroramc.quests.quest;

import gg.auroramc.quests.api.quest.ItemRecoveryDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Window and trigger rules of item-recovery, on the terres_sauvages example. */
class ItemRecoveryRulesTest {
    private static final List<String> TASKS = List.of(
            "parler_ruben", "aller_ressource", "couper_bois", "fabriquer_coffres",
            "rejoindre_royaume", "poser_coffres", "rapporter_chene", "rendre_gland");

    private static final ItemRecoveryDefinition GLAND = recovery("couper_bois", "rendre_gland", 1.0, 5);

    private static ItemRecoveryDefinition recovery(String after, String until, double chance, int cooldownSeconds) {
        return ItemRecoveryDefinition.builder()
                .id("gland")
                .after(after)
                .until(until)
                .chance(chance)
                .cooldownMillis(cooldownSeconds * 1000L)
                .onRecover(List.of("[console] nexo give gland_dore 1 %player_name%"))
                .build();
    }

    /** Linear quest: player currently on task number {@code step} (1-based), all before are done. */
    private static Predicate<String> onStep(int step) {
        return task -> TASKS.indexOf(task) < step - 1;
    }

    @Test
    void windowClosedBeforeAfterTask() {
        assertFalse(GLAND.isWindowOpen(true, onStep(3)));
    }

    @Test
    void windowOpenFromTaskFourToEight() {
        for (int step = 4; step <= 8; step++) {
            assertTrue(GLAND.isWindowOpen(true, onStep(step)), "step " + step);
        }
    }

    @Test
    void windowClosedOnceUntilTaskOrQuestCompleted() {
        assertFalse(GLAND.isWindowOpen(true, onStep(9)));
        assertFalse(GLAND.isWindowOpen(false, onStep(5)));
    }

    /** Free (non linear) mode: only the completion of the two bounds matters. */
    @Test
    void windowIgnoresOrderInFreeMode() {
        assertTrue(GLAND.isWindowOpen(true, Set.of("couper_bois", "rapporter_chene")::contains));
        assertFalse(GLAND.isWindowOpen(true, Set.of("rapporter_chene", "poser_coffres")::contains));
        assertFalse(GLAND.isWindowOpen(true, Set.of("couper_bois", "rendre_gland")::contains));
    }

    @Test
    void missingBoundsCoverTheWholeQuest() {
        var always = recovery(null, null, 1.0, 5);
        assertTrue(always.isWindowOpen(true, onStep(1)));
        assertTrue(always.isWindowOpen(true, onStep(8)));
        assertFalse(always.isWindowOpen(false, onStep(1)));
    }

    @Test
    void givesBackOnlyWithoutTheItem() {
        assertTrue(GLAND.shouldRecover(true, 0, 0.5, 10_000, 0));
        assertFalse(GLAND.shouldRecover(true, 1, 0.5, 10_000, 0));
        assertFalse(GLAND.shouldRecover(false, 0, 0.5, 10_000, 0));
    }

    @Test
    void cooldownBlocksSecondRecovery() {
        assertFalse(GLAND.shouldRecover(true, 0, 0.5, 10_000 + 4_999, 10_000));
        assertTrue(GLAND.shouldRecover(true, 0, 0.5, 10_000 + 5_000, 10_000));
    }

    @Test
    void chanceRoll() {
        var half = recovery("couper_bois", "rendre_gland", 0.5, 0);
        assertTrue(half.shouldRecover(true, 0, 0.49, 1, 0));
        assertFalse(half.shouldRecover(true, 0, 0.5, 1, 0));
        var never = recovery("couper_bois", "rendre_gland", 0.0, 0);
        assertFalse(never.shouldRecover(true, 0, 0.0, 1, 0));
    }
}
