package gg.auroramc.quests.reward;

import gg.auroramc.quests.reward.RewardScale.Mode;
import gg.auroramc.quests.reward.RewardScale.Tier;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardScaleTest {
    /** The spec's "aventure-facile" table. */
    private static final List<Tier> FACILE = List.of(
            new Tier(25d, 40), new Tier(50d, 125), new Tier(75d, 300), new Tier(100d, 550), new Tier(null, 850));

    // ---------------------------------------------------------------- tier picking

    @Test
    void firstMatchingTierWins() {
        assertEquals(40, RewardScale.pick(FACILE, Mode.STEP, 10).value());   // acceptance #1/#2
        assertEquals(300, RewardScale.pick(FACILE, Mode.STEP, 60).value());  // acceptance #3/#4
    }

    @Test
    void upToIsInclusive() {
        assertEquals(40, RewardScale.pick(FACILE, Mode.STEP, 25).value());   // acceptance #5: level 25...
        assertEquals(125, RewardScale.pick(FACILE, Mode.STEP, 26).value());  // ...then 26
    }

    @Test
    void lastTierIsTheCap() {
        assertEquals(850, RewardScale.pick(FACILE, Mode.STEP, 120).value()); // acceptance #6
        assertEquals(5, RewardScale.pick(FACILE, Mode.STEP, 120).tier());
        assertEquals(850, RewardScale.pick(FACILE, Mode.STEP, 9999).value());
    }

    @Test
    void nonPositiveSourceFallsInTheFirstTier() {
        assertEquals(40, RewardScale.pick(FACILE, Mode.STEP, 0).value());
        assertEquals(40, RewardScale.pick(FACILE, Mode.STEP, -12).value());
        assertEquals(1, RewardScale.pick(FACILE, Mode.STEP, 0).tier());
    }

    @Test
    void tierIndexIsOneBased() {
        assertEquals(1, RewardScale.pick(FACILE, Mode.STEP, 10).tier());
        assertEquals(3, RewardScale.pick(FACILE, Mode.STEP, 60).tier());
    }

    @Test
    void linearInterpolatesBetweenTierBounds() {
        // Between tier 2 (value 125 at up-to 50) and tier 3 (value 300 at up-to 75):
        // level 60 -> 125 + (300-125) * (60-50)/(75-50) = 195.
        assertEquals(195, RewardScale.pick(FACILE, Mode.LINEAR, 60).value(), 1e-9);
        // Exactly on a bound: the bound's own value.
        assertEquals(125, RewardScale.pick(FACILE, Mode.LINEAR, 50).value(), 1e-9);
    }

    @Test
    void linearBehavesLikeStepOnFirstAndLastTier() {
        assertEquals(40, RewardScale.pick(FACILE, Mode.LINEAR, 10).value(), 1e-9);
        assertEquals(850, RewardScale.pick(FACILE, Mode.LINEAR, 110).value(), 1e-9);
    }

    @Test
    void formatCleanIsCommandSafe() {
        assertEquals("40", RewardScale.formatClean(40.0));
        assertEquals("1700", RewardScale.formatClean(1700.0));   // never "1,700" nor "1700.0"
        assertEquals("2.5", RewardScale.formatClean(2.5));
    }

    // ---------------------------------------------------------------- parsing & validation

    private static RewardScales parse(String yaml) throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString(yaml);
        return RewardScales.parse(config.getConfigurationSection("reward-scales"));
    }

    @Test
    void parsesTheSpecExample() throws Exception {
        var scales = parse("""
                reward-scales:
                  aventure-facile:
                    source: aurora-level
                    mode: step
                    tiers:
                      - { up-to: 25,  value: 40 }
                      - { up-to: 50,  value: 125 }
                      - { up-to: 75,  value: 300 }
                      - { up-to: 100, value: 550 }
                      - { value: 850 }
                  metier-mineur:
                    source: placeholder
                    placeholder: "%jobs_level_mineur%"
                    fallback: 1
                    tiers:
                      - { up-to: 10, value: 100 }
                      - { value: 250 }
                """);
        assertNotNull(scales.get("aventure-facile"));
        assertNotNull(scales.get("metier-mineur"));
        assertTrue(scales.hasAuroraLevelSource());
        assertEquals(RewardScale.SourceType.PLACEHOLDER, scales.get("metier-mineur").getSource());
    }

    @Test
    void rejectsEmptyTiers() throws Exception {
        assertNull(parse("""
                reward-scales:
                  broken: { source: aurora-level, tiers: [] }
                """).get("broken"));
    }

    @Test
    void rejectsNonIncreasingBounds() throws Exception {
        assertNull(parse("""
                reward-scales:
                  broken:
                    tiers:
                      - { up-to: 50, value: 1 }
                      - { up-to: 50, value: 2 }
                      - { value: 3 }
                """).get("broken"));
    }

    @Test
    void rejectsMissingUpToOnANonLastTier() throws Exception {
        assertNull(parse("""
                reward-scales:
                  broken:
                    tiers:
                      - { value: 1 }
                      - { up-to: 50, value: 2 }
                """).get("broken"));
    }

    @Test
    void rejectsMissingValue() throws Exception {
        assertNull(parse("""
                reward-scales:
                  broken:
                    tiers:
                      - { up-to: 50 }
                      - { value: 3 }
                """).get("broken"));
    }

    @Test
    void rejectsPlaceholderSourceWithoutPlaceholder() throws Exception {
        assertNull(parse("""
                reward-scales:
                  broken:
                    source: placeholder
                    tiers:
                      - { value: 3 }
                """).get("broken"));
    }

    @Test
    void rejectsUnknownSource() throws Exception {
        assertNull(parse("""
                reward-scales:
                  broken:
                    source: skill-level
                    tiers:
                      - { value: 3 }
                """).get("broken"));
    }

    @Test
    void aValidTableSurvivesNextToABrokenOne() throws Exception {
        var scales = parse("""
                reward-scales:
                  broken: { tiers: [] }
                  fine:
                    tiers:
                      - { up-to: 10, value: 5 }
                      - { value: 9 }
                """);
        assertNull(scales.get("broken"));
        assertNotNull(scales.get("fine"));
        assertFalse(RewardScales.empty().hasAuroraLevelSource());
    }
}
