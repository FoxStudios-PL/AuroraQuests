package gg.auroramc.quests.util;

import gg.auroramc.quests.config.Config;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders the {@code {tasks}} token: the quest's objective list with, optionally, only a
 * window around the current step — the rest collapsed into one summary line per side.
 * <p>
 * Pure string logic (no Bukkit types) so the window rules are unit-testable. The caller
 * ({@code Quest}) provides the already-rendered per-objective lines (locked lore, live
 * counters and strikethrough applied) and the completion flags; this class only decides
 * which lines to keep and how to fold the others.
 */
public final class ObjectiveListRenderer {
    public static final String MODE_ALL = "all";
    public static final String MODE_WINDOW = "window";
    public static final String MODE_CURRENT = "current";

    /** Effective settings after merging quest override -> global config -> defaults. */
    public record Settings(String mode, int before, int after, String lineFormat,
                           String collapsedBefore, String collapsedAfter) {
    }

    private ObjectiveListRenderer() {
    }

    /**
     * Merges the per-quest {@code objective-list} override over the global
     * {@code menus.objective-list} section. A key absent from the override falls back to
     * the global value, then to the built-in default. An explicit empty string on the
     * collapsed templates is a valid value (it suppresses that summary line), which is
     * why absence is represented by {@code null}, never by {@code ""}.
     */
    public static Settings resolve(@Nullable Config.ObjectiveListConfig global,
                                   @Nullable Config.ObjectiveListConfig override) {
        String mode = pick(override != null ? override.getMode() : null,
                global != null ? global.getMode() : null, MODE_ALL);
        Integer before = pick(override != null ? override.getBefore() : null,
                global != null ? global.getBefore() : null, 1);
        Integer after = pick(override != null ? override.getAfter() : null,
                global != null ? global.getAfter() : null, 2);
        String lineFormat = pick(override != null ? override.getLineFormat() : null,
                global != null ? global.getLineFormat() : null, "{task}");
        String collapsedBefore = pick(override != null ? override.getCollapsedBefore() : null,
                global != null ? global.getCollapsedBefore() : null, " &8- &7... {count}");
        String collapsedAfter = pick(override != null ? override.getCollapsedAfter() : null,
                global != null ? global.getCollapsedAfter() : null, " &8- &7... {count}");

        String normalizedMode = switch (mode.trim().toLowerCase(Locale.ROOT)) {
            case MODE_WINDOW -> MODE_WINDOW;
            case MODE_CURRENT -> MODE_CURRENT;
            default -> MODE_ALL;
        };

        return new Settings(normalizedMode, Math.max(0, before), Math.max(0, after),
                lineFormat, collapsedBefore, collapsedAfter);
    }

    private static <T> T pick(T override, T global, T fallback) {
        if (override != null) return override;
        if (global != null) return global;
        return fallback;
    }

    /**
     * @param taskLines      rendered display of every objective, in declaration order
     * @param completed      completion flag per objective (same order)
     * @param currentIndex   index of the first non-completed objective
     * @param linear         quest uses {@code linear-objectives: true}
     * @param questCompleted the whole quest is completed
     * @return the visible lines, in order (empty list when there is nothing to show)
     */
    public static List<String> render(List<String> taskLines, List<Boolean> completed,
                                      int currentIndex, boolean linear, boolean questCompleted,
                                      Settings s) {
        int n = taskLines.size();
        if (n == 0) return List.of();

        // all: current behaviour, one line per step — no regression possible.
        if (MODE_ALL.equals(s.mode())) {
            var lines = new ArrayList<String>(n);
            for (var task : taskLines) lines.add(formatLine(s.lineFormat(), task));
            return lines;
        }

        // Completed quest: no current step any more. One single summary line with the
        // total, instead of a full list of struck-through steps.
        if (questCompleted) {
            var line = collapsedLine(s.collapsedBefore(), n);
            return line == null ? List.of() : List.of(line);
        }

        // Non-linear quests: every non-completed step is playable at once, so they all
        // stay visible; only completed steps fold (keeping the last `before` of them as
        // context). `after` has no meaning here and is ignored.
        if (!linear) {
            return renderNonLinear(taskLines, completed, s);
        }

        int lo;
        int hi;
        if (MODE_CURRENT.equals(s.mode())) {
            lo = clamp(currentIndex, 0, n - 1);
            hi = lo;
        } else {
            lo = Math.max(0, currentIndex - s.before());
            hi = Math.min(n - 1, currentIndex + s.after());
            // Folding a single step into a "... 1 step" line saves nothing and costs
            // readability: only fold when it removes at least 2 lines.
            if (lo == 1) lo = 0;
            if (n - 1 - hi == 1) hi = n - 1;
        }

        var lines = new ArrayList<String>(hi - lo + 3);
        if (!MODE_CURRENT.equals(s.mode()) && lo > 0) {
            var line = collapsedLine(s.collapsedBefore(), lo);
            if (line != null) lines.add(line);
        }
        for (int i = lo; i <= hi; i++) {
            lines.add(formatLine(s.lineFormat(), taskLines.get(i)));
        }
        if (!MODE_CURRENT.equals(s.mode()) && hi < n - 1) {
            var line = collapsedLine(s.collapsedAfter(), n - 1 - hi);
            if (line != null) lines.add(line);
        }
        return lines;
    }

    private static List<String> renderNonLinear(List<String> taskLines, List<Boolean> completed, Settings s) {
        int completedCount = 0;
        for (var flag : completed) {
            if (Boolean.TRUE.equals(flag)) completedCount++;
        }
        int toFold = completedCount - s.before();
        // Same >= 2 lines gain rule as the linear window.
        if (toFold < 2) toFold = 0;

        var lines = new ArrayList<String>(taskLines.size() - toFold + 1);
        if (toFold > 0) {
            var line = collapsedLine(s.collapsedBefore(), toFold);
            if (line != null) lines.add(line);
        }
        int folded = 0;
        for (int i = 0; i < taskLines.size(); i++) {
            if (Boolean.TRUE.equals(completed.get(i)) && folded < toFold) {
                folded++;
                continue;
            }
            lines.add(formatLine(s.lineFormat(), taskLines.get(i)));
        }
        return lines;
    }

    private static String formatLine(String lineFormat, String task) {
        if (lineFormat == null || lineFormat.isEmpty()) return task;
        return lineFormat.replace("{task}", task);
    }

    /** {@code null} when the summary line is suppressed (empty template) or count is 0. */
    private static @Nullable String collapsedLine(String template, int count) {
        if (count <= 0 || template == null || template.isEmpty()) return null;
        return template.replace("{count}", String.valueOf(count));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
