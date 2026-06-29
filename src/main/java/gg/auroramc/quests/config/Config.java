package gg.auroramc.quests.config;

import gg.auroramc.aurora.api.config.AuroraConfig;
import gg.auroramc.aurora.api.config.decorators.IgnoreField;
import gg.auroramc.aurora.api.config.premade.ItemConfig;
import gg.auroramc.quests.AuroraQuests;
import lombok.Getter;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Getter
public class Config extends AuroraConfig {
    private Boolean debug = false;
    private Boolean purgeInvalidDataOnLogin = false;
    private String language = "en";
    private Boolean perPlayerLocale = false;
    private Map<String, String> difficulties;
    private Boolean preventCreativeMode = false;
    private LeaderboardConfig leaderboards;
    private Map<String, DisplayComponent> displayComponents;
    private LevelUpSound levelUpSound;
    private LevelUpMessage levelUpMessage;
    private LevelUpSound questCompleteSound;
    private LevelUpMessage questCompleteMessage;
    private CommandAliasConfig commandAliases;
    private List<String> sortOrder;
    private UnlockTaskConfig unlockTask = new UnlockTaskConfig();
    private TrackingConfig tracking = new TrackingConfig();
    private QuestBookConfig questBook = new QuestBookConfig();

    @IgnoreField
    private Map<String, Integer> sortOderMap;

    @Override
    public void load() {
        super.load();
        Map<String, Integer> difficultyOrder = new HashMap<>();
        for (int i = 0; i < sortOrder.size(); i++) {
            difficultyOrder.put(sortOrder.get(i), i);
        }
        sortOderMap = difficultyOrder;
    }

    public Config(AuroraQuests plugin) {
        super(getFile(plugin));
    }

    @Getter
    public static final class LeaderboardConfig {
        private Integer cacheSize = 10;
        private Integer minCompleted = 3;
        private Boolean includeGlobal = false;
    }

    @Getter
    public static final class DisplayComponent {
        private String title;
        private String line;
    }

    @Getter
    public static final class UnlockTaskConfig {
        private Boolean enabled = false;
        private Integer interval = 5;
    }

    @Getter
    public static final class QuestBookConfig {
        // Whether the whole quest book module is active. Enabling it requires a
        // restart; once enabled it can be tuned/disabled live with /quests reload.
        private Boolean enabled = false;
        // Inventory slot the book is locked to (0-8 hotbar, 9-35 main inventory).
        private Integer slot = 17;
        // Commands run on click. Supports Aurora action prefixes
        // ([player], [console], [open-gui], ...), {player} and PlaceholderAPI.
        private List<String> clickCommands = List.of("[player] quests");
        private QuestBookStateConfig initialState = new QuestBookStateConfig();
        private QuestBookStateConfig newQuestState = new QuestBookStateConfig();
    }

    @Getter
    public static final class QuestBookStateConfig {
        private ItemConfig item;
        private QuestBookSound sound = new QuestBookSound();
    }

    @Getter
    public static final class QuestBookSound {
        private String sound = "";
        private Float volume = 1.0f;
        private Float pitch = 1.0f;
    }

    @Getter
    public static final class LevelUpMessage {
        private Boolean enabled;
        private List<String> message;
    }

    @Getter
    public static final class LevelUpSound {
        private Boolean enabled;
        private String sound;
        private Float volume;
        private Float pitch;
    }

    @Getter
    public static final class CommandAliasConfig {
        private List<String> quests = List.of("quests");
    }

    @Getter
    public static final class TrackingConfig {
        private List<String> onTrack = List.of();
        private List<String> onUntrack = List.of();
        private List<String> trackedLore = List.of();
        private boolean autoTrackOnUnlock = true;
        private int maxTrackedQuests = 5;
    }

    public static File getFile(AuroraQuests plugin) {
        return new File(plugin.getDataFolder(), "config.yml");
    }

    public static void saveDefault(AuroraQuests plugin) {
        if (!getFile(plugin).exists()) {
            plugin.saveResource("config.yml", false);
        }
    }

