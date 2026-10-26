package gg.auroramc.quests.api.objective.filter;

import gg.auroramc.quests.api.objective.ObjectiveMeta;
import lombok.AllArgsConstructor;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Passes when the meta variable {@code key} is one of the configured values, compared without
 * case. Nothing configured lets everything through. The argument may be a list
 * ({@code kind: [RANK, ENERGY]}) or a single value ({@code shiny: true}).
 */
@AllArgsConstructor
public class StringListFilter implements ObjectiveFilter {
    private final String key;
    private final Set<String> values;

    @Override
    public boolean filter(ObjectiveMeta meta) {
        if (values.isEmpty()) return true;
        return meta.getVariable(key, String.class)
                .map(value -> values.contains(value.toUpperCase(Locale.ROOT)))
                .orElse(false);
    }

    public static StringListFilter stringListFilter(ConfigurationSection args, String key) {
        Collection<String> raw = args.isList(key)
                ? args.getStringList(key)
                : (args.isSet(key) ? List.of(args.getString(key, "")) : List.of());
        return new StringListFilter(key, raw.stream()
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet()));
    }
}
