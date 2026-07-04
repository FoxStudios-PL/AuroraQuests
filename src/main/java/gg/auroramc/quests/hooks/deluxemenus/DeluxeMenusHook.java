package gg.auroramc.quests.hooks.deluxemenus;

import gg.auroramc.quests.AuroraQuests;
import gg.auroramc.quests.api.event.objective.PlayerOpenMenuEvent;
import gg.auroramc.quests.hooks.Hook;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.InventoryHolder;

import java.lang.reflect.Method;

/**
 * DeluxeMenus does not expose a public "menu opened" event, so we detect opens through Bukkit's
 * {@link InventoryOpenEvent}: DeluxeMenus inventories are backed by a
 * {@code com.extendedclip.deluxemenus.menu.MenuHolder}. The menu id is read from it reflectively
 * to avoid a compile-time dependency on the (heavily shaded, ~4MB) DeluxeMenus jar.
 */
public class DeluxeMenusHook implements Hook, Listener {

    private static final String MENU_HOLDER_CLASS = "com.extendedclip.deluxemenus.menu.MenuHolder";
    private Method getMenuName;

    @Override
    public void hook(AuroraQuests plugin) {
        try {
            getMenuName = Class.forName(MENU_HOLDER_CLASS).getMethod("getMenuName");
            AuroraQuests.logger().info("Hooked into DeluxeMenus for OPEN_MENU objective.");
        } catch (ReflectiveOperationException e) {
            AuroraQuests.logger().warning("DeluxeMenus present but MenuHolder#getMenuName not found; OPEN_MENU disabled: " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMenuOpen(InventoryOpenEvent event) {
        if (getMenuName == null) return;
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder == null || !holder.getClass().getName().equals(MENU_HOLDER_CLASS)) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        try {
            Object name = getMenuName.invoke(holder);
            if (name instanceof String menuName && !menuName.isEmpty()) {
                Bukkit.getPluginManager().callEvent(new PlayerOpenMenuEvent(player, menuName));
            }
        } catch (ReflectiveOperationException ignored) {
            // MenuHolder shape changed; skip rather than spam the log on every inventory open.
        }
    }
}
