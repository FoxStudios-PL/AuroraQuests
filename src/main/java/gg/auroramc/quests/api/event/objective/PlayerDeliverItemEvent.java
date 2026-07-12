package gg.auroramc.quests.api.event.objective;

import gg.auroramc.quests.api.quest.Quest;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Fired by {@code /quests deliver} to ask the DELIVER_ITEM objectives of a quest to
 * check the player's inventory and take the required items. Only active (started, not
 * completed) objectives are subscribed, so locked quests and not-yet-reached linear
 * steps ignore it. Handlers report their outcome through {@link #markDelivered(String)}
 * and {@link #markMissing(String)} so the command can give feedback to the sender.
 */
@Getter
public class PlayerDeliverItemEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    private final Quest quest;
    /**
     * Task id this delivery targets, or null to target every DELIVER_ITEM task of the quest.
     */
    @Nullable
    private final String objectiveId;
    /**
     * Ids of the objectives that took their items (and completed) during this event.
     */
    private final List<String> delivered = new ArrayList<>();
    /**
     * Ids of the active objectives whose required items were missing; nothing was taken for these.
     */
    private final List<String> missing = new ArrayList<>();

    public PlayerDeliverItemEvent(@NotNull Player who, Quest quest, @Nullable String objectiveId) {
        super(who, !Bukkit.isPrimaryThread());
        this.quest = quest;
        this.objectiveId = objectiveId;
    }

    public void markDelivered(String objectiveId) {
        delivered.add(objectiveId);
    }

    public void markMissing(String objectiveId) {
        missing.add(objectiveId);
    }
}
