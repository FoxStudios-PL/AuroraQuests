package gg.auroramc.quests.api.event;

import gg.auroramc.quests.api.questpool.QuestPool;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired right after a timed-random pool rolled a new set of quests for a player
 * (scheduled reroll, reroll-on-completion or the admin command). May fire off the
 * main thread (the periodic unlock task runs async), hence the dynamic async flag.
 */
@Getter
public class QuestPoolReRolledEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    private final Player player;
    private final QuestPool pool;

    public QuestPoolReRolledEvent(Player player, QuestPool pool) {
        super(!Bukkit.isPrimaryThread());
        this.player = player;
        this.pool = pool;
    }
}
