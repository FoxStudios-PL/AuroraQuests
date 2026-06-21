package gg.auroramc.quests.api.event.objective;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

@Getter
public class PlayerEpicCraftEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    private final String craftId;
    private final String category;
    private final int amount;

    public PlayerEpicCraftEvent(@NotNull Player who, String craftId, String category, int amount) {
        super(who, !Bukkit.isPrimaryThread());
        this.craftId = craftId;
        this.category = category;
        this.amount = amount;
    }
}
