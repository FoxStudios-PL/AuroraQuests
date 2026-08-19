package gg.auroramc.quests.advancement;

import io.papermc.paper.adventure.PaperAdventure;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;
import org.bukkit.Material;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The single NMS touch point of the advancement GUI: builds client-facing advancement
 * holders/progress and ships {@link ClientboundUpdateAdvancementsPacket}s.
 * <p>
 * Everything here is pure data assembly — no world access — so it is safe to call from
 * a player's region thread (Folia) and the resulting send is handed to Netty off the
 * server thread. Criteria maps are never sent over the wire (the protocol only carries
 * parent + display + requirements), which keeps packets small: a progress-only update
 * is a few dozen bytes.
 */
final class AdvancementPacketFactory {
    static final String NAMESPACE = "aurora_quests";

    /** Criterion name pool shared by every quest ("c0".."cN"), sized to the step cap. */
    private static final List<String> CRITERIA_POOL;

    static {
        CRITERIA_POOL = java.util.stream.IntStream.range(0, 128).mapToObj(i -> "c" + i).toList();
    }

    private AdvancementPacketFactory() {
    }

    /** First {@code steps} shared criterion names (also the requirement order). */
    static List<String> criteriaNames(int steps) {
        return CRITERIA_POOL.subList(0, Math.min(steps, CRITERIA_POOL.size()));
    }

    /**
     * Builds one displayable advancement entry.
     *
     * @param background only roots carry one (it makes the client create a tab)
     * @param x          grid column, {@code y} grid row (1.0 = one advancement cell)
     */
    static AdvancementHolder holder(Identifier id, @Nullable Identifier parent,
                                    net.kyori.adventure.text.Component title,
                                    net.kyori.adventure.text.Component description,
                                    @Nullable ItemStack icon, AdvancementType frame,
                                    Optional<ClientAsset.ResourceTexture> background,
                                    boolean showToast, float x, float y,
                                    AdvancementRequirements requirements) {
        var iconStack = icon == null || icon.isEmpty() ? new ItemStack(Material.BOOK) : icon;
        var display = new DisplayInfo(
                ItemStackTemplate.fromStack(CraftItemStack.asNMSCopy(iconStack)),
                PaperAdventure.asVanilla(title),
                PaperAdventure.asVanilla(description),
                background,
                frame,
                showToast,
                false,   // announce_to_chat: the plugin sends its own chat messages
                false);  // hidden is handled server-side by not sending the entry at all
        display.setLocation(x, y);
        var advancement = new Advancement(
                Optional.ofNullable(parent),
                Optional.of(display),
                net.minecraft.advancements.AdvancementRewards.EMPTY,
                Map.of(),
                requirements,
                false);
        return new AdvancementHolder(id, advancement);
    }

    /** Progress with the first {@code granted} of {@code node.steps} criteria completed. */
    static AdvancementProgress progress(AdvancementRequirements requirements, List<String> criteria, int granted) {
        var progress = new AdvancementProgress();
        progress.update(requirements);
        for (int i = 0; i < granted && i < criteria.size(); i++) {
            progress.grantProgress(criteria.get(i));
        }
        return progress;
    }

    /**
     * Sends one advancement update. {@code reset} wipes the whole client-side screen
     * first (used by {@code hide-vanilla-tabs} resyncs); ids listed in {@code removed}
     * are processed before {@code added}, so re-sending an entry to refresh its display
     * is done by putting it (and, because the client drops whole subtrees on removal,
     * its visible descendants) in both sets of the same packet.
     */
    static void send(Player player, boolean reset, Collection<AdvancementHolder> added,
                     Set<Identifier> removed, Map<Identifier, AdvancementProgress> progress) {
        if (!reset && added.isEmpty() && removed.isEmpty() && progress.isEmpty()) return;
        ((CraftPlayer) player).getHandle().connection.send(
                new ClientboundUpdateAdvancementsPacket(reset, added, removed, progress, true));
    }

    /**
     * Pre-selects an advancement tab on the client. The client remembers the selection,
     * so the next time the player opens the progress screen (L key) it lands directly
     * on that tab; if the screen is already open, it switches immediately. This is the
     * closest the protocol gets to "opening" the screen — no clientbound packet exists
     * to actually open it (it is pure client-side input handling).
     */
    static void selectTab(Player player, Identifier rootId) {
        ((CraftPlayer) player).getHandle().connection.send(
                new net.minecraft.network.protocol.game.ClientboundSelectAdvancementsTabPacket(rootId));
    }

    /**
     * Re-runs the vanilla advancement sync for the player, restoring the vanilla tabs
     * after the module (or its hide-vanilla-tabs option) is turned off at runtime.
     */
    static void resendVanillaAdvancements(Player player) {
        var handle = ((CraftPlayer) player).getHandle();
        var server = handle.level().getServer();
        if (server == null) return;
        handle.getAdvancements().reload(server.getAdvancements());
    }
}