    @Override
    protected List<Consumer<YamlConfiguration>> getMigrationSteps() {
        return List.of(
                (yaml) -> {
                    yaml.set("purge-invalid-data-on-login", false);
                    yaml.setComments("purge-invalid-data-on-login", List.of("Only enable this if you are heavily modifying your quest pools/quests data in production (WHICH YOU SHOULDN'T)"));

                    yaml.set("unlock-task.enabled", false);
                    yaml.set("unlock-task.interval", 5);
                    yaml.setComments("unlock-task", List.of("Timer to try to unlock global quests and quest pools if for some reason the event driven method doesn't work"));
                    yaml.setComments("unlock-task.interval", List.of("Interval in seconds"));

                    for (var key : List.of("weeks", "days", "hours", "minutes", "seconds")) {
                        yaml.set("timer-format.short-format.plural." + key, "{value}" + key.charAt(0));
                        yaml.set("timer-format.short-format.singular." + key, "{value}" + key.charAt(0));
                        yaml.set("timer-format.long-format.plural." + key, "{value} " + key);
                        yaml.set("timer-format.long-format.singular." + key, "{value} " + key.substring(0, key.length() - 1));
                    }

                    yaml.set("config-version", 1);
                },
                (yaml) -> {
                    yaml.set("level-up-sound.sound", "entity.player.levelup");
                    yaml.set("quest-complete-sound.sound", "entity.player.levelup");

                    yaml.set("config-version", 2);
                },
                (yaml) -> {
                    yaml.set("timer-format", null);
                    yaml.set("config-version", 3);
                },
                (yaml) -> {
                    yaml.set("per-player-locale", false);
                    yaml.set("config-version", 4);
                },
                (yaml) -> {
                    yaml.set("tracking.on-track", List.of());
                    yaml.set("tracking.on-untrack", List.of());
                    yaml.set("tracking.tracked-lore", List.of("&e&l► TRACKED"));
                    yaml.setComments("tracking", List.of(
                            "Global tracking configuration",
                            "on-track: commands to execute when a player tracks a quest (supports {player}, {quest}, {quest_id}, {pool_id} placeholders)",
                            "on-untrack: commands to execute when a player untracks a quest",
                            "tracked-lore: extra lore lines appended to the quest menu item when it is being tracked"
                    ));
                    yaml.set("quest-book.enabled", false);
                    yaml.setComments("quest-book", List.of(
                            "Places a clickable \"quest book\" item in a fixed inventory slot of every",
                            "player. Clicking it runs the configured command(s) (e.g. opens the quest menu).",
                            "The item is protected: it cannot be dropped, moved or lost on death.",
                            "NOTE: enabling this requires a restart. Once enabled you can tune it",
                            "      (or temporarily disable it) live with /quests reload."));
                    yaml.set("quest-book.slot", 17);
                    yaml.setComments("quest-book.slot", List.of("0-8 = hotbar, 9-35 = main inventory"));
                    yaml.set("quest-book.click-commands", List.of("[player] quests"));
                    yaml.setComments("quest-book.click-commands", List.of(
                            "Command(s) run when the player clicks the book.",
                            "Supports Aurora action prefixes ([player], [console], [open-gui], ...),",
                            "{player} and PlaceholderAPI."));

                    yaml.set("quest-book.initial-state.item.material", "WRITTEN_BOOK");
                    yaml.set("quest-book.initial-state.item.custom-model-data", 0);
                    yaml.set("quest-book.initial-state.item.name", "&6&lQuest Book");
                    yaml.set("quest-book.initial-state.item.lore", List.of("", "&7Click to open your quests", ""));
                    yaml.set("quest-book.initial-state.sound.sound", "item.book.page_turn");
                    yaml.set("quest-book.initial-state.sound.volume", 1.0);
                    yaml.set("quest-book.initial-state.sound.pitch", 1.0);

                    yaml.set("quest-book.new-quest-state.item.material", "WRITTEN_BOOK");
                    yaml.set("quest-book.new-quest-state.item.custom-model-data", 1);
                    yaml.set("quest-book.new-quest-state.item.name", "&a&lQuest Book &e⭐");
                    yaml.set("quest-book.new-quest-state.item.lore", List.of("", "&aA new quest is available!", "&7Click to open your quests", ""));
                    yaml.set("quest-book.new-quest-state.sound.sound", "item.book.page_turn");
                    yaml.set("quest-book.new-quest-state.sound.volume", 1.0);
                    yaml.set("quest-book.new-quest-state.sound.pitch", 1.2);

                    yaml.set("config-version", 5);
                },
                (yaml) -> {
                    yaml.set("tracking.auto-track-on-unlock", true);
                    yaml.setComments("tracking.auto-track-on-unlock", List.of(
                            "When the unlock command unlocks a quest, automatically track it",
                            "— but only if the player has no quest currently tracked."));

                    yaml.set("config-version", 6);
                },
                (yaml) -> {
                    yaml.set("tracking.max-tracked-quests", 5);
                    yaml.setComments("tracking.max-tracked-quests", List.of(
                            "Maximum number of quests a player can track at once (tracking queue).",
                            "Only the first tracked quest is shown (placeholders/scoreboard); when it is",
                            "completed, the next tracked quest takes its place. Use 0 or less for unlimited."));

                    yaml.set("config-version", 7);
                }
        );
    }
}
