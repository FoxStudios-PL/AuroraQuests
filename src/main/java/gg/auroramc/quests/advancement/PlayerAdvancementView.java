package gg.auroramc.quests.advancement;

import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mutable per-player advancement-GUI state.
 * <p>
 * Threading contract: {@link #dirty}, {@link #fullResync}, {@link #forgetClient} and
 * {@link #flushScheduled} are written from any thread (progress events, Bukkit events,
 * the async flusher); everything else is only touched on the player's region thread
 * inside a flush, so it needs no synchronization.
 */
final class PlayerAdvancementView {
    final UUID playerId;

    /** Quest keys whose progress/display changed since the last flush (cross-thread). */
    final Set<String> dirty = ConcurrentHashMap.newKeySet();
    /** Rebuild and re-send everything on the next flush (joins, completions, rerolls). */
    volatile boolean fullResync = true;
    /**
     * The client forgot our advancements (vanilla datapack reload sent a reset packet):
     * clear {@link #sent} before the next sync instead of diffing against ghosts.
     */
    volatile boolean forgetClient = false;
    /** Guards against double-scheduling a flush for this player. */
    final AtomicBoolean flushScheduled = new AtomicBoolean(false);

    /** Last state sent per quest key (region thread only). */
    final Map<String, SentState> sent = new HashMap<>();
    /** Tab roots the client currently knows, by tab id (region thread only). */
    final Map<String, Identifier> sentRoots = new HashMap<>();

    PlayerAdvancementView(UUID playerId) {
        this.playerId = playerId;
    }

    /**
     * @param id          advancement id actually sent (kept so entries of a previous
     *                    model can be removed after a config reload changed the model)
     * @param granted     criteria granted out of {@code steps}
     * @param done        completion flag as last sent
     * @param displayHash hash of the rendered title/description/icon state, to skip
     *                    re-sends when only irrelevant data changed
     * @param x,y         grid position used (dynamic tabs recompute per roll)
     */
    record SentState(Identifier id, int granted, boolean done, int displayHash, float x, float y) {
    }
}
