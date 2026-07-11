package gg.auroramc.quests.api.quest;

import gg.auroramc.quests.api.data.QuestData;
import gg.auroramc.quests.api.profile.Profile;
import gg.auroramc.quests.api.questpool.QuestPool;

/**
 * Centralizes quest tracking-queue operations so the command, the menu and the unlock flow share the
 * exact same behaviour.
 * <p>
 * The tracking model is an ordered queue: only the head is "visible" (placeholders, scoreboard,
 * {@code on-track} commands). Quests behind the head are queued silently and surface one by one as the
 * head is completed or untracked.
 */
public final class QuestTracker {

    private QuestTracker() {}

    public enum ToggleResult {
        /** The quest was tracked and became the head (queue was empty). */
        TRACKED_HEAD,
        /** The quest was added behind the current head. */
        TRACKED_QUEUED,
        /** The quest was removed from the queue. */
        UNTRACKED,
        /** The queue already holds the configured maximum number of quests. */
        QUEUE_FULL
    }

    /**
     * Toggles tracking for a quest: removes it if already tracked, otherwise enqueues it (respecting the
     * configured maximum). Only head transitions trigger {@code on-track}/{@code on-untrack} commands.
     *
     * @param max maximum number of tracked quests, or {@code <= 0} for unlimited
     */
    public static ToggleResult toggle(Profile profile, QuestPool pool, Quest quest, QuestData data, int max) {
        if (untrack(profile, pool, quest, data)) {
            return ToggleResult.UNTRACKED;
        }

        String poolId = pool.getId();
        String questId = quest.getId();

        if (max > 0 && data.getTrackedCount() >= max) {
            return ToggleResult.QUEUE_FULL;
        }

        boolean wasEmpty = !data.hasTrackedQuest();
        data.addTrackedQuest(poolId, questId);
        if (wasEmpty) {
            quest.executeTrackCommands();
            return ToggleResult.TRACKED_HEAD;
        }
        return ToggleResult.TRACKED_QUEUED;
    }

    /**
     * Removes a quest from the tracking queue with the same head-transition behaviour as a manual
     * untrack: {@code on-untrack} commands run if it was the head, and the next queued quest (if any)
     * takes over visibility. No-op when the quest is not tracked.
     *
     * @return true if the quest was tracked and got removed
     */
    public static boolean untrack(Profile profile, QuestPool pool, Quest quest, QuestData data) {
        String poolId = pool.getId();
        String questId = quest.getId();

        if (!data.isTracking(poolId, questId)) return false;

        boolean wasHead = data.isHeadTrackedQuest(poolId, questId);
        if (wasHead) quest.executeUntrackCommands();
        data.removeTrackedQuest(poolId, questId);
        if (wasHead) runHeadTrackCommands(profile, data);
        return true;
    }

    /**
     * Enqueues a freshly unlocked quest. Adds it behind the current head if any, or makes it the head when
     * the queue is empty. No-op if it is already tracked or the queue is full.
     *
     * @param max maximum number of tracked quests, or {@code <= 0} for unlimited
     * @return true if the quest was enqueued
     */
    public static boolean enqueueFromUnlock(Profile profile, QuestPool pool, Quest quest, QuestData data, int max) {
        String poolId = pool.getId();
        String questId = quest.getId();

        if (data.isTracking(poolId, questId)) return false;
        if (max > 0 && data.getTrackedCount() >= max) return false;

        boolean wasEmpty = !data.hasTrackedQuest();
        boolean added = data.addTrackedQuest(poolId, questId);
        if (added && wasEmpty) {
            quest.executeTrackCommands();
        }
        return added;
    }

    /**
     * Runs the {@code on-track} commands of the quest currently at the head of the queue, if any.
     * Used to hand off visibility to the next quest after the previous head was removed/completed.
     */
    public static void runHeadTrackCommands(Profile profile, QuestData data) {
        QuestData.TrackedQuest head = data.getHeadTrackedQuest();
        if (head == null) return;

        QuestPool pool = profile.getQuestPool(head.poolId());
        if (pool == null) return;

        Quest quest = pool.getQuest(head.questId());
        if (quest != null) {
            quest.executeTrackCommands();
        }
    }
}
