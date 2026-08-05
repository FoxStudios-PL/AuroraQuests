package gg.auroramc.quests.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnumCompatTest {
    private enum Result {
        SUCCESS, SUCCESS_COMMANDS_EXECUTED
    }

    @Test
    void resolvesExistingConstant() {
        assertSame(Result.SUCCESS, EnumCompat.resolve(Result.class, "SUCCESS"));
    }

    @Test
    void returnsNullForUnknownConstant() {
        assertNull(EnumCompat.resolve(Result.class, "NOT_ALL_ITEMS_ADDED"));
    }

    @Test
    void skipsUnknownConstantsWhenBuildingASet() {
        var set = EnumCompat.setOf(Result.class, "SUCCESS", "NOT_ALL_ITEMS_ADDED", "SUCCESS_COMMANDS_EXECUTED");

        assertEquals(2, set.size());
        assertTrue(set.contains(Result.SUCCESS));
        assertTrue(set.contains(Result.SUCCESS_COMMANDS_EXECUTED));
    }

    @Test
    void returnsAnEmptySetWhenNoConstantMatches() {
        assertTrue(EnumCompat.setOf(Result.class, "NOT_ALL_ITEMS_ADDED").isEmpty());
    }

    @Test
    void returnedSetIsImmutable() {
        var set = EnumCompat.setOf(Result.class, "SUCCESS");

        assertThrows(UnsupportedOperationException.class, () -> set.add(Result.SUCCESS_COMMANDS_EXECUTED));
    }
}
