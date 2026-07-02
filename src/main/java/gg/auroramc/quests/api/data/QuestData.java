package gg.auroramc.quests.api.data;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import gg.auroramc.aurora.api.user.UserDataHolder;
import gg.auroramc.aurora.api.util.NamespacedId;
import gg.auroramc.quests.api.quest.QuestDefinition;
import gg.auroramc.quests.api.questpool.Pool;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

public class QuestData extends UserDataHolder {
    private final Map<String, PoolRollData> rolledQuests = Maps.newConcurrentMap();
    private final Map<String, Map<String, Map<String, Double>>> progression = Maps.newConcurrentMap();
    private final Map<String, Set<String>> completedQuests = Maps.newConcurrentMap();
    private final Map<String, Long> completedCount = Maps.newConcurrentMap();
    private final Map<String, Set<String>> questUnlocks = Maps.newConcurrentMap();
    private final Set<String> poolUnlocks = Sets.newConcurrentHashSet();
    // Ordered tracking queue. Only the head is "visible" (placeholders, scoreboard, on-track commands).
    private final List<TrackedQuest> trackedQuests = new CopyOnWriteArrayList<>();

    public record TrackedQuest(String poolId, String questId) {}

    public PoolRollData getPoolRollData(String poolId) {
        return rolledQuests.get(poolId);
    }

    public void unlockPool(String poolId) {
        poolUnlocks.add(poolId);
        dirty.set(true);
    }

    public boolean isPoolUnlocked(String poolId) {
        return poolUnlocks.contains(poolId);
    }

    public List<TrackedQuest> getTrackedQuests() {
        return List.copyOf(trackedQuests);
    }

    public boolean isTracking(String poolId, String questId) {
        return trackedQuests.contains(new TrackedQuest(poolId, questId));
    }

    @Nullable
    public TrackedQuest getHeadTrackedQuest() {
        return trackedQuests.isEmpty() ? null : trackedQuests.get(0);
    }

    public boolean isHeadTrackedQuest(String poolId, String questId) {
        TrackedQuest head = getHeadTrackedQuest();
        return head != null && head.poolId().equals(poolId) && head.questId().equals(questId);
    }

    public int getTrackedCount() {
        return trackedQuests.size();
    }

    /**
     * Appends a quest to the tracking queue if it is not already present.
     *
     * @return true if it was added, false if it was already in the queue
     */
    public boolean addTrackedQuest(String poolId, String questId) {
        TrackedQuest entry = new TrackedQuest(poolId, questId);
        if (trackedQuests.contains(entry)) return false;
        trackedQuests.add(entry);
        dirty.set(true);
        return true;
    }

    /**
     * Removes a quest from the tracking queue.
     *
     * @return true if it was present and removed
     */
    public boolean removeTrackedQuest(String poolId, String questId) {
        boolean removed = trackedQuests.remove(new TrackedQuest(poolId, questId));
        if (removed) dirty.set(true);
        return removed;
    }

    public void removeTrackedQuestsByPool(String poolId) {
        if (trackedQuests.removeIf(t -> t.poolId().equals(poolId))) {
            dirty.set(true);
        }
    }

    public boolean hasTrackedQuest() {
        return !trackedQuests.isEmpty();
    }

    // Legacy single-tracked accessors kept for backward compatibility: they expose the head of the queue,
    // so existing placeholders and the scoreboard automatically show only the first tracked quest.
    @Nullable
    public String getTrackedPoolId() {
        TrackedQuest head = getHeadTrackedQuest();
        return head == null ? null : head.poolId();
    }

    @Nullable
    public String getTrackedQuestId() {
        TrackedQuest head = getHeadTrackedQuest();
        return head == null ? null : head.questId();
    }

    public void setRolledQuests(String poolId, List<String> quests) {
        rolledQuests.put(poolId, new PoolRollData(System.currentTimeMillis(), quests));
        completedQuests.computeIfAbsent(poolId, k -> Sets.newConcurrentHashSet()).clear();
        progression.computeIfAbsent(poolId, k -> Maps.newConcurrentMap()).clear();
        dirty.set(true);
    }

    public void setQuestStartUnlock(String poolId, String questId) {
        questUnlocks.computeIfAbsent(poolId, k -> Sets.newConcurrentHashSet()).add(questId);
        dirty.set(true);
    }

    public boolean isQuestStartUnlocked(String poolId, String questId) {
        return hasCompletedQuest(poolId, questId) || questUnlocks.computeIfAbsent(poolId, k -> Sets.newConcurrentHashSet()).contains(questId);
    }

