package gg.auroramc.quests.config.advancement;

import gg.auroramc.aurora.api.config.premade.ItemConfig;
import lombok.Getter;

/**
 * Per-pool {@code advancement:} section of a pool's {@code config.yml}. Optional: a pool
 * without it gets its own auto tab (id = pool id) using the pool name and menu icon.
 */
@Getter
public class PoolAdvancementConfig {
    /** Set to false to keep this pool out of the advancement GUI entirely. */
    private Boolean enabled;
    /**
     * Default tab (category) for this pool's quests. Defaults to the pool id, which
     * creates a dedicated auto tab. Point several pools at the same tab to merge them.
     */
    private String tab;
    /** Appearance of the auto tab root (ignored when {@code tab} names a declared tab). */
    private RootConfig root = new RootConfig();

    @Getter
    public static class RootConfig {
        /** Root advancement title; {@code {name}} resolves to the pool name. */
        private String title;
        /** Root advancement description lines (single string). */
        private String description;
        /** Root icon; defaults to the pool's main-menu item. */
        private ItemConfig icon;
        /** Background texture id, e.g. {@code minecraft:gui/advancements/backgrounds/stone}. */
        private String background;
    }
}
