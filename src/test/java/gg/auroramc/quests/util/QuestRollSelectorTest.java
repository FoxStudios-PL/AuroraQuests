package gg.auroramc.quests.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestRollSelectorTest {
    /** Stand-in for a Quest: the selector only ever asks a candidate for its tags. */
    private record Q(String id, String difficulty, List<String> tags) {
        static Q of(String id, String difficulty, String... tags) {
            return new Q(id, difficulty, QuestRollSelector.normalizeTags(List.of(tags)));
        }
    }

    private static final Function<Q, Collection<String>> TAGS = Q::tags;

    /** Orylium's daily pool as specced: 13 easy, 6 medium, 7 hard. */
    private static final List<Q> ORYLIUM = List.of(
            Q.of("daily_minerais_64", "easy", "minage"),
            Q.of("daily_buches_64", "easy", "bucheronnage"),
            Q.of("daily_recolte_48", "easy", "agriculture"),
            Q.of("daily_peche_10", "easy", "peche"),
            Q.of("daily_cuisson_32", "easy", "cuisson"),
            Q.of("daily_craft_20", "easy", "artisanat"),
            Q.of("daily_marche_3000", "easy", "deplacement"),
            Q.of("daily_manger_15", "easy", "nourriture"),
            Q.of("daily_tonte_15", "easy", "tonte"),
            Q.of("daily_poser_256", "easy", "construction"),
            Q.of("daily_xp_500", "easy", "progression"),
            Q.of("daily_vente_2000", "easy", "commerce"),
            Q.of("daily_recolte_pierre", "easy", "minage", "combat"),
            Q.of("daily_enchant_3", "medium", "enchantement"),
            Q.of("daily_brassage_5", "medium", "alchimie"),
            Q.of("daily_peche_30", "medium", "peche"),
            Q.of("daily_apprivoiser_3", "medium", "apprivoisement"),
            Q.of("daily_degats_5000", "medium", "combat"),
            Q.of("daily_artisanat_1", "medium", "artisanat"),
            Q.of("daily_minerais_256", "hard", "minage"),
            Q.of("daily_recolte_256", "hard", "agriculture"),
            Q.of("daily_outil_casse", "hard", "usure"),
            Q.of("daily_kill_100", "hard", "combat"),
            Q.of("daily_vente_25000", "hard", "commerce"),
            Q.of("daily_peche_120", "hard", "peche"),
            Q.of("daily_enchant_12", "hard", "enchantement"));

    /** The pool's quotas: 2 easy, 1 medium, 1 hard. */
    private static final Map<String, Integer> QUOTAS = new LinkedHashMap<>(
            Map.of("easy", 2, "medium", 1, "hard", 1));

    private static Map<String, List<Q>> groupBy(List<Q> quests) {
        var grouped = new HashMap<String, List<Q>>();
        for (var quest : quests) {
            grouped.computeIfAbsent(quest.difficulty(), k -> new ArrayList<>()).add(quest);
        }
        return grouped;
    }

    /** Same shape as the caller: group, shuffle, then select. */
    private static Map<String, List<Q>> roll(List<Q> quests, Map<String, Integer> quotas, boolean avoid,
                                             Random random, List<String> fallbackLog) {
        var pickable = groupBy(quests);
        for (var candidates : pickable.values()) {
            Collections.shuffle(candidates, random);
        }
        return QuestRollSelector.select(pickable, quotas, avoid, TAGS,
                (difficulty, count) -> fallbackLog.add(difficulty + ":" + count));
    }

    private static List<Q> flatten(Map<String, List<Q>> picked) {
        return picked.values().stream().flatMap(List::stream).toList();
    }

    private static String sharedTag(List<Q> picked) {
        var seen = new HashSet<String>();
        for (var quest : picked) {
            for (var tag : quest.tags()) {
                if (!seen.add(tag)) return tag;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ tag normalization

    @Test
    void tagsAreLowercasedAndTrimmed() { // acceptance #7
        assertIterableEquals(List.of("peche"), QuestRollSelector.normalizeTags(List.of("Peche")));
        assertIterableEquals(List.of("peche"), QuestRollSelector.normalizeTags(List.of(" PECHE ")));
        // Three spellings of the same tag collapse into one.
        assertIterableEquals(List.of("peche"), QuestRollSelector.normalizeTags(List.of("Peche", "peche", " PECHE ")));
    }

    @Test
    void missingOrEmptyTagsGiveAnEmptyList() {
        assertIterableEquals(List.of(), QuestRollSelector.normalizeTags(null));
        assertIterableEquals(List.of(), QuestRollSelector.normalizeTags(List.of()));
        assertIterableEquals(List.of(), QuestRollSelector.normalizeTags(List.of("", "   ")));
    }

    @Test
    void spellingVariantsExcludeEachOther() { // acceptance #7, end to end
        var quests = List.of(
                Q.of("a", "easy", "Peche"), Q.of("b", "easy", " PECHE "), Q.of("c", "easy", "minage"));
        var picked = QuestRollSelector.select(groupBy(quests), Map.of("easy", 2), true, TAGS, null);
        assertEquals(List.of("a", "c"), picked.get("easy").stream().map(Q::id).toList());
    }

    // ------------------------------------------------------------------ backwards compatibility

    @Test
    void withoutTheOptionTheDrawIsTheOldFirstNOfTheShuffledList() { // acceptance #1 and #2
        var random = new Random(1234);
        for (int i = 0; i < 200; i++) {
            var pickable = groupBy(ORYLIUM);
            for (var candidates : pickable.values()) {
                Collections.shuffle(candidates, random);
            }
            // A copy of what the plugin did before this feature existed.
            var expected = new HashMap<String, List<Q>>();
            for (var quota : QUOTAS.entrySet()) {
                var candidates = pickable.get(quota.getKey());
                expected.put(quota.getKey(), candidates.subList(0, Math.min(quota.getValue(), candidates.size())));
            }

            var picked = QuestRollSelector.select(pickable, QUOTAS, false, TAGS, null);
            assertEquals(expected, picked);
        }
    }

    @Test
    void withoutTheOptionDuplicateTagsStillHappen() { // acceptance #2
        var quests = List.of(
                Q.of("peche_10", "easy", "peche"), Q.of("peche_30", "medium", "peche"));
        var picked = QuestRollSelector.select(groupBy(quests), Map.of("easy", 1, "medium", 1), false, TAGS, null);
        assertEquals(2, flatten(picked).size());
        assertEquals("peche", sharedTag(flatten(picked)));
    }

    @Test
    void untaggedQuestsRollExactlyAsBeforeEvenWithTheOptionOn() { // acceptance #1
        var quests = List.of(
                Q.of("a", "easy"), Q.of("b", "easy"), Q.of("c", "medium"), Q.of("d", "hard"));
        var on = QuestRollSelector.select(groupBy(quests), QUOTAS, true, TAGS, null);
        var off = QuestRollSelector.select(groupBy(quests), QUOTAS, false, TAGS, null);
        assertEquals(off, on);
        assertEquals(4, flatten(on).size());
    }

    // ------------------------------------------------------------------ the exclusion itself

    @Test
    void twoHundredRollsOfTheOryliumPoolNeverShareATag() { // acceptance #3 and #4
        var random = new Random(20260912L);
        var fallbackLog = new ArrayList<String>();

        for (int i = 0; i < 200; i++) {
            var picked = flatten(roll(ORYLIUM, QUOTAS, true, random, fallbackLog));
            assertEquals(4, picked.size(), "roll #" + i + " did not hand out 4 quests: " + picked);
            assertEquals(4, picked.stream().map(Q::id).distinct().count(), "roll #" + i + " picked a quest twice");
            assertEquals(null, sharedTag(picked), "roll #" + i + " shares a tag: " + picked);
        }

        // The pool is wide enough that the fallback never has to break the rule.
        assertTrue(fallbackLog.isEmpty(), "unexpected fallback: " + fallbackLog);
    }

    @Test
    void bothTagsOfATwoTagQuestBlockTheOtherDifficulties() { // acceptance #5
        // Only one easy candidate, so "daily_recolte_pierre" (minage + combat) is always picked;
        // both of its tags must then be out of reach for medium and hard.
        var quests = List.of(
                Q.of("daily_recolte_pierre", "easy", "minage", "combat"),
                Q.of("daily_degats_5000", "medium", "combat"),
                Q.of("daily_brassage_5", "medium", "alchimie"),
                Q.of("daily_minerais_256", "hard", "minage"),
                Q.of("daily_outil_casse", "hard", "usure"));

        var random = new Random(7);
        for (int i = 0; i < 50; i++) {
            var picked = flatten(roll(quests, Map.of("easy", 1, "medium", 1, "hard", 1), true, random, new ArrayList<>()));
            var ids = picked.stream().map(Q::id).toList();
            assertEquals(3, ids.size());
            assertTrue(ids.contains("daily_brassage_5"), "combat should have been blocked, got " + ids);
            assertTrue(ids.contains("daily_outil_casse"), "minage should have been blocked, got " + ids);
        }
    }

    @Test
    void anUntaggedQuestIsAlwaysPickableAndBlocksNobody() { // acceptance #8
        var quests = List.of(
                Q.of("untagged_1", "easy"), Q.of("untagged_2", "easy"),
                Q.of("peche", "medium", "peche"), Q.of("minage", "hard", "minage"));

        var random = new Random(42);
        for (int i = 0; i < 50; i++) {
            var picked = flatten(roll(quests, QUOTAS, true, random, new ArrayList<>()));
            assertEquals(4, picked.size());
            assertEquals(4, picked.stream().map(Q::id).distinct().count());
        }
    }

    @Test
    void aQuestNotHandedToTheSelectorNeitherRollsNorBlocks() { // acceptance #12
        // "daily_peche_30" is unavailable (canStart() false), so the caller filtered it out:
        // the easy fishing quest must stay pickable.
        var available = List.of(
                Q.of("daily_peche_10", "easy", "peche"), Q.of("daily_brassage_5", "medium", "alchimie"));
        var picked = flatten(QuestRollSelector.select(groupBy(available), Map.of("easy", 1, "medium", 1), true, TAGS, null));
        assertEquals(List.of("daily_brassage_5", "daily_peche_10"),
                picked.stream().map(Q::id).sorted().toList());
    }

    // ------------------------------------------------------------------ fallback

    @Test
    void fallbackFillsTheQuotaAndLogs() { // acceptance #6
        var quests = List.of(Q.of("peche_a", "easy", "peche"), Q.of("peche_b", "easy", "peche"));
        var fallbackLog = new ArrayList<String>();

        var picked = QuestRollSelector.select(groupBy(quests), Map.of("easy", 2), true, TAGS,
                (difficulty, count) -> fallbackLog.add(difficulty + ":" + count));

        assertEquals(2, picked.get("easy").size());
        assertEquals(List.of("easy:1"), fallbackLog);
    }

    @Test
    void fallbackNeverPicksTheSameQuestTwice() { // piège #6
        // Quota of 3 against 2 conflicting candidates: the roll is short, but never doubled.
        var quests = List.of(Q.of("peche_a", "easy", "peche"), Q.of("peche_b", "easy", "peche"));
        var fallbackLog = new ArrayList<String>();

        var picked = QuestRollSelector.select(groupBy(quests), Map.of("easy", 3), true, TAGS,
                (difficulty, count) -> fallbackLog.add(difficulty + ":" + count));

        assertEquals(List.of("peche_a", "peche_b"), picked.get("easy").stream().map(Q::id).toList());
        assertEquals(List.of("easy:1"), fallbackLog);
    }

    @Test
    void fallbackDoesNotFireWhenTheQuotaIsMetWithoutIt() {
        var quests = List.of(Q.of("peche", "easy", "peche"), Q.of("minage", "easy", "minage"));
        var fallbackLog = new ArrayList<String>();

        QuestRollSelector.select(groupBy(quests), Map.of("easy", 2), true, TAGS,
                (difficulty, count) -> fallbackLog.add(difficulty + ":" + count));

        assertTrue(fallbackLog.isEmpty(), "unexpected fallback: " + fallbackLog);
    }

    @Test
    void aDifficultyWithoutCandidatesStillGetsAnEmptyEntry() {
        var picked = QuestRollSelector.select(groupBy(List.of(Q.of("a", "easy"))), QUOTAS, true, TAGS, null);
        assertEquals(QUOTAS.keySet(), picked.keySet());
        assertTrue(picked.get("medium").isEmpty());
        assertTrue(picked.get("hard").isEmpty());
    }

    // ------------------------------------------------------------------ difficulty order

    @Test
    void difficultiesAreServedFromTheLeastSuppliedToTheMost() { // §5
        var pickable = groupBy(ORYLIUM);
        assertEquals(List.of("medium", "hard", "easy"), QuestRollSelector.orderedDifficulties(pickable, QUOTAS));
    }

    @Test
    void tiesAreBrokenAlphabeticallyAndDoNotDependOnMapOrder() { // acceptance #9
        var quests = List.of(
                Q.of("e1", "easy"), Q.of("e2", "easy"),
                Q.of("m1", "medium"), Q.of("m2", "medium"),
                Q.of("h1", "hard"), Q.of("h2", "hard"));
        var pickable = groupBy(quests);

        // Same three difficulties, three different map iteration orders: one single answer.
        var declaration = new LinkedHashMap<String, Integer>();
        declaration.put("easy", 1);
        declaration.put("medium", 1);
        declaration.put("hard", 1);
        var reversed = new LinkedHashMap<String, Integer>();
        reversed.put("hard", 1);
        reversed.put("medium", 1);
        reversed.put("easy", 1);

        assertEquals(List.of("easy", "hard", "medium"), QuestRollSelector.orderedDifficulties(pickable, declaration));
        assertEquals(List.of("easy", "hard", "medium"), QuestRollSelector.orderedDifficulties(pickable, reversed));
        assertEquals(List.of("easy", "hard", "medium"), QuestRollSelector.orderedDifficulties(pickable, new HashMap<>(declaration)));
    }

    @Test
    void theScarcestDifficultyIsNotStarvedByTheOthers() { // §5, the reason for the order
        // medium has a single candidate, easy has plenty: serving easy first would eat "peche"
        // and leave medium empty.
        var quests = List.of(
                Q.of("e_peche", "easy", "peche"), Q.of("e_minage", "easy", "minage"),
                Q.of("e_combat", "easy", "combat"), Q.of("m_peche", "medium", "peche"));

        var random = new Random(3);
        for (int i = 0; i < 50; i++) {
            var picked = flatten(roll(quests, Map.of("easy", 1, "medium", 1), true, random, new ArrayList<>()));
            var ids = picked.stream().map(Q::id).toList();
            assertEquals(2, ids.size());
            assertTrue(ids.contains("m_peche"));
            assertFalse(ids.contains("e_peche"), "easy took the tag medium needed: " + ids);
        }
    }
}
