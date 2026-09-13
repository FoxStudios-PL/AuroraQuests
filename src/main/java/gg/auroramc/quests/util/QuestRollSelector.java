package gg.auroramc.quests.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Picks the quests of one roll out of the per-difficulty candidate lists.
 *
 * <p>Kept free of any Bukkit type on purpose: the whole draw (ordering, tag exclusion and
 * fallback) is decided here and can be exercised without a server.
 */
public final class QuestRollSelector {
    private QuestRollSelector() {
    }

    /**
     * Normalizes a raw {@code tags:} list: trimmed, lowercased, blanks dropped, duplicates
     * removed, declaration order kept. Tags are normalized once here at parse time and only
     * ever compared in this form afterwards.
     *
     * @return an immutable list, empty when {@code raw} is {@code null} or holds nothing usable
     */
    public static List<String> normalizeTags(Collection<String> raw) {
        if (raw == null || raw.isEmpty()) return List.of();

        var normalized = new LinkedHashSet<String>();
        for (var tag : raw) {
            if (tag == null) continue;
            var trimmed = tag.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) normalized.add(trimmed);
        }

        return List.copyOf(normalized);
    }

    /**
     * Runs the draw.
     *
     * <p>Difficulties are served from the least supplied to the most supplied (ties broken on
     * the difficulty id), so the difficulty with the fewest candidates is not left with only
     * quests the others already excluded. Within a difficulty, candidates are taken in the
     * order they come in — the caller is expected to have shuffled them.
     *
     * <p>When {@code avoidDuplicateTags} is on, a candidate sharing a tag with an
     * already-picked quest is skipped. If that leaves a difficulty short of its quota, the
     * fallback fills the remaining slots from the same list, tags ignored, so the player
     * always gets the number of quests the pool promises. A quest is never picked twice.
     *
     * @param pickable           difficulty -&gt; candidates, already shuffled
     * @param quotas             difficulty -&gt; how many quests to pick; drives which
     *                           difficulties end up in the result
     * @param avoidDuplicateTags whether two quests sharing a tag may come out of the same roll
     * @param tagsOf             normalized tags of a candidate
     * @param onFallback         called as (difficulty, number of quests picked despite a tag
     *                           conflict) whenever the fallback had to step in; may be {@code null}
     * @return difficulty -&gt; picked quests, one entry per quota entry (possibly empty)
     */
    public static <T> Map<String, List<T>> select(Map<String, List<T>> pickable,
                                                  Map<String, Integer> quotas,
                                                  boolean avoidDuplicateTags,
                                                  Function<T, Collection<String>> tagsOf,
                                                  BiConsumer<String, Integer> onFallback) {
        // A HashMap on purpose: the caller flattens values() into the rolled quest list, and
        // this keeps that order identical to what the plugin produced before tags existed.
        var picked = new HashMap<String, List<T>>();
        var usedTags = new HashSet<String>();

        for (var difficulty : orderedDifficulties(pickable, quotas)) {
            var quota = quotas.get(difficulty);
            var candidates = pickable.get(difficulty);

            if (quota == null || quota <= 0 || candidates == null || candidates.isEmpty()) {
                picked.put(difficulty, List.of());
                continue;
            }

            var chosen = new ArrayList<T>(Math.min(quota, candidates.size()));

            for (var candidate : candidates) {
                if (chosen.size() >= quota) break;
                var tags = tagsOf.apply(candidate);
                if (avoidDuplicateTags && conflicts(tags, usedTags)) continue;
                chosen.add(candidate);
                usedTags.addAll(tags);
            }

            if (chosen.size() < quota && chosen.size() < candidates.size()) {
                var filled = fillFromConflicting(chosen, candidates, quota, tagsOf, usedTags);
                if (filled > 0 && onFallback != null) onFallback.accept(difficulty, filled);
            }

            picked.put(difficulty, List.copyOf(chosen));
        }

        return picked;
    }

    /**
     * Least supplied first, then alphabetically on the difficulty id so a tie resolves the
     * same way on every roll and across restarts. Difficulties absent from {@code pickable}
     * count as having no candidate at all.
     */
    static <T> List<String> orderedDifficulties(Map<String, List<T>> pickable, Map<String, Integer> quotas) {
        var difficulties = new ArrayList<>(quotas.keySet());
        difficulties.sort(Comparator
                .comparingInt((String difficulty) -> {
                    var candidates = pickable.get(difficulty);
                    return candidates == null ? 0 : candidates.size();
                })
                .thenComparing(Comparator.naturalOrder()));
        return difficulties;
    }

    /**
     * Tops {@code chosen} up to {@code quota} with candidates it doesn't already hold, tags
     * ignored. Identity-based so the same quest can never land in the roll twice.
     *
     * @return how many quests the fallback added
     */
    private static <T> int fillFromConflicting(List<T> chosen, List<T> candidates, int quota,
                                               Function<T, Collection<String>> tagsOf, Set<String> usedTags) {
        Set<T> alreadyChosen = Collections.newSetFromMap(new IdentityHashMap<>());
        alreadyChosen.addAll(chosen);

        var added = 0;
        for (var candidate : candidates) {
            if (chosen.size() >= quota) break;
            if (!alreadyChosen.add(candidate)) continue;
            chosen.add(candidate);
            usedTags.addAll(tagsOf.apply(candidate));
            added++;
        }

        return added;
    }

    private static boolean conflicts(Collection<String> tags, Set<String> usedTags) {
        if (tags.isEmpty() || usedTags.isEmpty()) return false;
        for (var tag : tags) {
            if (usedTags.contains(tag)) return true;
        }
        return false;
    }
}