    public void removeQuestStartUnlock(String poolId, String questId) {
        questUnlocks.computeIfAbsent(poolId, k -> Sets.newConcurrentHashSet()).remove(questId);
        dirty.set(true);
    }

    public void progress(String poolId, String questId, String taskId, double count) {
        progression.computeIfAbsent(poolId, k -> Maps.newConcurrentMap())
                .computeIfAbsent(questId, k -> Maps.newConcurrentMap())
                .merge(taskId, count, (a, b) -> Math.max(a + b, 0));
        dirty.set(true);
    }

    public void setProgress(String poolId, String questId, String taskId, double count) {
        progression.computeIfAbsent(poolId, k -> Maps.newConcurrentMap())
                .computeIfAbsent(questId, k -> Maps.newConcurrentMap())
                .put(taskId, count);
        dirty.set(true);
    }

    public void completeQuest(String poolId, String questId) {
        completedQuests.computeIfAbsent(poolId, k -> new HashSet<>()).add(questId);
        dirty.set(true);
    }

    public void resetQuestProgress(String poolId, String questId) {
        var completesQuests = completedQuests.computeIfAbsent(poolId, k -> new HashSet<>());
        completesQuests.remove(questId);
        progression.computeIfAbsent(poolId, k -> Maps.newConcurrentMap()).remove(questId);
        dirty.set(true);
    }

    public void resetTaskProgress(String poolId, String questId, String taskId) {
        var completesQuests = completedQuests.computeIfAbsent(poolId, k -> new HashSet<>());
        completesQuests.remove(questId);
        var progression = this.progression.computeIfAbsent(poolId, k -> Maps.newConcurrentMap()).get(questId);
        if (progression != null) {
            progression.remove(taskId);
        }
        dirty.set(true);
    }

    public boolean hasCompletedQuest(String poolId, String questId) {
        return completedQuests.computeIfAbsent(poolId, k -> Sets.newConcurrentHashSet()).contains(questId);
    }

    public double getProgression(String poolId, String questId, String taskId) {
        return progression.computeIfAbsent(poolId, k -> Maps.newConcurrentMap())
                .computeIfAbsent(questId, k -> Maps.newConcurrentMap())
                .computeIfAbsent(taskId, k -> 0D);
    }

    public void clearPoolProgression(String poolId) {
        progression.remove(poolId);
    }

    public void incrementCompletedCount(String poolId) {
        completedCount.merge(poolId, 1L, Long::sum);
        dirty.set(true);
    }

    public long getCompletedCount(String poolId) {
        return completedCount.getOrDefault(poolId, 0L);
    }

    @Override
    public NamespacedId getId() {
        return NamespacedId.fromDefault("quests");
    }

    @Override
    public void serializeInto(ConfigurationSection data) {
        // Reset
        data.getKeys(false).forEach(key -> data.set(key, null));

        // Roll data
        var rolledSection = data.createSection("rolled");
        for (var entry : rolledQuests.entrySet()) {
            var poolSection = rolledSection.createSection(entry.getKey());
            poolSection.set("time", entry.getValue().timestamp());
            poolSection.set("quests", entry.getValue().quests());
        }

        // Progression data
        var progressionSection = data.createSection("progression");
        for (var poolEntry : progression.entrySet()) {
            var poolSection = progressionSection.createSection(poolEntry.getKey());
            for (var questEntry : poolEntry.getValue().entrySet()) {
                var questSection = poolSection.createSection(questEntry.getKey());
                for (var taskEntry : questEntry.getValue().entrySet()) {
                    if (taskEntry.getValue() > 0) {
                        questSection.set(taskEntry.getKey(), taskEntry.getValue());
                    }
                }
            }
        }

        // Quest unlocks
        for (var poolEntry : questUnlocks.entrySet()) {
            for (var questEntry : poolEntry.getValue()) {
                data.set("progression." + poolEntry.getKey() + "." + questEntry + ".unlocked", true);
            }
        }

        // Pool unlocks
        data.set("pool_unlocks", poolUnlocks.stream().toList());

        // Completed quests
        for (var poolEntry : completedQuests.entrySet()) {
            for (var questEntry : poolEntry.getValue()) {
                data.set("progression." + poolEntry.getKey() + "." + questEntry, true);
            }
        }

        // Completed count
        var completedCountSection = data.createSection("completed_count");
        for (var entry : completedCount.entrySet()) {
            completedCountSection.set(entry.getKey(), entry.getValue());
        }

        // Tracked quest queue (ordered; head is the visible one)
        if (!trackedQuests.isEmpty()) {
            List<Map<String, String>> queue = new ArrayList<>(trackedQuests.size());
            for (var tracked : trackedQuests) {
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("pool", tracked.poolId());
                entry.put("quest", tracked.questId());
                queue.add(entry);
            }
            data.set("tracked.queue", queue);
        }
    }

