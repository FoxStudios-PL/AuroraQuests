package gg.auroramc.quests.util;

import gg.auroramc.quests.AuroraQuests;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/**
 * Auto-centering for chat lines. A line whose content starts with the {@code <center>}
 * marker is centered on {@code chat-center-px} (config.yml) at render time — after
 * placeholder resolution, right before the text is turned into a component — so lines
 * containing variable-width placeholders ({quest}, {reward}, ...) stay centered whatever
 * their resolved length. The marker is removed from the rendered output.
 * <p>
 * Width is computed with the vanilla font advance table (the classic "DefaultFontInfo"
 * +1px spacing, +1px when bold, space = 4px). Legacy codes ({@code &x}, {@code &#RRGGBB},
 * {@code §x§F§F...}) and MiniMessage tags take 0px; the bold state ({@code &l},
 * {@code <b>}) is tracked because it widens glyphs. Characters outside the table
 * (resource-pack glyphs, CJK, ...) use the {@code glyph-widths} config table when
 * present, otherwise {@code unknown-char-width}; accented Latin letters fall back to
 * their base letter's width automatically. Keys of {@code glyph-widths} longer than one
 * character give a width to the matching {@code <glyph:NAME>} tag (useful when the tag
 * is substituted client-side by Nexo/Oraxen).
 * <p>
 * A line wider than the whole chat ({@code >= 2 * chat-center-px}) is left unpadded so
 * the client doesn't wrap it even further. Only chat output should be centered — menus
 * and other renders must use {@link #strip(String)} to drop the marker instead.
 */
public final class ChatCenterer {
    public static final String MARKER = "<center>";

    private static final int SPACE_ADVANCE = 4;
    private static final String COLOR_CODES = "0123456789abcdef";

    private ChatCenterer() {
    }

    /**
     * True when the line starts with the {@code <center>} marker (leading whitespace tolerated).
     */
    public static boolean isMarked(String line) {
        return line != null && line.stripLeading().startsWith(MARKER);
    }

    /**
     * Removes the marker without centering — for non-chat renders (menu lore, ACF locale
     * messages) where the raw marker must not leak but chat padding makes no sense.
     */
    public static String strip(String line) {
        if (!isMarked(line)) return line;
        return line.stripLeading().substring(MARKER.length());
    }

    /**
     * Centers a single marked line; placeholders must already be resolved. Unmarked
     * lines are returned untouched.
     */
    public static String center(String line) {
        if (!isMarked(line)) return line;
        var content = line.stripLeading().substring(MARKER.length());

        var config = AuroraQuests.getInstance().getConfigManager().getConfig();
        var centerPx = config.getChatCenterPx() != null ? config.getChatCenterPx() : 154;
        var unknownWidth = config.getUnknownCharWidth() != null ? config.getUnknownCharWidth() : 8;
        var glyphWidths = config.getGlyphWidths() != null ? config.getGlyphWidths() : Map.<String, Integer>of();

        var width = pixelWidth(content, unknownWidth, glyphWidths);
        // Wider than the chat: any padding would only force one more wrap.
        if (width <= 0 || width >= centerPx * 2) return content;

        var spaces = (int) Math.round((centerPx - width / 2.0) / SPACE_ADVANCE);
        if (spaces <= 0) return content;
        return " ".repeat(spaces) + content;
    }

