package gg.auroramc.quests.scoreboard;

import gg.auroramc.aurora.api.AuroraAPI;
import gg.auroramc.aurora.api.message.Placeholder;
import gg.auroramc.aurora.api.message.Text;
import gg.auroramc.quests.api.objective.Objective;
import gg.auroramc.quests.api.quest.Quest;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the configured scoreboard lines into Components for the tracked quest.
 * <p>
 * A line containing {@code {reward}} is repeated once per reward of the current
 * step; a line containing {@code {description}} once per description line. All
 * other tokens ({chapter}, {quest_name}, {step}, {step_total}, {display}) are
 * scalar. Rendering goes through {@link Text#component} so legacy, MiniMessage
 * and PlaceholderAPI all work.
 */
public class ScoreboardRenderer {

    public static Component renderTitle(Player player, String title) {
        return Text.component(player, title == null ? "" : title);
    }

    public static List<Component> renderLines(Player player, Quest quest, List<String> configLines) {
        List<Component> out = new ArrayList<>();
        if (configLines == null) return out;

        var def = quest.getDefinition();
        var objectives = quest.getObjectives();
        int idx = quest.getCurrentObjectiveIndex();
        Objective current = (idx >= 0 && idx < objectives.size()) ? objectives.get(idx) : null;

        List<Placeholder<?>> base = new ArrayList<>();
        base.add(Placeholder.of("{chapter}", def.getChapter() != null ? def.getChapter() : ""));
        base.add(Placeholder.of("{quest_name}", def.getName() != null ? def.getName() : ""));
        base.add(Placeholder.of("{step}", String.valueOf(idx + 1)));
        base.add(Placeholder.of("{step_total}", String.valueOf(objectives.size())));
        base.add(Placeholder.of("{display}", current != null ? cleanDisplay(current) : ""));

        for (String line : configLines) {
            if (line == null) {
                continue;
            }
            if (line.contains("{reward}")) {
                if (current != null && !current.getDefinition().getRewards().isEmpty()) {
                    for (var reward : current.getDefinition().getRewards().values()) {
                        out.add(Text.component(player, line, withExtra(base, Placeholder.of("{reward}", reward.getDisplay(player, base)))));
                    }
                } else {
                    // No reward on this step: render the line once with a dash instead of dropping it.
                    out.add(Text.component(player, line, withExtra(base, Placeholder.of("{reward}", "&c/"))));
                }
            } else if (line.contains("{description}")) {
                if (current != null && current.getDefinition().getDescription() != null) {
                    for (String desc : current.getDefinition().getDescription()) {
                        out.add(Text.component(player, line, withExtra(base, Placeholder.of("{description}", desc))));
                    }
                }
            } else {
                out.add(Text.component(player, line, base));
            }
        }
        return out;
    }

    /** The step's display with {current}/{required} resolved to live values and the menu-only {status} icon removed. */
    private static String cleanDisplay(Objective objective) {
        String display = objective.getDefinition().getDisplay();
        if (display == null) return "";
        return display.replace("{status}", "")
                .replace("{current}", AuroraAPI.formatNumber(objective.getProgress()))
                .replace("{required}", AuroraAPI.formatNumber(objective.getTarget()))
                .trim();
    }

    private static List<Placeholder<?>> withExtra(List<Placeholder<?>> base, Placeholder<?> extra) {
        List<Placeholder<?>> list = new ArrayList<>(base.size() + 1);
        list.addAll(base);
        list.add(extra);
        return list;
    }
}
