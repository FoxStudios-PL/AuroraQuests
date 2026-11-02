package gg.auroramc.quests.api.profile;

import gg.auroramc.aurora.api.user.AuroraUser;
import gg.auroramc.quests.AuroraQuests;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ProfileManager {
    private final Map<UUID, Profile> profileMap = new ConcurrentHashMap<>();

    public void createProfile(AuroraUser user) {
        // A user can be (re)loaded while a profile for the same UUID still exists (loaded event
        // fired again for an online player, reconnection racing the quit). Destroy it first:
        // otherwise its objectives stay subscribed and, now that Profile.getPlayer() resolves
        // the live player, would progress a second time on every event.
        var previous = profileMap.remove(user.getUniqueId());
        if (previous != null) {
            AuroraQuests.logger().debug("Replacing the quest profile of " + user.getUniqueId() + " (user loaded again)");
            previous.destroy();
        }
        profileMap.put(user.getUniqueId(), new Profile(user));
        AuroraQuests.logger().debug("Created quest profile for " + user.getUniqueId());
    }

    public Profile getProfile(Player player) {
        return profileMap.get(player.getUniqueId());
    }

    public void destroyProfile(UUID uuid) {
        var profile = profileMap.remove(uuid);
        if (profile != null) {
            profile.destroy();
        }
    }

    public Collection<Profile> getProfiles() {
        return profileMap.values();
    }
}
