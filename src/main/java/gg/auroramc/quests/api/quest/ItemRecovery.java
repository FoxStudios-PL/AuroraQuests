package gg.auroramc.quests.api.quest;

import gg.auroramc.aurora.api.command.CommandDispatcher;
import gg.auroramc.aurora.api.message.Placeholder;
import gg.auroramc.quests.AuroraQuests;
import gg.auroramc.quests.api.factory.ObjectiveFactory;
import gg.auroramc.quests.api.objective.Objective;
import gg.auroramc.quests.api.objective.ObjectiveMeta;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Per-player runtime of an {@link ItemRecoveryDefinition}. The trigger is a regular
 * objective of the configured type running as a trigger (see
 * {@link Objective#asTrigger}), so it listens to exactly the same events and applies
 * exactly the same filters as a task of that type, without ever recording progress.
 */
public class ItemRecovery {
    @Getter
    private final ItemRecoveryDefinition definition;
    private final Quest quest;
    @Nullable
    private final Objective trigger;
    private volatile long lastRecovery = 0;

    public ItemRecovery(Quest quest, ItemRecoveryDefinition definition) {
        this.quest = quest;
        this.definition = definition;
        // Never stored: the trigger records no progress, the id only shows up in logs.
        this.trigger = ObjectiveFactory.createObjective(quest, definition.getTrigger());
        if (trigger != null) {
            trigger.asTrigger(this::onAction);
        }
    }

    /** Starts listening, unless the window can no longer open for this player. */
    public void start() {
        if (trigger == null) return;
        if (quest.isCompleted() || isTaskCompleted(definition.getUntil())) return;
        trigger.start();
    }

    /**
     * Called when the {@code after} task completes: the task's own rewards are handing the
     * item out right now (possibly a few ticks later through the command dispatcher), so
     * the cooldown starts here to never give a second one on the same action.
     */
    public void onWindowOpened() {
        lastRecovery = System.currentTimeMillis();
    }

    public void dispose() {
        if (trigger != null) trigger.dispose();
    }

    public void destroy() {
        if (trigger != null) trigger.destroy();
    }

    private void onAction(ObjectiveMeta meta) {
        var player = meta.getPlayer();
        if (player == null || !player.isOnline()) return;
        // Inventory and commands belong to the player's region (Folia); trigger events
        // already fire there, anything else is handed over.
        if (Bukkit.isOwnedByCurrentRegion(player)) {
            tryRecover();
        } else {
            player.getScheduler().run(AuroraQuests.getInstance(), (t) -> tryRecover(), null);
        }
    }

    private void tryRecover() {
        var profile = quest.getData().profile();
        var player = profile.getPlayer();
        if (player == null || !player.isOnline()) return;
        // Profile replaced (reload / rejoin) or data not loaded: this instance is stale.
        if (AuroraQuests.getInstance().getProfileManager().getProfile(player) != profile) return;
        if (!profile.getUser().isLoaded()) return;

        var now = System.currentTimeMillis();
        boolean windowOpen = definition.isWindowOpen(quest.isUnlocked() && !quest.isCompleted(), this::isTaskCompleted);
        // Inventory scan only when everything else already allows it.
        int held = windowOpen && now - lastRecovery >= definition.getCooldownMillis()
                ? definition.getItem().count(player) : 0;
        if (!definition.shouldRecover(windowOpen, held, ThreadLocalRandom.current().nextDouble(), now, lastRecovery)) {
            return;
        }

        lastRecovery = now;
        AuroraQuests.logger().debug("Item recovery " + definition.getId() + " of quest " + quest.getId()
                + " triggered for " + player.getName());

        List<Placeholder<?>> pl = List.of(
                Placeholder.of("{player}", player.getName()),
                Placeholder.of("{quest_id}", quest.getId()),
                Placeholder.of("{pool_id}", quest.getPool().getId()),
                Placeholder.of("{recovery_id}", definition.getId())
        );
        for (var command : definition.getOnRecover()) {
            CommandDispatcher.dispatch(player, command, pl);
        }
    }

    private boolean isTaskCompleted(@Nullable String taskId) {
        if (taskId == null) return false;
        for (var objective : quest.getObjectives()) {
            if (objective.getId().equals(taskId)) return objective.isCompleted();
        }
        return false;
    }
}
