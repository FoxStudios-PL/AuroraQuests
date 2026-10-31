package gg.auroramc.quests.hooks.foxrecycle.objective;

import fr.orylium.recyclage.api.event.FoxRecycleItemEvent;
import gg.auroramc.quests.api.objective.Objective;
import gg.auroramc.quests.api.objective.ObjectiveDefinition;
import gg.auroramc.quests.api.objective.filter.ObjectiveFilter;
import gg.auroramc.quests.api.profile.Profile;
import gg.auroramc.quests.api.quest.Quest;
import org.bukkit.event.EventPriority;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code RECYCLE_ITEM}: +1 per item FoxRecycle actually destroyed. FoxRecycle fires one
 * {@link FoxRecycleItemEvent} per item after the confirmation, so two items recycled in
 * one confirmation count twice, and a cancelled or closed menu never counts.
 * <p>
 * Arguments: {@code types} filters on the rule's category ({@code ARMOR}, {@code JEWEL},
 * {@code OTHER}; any case; {@code mode: blacklist} inverts it), {@code require-reward: true}
 * only counts a recycle that gave at least one reward.
 */
public class RecycleItemObjective extends Objective {

    public RecycleItemObjective(Quest quest, ObjectiveDefinition definition, Profile.TaskDataWrapper data) {
        super(quest, definition, data);
    }

    @Override
    protected void activate() {
        onEvent(FoxRecycleItemEvent.class, this::handle, EventPriority.MONITOR);
    }

    public void handle(FoxRecycleItemEvent e) {
        var meta = meta();
        meta.setVariable("category", e.getCategory());
        meta.setVariable("rewarded", e.hasRewards());
        progress(1, meta);
    }

    @Override
    public List<ObjectiveFilter> getFilters() {
        var args = definition.getArgs();
        List<ObjectiveFilter> filters = new ArrayList<>();

        var raw = args.isList("types")
                ? args.getStringList("types")
                : (args.isSet("types") ? List.of(args.getString("types", "")) : List.<String>of());
        Set<String> categories = raw.stream()
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        if (!categories.isEmpty()) {
            boolean blacklist = args.getString("mode", "whitelist").equalsIgnoreCase("blacklist");
            filters.add(meta -> {
                var category = meta.getVariable("category", String.class)
                        .map(value -> value.toUpperCase(Locale.ROOT));
                boolean listed = category.map(categories::contains).orElse(false);
                return blacklist != listed;
            });
        }

        if (args.getBoolean("require-reward", false)) {
            filters.add(meta -> meta.getVariable("rewarded", Boolean.class).orElse(false));
        }
        return filters;
    }
}
