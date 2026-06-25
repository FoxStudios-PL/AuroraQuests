package gg.auroramc.quests.api.quest;

import gg.auroramc.aurora.api.AuroraAPI;
import gg.auroramc.aurora.api.command.CommandDispatcher;
import gg.auroramc.aurora.api.message.Placeholder;
import gg.auroramc.aurora.api.reward.RewardExecutor;
import gg.auroramc.quests.AuroraQuests;
import gg.auroramc.quests.api.data.QuestData;
import gg.auroramc.quests.api.event.EventBus;
import gg.auroramc.quests.api.event.EventType;
import gg.auroramc.quests.api.event.QuestCompletedEvent;
import gg.auroramc.quests.api.factory.ObjectiveFactory;
import gg.auroramc.quests.api.profile.Profile;
import gg.auroramc.quests.api.questpool.QuestPool;
import gg.auroramc.quests.api.objective.Objective;
import gg.auroramc.quests.config.Config;
import gg.auroramc.quests.util.RewardUtil;
import gg.auroramc.quests.util.SoundUtil;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
public class Quest extends EventBus {
    private final QuestDefinition definition;
    private final Profile.QuestDataWrapper data;
    private final List<Objective> objectives;
    private final QuestPool pool;
    private boolean started = false;

    public Quest(QuestPool pool, QuestDefinition definition, Profile.QuestDataWrapper data) {
        this.pool = pool;
        this.data = data;
        this.definition = definition;
        this.objectives = definition.getTasks().values().stream()
                .map(d -> ObjectiveFactory.createObjective(this, d))
                .filter(Objects::nonNull)
                .toList();

        for (var obj : objectives) {
            obj.subscribe(EventType.TASK_PROGRESS, objective -> {
                this.publish(EventType.TASK_PROGRESS, objective);
                if (!objective.getDefinition().getOnProgress().isEmpty()) {
                    List<Placeholder<?>> pl = List.of(
                            Placeholder.of("{player}", data.profile().getPlayer().getName()),
                            Placeholder.of("{progress_raw}", objective.getProgress()),
                            Placeholder.of("{progress}", AuroraAPI.formatNumber(objective.getProgress())),
                            Placeholder.of("{target_raw}", objective.getTarget()),
                            Placeholder.of("{target}", AuroraAPI.formatNumber(objective.getTarget())),
                            Placeholder.of("{percent}", AuroraAPI.formatNumber(objective.getProgress() / objective.getTarget() * 100))
                    );
                    for (var command : objective.getDefinition().getOnProgress()) {
                        CommandDispatcher.dispatch(data.profile().getPlayer(), command, pl);
                    }
                }
                refreshScoreboard();
            });

            obj.subscribe(EventType.TASK_COMPLETED, objective -> {
                this.publish(EventType.TASK_COMPLETED, objective);

                if (!objective.getDefinition().getOnComplete().isEmpty()) {
                    var pl = Placeholder.of("{player}", data.profile().getPlayer().getName());
                    for (var command : objective.getDefinition().getOnComplete()) {
                        CommandDispatcher.dispatch(data.profile().getPlayer(), command, pl);
                    }
                }

                grantTaskRewards(objective);

                if (definition.isLinearObjectives()) {
                    startNextObjective();
                }

                refreshScoreboard();

                var completed = true;

                for (var obj2 : objectives) {
                    completed = completed && obj2.isCompleted();
                }

                if (completed) this.handleCompletion(objective);
            });
        }
    }

    private void handleCompletion(@Nullable Objective trigger) {
        data.complete();

        autoUntrackOnComplete();

        Bukkit.getPluginManager().callEvent(new QuestCompletedEvent(data.profile().getPlayer(), pool, this));

        reward();
        dispose();

        this.publish(EventType.QUEST_COMPLETED, trigger);
    }

    private void grantTaskRewards(Objective objective) {
        var rewards = objective.getDefinition().getRewards();
        if (rewards == null || rewards.isEmpty()) return;
        RewardExecutor.execute(rewards.values().stream().toList(), data.profile().getPlayer(), 1, getPlaceholders());
    }

    public boolean isUnlocked() {
        return started || !definition.getRequirements().hasRequirements() || data.isUnlocked();
    }

    public boolean canStart() {
        return definition.getRequirements().canStart(data);
    }

    public boolean start() {
        return start(false);
    }

