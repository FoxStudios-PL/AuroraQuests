package gg.auroramc.quests.util;

import gg.auroramc.aurora.api.message.Placeholder;
import gg.auroramc.aurora.api.message.Text;
import gg.auroramc.aurora.api.reward.Reward;
import gg.auroramc.quests.AuroraQuests;
import gg.auroramc.quests.config.Config;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

public class RewardUtil {
    public static Component fillRewardMessage(Player player, Config.DisplayComponent config, List<String> lines, List<Placeholder<?>> placeholders, Collection<Reward> rewards) {
        var localization = AuroraQuests.getInstance().getLocalizationProvider();
        var text = Component.text();

        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            if (line.equals("component:rewards")) {
                if (!rewards.isEmpty()) {
                    text.append(render(player, localization.fillVariables(player, config.getTitle(), placeholders)));
                }
                for (var reward : rewards) {
                    var rewardText = reward.getDisplay(player, placeholders);
                    if (rewardText.isBlank()) continue;
                    text.append(Component.newline());
                    var display = config.getLine().replace("{reward}", rewardText);
                    text.append(render(player, localization.fillVariables(player, display, placeholders)));
                }
            } else {
                text.append(render(player, localization.fillVariables(player, line, placeholders)));
            }

            // Index-based on purpose: comparing by value (the old code) dropped the newline
            // after any line that happened to equal the last one (e.g. two ' ' spacers),
            // gluing it to the next line and shifting centered lines.
            if (i < lines.size() - 1) text.append(Component.newline());
        }

        return text.build();
    }

    /**
     * PlaceholderAPI is resolved before centering so <center> lines are measured on the
     * final text; Text.component would otherwise resolve it after the padding was computed.
     */
    private static Component render(Player player, String line) {
        var resolved = Text.fillPlaceholders(player, line);
        return Text.component(ChatCenterer.center(resolved));
    }
}
