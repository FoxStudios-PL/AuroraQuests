package gg.auroramc.quests.advancement;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancementLayoutTest {
    private static AdvancementLayout.Entry e(String key, String parent) {
        return new AdvancementLayout.Entry(key, parent);
    }

    @Test
    void independentQuestsStackUnderRootColumn() {
        var coords = AdvancementLayout.layout(List.of(e("a", null), e("b", null), e("c", null)));

        assertEquals(1f, coords.get("a")[0]);
        assertEquals(1f, coords.get("b")[0]);
        assertEquals(1f, coords.get("c")[0]);
        assertEquals(0f, coords.get("a")[1]);
        assertEquals(1f, coords.get("b")[1]);
        assertEquals(2f, coords.get("c")[1]);
        // Root centered on its children, in column 0.
        assertEquals(0f, coords.get("")[0]);
        assertEquals(1f, coords.get("")[1]);
    }

    @Test
    void chainAdvancesOneColumnPerDepth() {
        var coords = AdvancementLayout.layout(List.of(e("a", null), e("b", "a"), e("c", "b")));

        assertEquals(1f, coords.get("a")[0]);
        assertEquals(2f, coords.get("b")[0]);
        assertEquals(3f, coords.get("c")[0]);
        // A pure chain stays on a single row.
        assertEquals(0f, coords.get("a")[1]);
        assertEquals(0f, coords.get("b")[1]);
        assertEquals(0f, coords.get("c")[1]);
    }

    @Test
    void parentIsCenteredOnItsChildren() {
        var coords = AdvancementLayout.layout(List.of(
                e("root1", null), e("kid1", "root1"), e("kid2", "root1"), e("kid3", "root1")));

        assertEquals(0f, coords.get("kid1")[1]);
        assertEquals(1f, coords.get("kid2")[1]);
        assertEquals(2f, coords.get("kid3")[1]);
        assertEquals(1f, coords.get("root1")[1]);
    }

    @Test
    void mixedTreesAndLeavesDoNotOverlap() {
        var coords = AdvancementLayout.layout(List.of(
                e("solo", null),
                e("branch", null), e("b1", "branch"), e("b2", "branch"),
                e("tail", "b2")));

        // Every node placed, no two nodes share a cell.
        assertEquals(6, coords.size()); // 5 nodes + synthetic root
        long distinct = coords.values().stream().map(c -> c[0] + ":" + c[1]).distinct().count();
        assertEquals(6, distinct);
    }

    @Test
    void unknownParentFallsBackToRoot() {
        var coords = AdvancementLayout.layout(List.of(e("a", "ghost"), e("b", null)));

        assertEquals(1f, coords.get("a")[0]);
        assertNotNull(coords.get("b"));
        assertTrue(coords.containsKey(""));
    }

    @Test
    void emptyTabStillPlacesTheRoot() {
        var coords = AdvancementLayout.layout(List.of());
        assertEquals(0f, coords.get("")[0]);
        assertEquals(0f, coords.get("")[1]);
    }
}
