package gg.auroramc.quests.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoreLinesTest {
    @Test
    void splitsMultiLineTokens() {
        assertEquals(List.of("a", "b", "c"), LoreLines.expand("{tasks}", "a\nb\nc", false));
    }

    @Test
    void singleLineStaysAsIs() {
        assertEquals(List.of(" &7Hello"), LoreLines.expand(" &7Hello", " &7Hello", false));
    }

    /** A line whose {tasks} rendered empty vanishes even with the option off (no hole). */
    @Test
    void emptyTasksLineIsAlwaysDropped() {
        assertEquals(List.of(), LoreLines.expand("{tasks}", "", false));
        assertEquals(List.of(), LoreLines.expand(" &8• &f{tasks}", " &8• &f", false));
    }

    /** " &8• &f{current_task}" on a completed quest: lone bullet dropped when opted in. */
    @Test
    void emptiedLineDroppedOnlyWhenOptedIn() {
        var template = " &8• &f{current_task}";
        var filled = " &8• &f";
        assertEquals(List.of(filled), LoreLines.expand(template, filled, false));
        assertEquals(List.of(), LoreLines.expand(template, filled, true));
    }

    /** Deliberately blank lines and code-only separators are never dropped. */
    @Test
    void deliberateBlankAndSeparatorLinesAreKept() {
        assertEquals(List.of(""), LoreLines.expand("", "", true));
        assertEquals(List.of("&8&m----------"), LoreLines.expand("&8&m----------", "&8&m----------", true));
    }

    @Test
    void lineWithRealContentIsNeverDropped() {
        assertEquals(List.of(" &8• &fTuer 5 slimes 4/5"),
                LoreLines.expand(" &8• &f{current_task}", " &8• &fTuer 5 slimes 4/5", true));
    }

    @Test
    void visibleTextDetectionStripsColourCodesAndTags() {
        assertFalse(LoreLines.hasVisibleText(" &8• &f"));
        assertFalse(LoreLines.hasVisibleText("&#a1b2c3<bold> • </bold>"));
        assertTrue(LoreLines.hasVisibleText(" &8• &fEtape"));
        assertTrue(LoreLines.hasVisibleText("{tasks}"));
    }
}
