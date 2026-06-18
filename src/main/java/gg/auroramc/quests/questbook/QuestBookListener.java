package gg.auroramc.quests.questbook;

import gg.auroramc.aurora.api.events.user.AuroraUserLoadedEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Player lifecycle / protection listener for the quest book.
 * <p>
 * Injection is driven by {@link AuroraUserLoadedEvent} (fired once the player's
 * Aurora data — including {@link QuestBookData} — is available), so we always
 * have the persisted state before placing the item.
 */
public class QuestBookListener implements Listener {
    private final QuestBookManager manager;

    public QuestBookListener(QuestBookManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUserLoaded(AuroraUserLoadedEvent event) {
        if (!manager.isActive()) return;
        var player = event.getUser().getPlayer();
        if (player == null || !player.isOnline()) return;
        manager.scheduleInject(player, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!manager.isActive()) return;
        manager.scheduleInject(event.getPlayer(), 5L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!manager.isActive()) return;
        if (manager.isQuestBook(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (!manager.isActive()) return;
        if (manager.isQuestBook(event.getOffHandItem()) || manager.isQuestBook(event.getMainHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        if (!manager.isActive()) return;
        // Never drop the book on death (also covers keepInventory=false).
        // Re-injection is handled by onRespawn.
        event.getDrops().removeIf(manager::isQuestBook);
    }
}
