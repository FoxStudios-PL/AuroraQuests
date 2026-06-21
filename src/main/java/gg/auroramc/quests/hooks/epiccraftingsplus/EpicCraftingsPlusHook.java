package gg.auroramc.quests.hooks.epiccraftingsplus;

import ecp.ajneb97.api.EpicCraftingsCraftEvent;
import ecp.ajneb97.model.Crafting;
import gg.auroramc.quests.AuroraQuests;
import gg.auroramc.quests.api.event.objective.PlayerEpicCraftEvent;
import gg.auroramc.quests.hooks.Hook;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class EpicCraftingsPlusHook implements Hook, Listener {

    @Override
    public void hook(AuroraQuests plugin) {
        AuroraQuests.logger().info("Hooked into EpicCraftingsPlus for EPICCRAFTINGS_CRAFT objective.");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEpicCraft(EpicCraftingsCraftEvent event) {
        Crafting crafting = event.getCrafting();
        if (crafting == null) return;

        var item = crafting.getItem();
        int amount = item != null ? item.getAmount() : 1;

        Bukkit.getPluginManager().callEvent(new PlayerEpicCraftEvent(
                event.getPlayer(), crafting.getName(), crafting.getCategory(), amount));
    }
}
