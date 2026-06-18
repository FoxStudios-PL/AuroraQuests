package gg.auroramc.quests.questbook;

import gg.auroramc.aurora.api.AuroraAPI;
import gg.auroramc.aurora.api.command.CommandDispatcher;
import gg.auroramc.aurora.api.menu.ItemBuilder;
import gg.auroramc.aurora.api.message.Placeholder;
import gg.auroramc.quests.AuroraQuests;
import gg.auroramc.quests.config.Config;
import gg.auroramc.quests.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Core service of the quest book feature.
 * <p>
 * Responsible for building the book item, injecting/removing it from player
 * inventories, identifying it, handling clicks and keeping every action on the
 * correct (Folia-safe) scheduler. State is read from / written to
 * {@link QuestBookData} through Aurora's user pipeline; no separate cache is
 * kept, so there is nothing to leak.
 */
public class QuestBookManager {
    private final AuroraQuests plugin;
    private final NamespacedKey itemKey;

    private volatile boolean active;
    private volatile boolean shuttingDown = false;

    public QuestBookManager(AuroraQuests plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "quest_book");
        this.active = config() != null && Boolean.TRUE.equals(config().getEnabled());
    }

    // ---------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------

    /**
     * Re-applies the live config. Called from {@code /quests reload}.
     * Handles live enable/disable transitions (only possible if the feature
     * was enabled at startup, otherwise this manager wouldn't exist).
     */
    public void reload() {
        if (shuttingDown) return;
        boolean now = config() != null && Boolean.TRUE.equals(config().getEnabled());
        if (now && !active) {
            active = true;
            refreshAll();
        } else if (!now && active) {
            active = false;
            removeFromAll();
        } else if (now) {
            // Settings (slot / appearance / commands) may have changed.
            refreshAll();
        }
    }

    public void shutdown() {
        shuttingDown = true;
    }

    public boolean isActive() {
        return active && !shuttingDown;
    }

    public NamespacedKey getItemKey() {
        return itemKey;
    }

    // ---------------------------------------------------------------------
    // Config helpers
    // ---------------------------------------------------------------------

    private Config.QuestBookConfig config() {
        var cm = AuroraQuests.getInstance().getConfigManager();
        if (cm == null || cm.getConfig() == null) return null;
        return cm.getConfig().getQuestBook();
    }

    private int getSlot() {
        var c = config();
        int slot = (c == null || c.getSlot() == null) ? 17 : c.getSlot();
        // Clamp to the storage range (0-35) to avoid touching armor/offhand by index.
        if (slot < 0 || slot > 35) return 17;
        return slot;
    }

    private Config.QuestBookStateConfig stateConfig(QuestBookState state) {
        var c = config();
        if (c == null) return null;
        return state == QuestBookState.NEW_QUEST ? c.getNewQuestState() : c.getInitialState();
    }

    // ---------------------------------------------------------------------
    // State access (delegates to Aurora user data)
    // ---------------------------------------------------------------------

    public QuestBookData getData(UUID uuid) {
        var user = AuroraAPI.getUser(uuid);
        if (user == null) return null;
        return user.getData(QuestBookData.class);
    }

    public boolean isLoaded(Player player) {
        return getData(player.getUniqueId()) != null;
    }

    public QuestBookState getState(UUID uuid) {
        var data = getData(uuid);
        return data == null ? QuestBookState.INITIAL : data.getState();
    }

    // ---------------------------------------------------------------------
    // Item building / identification
    // ---------------------------------------------------------------------

    public ItemStack createItem(Player player, QuestBookState state) {
        var sc = stateConfig(state);
        ItemStack item;
        if (sc != null && sc.getItem() != null) {
            item = ItemBuilder.of(sc.getItem())
                    .localization(AuroraQuests.getInstance().getLocalizationProvider())
                    .placeholder(Placeholder.of("{player}", player.getName()))
                    .toItemStack(player);
        } else {
            item = new ItemStack(Material.WRITTEN_BOOK);
        }
        if (item == null || item.getType() == Material.AIR) {
            item = new ItemStack(Material.WRITTEN_BOOK);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, state.name());
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isQuestBook(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(itemKey, PersistentDataType.STRING);
    }

    // ---------------------------------------------------------------------
    // Injection / removal (must run on the player's region/entity thread)
    // ---------------------------------------------------------------------

    private void inject(Player player) {
        if (!isActive() || !player.isOnline()) return;
        if (isCustomMenuOpen(player)) return; // don't fight inventory-hiding menus (DeluxeMenus etc.)
        var data = getData(player.getUniqueId());
        if (data == null) return;
        int slot = getSlot();
        removeOtherCopies(player, slot);
        player.getInventory().setItem(slot, createItem(player, data.getState()));
    }

    private void removeOtherCopies(Player player, int targetSlot) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (i == targetSlot) continue;
            if (isQuestBook(inv.getItem(i))) {
                inv.setItem(i, null);
            }
        }
        if (isQuestBook(inv.getItemInOffHand())) {
            inv.setItemInOffHand(null);
        }
    }

    private void removeAll(Player player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (isQuestBook(inv.getItem(i))) {
                inv.setItem(i, null);
            }
        }
        if (isQuestBook(inv.getItemInOffHand())) {
            inv.setItemInOffHand(null);
        }
    }

    /**
     * Schedules an injection on the player's entity scheduler (Folia-safe).
     */
    public void scheduleInject(Player player, long delayTicks) {
        if (!isActive() || !player.isOnline()) return;
        if (delayTicks <= 0) {
            player.getScheduler().run(plugin, t -> inject(player), null);
        } else {
            player.getScheduler().runDelayed(plugin, t -> inject(player), null, delayTicks);
        }
    }

    /**
     * Re-injects only if the book is missing from its slot. Used after inventory
     * interactions/closes so we never duplicate.
     */
    public void scheduleEnsure(Player player, long delayTicks) {
        if (!isActive() || !player.isOnline()) return;
        player.getScheduler().runDelayed(plugin, t -> {
            if (!isActive() || !player.isOnline()) return;
            if (isCustomMenuOpen(player)) return;
            var data = getData(player.getUniqueId());
            if (data == null) return;
            var current = player.getInventory().getItem(getSlot());
            // Re-inject if the book is missing or its appearance is stale.
            if (!isQuestBook(current) || readItemState(current) != data.getState()) {
                inject(player);
            }
        }, null, Math.max(1, delayTicks));
    }

    private QuestBookState readItemState(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        String raw = meta.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return QuestBookState.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isLoaded(player)) {
                scheduleInject(player, 0);
            }
        }
    }

    private void removeFromAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, t -> removeAll(player), null);
        }
    }

    // ---------------------------------------------------------------------
    // Click handling & admin notifications
    // ---------------------------------------------------------------------

    public void handleClick(Player player) {
        if (!isActive()) return;
        var data = getData(player.getUniqueId());
        if (data == null) return;
        QuestBookState stateAtClick = data.getState();

        playSound(player, stateAtClick);

        if (stateAtClick == QuestBookState.NEW_QUEST) {
            data.setState(QuestBookState.INITIAL);
            scheduleInject(player, 0);
        }

        runClickCommands(player);
    }

    private void runClickCommands(Player player) {
        var c = config();
        if (c == null || c.getClickCommands() == null) return;
        var placeholder = Placeholder.of("{player}", player.getName());
        for (String command : c.getClickCommands()) {
            if (command == null || command.isBlank()) continue;
            // CommandDispatcher schedules console/player execution itself (Folia-safe).
            CommandDispatcher.dispatch(player, command, placeholder);
        }
    }

    private void playSound(Player player, QuestBookState state) {
        var sc = stateConfig(state);
        if (sc == null || sc.getSound() == null) return;
        var sound = sc.getSound();
        if (sound.getSound() == null || sound.getSound().isBlank()) return;
        float volume = sound.getVolume() == null ? 1.0f : sound.getVolume();
        float pitch = sound.getPitch() == null ? 1.0f : sound.getPitch();
        SoundUtil.playSound(player, sound.getSound(), volume, pitch);
    }

    /**
     * Sets the notification state for a player (admin command). Returns the new
     * state, or {@code null} if the player's data isn't loaded.
     */
    public QuestBookState setNotification(Player player, boolean newQuest) {
        var data = getData(player.getUniqueId());
        if (data == null) return null;
        var state = newQuest ? QuestBookState.NEW_QUEST : QuestBookState.INITIAL;
        data.setState(state);
        scheduleInject(player, 0);
        return state;
    }

    /**
     * Toggles the notification state for a player. Returns the new state, or
     * {@code null} if the player's data isn't loaded.
     */
    public QuestBookState toggleNotification(Player player) {
        var data = getData(player.getUniqueId());
        if (data == null) return null;
        var state = data.getState() == QuestBookState.NEW_QUEST ? QuestBookState.INITIAL : QuestBookState.NEW_QUEST;
        data.setState(state);
        scheduleInject(player, 0);
        return state;
    }

    // ---------------------------------------------------------------------
    // Utils
    // ---------------------------------------------------------------------

    public int getQuestBookSlot() {
        return getSlot();
    }

    /**
     * True if the player currently has a custom (chest-like) GUI open. We never
     * inject while such a menu is open, which generically avoids corrupting
     * inventory-hiding menus (DeluxeMenus and similar) without a hard dependency.
     */
    private boolean isCustomMenuOpen(Player player) {
        try {
            InventoryType type = player.getOpenInventory().getTopInventory().getType();
            return type != InventoryType.CRAFTING && type != InventoryType.CREATIVE;
        } catch (Throwable t) {
            return false;
        }
    }
}
