package gg.auroramc.quests.hooks.economyshopgui;

import gg.auroramc.aurora.api.AuroraAPI;
import gg.auroramc.quests.api.event.objective.PlayerEarnFromSellEvent;
import gg.auroramc.quests.api.event.objective.PlayerPurchaseItemEvent;
import gg.auroramc.quests.api.event.objective.PlayerSellItemEvent;
import gg.auroramc.quests.api.event.objective.PlayerSpendOnPurchaseEvent;
import gg.auroramc.quests.util.EnumCompat;
import me.gypopo.economyshopgui.api.events.PostTransactionEvent;
import me.gypopo.economyshopgui.util.Transaction;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Set;

public class EconomyShopGUIListener implements Listener {
    // Constants are resolved by name: EconomyShopGUI keeps reshuffling these enums between
    // versions, and a direct reference to one that no longer exists kills the whole plugin
    // with a NoSuchFieldError while this class is being initialized. NOT_ALL_ITEMS_ADDED was
    // dropped in EconomyShopGUI-API 1.8, it is kept here for older installations.
    private final Set<Transaction.Result> successResults = EnumCompat.setOf(Transaction.Result.class,
            "SUCCESS", "SUCCESS_COMMANDS_EXECUTED", "NOT_ALL_ITEMS_ADDED"
    );
    private final Set<Transaction.Type> buyTypes = EnumCompat.setOf(Transaction.Type.class,
            "BUY_SCREEN", "BUY_STACKS_SCREEN", "QUICK_BUY", "SHOPSTAND_BUY_SCREEN"
    );
    private final Set<Transaction.Type> sellTypes = EnumCompat.setOf(Transaction.Type.class,
            "SELL_GUI_SCREEN", "SELL_SCREEN", "SELL_ALL_SCREEN", "SELL_ALL_COMMAND", "QUICK_SELL",
            "AUTO_SELL_CHEST", "SHOPSTAND_SELL_SCREEN"
    );


    @EventHandler(priority = EventPriority.MONITOR)
    public void onTransaction(PostTransactionEvent e) {
        if (!successResults.contains(e.getTransactionResult())) {
            return;
        }

        var price = e.getPrice();

        if (sellTypes.contains(e.getTransactionType())) {
            Bukkit.getPluginManager().callEvent(new PlayerEarnFromSellEvent(e.getPlayer(), price));
        } else if (buyTypes.contains(e.getTransactionType())) {
            Bukkit.getPluginManager().callEvent(new PlayerSpendOnPurchaseEvent(e.getPlayer(), price));
        }

        if (sellTypes.contains(e.getTransactionType()) && !e.getItems().isEmpty()) {
            for (var entry : e.getItems().entrySet()) {
                var item = entry.getKey().getItemToGive();
                var amount = entry.getValue();

                if (item != null) {
                    var id = AuroraAPI.getItemManager().resolveId(item);
                    if (id != null) {
                        Bukkit.getPluginManager().callEvent(new PlayerSellItemEvent(e.getPlayer(), new PlayerSellItemEvent.TransactionItem(id, amount)));
                    }
                }
            }
        } else if (sellTypes.contains(e.getTransactionType())) {
            var item = e.getShopItem().getItemToGive();
            var amount = e.getAmount();
            if (item != null) {
                var id = AuroraAPI.getItemManager().resolveId(item);
                if (id != null) {
                    Bukkit.getPluginManager().callEvent(new PlayerSellItemEvent(e.getPlayer(), new PlayerSellItemEvent.TransactionItem(id, amount)));
                }
            }
        } else if (buyTypes.contains(e.getTransactionType())) {
            var item = e.getShopItem().getItemToGive();
            var amount = e.getAmount();
            if (item != null) {
                var id = AuroraAPI.getItemManager().resolveId(item);
                if (id != null) {
                    Bukkit.getPluginManager().callEvent(new PlayerPurchaseItemEvent(e.getPlayer(), new PlayerPurchaseItemEvent.TransactionItem(id, amount)));
                }
            }
        }
    }
}
