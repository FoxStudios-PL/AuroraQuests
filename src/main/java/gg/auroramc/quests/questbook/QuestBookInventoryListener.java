package gg.auroramc.quests.questbook;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Protects the quest book item from being moved, duplicated or removed, and
 * turns a click on it into the configured action. Faithful port of the
 * FoxQuestBook protection logic, adapted to {@link QuestBookManager}.
 */
public class QuestBookInventoryListener implements Listener {
    private final QuestBookManager manager;

    public QuestBookInventoryListener(QuestBookManager manager) {
        this.manager = manager;
    }

    @SuppressWarnings("deprecation") // HOTBAR_MOVE_AND_READD kept for full hotbar-swap protection
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!manager.isActive()) return;
        HumanEntity human = event.getWhoClicked();
        if (!(human instanceof Player player)) return;

        int questBookSlot = manager.getQuestBookSlot();
        ItemStack currentItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        // Clicking the book itself.
        if (manager.isQuestBook(currentItem)) {
            event.setCancelled(true);
            if (event.getClickedInventory() instanceof PlayerInventory && event.getSlot() == questBookSlot) {
                manager.handleClick(player);
            }
            return;
        }

        // Never let a book sit on the cursor.
        if (manager.isQuestBook(cursorItem)) {
            event.setCancelled(true);
            return;
        }

        InventoryAction action = event.getAction();

        // Number-key / hotbar swaps that would move the book or target its slot.
        if ((action == InventoryAction.HOTBAR_SWAP || action == InventoryAction.HOTBAR_MOVE_AND_READD)
                && event.getClickedInventory() instanceof PlayerInventory) {
            int hotbarButton = event.getHotbarButton();
            if (hotbarButton >= 0 && manager.isQuestBook(player.getInventory().getItem(hotbarButton))) {
                event.setCancelled(true);
                return;
            }
            if (event.getSlot() == questBookSlot) {
                event.setCancelled(true);
                return;
            }
        }

        // Shift-clicking items from a top inventory into the player inventory:
        // make sure the book is still present afterwards.
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && event.getClickedInventory() != null
                && !(event.getClickedInventory() instanceof PlayerInventory)) {
            manager.scheduleEnsure(player, 1L);
        }

        // Defensive: any interaction targeting the book's slot.
        if (event.getClickedInventory() instanceof PlayerInventory && event.getSlot() == questBookSlot
                && manager.isQuestBook(player.getInventory().getItem(questBookSlot))) {
            event.setCancelled(true);
            manager.handleClick(player);
            return;
        }

        if (event.getHotbarButton() >= 0 && event.getHotbarButton() == questBookSlot) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!manager.isActive()) return;
        if (!(event.getWhoClicked() instanceof Player)) return;

        if (manager.isQuestBook(event.getOldCursor())) {
            event.setCancelled(true);
            return;
        }

        int questBookSlot = manager.getQuestBookSlot();
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            int localSlot = rawSlot - topSize;
            if (localSlot == questBookSlot) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!manager.isActive()) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!manager.isLoaded(player)) return;
        // Slight delay so any inventory-restoring plugin (e.g. DeluxeMenus) runs first.
        manager.scheduleEnsure(player, 3L);
    }
}
