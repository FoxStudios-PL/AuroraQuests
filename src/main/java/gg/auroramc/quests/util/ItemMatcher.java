package gg.auroramc.quests.util;

import gg.auroramc.aurora.api.AuroraAPI;
import gg.auroramc.aurora.api.item.TypeId;
import gg.auroramc.aurora.api.message.Chat;
import gg.auroramc.aurora.api.message.Text;
import gg.auroramc.quests.AuroraQuests;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Matches inventory items against criteria defined in a task's {@code args.item}
 * section: {@code material} (vanilla material or a namespaced custom item id resolved
 * through Aurora's item manager), {@code name}, {@code lore} and {@code custom-model-data}.
 * Every criterion is optional; only the configured ones are checked.
 * <p>
 * Name and lore accept legacy {@code &} codes and MiniMessage. Both the configured text
 * and the item's own text are normalized to the same legacy representation and compared
 * exactly, colors included (lore must match line by line, in order).
 */
public class ItemMatcher {
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character(LegacyComponentSerializer.SECTION_CHAR).hexColors().build();

    private final Material material;
    private final TypeId customTypeId;
    private final String name;
    private final List<String> lore;
    private final Integer customModelData;
    /**
     * False when no criterion is configured or the configured material doesn't exist —
     * an invalid matcher never matches anything, so a misconfigured task can't take
     * arbitrary items from players.
     */
    @Getter
    private final boolean valid;

    public ItemMatcher(@Nullable ConfigurationSection section) {
        if (section == null) {
            material = null;
            customTypeId = null;
            name = null;
            lore = null;
            customModelData = null;
            valid = false;
            return;
        }

        Material material = null;
        TypeId customTypeId = null;
        var materialInvalid = false;
        var materialStr = section.getString("material");
        if (materialStr != null && !materialStr.isBlank()) {
            var typeId = TypeId.fromString(materialStr);
            if (typeId.namespace().equals("minecraft")) {
                material = Material.matchMaterial(typeId.id());
                if (material == null) {
                    AuroraQuests.logger().warning("Unknown material '" + materialStr + "' in item matcher at: " + section.getCurrentPath());
                    materialInvalid = true;
                }
            } else {
                customTypeId = typeId;
            }
        }
        this.material = material;
        this.customTypeId = customTypeId;

        var nameStr = section.getString("name");
        this.name = nameStr != null ? normalize(nameStr) : null;

        var loreList = section.getStringList("lore");
        this.lore = loreList.isEmpty() ? null : loreList.stream().map(ItemMatcher::normalize).toList();

        this.customModelData = section.contains("custom-model-data") ? section.getInt("custom-model-data") : null;

        this.valid = !materialInvalid
                && (this.material != null || this.customTypeId != null || this.name != null || this.lore != null || this.customModelData != null);
    }

    /**
     * Canonical legacy form of a configured string ({@code &} codes / MiniMessage in,
     * section-char legacy out) so it can be compared against {@link #normalize(Component)}.
     */
    private static String normalize(String configText) {
        return LEGACY.serialize(Text.component(Chat.translateToMM(configText)));
    }

    private static String normalize(Component component) {
        return LEGACY.serialize(component);
    }

    public boolean matches(@Nullable ItemStack item) {
        if (!valid || item == null || item.getType().isAir()) return false;

        if (material != null && item.getType() != material) return false;
        if (customTypeId != null && !customTypeId.equals(AuroraAPI.getItemManager().resolveId(item))) return false;

        if (name == null && lore == null && customModelData == null) return true;

        var meta = item.getItemMeta();
        if (meta == null) return false;

        if (customModelData != null && (!meta.hasCustomModelData() || meta.getCustomModelData() != customModelData)) {
            return false;
        }

        if (name != null) {
            var displayName = meta.displayName();
            if (displayName == null || !name.equals(normalize(displayName))) return false;
        }

        if (lore != null) {
            var itemLore = meta.lore();
            if (itemLore == null || itemLore.size() != lore.size()) return false;
            for (int i = 0; i < lore.size(); i++) {
                if (!lore.get(i).equals(normalize(itemLore.get(i)))) return false;
            }
        }

        return true;
    }

    /**
     * Total amount of matching items in the player's inventory (same slots as
     * TAKE_ITEM: storage, armor and offhand).
     */
    public int count(Player player) {
        var total = 0;
        for (var item : player.getInventory().getContents()) {
            if (matches(item)) total += item.getAmount();
        }
        return total;
    }

    /**
     * Removes exactly {@code amount} matching items, all-or-nothing: when the player
     * doesn't have enough, nothing is removed and false is returned.
     */
    public boolean take(Player player, int amount) {
        if (amount <= 0 || count(player) < amount) return false;

        var remaining = amount;
        for (var item : player.getInventory().getContents()) {
            if (remaining <= 0) break;
            if (!matches(item)) continue;

            var stackAmount = item.getAmount();
            if (stackAmount > remaining) {
                item.setAmount(stackAmount - remaining);
                remaining = 0;
            } else {
                remaining -= stackAmount;
                item.setAmount(0);
            }
        }
        return true;
    }
}