    public boolean start(boolean force) {
        if (started) return false;

        if (!force && !definition.getRequirements().canStart(data)) {
            return false;
        }

        if (definition.isLinearObjectives()) {
            startNextObjective();
        } else {
            for (var obj : objectives) {
                obj.start();
            }
        }

        if (pool.isGlobal()) {
            data.unlock();
        }

        started = true;

        return true;
    }

    public void unlock() {
        data.unlock();
    }

    public String getId() {
        return definition.getId();
    }

    public void reset() {
        boolean wasStarted = started;
        for (var obj : objectives) {
            obj.dispose();
            obj.resetProgress();
        }
        data.reset();
        started = false;
        // start(true) re-activates correctly for both linear and parallel quests.
        if (wasStarted) start(true);
    }

    public boolean isCompleted() {
        return data.isCompleted();
    }

    public void complete() {
        for (var obj : objectives) {
            obj.complete(true);
        }
        handleCompletion(null);
    }

    public void dispose() {
        for (var obj : objectives) {
            obj.dispose();
        }

        started = false;
    }

    public void destroy() {
        super.dispose();

        for (var obj : objectives) {
            obj.destroy();
        }

        started = false;
    }

    public List<Placeholder<?>> getPlaceholders() {
        var gc = AuroraQuests.getInstance().getConfigManager().getConfig();
        List<Placeholder<?>> placeholders = new ArrayList<>(9 + objectives.size() + definition.getRewards().size());

        placeholders.add(Placeholder.of("{name}", definition.getName()));
        placeholders.add(Placeholder.of("{difficulty}", gc.getDifficulties().get(definition.getDifficulty())));
        placeholders.add(Placeholder.of("{difficulty_id}", definition.getDifficulty()));
        placeholders.add(Placeholder.of("{quest_id}", definition.getId()));
        placeholders.add(Placeholder.of("{quest}", definition.getName()));
        placeholders.add(Placeholder.of("{pool_id}", pool.getId()));
        placeholders.add(Placeholder.of("{pool}", pool.getName()));
        placeholders.add(Placeholder.of("{player}", data.profile().getPlayer().getName()));
        placeholders.add(Placeholder.of("{pool_level}", pool.getLevel()));

        for (int i = 0; i < objectives.size(); i++) {
            var objective = objectives.get(i);
            if (isObjectiveLocked(i) && definition.getLockedObjectiveLore() != null) {
                placeholders.add(Placeholder.of("{task_" + objective.getId() + "}", definition.getLockedObjectiveLore()));
            } else {
                placeholders.add(Placeholder.of("{task_" + objective.getId() + "}", objective.display()));
            }
        }

        for (var reward : definition.getRewards().entrySet()) {
            placeholders.add(Placeholder.of("{reward_" + reward.getKey() + "}", reward.getValue().getDisplay(data.profile().getPlayer(), placeholders)));
        }

        return placeholders;
    }

    private void startNextObjective() {
        for (Objective obj : objectives) {
            if (!obj.isCompleted()) {
                obj.start();
                return;
            }
        }
    }

    /** Refreshes the scoreboard immediately when this quest is the player's tracked quest. */
    private void refreshScoreboard() {
        try {
            var scoreboardManager = AuroraQuests.getInstance().getScoreboardManager();
            if (scoreboardManager == null) return;
            Player player = data.profile().getPlayer();
            if (player == null) return;
            QuestData questData = AuroraAPI.getUser(player.getUniqueId()).getData(QuestData.class);
            if (questData.hasTrackedQuest()
                    && pool.getId().equals(questData.getTrackedPoolId())
                    && definition.getId().equals(questData.getTrackedQuestId())) {
                scoreboardManager.refresh(player);
            }
        } catch (Exception e) {
            AuroraQuests.logger().warning("[AQ] scoreboard refresh failed: " + e);
        }
    }

    private void autoUntrackOnComplete() {
        Player player = data.profile().getPlayer();
        QuestData questData = AuroraAPI.getUser(player.getUniqueId()).getData(QuestData.class);

        if (questData.hasTrackedQuest()
                && pool.getId().equals(questData.getTrackedPoolId())
                && definition.getId().equals(questData.getTrackedQuestId())) {
            executeUntrackCommands();
            questData.clearTrackedQuest();
            var scoreboardManager = AuroraQuests.getInstance().getScoreboardManager();
            if (scoreboardManager != null) {
                scoreboardManager.refresh(player);
            }
        }
    }

