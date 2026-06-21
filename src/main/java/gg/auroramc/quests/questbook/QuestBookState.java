package gg.auroramc.quests.questbook;

/**
 * The visual/behavioral state of a player's quest book item.
 */
public enum QuestBookState {
    /**
     * Default state, no pending notification.
     */
    INITIAL,
    /**
     * A "new quest available" notification is active. Reset back to
     * {@link #INITIAL} when the player clicks the book.
     */
    NEW_QUEST
}
