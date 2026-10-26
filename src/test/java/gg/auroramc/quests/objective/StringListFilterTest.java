package gg.auroramc.quests.objective;

import gg.auroramc.quests.api.objective.ObjectiveMeta;
import gg.auroramc.quests.api.objective.filter.StringListFilter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StringListFilterTest {

    private static StringListFilter filter(String yaml, String key) throws Exception {
        var args = new YamlConfiguration();
        args.loadFromString(yaml);
        return StringListFilter.stringListFilter(args, key);
    }

    private static ObjectiveMeta meta(String key, String value) {
        var meta = new ObjectiveMeta(null, null);
        meta.setVariable(key, value);
        return meta;
    }

    @Test
    void letsEverythingThroughWhenNotConfigured() throws Exception {
        var filter = filter("amount: 3", "kind");

        assertTrue(filter.filter(meta("kind", "EGG")));
    }

    @Test
    void matchesAnyValueOfAList() throws Exception {
        var filter = filter("kind: [RANK, energy]", "kind");

        assertTrue(filter.filter(meta("kind", "RANK")));
        assertTrue(filter.filter(meta("kind", "ENERGY")));
        assertFalse(filter.filter(meta("kind", "EGG")));
    }

    @Test
    void readsASingleValueAndABoolean() throws Exception {
        assertTrue(filter("outcome: success", "outcome").filter(meta("outcome", "SUCCESS")));

        var shiny = filter("shiny: true", "shiny");
        assertTrue(shiny.filter(meta("shiny", "true")));
        assertFalse(shiny.filter(meta("shiny", "false")));
    }

    @Test
    void rejectsAnEventThatCarriesNoValue() throws Exception {
        assertFalse(filter("kind: [RANK]", "kind").filter(new ObjectiveMeta(null, null)));
    }
}
