package gg.auroramc.quests.api.event.objective;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player places a custom furniture (currently Nexo). Unlike
 * {@link PlayerPlaceCustomBlockEvent}, it does not require a backing block, so it also
 * covers display-entity furniture that has no barrier hitbox.
 */
@Getter
public class PlayerPlaceFurnitureEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    private final String furnitureId;

    public PlayerPlaceFurnitureEvent(@NotNull Player who, String furnitureId) {
        super(who, !Bukkit.isPrimaryThread());
        this.furnitureId = furnitureId;
    }
}
