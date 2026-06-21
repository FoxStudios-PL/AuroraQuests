package gg.auroramc.quests.hooks.excellentshop;

import gg.auroramc.quests.api.event.objective.PlayerEarnFromSellEvent;
import gg.auroramc.quests.api.event.objective.PlayerSpendOnPurchaseEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import su.nightexpress.excellentshop.api.event.TransactionCompletedEvent;
import su.nightexpress.excellentshop.api.product.TradeType;

import java.util.Map;

public class TransactionListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTransaction(TransactionCompletedEvent event) {
        var transaction = event.getTransaction();
        if (!transaction.successful()) return;

        var player = transaction.player();
        if (player == null) return;

        boolean buy = transaction.type() == TradeType.BUY;

        // A transaction can involve several currencies; fire one event per currency.
        for (Map.Entry<String, Double> entry : transaction.worth().getBalanceMap().entrySet()) {
            String currency = entry.getKey();
            double amount = entry.getValue() == null ? 0D : entry.getValue();
            if (amount <= 0D) continue;

            if (buy) {
                Bukkit.getPluginManager().callEvent(new PlayerSpendOnPurchaseEvent(player, amount, currency));
            } else {
                Bukkit.getPluginManager().callEvent(new PlayerEarnFromSellEvent(player, amount, currency));
            }
        }
    }
}
