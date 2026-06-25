package gg.auroramc.quests.api.event.objective;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player deals damage to an entity through a source that is not a plain
 * {@link org.bukkit.event.entity.EntityDamageByEntityEvent} with the player as the direct
 * damager — currently player-cast MythicMobs skill damage, bridged by the MythicMobs hook.
 * Lets {@code DEAL_DAMAGE} objectives count such damage when {@code count-skill-damage} is enabled.
 */
@Getter
public class PlayerDealDamageEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    private final Entity target;
    private final double damage;

    public PlayerDealDamageEvent(@NotNull Player who, Entity target, double damage) {
        super(who, !Bukkit.isPrimaryThread());
        this.target = target;
        this.damage = damage;
    }
}
