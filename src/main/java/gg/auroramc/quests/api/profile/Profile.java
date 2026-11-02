package gg.auroramc.quests.api.profile;

import gg.auroramc.quests.util.ChatUtil;
import gg.auroramc.aurora.api.message.Placeholder;
import gg.auroramc.aurora.api.user.AuroraUser;
import gg.auroramc.quests.AuroraQuests;
import gg.auroramc.quests.api.data.QuestData;
import gg.auroramc.quests.api.questpool.QuestPool;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Getter
public class Profile {
    private final AuroraUser user;
    // The Player object of the session this profile was created for. Read through getPlayer(),
    // never directly: see there.
    @Getter(lombok.AccessLevel.NONE)
    private final Player player;
    @Getter(lombok.AccessLevel.NONE)
    private volatile boolean staleWarned = false;

    private final Map<String, QuestPool> data = new LinkedHashMap<>();

    public Profile(AuroraUser user) {
        this.user = user;
        this.player = user.getPlayer();
        reload(false);
    }

    public void reload(boolean destroy) {
        for (var pool : data.values()) {
            if (destroy) {
                pool.destroy();
            } else {
                pool.dispose();
            }
        }

        data.clear();

        Set<QuestPool> rolledPools = new HashSet<>();

        for (var pool : AuroraQuests.getInstance().getPoolManager().getPools()) {
            var questPool = new QuestPool(this, pool);
            data.put(pool.getId(), questPool);
            questPool.unlock(false);

            if (questPool.rollIfNecessary(false)) {
                rolledPools.add(questPool);
            }

            questPool.startQuests();
        }

        if (!rolledPools.isEmpty() && getPlayer().hasPlayedBefore()) {
            var placeholder = Placeholder.of("{pool}", String.join(", ", rolledPools.stream().map(QuestPool::getName).toList()));
            var msg = AuroraQuests.getInstance().getConfigManager().getMessageConfig(getPlayer()).getReRolledTarget();
            msg = Placeholder.execute(msg, placeholder);
            ChatUtil.sendMessage(getPlayer(), AuroraQuests.getInstance().getLocalizationProvider().fillVariables(getPlayer(), msg, placeholder));
        }

        if (AuroraQuests.getInstance().getConfigManager().getConfig().getPurgeInvalidDataOnLogin()) {
            getData().purgeInvalidData(AuroraQuests.getInstance().getPoolManager().getPools());
        }

        Bukkit.getAsyncScheduler().runDelayed(AuroraQuests.getInstance(),
                (t) -> AuroraQuests.getInstance().getPoolManager().getRewardAutoCorrector().correctRewards(getPlayer()), 50, TimeUnit.MILLISECONDS);
    }

    /**
     * The player's current {@link Player} object, looked up by UUID.
     * <p>
     * Bukkit creates a new Player object on every login, and objectives match events with
     * {@code event.getPlayer() == profile.getPlayer()} and schedule their tasks on this
     * player's entity scheduler. A profile still holding the Player of a previous session
     * would make every event mismatch and every scheduled task (login checks) silently do
     * nothing after a reconnection, so the live object wins whenever the player is online.
     * Falls back to the stored object when offline (quit handling, async tasks finishing).
     */
    public Player getPlayer() {
        var live = Bukkit.getPlayer(user.getUniqueId());
        if (live == null) return player;
        if (live != player && !staleWarned) {
            staleWarned = true;
            AuroraQuests.logger().warning("Quest profile of " + live.getName() + " was holding the Player object of "
                    + "a previous session; using the current one. Please report this with the timing of the reconnection.");
        }
        return live;
    }

    public Collection<QuestPool> getQuestPools() {
        return data.values();
    }

    public QuestPool getQuestPool(String id) {
        return data.get(id);
    }

    public TaskDataWrapper toTaskDataWrapper(String poolId, String questId, String taskId) {
        return new TaskDataWrapper(this, poolId, questId, taskId);
    }

    public QuestDataWrapper toQuestDataWrapper(String poolId, String questId) {
        return new QuestDataWrapper(this, poolId, questId);
    }

    public QuestData getData() {
        return user.getData(QuestData.class);
    }

    public void destroy() {
        for (var pool : data.values()) {
            pool.destroy();
        }
        data.clear();
    }

    @Override
    public int hashCode() {
        return user.getUniqueId().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Profile profile) {
            return user == profile.user;
        }
        return false;
    }


    public record TaskDataWrapper(Profile profile, String poolId, String questId, String taskId) {
        public boolean isCompleted(double target) {
            var data = profile.getData();
            return data.hasCompletedQuest(poolId, questId) || data.getProgression(poolId, questId, taskId) >= target;
        }

        public double getProgress() {
            return profile.getData().getProgression(poolId, questId, taskId);
        }

        public void progress(double amount) {
            profile.getData().progress(poolId, questId, taskId, amount);
        }

        public void setProgress(double amount) {
            profile.getData().setProgress(poolId, questId, taskId, amount);
        }

        public void resetProgress() {
            profile.getData().resetTaskProgress(poolId, questId, taskId);
        }
    }


    public record QuestDataWrapper(Profile profile, String poolId, String questId) {
        public void complete() {
            var data = profile.getData();
            data.completeQuest(poolId, questId);
        }

        public void reset() {
            profile.getData().resetQuestProgress(poolId, questId);
        }

        public boolean isCompleted() {
            return profile.getData().hasCompletedQuest(poolId, questId);
        }

        public boolean isUnlocked() {
            return profile.getData().isQuestStartUnlocked(poolId, questId);
        }

        public void unlock() {
            profile.getData().setQuestStartUnlock(poolId, questId);
        }

        public boolean hasCompletedQuest(String poolId, String questId) {
            return profile.getData().hasCompletedQuest(poolId, questId);
        }

        public TaskDataWrapper toTaskDataWrapper(String taskId) {
            return new TaskDataWrapper(profile, poolId, questId, taskId);
        }
    }
}
