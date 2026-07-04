package gg.auroramc.quests.api.event.objective;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player opens a named menu (currently DeluxeMenus). {@code menuName} is the
 * menu id, matched against the objective's configured {@code types}.
 */
@Getter
public class PlayerOpenMenuEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    private final String menuName;

    public PlayerOpenMenuEvent(@NotNull Player who, String menuName) {
        super(who, !Bukkit.isPrimaryThread());
        this.menuName = menuName;
    }
}
