package gg.auroramc.quests.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import gg.auroramc.aurora.api.AuroraAPI;
import gg.auroramc.aurora.api.message.Chat;
import gg.auroramc.aurora.api.message.Placeholder;
import gg.auroramc.quests.AuroraQuests;
import gg.auroramc.quests.api.data.QuestData;
import gg.auroramc.quests.api.objective.Objective;
import gg.auroramc.quests.api.profile.Profile;
import gg.auroramc.quests.api.quest.Quest;
import gg.auroramc.quests.api.questpool.QuestPool;
import gg.auroramc.quests.menu.MainMenu;
import gg.auroramc.quests.menu.PoolMenu;
import gg.auroramc.quests.questbook.QuestBookState;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CommandAlias("%questsAlias")
public class QuestsCommand extends BaseCommand {
    private final AuroraQuests plugin;

    public QuestsCommand(AuroraQuests plugin) {
        this.plugin = plugin;
    }

    @Default
    @Description("Opens the quests menu")
    @CommandPermission("aurora.quests.use")
    public void onMenu(Player player) {
        var profile = plugin.getProfileManager().getProfile(player);
        if (profile == null) {
            Chat.sendMessage(player, plugin.getConfigManager().getMessageConfig(player).getDataNotLoadedYetSelf());
            return;
        }
        new MainMenu(profile).open();
    }

    @Subcommand("reload")
    @Description("Reloads the plugin configs and applies reward auto correctors to players")
    @CommandPermission("aurora.quests.admin.reload")
    public void onReload(CommandSender sender) {
        plugin.reload();
        Chat.sendMessage(sender, plugin.getConfigManager().getMessageConfig(sender).getReloaded());
    }

    @Subcommand("open")
    @Description("Opens the quest menu for another player in a specific pool")
    @CommandCompletion("@players @pools|none|all true|false")
    @CommandPermission("aurora.quests.admin.open")
    public void onOpenMenu(CommandSender sender, @Flags("other") Player target, @Default("none") String poolId, @Default("false") Boolean silent) {
        var profile = plugin.getProfileManager().getProfile(target);
        if (profile == null) {
            Chat.sendMessage(sender, plugin.getConfigManager().getMessageConfig(sender).getDataNotLoadedYet(), Placeholder.of("{target}", target.getName()));
            return;
        }

        if (poolId.equals("none") || poolId.equals("all")) {
            new MainMenu(profile).open();
        } else {
            var pool = profile.getQuestPool(poolId);
            if (pool == null) {
                Chat.sendMessage(sender, plugin.getConfigManager().getMessageConfig(sender).getPoolNotFound(), Placeholder.of("{pool}", poolId));
                return;
            }
            if (pool.isUnlocked()) {
                new PoolMenu(profile, pool).open();
                if (!silent) {
                    Chat.sendMessage(sender, plugin.getConfigManager().getMessageConfig(sender).getMenuOpened(), Placeholder.of("{player}", target.getName()));
                }
            }
        }
    }

    @Subcommand("reroll")
    @Description("Rerolls quests for another player in a specific pool")
    @CommandCompletion("@players @pools|none|all true|false")
    @CommandPermission("aurora.quests.admin.reroll")
    public void onReroll(CommandSender sender, @Flags("other") Player target, @Default("all") String poolId, @Default("false") Boolean silent) {
        var profile = plugin.getProfileManager().getProfile(target);
        if (profile == null) {
            Chat.sendMessage(sender, plugin.getConfigManager().getMessageConfig(sender).getDataNotLoadedYet(), Placeholder.of("{target}", target.getName()));
            return;
        }

        if (poolId.equals("none") || poolId.equals("all")) {
            profile.getQuestPools().forEach((pool) -> pool.reRollQuests(!silent));
        } else {
            var pool = profile.getQuestPool(poolId);
            if (pool != null) {
                if (!pool.isUnlocked()) return;
                pool.reRollQuests(!silent);
                if (!silent) {
                    Chat.sendMessage(sender, plugin.getConfigManager().getMessageConfig(sender).getReRolledSource(), Placeholder.of("{player}", target.getName()), Placeholder.of("{pool}", pool.getDefinition().getName()));
                }
            } else {
                Chat.sendMessage(sender, plugin.getConfigManager().getMessageConfig(sender).getPoolNotFound(), Placeholder.of("{pool}", poolId));
            }
        }
    }