    /**
     * Centers every marked line of a possibly multi-line ({@code \n}) message.
     */
    public static String centerLines(String message) {
        if (message == null || !message.contains(MARKER)) return message;
        var lines = message.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            lines[i] = center(lines[i]);
        }
        return String.join("\n", lines);
    }

    /**
     * Rendered pixel width of a line still containing legacy/MiniMessage formatting codes.
     */
    static int pixelWidth(String text, int unknownWidth, Map<String, Integer> glyphWidths) {
        var width = 0;
        var bold = false;
        var i = 0;

        while (i < text.length()) {
            var c = text.charAt(i);

            // Legacy codes: &x / §x, &#RRGGBB, §x§R§R§G§G§B§B
            if ((c == '§' || c == '&') && i + 1 < text.length()) {
                var code = Character.toLowerCase(text.charAt(i + 1));
                if (code == '#' && isHex(text, i + 2, 6)) {
                    bold = false; // hex color, like any color code, resets decorations
                    i += 8;
                    continue;
                }
                if (code == 'x' && isLegacyHex(text, i + 2)) {
                    bold = false;
                    i += 14;
                    continue;
                }
                if (code == 'l') {
                    bold = true;
                    i += 2;
                    continue;
                }
                if (code == 'r' || COLOR_CODES.indexOf(code) >= 0) {
                    bold = false;
                    i += 2;
                    continue;
                }
                if (code == 'k' || code == 'm' || code == 'n' || code == 'o') {
                    i += 2;
                    continue;
                }
                // not a formatting code: the '&'/'§' renders as a normal character
            }

            // MiniMessage tags: <bold>, <#FFAA00>, <gradient:...>, </b>, <glyph:xxx>, ...
            if (c == '<') {
                var close = text.indexOf('>', i + 1);
                if (close > i + 1) {
                    var tag = text.substring(i + 1, close);
                    var name = tag.toLowerCase(Locale.ROOT);
                    switch (name) {
                        case "b", "bold" -> bold = true;
                        case "/b", "/bold", "reset", "r" -> bold = false;
                        default -> {
                            if (name.startsWith("glyph:")) {
                                // Substituted client-side (Nexo/Oraxen); width only known via config.
                                var glyph = tag.substring("glyph:".length());
                                var w = glyphWidths.get(glyph);
                                if (w == null) w = glyphWidths.get(glyph.toLowerCase(Locale.ROOT));
                                if (w != null) width += w;
                            }
                            // any other tag (colors, gradients, decorations, closers): 0 px,
                            // and MiniMessage colors do NOT reset the bold decoration
                        }
                    }
                    i = close + 1;
                    continue;
                }
                // no closing '>': literal '<'
            }

            width += advance(c, bold, unknownWidth, glyphWidths);
            i++;
        }

        return width;
    }

    /**
     * Full advance (spacing included) of a rendered character.
     */
    private static int advance(char c, boolean bold, int unknownWidth, Map<String, Integer> glyphWidths) {
        var override = glyphWidths.get(String.valueOf(c));
        if (override != null) return override;

        if (c == ' ') return SPACE_ADVANCE; // vanilla doesn't widen bold spaces

        var base = vanillaAdvance(c);
        if (base < 0) {
            // Accented Latin letters render with (nearly) their base letter's advance.
            var decomposed = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
            var baseChar = decomposed.charAt(0);
            if (baseChar != c) {
                // i/l-family keeps the diacritic width (î, ï, í, ì render ~3px wide)
                if (Character.toLowerCase(baseChar) == 'i' || Character.toLowerCase(baseChar) == 'l') {
                    base = 4;
                } else {
                    base = vanillaAdvance(baseChar);
                }
            }
        }
        if (base < 0) return unknownWidth;
        return base + (bold ? 1 : 0);
    }

    /**
     * Vanilla font advance (glyph width + 1px spacing) for the default ascii table,
     * -1 when unknown. Values are the classic Bukkit "DefaultFontInfo" table + 1.
     */
    private static int vanillaAdvance(char c) {
        if (c >= 'A' && c <= 'Z') return c == 'I' ? 4 : 6;
        if (c >= 'a' && c <= 'z') {
            return switch (c) {
                case 'i' -> 2;
                case 'l' -> 3;
                case 't' -> 4;
                case 'f' -> 5;
                case 'k' -> 5;
                default -> 6;
            };
        }
        if (c >= '0' && c <= '9') return 6;
        return switch (c) {
            case ' ' -> SPACE_ADVANCE;
            case '!', '\'', ',', '.', ':', ';', '|' -> 2;
            case '`' -> 3;
            case '‘', '’' -> 3; // ‘ ’ typographic quotes, common in French text
            case '"', '[', ']' -> 4;
            case '(', ')', '*', '{', '}', '<', '>' -> 5;
            case '#', '$', '%', '&', '+', '-', '/', '=', '?', '\\', '^', '_', '~' -> 6;
            case '@' -> 7;
            default -> -1;
        };
    }

    private static boolean isHex(String text, int from, int count) {
        if (from + count > text.length()) return false;
        for (int i = from; i < from + count; i++) {
            if (Character.digit(text.charAt(i), 16) < 0) return false;
        }
        return true;
    }

    /**
     * Matches the six {@code §R} pairs of a {@code §x§R§R§G§G§B§B} sequence, {@code from}
     * pointing right after the {@code §x}.
     */
    private static boolean isLegacyHex(String text, int from) {
        if (from + 12 > text.length()) return false;
        for (int i = 0; i < 6; i++) {
            var marker = text.charAt(from + i * 2);
            if (marker != '§' && marker != '&') return false;
            if (Character.digit(text.charAt(from + i * 2 + 1), 16) < 0) return false;
        }
        return true;
    }
}
