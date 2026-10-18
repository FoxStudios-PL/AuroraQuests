package gg.auroramc.quests.api.quest;

import gg.auroramc.quests.api.objective.ObjectiveDefinition;
import gg.auroramc.quests.util.ItemMatcher;
import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * Parsed {@code item-recovery:} entry of a quest: while the window
 * [{@code after} completed, {@code until} not completed] is open, repeating the
 * {@code trigger} action without holding a matching item runs {@code onRecover}.
 */
@Getter
@Builder
public class ItemRecoveryDefinition {
    private final String id;
    /** Task opening the window, {@code null} = open as soon as the quest is unlocked. */
    @Nullable
    private final String after;
    /** Task closing the window (excluded), {@code null} = open until the quest is completed. */
    @Nullable
    private final String until;
    /** Same matcher as DELIVER_ITEM's {@code args.item}, so a recovered item is always accepted. */
    private final ItemMatcher item;
    /** Synthetic task definition: listened to like a regular task, never records progress. */
    private final ObjectiveDefinition trigger;
    private final double chance;
    private final long cooldownMillis;
    private final List<String> onRecover;

    /**
     * Whether the recovery window is open: the quest is unlocked and not completed,
     * {@code after} (if any) is completed and {@code until} (if any) is not. Only task
     * completion counts, so it behaves the same with or without linear-objectives.
     */
    public boolean isWindowOpen(boolean questActive, Predicate<String> isTaskCompleted) {
        if (!questActive) return false;
        if (after != null && !isTaskCompleted.test(after)) return false;
        return until == null || !isTaskCompleted.test(until);
    }

    /**
     * Whether a matching trigger action gives the item back. Pure decision, kept apart
     * from the server state so the rules can be unit tested.
     *
     * @param windowOpen   result of {@link #isWindowOpen}
     * @param heldAmount   matching items in the player's inventory
     * @param roll         uniform random number in [0, 1)
     * @param now          current time, millis
     * @param lastRecovery time of the last recovery (or of the original grant), millis; 0 = never
     */
    public boolean shouldRecover(boolean windowOpen, int heldAmount, double roll, long now, long lastRecovery) {
        if (!windowOpen) return false;
        if (heldAmount > 0) return false;
        if (lastRecovery > 0 && now - lastRecovery < cooldownMillis) return false;
        return roll < chance;
    }
}
