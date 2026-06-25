package gg.auroramc.quests.api.event.objective;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player deals damage to an entity through a source that is not a plain
 * {@link org.bukkit.event.entity.EntityDamageByEntityEvent} with the player as the direct
 * damager — currently player-cast MythicMobs skill damage, bridged by the MythicMobs hook.
 * Lets {@code DEAL_DAMAGE} objectives count such damage when {@code count-skill-damage} is enabled.
 * <p>
 * Intentionally a plain {@link Event} (not a PlayerEvent) so the objective receives it and
 * matches the player by UUID — the bridged player reference may differ from the live one.
 */
@Getter
public class PlayerDealDamageEvent extends Event {
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
    private final Entity target;
    private final double damage;

    public PlayerDealDamageEvent(@NotNull Player player, Entity target, double damage) {
        super(!Bukkit.isPrimaryThread());
        this.player = player;
        this.target = target;
        this.damage = damage;
    }
}
