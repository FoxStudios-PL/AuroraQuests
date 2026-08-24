package gg.auroramc.quests.reward;

import gg.auroramc.aurora.api.message.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One {@code reward-scales} table: a value read on the player (an AuroraLevels level, or
 * any numeric placeholder) mapped to a reward amount through tiers.
 * <p>
 * Instances are immutable and validated by {@link RewardScales#parse}; {@link #resolve}
 * is called at the moment a reward is displayed or paid (on the player's scheduling
 * context — AuroraLevels keeps levels in memory, but a third-party placeholder is read
 * right there too, so a placeholder that hits a database blocks that player's thread:
 * documented limitation).
 */
public final class RewardScale {
    public enum SourceType {AURORA_LEVEL, PLACEHOLDER}

    public enum Mode {STEP, LINEAR}

    /** {@code upTo} is {@code null} only on the last tier (the cap). */
    public record Tier(@Nullable Double upTo, double value) {
    }

    /** {@code tier} is 1-based (debug display). */
    public record Resolution(double value, double source, int tier) {
    }

    private final String id;
    private final SourceType source;
    private final @Nullable String placeholder;
    private final @Nullable Double fallback;
    private final Mode mode;
    private final List<Tier> tiers;
    /** One "source unreadable" warning per table and per (re)load, not per completion. */
    private final AtomicBoolean unreadableWarned = new AtomicBoolean();

    RewardScale(String id, SourceType source, @Nullable String placeholder, @Nullable Double fallback,
                Mode mode, List<Tier> tiers) {
        this.id = id;
        this.source = source;
        this.placeholder = placeholder;
        this.fallback = fallback;
        this.mode = mode;
        this.tiers = List.copyOf(tiers);
    }

    public String getId() {
        return id;
    }

    public SourceType getSource() {
        return source;
    }

    /** Resolves the reward value for this player, right now. */
    public Resolution resolve(Player player) {
        Double raw = readSource(player);
        if (raw == null) {
            if (unreadableWarned.compareAndSet(false, true)) {
                ScaleLog.warning.accept("[reward-scales] Could not read the source of scale '" + id + "' ("
                        + (source == SourceType.AURORA_LEVEL ? "AuroraLevels level" : "placeholder " + placeholder)
                        + "); using " + (fallback != null ? "fallback " + fallback : "the first tier")
                        + ". This is only logged once per load.");
            }
            if (fallback == null) {
                // No fallback declared: the first tier applies.
                return new Resolution(tiers.getFirst().value(), 0, 1);
            }
            raw = fallback;
        }
        return pick(tiers, mode, raw);
    }

    /**
     * Pure tier selection, shared by every call site so the menu, the advancement screen
     * and the payout can never disagree. The first tier whose {@code up-to} is {@code >=}
     * the source wins; the last tier (no {@code up-to}) is the cap; a source {@code <= 0}
     * lands in the first tier. {@code LINEAR} interpolates from the previous tier's value
     * (anchored at its {@code up-to}) to the matched tier's value (at its own
     * {@code up-to}); the first and last tiers behave like {@code STEP}.
     */
    static Resolution pick(List<Tier> tiers, Mode mode, double sourceValue) {
        if (sourceValue <= 0) {
            return new Resolution(tiers.getFirst().value(), sourceValue, 1);
        }
        for (int i = 0; i < tiers.size(); i++) {
            var tier = tiers.get(i);
            if (tier.upTo() == null || sourceValue <= tier.upTo()) {
                double value = tier.value();
                if (mode == Mode.LINEAR && i > 0 && tier.upTo() != null) {
                    var previous = tiers.get(i - 1);
                    double low = previous.upTo();
                    double high = tier.upTo();
                    double t = high <= low ? 1 : (sourceValue - low) / (high - low);
                    value = previous.value() + (tier.value() - previous.value()) * t;
                }
                return new Resolution(value, sourceValue, i + 1);
            }
        }
        // Unreachable: the last tier has no upTo. Kept as a safe cap.
        return new Resolution(tiers.getLast().value(), sourceValue, tiers.size());
    }

    private @Nullable Double readSource(Player player) {
        return switch (source) {
            case AURORA_LEVEL -> {
                // The bridge class is only touched behind this gate, so AuroraLevels
                // types are never linked when the plugin is absent (same pattern as
                // BetterHudPopupHook).
                if (!Bukkit.getPluginManager().isPluginEnabled("AuroraLevels")) yield null;
                yield AuroraLevelsSource.level(player);
            }
            case PLACEHOLDER -> {
                if (placeholder == null) yield null;
                var rendered = Text.fillPlaceholders(player, placeholder);
                yield parseNumber(rendered);
            }
        };
    }

    private static @Nullable Double parseNumber(String rendered) {
        if (rendered == null) return null;
        var trimmed = rendered.trim().replace(',', '.').replace(" ", "").replace(" ", "");
        if (trimmed.isEmpty()) return null;
        try {
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Raw value rendering safe for console commands: whole numbers print without a
     * decimal part ({@code 1700}, never {@code 1700.0} or {@code 1,700}).
     */
    public static String formatClean(double value) {
        long asLong = (long) value;
        return value == asLong ? String.valueOf(asLong) : String.valueOf(value);
    }
}
