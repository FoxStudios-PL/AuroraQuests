package gg.auroramc.quests.reward;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Registry of the {@code reward-scales} tables of config.yml. Rebuilt on every
 * {@code /quests reload} (changing a number never needs a restart). Every structural
 * mistake is rejected here, at load time, with an explicit error naming the table —
 * never discovered at the first quest completion (a rejected table is simply absent,
 * which then refuses the quests that reference it).
 */
public final class RewardScales {
    private static final RewardScales EMPTY = new RewardScales(Map.of());

    private final Map<String, RewardScale> scales;

    private RewardScales(Map<String, RewardScale> scales) {
        this.scales = scales;
    }

    public static RewardScales empty() {
        return EMPTY;
    }

    public @Nullable RewardScale get(String id) {
        return id == null ? null : scales.get(id);
    }

    /** Whether any table indexes on the AuroraLevels level (drives level-up refreshes). */
    public boolean hasAuroraLevelSource() {
        return scales.values().stream().anyMatch(s -> s.getSource() == RewardScale.SourceType.AURORA_LEVEL);
    }

    public static RewardScales parse(@Nullable ConfigurationSection root) {
        if (root == null) return EMPTY;

        var scales = new LinkedHashMap<String, RewardScale>();
        for (var id : root.getKeys(false)) {
            var section = root.getConfigurationSection(id);
            if (section == null) {
                severe(id, "is not a section");
                continue;
            }
            var scale = parseOne(id, section);
            if (scale != null) {
                scales.put(id, scale);
            }
        }
        return new RewardScales(Map.copyOf(scales));
    }

    private static @Nullable RewardScale parseOne(String id, ConfigurationSection section) {
        var sourceRaw = section.getString("source", "aurora-level").trim().toLowerCase(Locale.ROOT);
        RewardScale.SourceType source;
        switch (sourceRaw) {
            case "aurora-level" -> source = RewardScale.SourceType.AURORA_LEVEL;
            case "placeholder" -> source = RewardScale.SourceType.PLACEHOLDER;
            default -> {
                severe(id, "has unknown source '" + sourceRaw + "' (use aurora-level or placeholder)");
                return null;
            }
        }

        var placeholder = section.getString("placeholder");
        if (source == RewardScale.SourceType.PLACEHOLDER && (placeholder == null || placeholder.isBlank())) {
            severe(id, "has source 'placeholder' but no 'placeholder' key");
            return null;
        }

        var modeRaw = section.getString("mode", "step").trim().toLowerCase(Locale.ROOT);
        RewardScale.Mode mode;
        switch (modeRaw) {
            case "step" -> mode = RewardScale.Mode.STEP;
            case "linear" -> mode = RewardScale.Mode.LINEAR;
            default -> {
                warn(id, "has unknown mode '" + modeRaw + "' (use step or linear), using step");
                mode = RewardScale.Mode.STEP;
            }
        }

        Double fallback = section.contains("fallback") ? section.getDouble("fallback") : null;

        var rawTiers = section.getMapList("tiers");
        if (rawTiers.isEmpty()) {
            severe(id, "has no tiers");
            return null;
        }

        var tiers = new ArrayList<RewardScale.Tier>(rawTiers.size());
        Double previousUpTo = null;
        for (int i = 0; i < rawTiers.size(); i++) {
            Map<?, ?> raw = rawTiers.get(i);
            var value = asDouble(raw.get("value"));
            if (value == null) {
                severe(id, "tier " + (i + 1) + " has no numeric 'value'");
                return null;
            }
            var upTo = asDouble(raw.get("up-to"));
            boolean last = i == rawTiers.size() - 1;
            if (upTo == null && !last) {
                severe(id, "tier " + (i + 1) + " has no 'up-to' but is not the last tier");
                return null;
            }
            if (upTo != null && previousUpTo != null && upTo <= previousUpTo) {
                severe(id, "tier " + (i + 1) + " 'up-to: " + trim(upTo) + "' is not strictly greater than the previous one ("
                        + trim(previousUpTo) + ")");
                return null;
            }
            if (upTo != null) previousUpTo = upTo;
            tiers.add(new RewardScale.Tier(upTo, value));
        }

        return new RewardScale(id, source, placeholder, fallback, mode, tiers);
    }

    private static @Nullable Double asDouble(Object raw) {
        if (raw instanceof Number number) return number.doubleValue();
        if (raw instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static String trim(double value) {
        return RewardScale.formatClean(value);
    }

    private static void severe(String id, String message) {
        ScaleLog.severe.accept("[reward-scales] Table '" + id + "' " + message + " - the table is ignored and every quest using it will be refused.");
    }

    private static void warn(String id, String message) {
        ScaleLog.warning.accept("[reward-scales] Table '" + id + "' " + message + ".");
    }
}
