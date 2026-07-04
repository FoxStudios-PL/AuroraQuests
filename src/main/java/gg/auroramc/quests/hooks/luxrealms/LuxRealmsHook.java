package gg.auroramc.quests.hooks.luxrealms;

import com.aselstudios.realms.api.event.RealmCreationEvent;
import gg.auroramc.quests.AuroraQuests;
import gg.auroramc.quests.api.event.objective.PlayerCreateRealmEvent;
import gg.auroramc.quests.hooks.Hook;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class LuxRealmsHook implements Hook, Listener {

    @Override
    public void hook(AuroraQuests plugin) {
        AuroraQuests.logger().info("Hooked into LuxRealms for CREATE_REALM objective.");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRealmCreate(RealmCreationEvent event) {
        var realmPlayer = event.getPlayer();
        if (realmPlayer == null) return;
        Player player = realmPlayer.getBukkitPlayer();
        if (player == null) return;
        Bukkit.getPluginManager().callEvent(new PlayerCreateRealmEvent(player));
    }
}
