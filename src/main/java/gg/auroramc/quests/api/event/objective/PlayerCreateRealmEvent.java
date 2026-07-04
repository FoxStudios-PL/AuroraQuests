package gg.auroramc.quests.api.event.objective;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player creates a realm (currently LuxRealms).
 */
public class PlayerCreateRealmEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public PlayerCreateRealmEvent(@NotNull Player who) {
        super(who, !Bukkit.isPrimaryThread());
    }
}
