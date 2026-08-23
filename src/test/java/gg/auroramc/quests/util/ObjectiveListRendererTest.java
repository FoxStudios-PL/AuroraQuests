package gg.auroramc.quests.util;

import gg.auroramc.quests.util.ObjectiveListRenderer.Settings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectiveListRendererTest {
    private static final Settings WINDOW = new Settings("window", 1, 2, "{task}",
            "... {count} done", "... {count} to come");

    private static List<String> steps(int n) {
        var out = new ArrayList<String>(n);
        for (int i = 1; i <= n; i++) out.add("step" + i);
        return out;
    }

    private static List<Boolean> completedUpTo(int n, int current) {
        var out = new ArrayList<Boolean>(n);
        for (int i = 0; i < n; i++) out.add(i < current);
        return out;
    }

    /** Recipe #1: 15 steps, player on step 6 -> fold above, 1 done, current, 2 next, fold below. */
    @Test
    void windowInTheMiddleOfALongQuest() {
        var lines = ObjectiveListRenderer.render(steps(15), completedUpTo(15, 5), 5, true, false, WINDOW);

        assertEquals(List.of("... 4 done", "step5", "step6", "step7", "step8", "... 7 to come"), lines);
    }

    /** Recipe #2: first step -> no fold above, no struck line, current + 2 next, fold of 12 below. */
    @Test
    void windowAtTheFirstStep() {
        var lines = ObjectiveListRenderer.render(steps(15), completedUpTo(15, 0), 0, true, false, WINDOW);

        assertEquals(List.of("step1", "step2", "step3", "... 12 to come"), lines);
    }

    /** Recipe #3: last step -> fold of 13 above, 1 struck, current, no fold below. */
    @Test
    void windowAtTheLastStep() {
        var lines = ObjectiveListRenderer.render(steps(15), completedUpTo(15, 14), 14, true, false, WINDOW);

        assertEquals(List.of("... 13 done", "step14", "step15"), lines);
    }

    /** Recipe #4: a 3-step quest never folds, whatever the current step. */
    @Test
    void shortQuestsRenderIdenticallyToModeAll() {
        for (int current = 0; current < 3; current++) {
            var windowed = ObjectiveListRenderer.render(steps(3), completedUpTo(3, current), current, true, false, WINDOW);
            assertEquals(steps(3), windowed, "current=" + current);
        }
    }

    /** Folding a single step gains nothing: the window extends instead ("gain >= 2" rule). */
    @Test
    void neverFoldsASingleStep() {
        // 5 steps, current = 1: exactly 1 upcoming step would fold (index 4) -> shown.
        var lines = ObjectiveListRenderer.render(steps(5), completedUpTo(5, 1), 1, true, false, WINDOW);
        assertEquals(List.of("step1", "step2", "step3", "step4", "step5"), lines);
    }

    /** Recipe #5: completed quest -> one single summary line with the total. */
    @Test
    void completedQuestCollapsesToOneLine() {
        var lines = ObjectiveListRenderer.render(steps(15), completedUpTo(15, 15), 14, true, true, WINDOW);
        assertEquals(List.of("... 15 done"), lines);
    }

    @Test
    void completedQuestInModeAllKeepsEveryLine() {
        var all = new Settings("all", 1, 2, "{task}", "... {count} done", "... {count} to come");
        var lines = ObjectiveListRenderer.render(steps(4), completedUpTo(4, 4), 3, true, true, all);
        assertEquals(steps(4), lines);
    }

    @Test
    void modeCurrentShowsOnlyTheCurrentStepWithoutSummaries() {
        var current = new Settings("current", 1, 2, "{task}", "... {count} done", "... {count} to come");
        var lines = ObjectiveListRenderer.render(steps(15), completedUpTo(15, 5), 5, true, false, current);
        assertEquals(List.of("step6"), lines);
    }

    /** Non-linear: every non-completed step stays; completed ones fold, keeping `before`. */
    @Test
    void nonLinearKeepsAllPlayableSteps() {
        var completed = List.of(true, false, true, true, false, true);
        var lines = ObjectiveListRenderer.render(steps(6), completed, 1, false, false, WINDOW);

        // 4 completed, before=1 -> the first 3 completed fold, the 4th stays as context.
        assertEquals(List.of("... 3 done", "step2", "step5", "step6"), lines);
    }

    @Test
    void nonLinearDoesNotFoldForASingleLineGain() {
        var completed = List.of(true, true, false, false);
        var lines = ObjectiveListRenderer.render(steps(4), completed, 2, false, false, WINDOW);
        // 2 completed, before=1 -> only 1 would fold -> nothing folds.
        assertEquals(steps(4), lines);
    }

    @Test
    void emptySummaryTemplateSuppressesTheLine() {
        var s = new Settings("window", 1, 2, "{task}", "", "... {count} to come");
        var lines = ObjectiveListRenderer.render(steps(15), completedUpTo(15, 5), 5, true, false, s);
        assertEquals(List.of("step5", "step6", "step7", "step8", "... 7 to come"), lines);
    }

    @Test
    void lineFormatWrapsEveryStep() {
        var s = new Settings("all", 1, 2, " - {task}", "x", "x");
        var lines = ObjectiveListRenderer.render(steps(2), completedUpTo(2, 0), 0, true, false, s);
        assertEquals(List.of(" - step1", " - step2"), lines);
    }

    @Test
    void resolveMergesOverrideThenGlobalThenDefaults() {
        var settings = ObjectiveListRenderer.resolve(null, null);
        assertEquals("all", settings.mode());
        assertEquals(1, settings.before());
        assertEquals(2, settings.after());
        assertEquals("{task}", settings.lineFormat());
        assertTrue(settings.collapsedBefore().contains("{count}"));
    }
}
