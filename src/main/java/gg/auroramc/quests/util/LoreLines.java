package gg.auroramc.quests.util;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Post-processing of a filled lore/description line before it reaches the client:
 * <ul>
 *   <li>splits multi-line tokens (the {@code {tasks}} objective list joins its lines
 *       with {@code \n}; item lore and advancement descriptions need real lines);</li>
 *   <li>optionally drops lines that carried content in the template but rendered empty
 *       (e.g. {@code " &8• &f{current_task}"} on a completed quest leaving a lone
 *       bullet). A line counts as empty when, after removing colour codes and format
 *       tags, no letter or digit remains — so decoration-only leftovers ("• ") are
 *       dropped while deliberate separators written verbatim in the template
 *       ({@code "&8&m----"}, blank lines) are never touched, because their template
 *       was already letterless.</li>
 * </ul>
 * Lines containing {@code {tasks}} always drop when the token renders empty (a locked
 * quest must not leave a hole), independently of {@code menus.drop-empty-lore-lines}.
 */
public final class LoreLines {
    private static final Pattern FORMATTING = Pattern.compile("(?i)[&§]#[0-9a-f]{6}|[&§][0-9a-fk-orx]|<[^<>]*>");

    private LoreLines() {
    }

    /**
     * @param template  the configured line, before placeholder execution
     * @param filled    the same line after placeholder execution
     * @param dropEmpty the {@code menus.drop-empty-lore-lines} setting
     * @return the real lines to display (possibly none)
     */
    public static List<String> expand(String template, String filled, boolean dropEmpty) {
        if (filled == null) return List.of();

        boolean dropIfEmptied = dropEmpty || (template != null && template.contains("{tasks}"));
        if (dropIfEmptied && hasVisibleText(template) && !hasVisibleText(filled)) {
            return List.of();
        }

        if (filled.indexOf('\n') < 0) return List.of(filled);
        return List.of(filled.split("\n", -1));
    }

    /** True when the string keeps at least one letter or digit once formatting is removed. */
    static boolean hasVisibleText(String line) {
        if (line == null || line.isEmpty()) return false;
        var stripped = FORMATTING.matcher(line).replaceAll("");
        for (int i = 0; i < stripped.length(); i++) {
            if (Character.isLetterOrDigit(stripped.charAt(i))) return true;
        }
        return false;
    }
}