    @Subcommand("unlock")
    @Description("Unlocks quest for player")
    @CommandCompletion("@players @pools @quests true|false")
    @CommandPermission("aurora.quests.admin.unlock")
    public void onQuestUnlock(CommandSender sender, @Flags("other") Player target, String poolId, String questId, @Default("false") Boolean silent) {
        var profile = plugin.getProfileManager().getProfile(target);
        if (profile == null) {
            Chat.sendMessage(sender, plugin.getConfigManager().getMessageConfig(sender).getDataNotLoadedYet(), Placeholder.of("{target}", target.getName()));
            return;
        }

        var pool = profile.getQuestPool(poolId);
        if (pool == null) {
            Chat.sendMessage(sender, plugin.getConfigManager().getMessageConfig(sender).getPoolNotFound(), Placeholder.of("{pool}", poolId));
            return;
        }

        var quest = pool.getQuest(questId);
        if (quest == null) {
            Chat.sendMessage(sender, plugin.getConfigManager().getMessageConfig(sender).getQuestNotFound(), Placeholder.of("{pool}", pool.getId()), Placeholder.of("{quest}", questId));
            return;
        }

        if (!quest.isUnlocked()) {
            // Will unlock any locked quest, not just the ones that have manual-unlock requirement
            quest.start(true);
            if (!silent) {
                Chat.sendMessage(sender, plugin.getConfigManager().getMessageConfig(sender).getQuestUnlocked(), Placeholder.of("{player}", target.getName()), Placeholder.of("{quest}", questId));
            }
        } else {
            Chat.sendMessage(sender, plugin.getConfigManager().getMessageConfig(sender).getQuestAlreadyUnlocked(), Placeholder.of("{player}", target.getName()), Placeholder.of("{quest}", questId));
        }
    }

    @Subcommand("complete")
    @Description("Completes a quest or objective for a player")
    @CommandCompletion("@players @pools @quests @objectives true|false")
    @CommandPermission("aurora.quests.admin.complete")
    public void onQuestComplete(CommandSender sender, @Flags("other") Player target, String poolId, String questId, @Default("all") String objectiveId, @Default("false") Boolean silent) {
        String targetObjective;
        boolean isSilent;

        if (objectiveId.equalsIgnoreCase("true") || objectiveId.equalsIgnoreCase("false")) {
            isSilent = Boolean.parseBoolean(objectiveId);
            targetObjective = "all";
        } else {
            targetObjective = objectiveId;
            isSilent = silent;
        }
        Profile profile = plugin.getProfileManager().getProfile(target);

        if (profile == null) {
            Chat.sendMessage(sender, plugin.getConfigManager().getMessageConfig(sender).getDataNotLoadedYet(), Placeholder.of("{target}", target.getName()));
            return;
        }

        var pool = profile.getQuestPool(poolId);
        if (pool == null) {
            Chat.sendMessage(sender, plugin.getConfigManager().getMessageConfig(sender).getPoolNotFound(), Placeholder.of("{pool}", poolId));
            return;
        }

        var quest = pool.getQuest(questId);
        if (quest == null) {
            Chat.sendMessage(sender, plugin.getConfigManager().getMessageConfig(sender).getQuestNotFound(), Placeholder.of("{pool}", pool.getId()), Placeholder.of("{quest}", questId));
            return;
        }

        if (targetObjective.equals("all")) {
            if (!quest.isCompleted()) {
                quest.complete();
                if (!isSilent) {
                    Chat.sendMessage(sender, plugin.getConfigManager().getMessageConfig(sender).getQuestCompleted(), Placeholder.of("{player}", target.getName()), Placeholder.of("{quest}", questId));
                }
            } else {
                Chat.sendMessage(sender, plugin.getConfigManager().getMessageConfig(sender).getQuestAlreadyCompleted(), Placeholder.of("{player}", target.getName()), Placeholder.of("{quest}", questId));
            }
        } else {
            Objective objective = quest.getObjectives().stream()
                    .filter(o -> o.getId().equals(targetObjective))
                    .findFirst().orElse(null);

            if (objective == null) {
                Chat.sendMessage(sender, plugin.getConfigManager().getMessageConfig(sender).getObjectiveNotFound(),
                        Placeholder.of("{objective}", targetObjective), Placeholder.of("{quest}", questId));
                return;
            }
            if (objective.isCompleted()) {
                Chat.sendMessage(sender, plugin.getConfigManager().getMessageConfig(sender).getObjectiveAlreadyCompleted(),
                        Placeholder.of("{objective}", targetObjective), Placeholder.of("{player}", target.getName()));
                return;
            }
            objective.complete(isSilent);

            if (!isSilent) {
                Chat.sendMessage(sender, plugin.getConfigManager().getMessageConfig(sender).getQuestCompleted(),
                        Placeholder.of("{player}", target.getName()), Placeholder.of("{quest}", questId + "/" + targetObjective));
            }
        }
    }

    @Subcommand("notify")
    @Description("Toggles the quest book 'new quest' notification for a player")
    @CommandCompletion("@players true|false")
    @CommandPermission("aurora.quests.admin.notify")
    public void onQuestBookNotify(CommandSender sender, @Flags("other") Player target, @Default("toggle") String value) {
        var manager = plugin.getQuestBookManager();
        if (manager == null || !manager.isActive()) {
            Chat.sendMessage(sender, plugin.getConfigManager().getMessageConfig(sender).getQuestBookDisabled());
            return;
        }

        QuestBookState newState;
        if (value.equalsIgnoreCase("true")) {
            newState = manager.setNotification(target, true);
        } else if (value.equalsIgnoreCase("false")) {
            newState = manager.setNotification(target, false);
        } else {
            newState = manager.toggleNotification(target);
        }

        if (newState == null) {
            Chat.sendMessage(sender, plugin.getConfigManager().getMessageConfig(sender).getDataNotLoadedYet(), Placeholder.of("{target}", target.getName()));
            return;
        }

        var msg = newState == QuestBookState.NEW_QUEST
                ? plugin.getConfigManager().getMessageConfig(sender).getQuestBookNotifyOn()
                : plugin.getConfigManager().getMessageConfig(sender).getQuestBookNotifyOff();
        Chat.sendMessage(sender, msg, Placeholder.of("{player}", target.getName()));
    }