    @Override
    public void initFrom(@Nullable ConfigurationSection data) {
        if (data == null) return;
        var rolledSection = data.getConfigurationSection("rolled");
        if (rolledSection != null) {
            for (var key : rolledSection.getKeys(false)) {
                var poolSection = rolledSection.getConfigurationSection(key);
                var quests = poolSection.getStringList("quests");
                rolledQuests.put(key, new PoolRollData(poolSection.getLong("time"), quests));
            }
        }

        var progressionSection = data.getConfigurationSection("progression");
        if (progressionSection != null) {
            for (var poolKey : progressionSection.getKeys(false)) {
                var poolSection = progressionSection.getConfigurationSection(poolKey);
                for (var questKey : poolSection.getKeys(false)) {
                    if (poolSection.isBoolean(questKey)) {
                        completedQuests.computeIfAbsent(poolKey, k -> Sets.newConcurrentHashSet()).add(questKey);
                        continue;
                    }
                    var questSection = poolSection.getConfigurationSection(questKey);
                    for (var taskKey : questSection.getKeys(false)) {
                        if (taskKey.equals("unlocked")) {
                            questUnlocks.computeIfAbsent(poolKey, k -> Sets.newConcurrentHashSet()).add(questKey);
                            continue;
                        }
                        var count = questSection.getDouble(taskKey, 0);
                        progression.computeIfAbsent(poolKey, k -> Maps.newConcurrentMap())
                                .computeIfAbsent(questKey, k -> Maps.newConcurrentMap())
                                .put(taskKey, count);
                    }
                }
            }
        }

        var completedCountSection = data.getConfigurationSection("completed_count");
        if (completedCountSection != null) {
            for (var key : completedCountSection.getKeys(false)) {
                completedCount.put(key, completedCountSection.getLong(key));
            }
        }

        poolUnlocks.addAll(data.getStringList("pool_unlocks"));

        var queue = data.getMapList("tracked.queue");

        if (!queue.isEmpty()) {
            for (var entry : queue) {
                Object poolId = entry.get("pool");
                Object questId = entry.get("quest");
                if (poolId != null && questId != null) {
                    addTrackedQuest(poolId.toString(), questId.toString());
                }
            }
        } else {
            // Backward compatibility: load the old single tracked quest format as a one-element queue
            ConfigurationSection trackedSection = data.getConfigurationSection("tracked");
            if (trackedSection != null) {
                String poolId = trackedSection.getString("pool");
                String questId = trackedSection.getString("quest");
                if (poolId != null && questId != null) {
                    addTrackedQuest(poolId, questId);
                }
            }
        }
    }

    public void purgeInvalidData(Collection<Pool> pools) {
        var poolIds = pools.stream().map(Pool::getId).collect(Collectors.toSet());

        completedCount.keySet().removeIf(poolId -> !poolIds.contains(poolId));
        progression.keySet().removeIf(poolId -> !poolIds.contains(poolId));
        questUnlocks.keySet().removeIf(poolId -> !poolIds.contains(poolId));
        rolledQuests.keySet().removeIf(poolId -> !poolIds.contains(poolId));
        completedQuests.keySet().removeIf(poolId -> !poolIds.contains(poolId));
        poolUnlocks.removeIf(poolId -> !poolIds.contains(poolId));

        for (var pool : pools) {
            var questIds = pool.getDefinition().getQuests().values().stream().map(QuestDefinition::getId).collect(Collectors.toSet());

            if (progression.containsKey(pool.getId())) {
                progression.get(pool.getId()).keySet().removeIf(id -> !questIds.contains(id));
            }

            if (questUnlocks.containsKey(pool.getId())) {
                questUnlocks.get(pool.getId()).removeIf(id -> !questIds.contains(id));
            }

            if (completedQuests.containsKey(pool.getId())) {
                completedQuests.get(pool.getId()).removeIf(id -> !questIds.contains(id));
            }
        }
        trackedQuests.removeIf(tracked -> {
            if (!poolIds.contains(tracked.poolId())) return true;
            Pool trackedPool = pools.stream()
                    .filter(p -> p.getId().equals(tracked.poolId()))
                    .findFirst().orElse(null);
            return trackedPool != null && !trackedPool.getDefinition().getQuests().containsKey(tracked.questId());
        });

        dirty.set(true);
    }
}
