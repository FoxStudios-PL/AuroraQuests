package gg.auroramc.quests.questbook;

import gg.auroramc.aurora.api.user.UserDataHolder;
import gg.auroramc.aurora.api.util.NamespacedId;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

/**
 * Per-player persisted data for the quest book feature.
 * <p>
 * Stored through Aurora's user data pipeline (async, MySQL cross-server safe),
 * exactly like {@link gg.auroramc.quests.api.data.QuestData}. It only holds the
 * current {@link QuestBookState}.
 */
public class QuestBookData extends UserDataHolder {
    private volatile QuestBookState state = QuestBookState.INITIAL;

    public QuestBookState getState() {
        return state;
    }

    public void setState(QuestBookState newState) {
        if (newState == null) newState = QuestBookState.INITIAL;
        if (this.state != newState) {
            this.state = newState;
            dirty.set(true);
        }
    }

    @Override
    public NamespacedId getId() {
        return NamespacedId.fromDefault("questbook");
    }

    @Override
    public void serializeInto(ConfigurationSection data) {
        // Reset to avoid stale keys
        data.getKeys(false).forEach(key -> data.set(key, null));
        // Only persist non-default states to keep user files clean
        if (state != QuestBookState.INITIAL) {
            data.set("state", state.name());
        }
    }

    @Override
    public void initFrom(@Nullable ConfigurationSection data) {
        if (data == null) {
            state = QuestBookState.INITIAL;
            return;
        }
        var raw = data.getString("state");
        if (raw == null) {
            state = QuestBookState.INITIAL;
            return;
        }
        try {
            state = QuestBookState.valueOf(raw);
        } catch (IllegalArgumentException e) {
            state = QuestBookState.INITIAL;
        }
    }
}
