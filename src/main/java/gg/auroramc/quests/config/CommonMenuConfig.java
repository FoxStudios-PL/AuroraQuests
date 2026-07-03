package gg.auroramc.quests.config;

import gg.auroramc.aurora.api.config.AuroraConfig;
import gg.auroramc.aurora.api.config.premade.ItemConfig;
import gg.auroramc.quests.AuroraQuests;
import lombok.Getter;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Getter
public class CommonMenuConfig extends AuroraConfig {
    private Map<String, Config.DisplayComponent> displayComponents;
    private Map<String, ItemConfig> items;
    private TaskStatuses taskStatuses;
    private ProgressBar progressBar;

    @Getter
    public static final class ProgressBar {
        private Integer length = 20;
        private String filledCharacter;
        private String unfilledCharacter;
    }

    public CommonMenuConfig(AuroraQuests plugin) {
        super(getFile(plugin));
    }


    @Getter
    public static class TaskStatuses {
        private String completed = "";
        private String notCompleted = "";
        private String locked = "";
        // Strike through (cross out) the line of a completed step in the quest menu.
        private boolean completedStrikethrough = true;

        // Falls back to the not-completed status when not configured, so existing
        // setups are visually unchanged unless an admin opts into a locked style.
        public String getLocked() {
            return (locked == null || locked.isEmpty()) ? notCompleted : locked;
        }
    }

    public static File getFile(AuroraQuests plugin) {
        return new File(plugin.getDataFolder(), "menu_common.yml");
    }

    public static void saveDefault(AuroraQuests plugin) {
        if (!getFile(plugin).exists()) {
            plugin.saveResource("menu_common.yml", false);
        }
    }

    @Override
    protected List<Consumer<YamlConfiguration>> getMigrationSteps() {
        return List.of(
                (yaml) -> {
                    yaml.set("task-statuses.completed-strikethrough", true);
                    yaml.setComments("task-statuses.completed-strikethrough", List.of(
                            "Strike through (cross out) the line of a completed step in the quest menu."));
                    yaml.set("config-version", 1);
                }
        );
    }
}