    @Subcommand("reset")
    @Description("Reset quest progress a player")
    @CommandCompletion("@players @pools @quests|all true|false")
    @CommandPermission("aurora.quests.admin.reset")
    public void onQuestReset(CommandSender sender, @Flags("other") Player target, String poolId, @Default("all") String questId, @Default("false") Boolean silent) {
        var profile = plugin.getProfileManager().getProfile(target);
        if (profile == null) {
            Chat.sendMessage(sender, plugin.getConfigManager().getMessageConfig(sender).getDataNotLoadedYet(), Placeholder.of("{target}", target.getName()));
            return;
        }

        var pool = profile.getQuestPool(poolId);
        if (pool == null) {
            Chat.sendMessage(sender, plugin.getConfigManager().getMessageConfig(sender).getPoolNotFound(), Placeholder.of("{pool}", poolId));
            return;
        }

        var quest = pool.getQuest(questId);
        if (questId.equals("all")) {
            pool.resetAllQuestProgress();
            if (!silent) {
                Chat.sendMessage(sender, plugin.getConfigManager().getMessageConfig(sender).getQuestReset(), Placeholder.of("{player}", target.getName()), Placeholder.of("{quest}", "all"));
            }
            return;
        } else if (quest == null) {
            Chat.sendMessage(sender, plugin.getConfigManager().getMessageConfig(sender).getQuestNotFound(), Placeholder.of("{pool}", pool.getId()), Placeholder.of("{quest}", questId));
            return;
        }

        quest.reset();
        if (pool.isGlobal()) {
            quest.start(false);
        } else if (pool.isRolledQuest(quest)) {
            quest.start(false);
        }

        if (!silent) {
            Chat.sendMessage(sender, plugin.getConfigManager().getMessageConfig(sender).getQuestReset(), Placeholder.of("{player}", target.getName()), Placeholder.of("{quest}", questId));
        }
    }

    @Subcommand("track")
    @Description("Toggles quest tracking for a player")
    @CommandCompletion("@players @pools @quests")
    @CommandPermission("aurora.quests.admin.track")
    public void onQuestTrack(CommandSender sender, @Flags("other") Player target, String poolId, String questId) {
        Profile profile = plugin.getProfileManager().getProfile(target);

        if (profile == null) {
            Chat.sendMessage(sender, plugin.getConfigManager().getMessageConfig(sender).getDataNotLoadedYet(), Placeholder.of("{target}", target.getName()));
            return;
        }
        QuestPool pool = profile.getQuestPool(poolId);

        if (pool == null) {
            Chat.sendMessage(sender, plugin.getConfigManager().getMessageConfig(sender).getPoolNotFound(), Placeholder.of("{pool}", poolId));
            return;
        }
        Quest quest = pool.getQuest(questId);

        if (quest == null) {
            Chat.sendMessage(sender, plugin.getConfigManager().getMessageConfig(sender).getQuestNotFound(), Placeholder.of("{pool}", pool.getId()), Placeholder.of("{quest}", questId));
            return;
        }
        if (quest.isCompleted() || !quest.isStarted()) {
            Chat.sendMessage(sender, plugin.getConfigManager().getMessageConfig(sender).getQuestNotActive(),
                    Placeholder.of("{quest}", questId), Placeholder.of("{player}", target.getName()));
            return;
        }
        QuestData questData = AuroraAPI.getUser(target.getUniqueId()).getData(QuestData.class);
        boolean isCurrentlyTracked = questData.hasTrackedQuest()
                && poolId.equals(questData.getTrackedPoolId())
                && questId.equals(questData.getTrackedQuestId());

        if (isCurrentlyTracked) {
            quest.executeUntrackCommands();
            questData.clearTrackedQuest();
        } else {
            if (questData.hasTrackedQuest()) {
                QuestPool oldPool = profile.getQuestPool(questData.getTrackedPoolId());

                if (oldPool != null) {
                    Quest oldQuest = oldPool.getQuest(questData.getTrackedQuestId());

                    if (oldQuest != null) {
                        oldQuest.executeUntrackCommands();
                    }
                }
            }
            questData.setTrackedQuest(poolId, questId);
            quest.executeTrackCommands();
        }
        Chat.sendMessage(sender, plugin.getConfigManager().getMessageConfig(sender).getQuestTrackToggled(),
                Placeholder.of("{quest}", questId), Placeholder.of("{player}", target.getName()));
    }
}