    public void executeTrackCommands() {
        Player player = data.profile().getPlayer();
        List<Placeholder<?>> placeholders = getPlaceholders();
        Config config = AuroraQuests.getInstance().getConfigManager().getConfig();
        List<String> questCommands = definition.getOnTrack();

        if (questCommands != null && !questCommands.isEmpty()) {
            for (String command : questCommands) {
                CommandDispatcher.dispatch(player, command, placeholders);
            }
        }
        List<String> globalCommands = config.getTracking().getOnTrack();

        if (!globalCommands.isEmpty()) {
            for (String command : globalCommands) {
                CommandDispatcher.dispatch(player, command, placeholders);
            }
        }
    }

    public void executeUntrackCommands() {
        Player player = data.profile().getPlayer();
        List<Placeholder<?>> placeholders = getPlaceholders();
        Config config = AuroraQuests.getInstance().getConfigManager().getConfig();
        List<String> questCommands = definition.getOnUntrack();

        if (questCommands != null && !questCommands.isEmpty()) {
            for (String command : questCommands) {
                CommandDispatcher.dispatch(player, command, placeholders);
            }
        }
        List<String> globalCommands = config.getTracking().getOnUntrack();

        if (!globalCommands.isEmpty()) {
            for (var command : globalCommands) {
                CommandDispatcher.dispatch(player, command, placeholders);
            }
        }
    }

    public int getCurrentObjectiveIndex() {
        for (int i = 0; i < objectives.size(); i++) {
            if (!objectives.get(i).isCompleted()) {
                return i;
            }
        }
        return objectives.size() - 1;
    }

    public boolean isObjectiveLocked(int index) {
        if (!definition.isLinearObjectives()) {
          return false;
        }
        for (int i = 0; i < index; i++) {
            if (!objectives.get(i).isCompleted()) {
                return true;
            }
        }
        return false;
    }

    private void reward() {
        var gConfig = AuroraQuests.getInstance().getConfigManager().getConfig();

        List<Placeholder<?>> placeholders = getPlaceholders();
        var player = data.profile().getPlayer();
        var rewards = definition.getRewards();

        //check if this quest has its own complete message
        if (definition.getQuestCompleteMessage() != null) {
            //separate check - we do NOT want to show the global quest complete message if the quest overrides the enable state
            if (definition.getQuestCompleteMessage().getEnabled()) {
                var lines = definition.getQuestCompleteMessage().getMessage();
                var text = RewardUtil.fillRewardMessage(player, gConfig.getDisplayComponents().get("rewards"), lines, placeholders, rewards.values());
                var delay = definition.getQuestCompleteMessage().getDelay();
                if (delay > 0) {
                    player.getScheduler().runDelayed(AuroraQuests.getInstance(), (task) -> player.sendMessage(text), null, delay);
                } else {
                    player.sendMessage(text);
                }
            }
        } else if (gConfig.getQuestCompleteMessage().getEnabled()) {
            var lines = gConfig.getQuestCompleteMessage().getMessage();
            var text = RewardUtil.fillRewardMessage(player, gConfig.getDisplayComponents().get("rewards"), lines, placeholders, rewards.values());
            player.sendMessage(text);
        }

        //same check, now for the quest complete sound
        if (definition.getQuestCompleteSound() != null) {
            //separate check - we do NOT want to play the global quest complete sound if the quest overrides the enable state
            if (definition.getQuestCompleteSound().getEnabled()) {
                var sound = definition.getQuestCompleteSound();
                var delay = definition.getQuestCompleteSound().getDelay();
                if (delay > 0) {
                    player.getScheduler().runDelayed(AuroraQuests.getInstance(), (task) ->
                            SoundUtil.playSound(player, sound.getSound(), sound.getVolume(), sound.getPitch()), null, delay);
                } else {
                    SoundUtil.playSound(player, sound.getSound(), sound.getVolume(), sound.getPitch());
                }
            }
        } else if (gConfig.getQuestCompleteSound().getEnabled()) {
            var sound = gConfig.getQuestCompleteSound();
            SoundUtil.playSound(player, sound.getSound(), sound.getVolume(), sound.getPitch());
        }

        RewardExecutor.execute(rewards.values().stream().toList(), player, 1, placeholders);
    }
}
