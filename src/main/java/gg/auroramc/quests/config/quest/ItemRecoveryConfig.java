package gg.auroramc.quests.config.quest;

import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * One entry of a quest file's {@code item-recovery:} section: gives a quest item back
 * when the player repeats the action that originally granted it.
 * <p>
 * The trigger's task arguments ({@code types}, {@code mode}, ...) sit directly under
 * {@code trigger:} and are read from the raw config by the parser.
 */
@Getter
public class ItemRecoveryConfig {
    private String after;
    private String until;
    private ConfigurationSection item;
    private TriggerConfig trigger;
    private Double chance;
    private Integer cooldownSeconds;
    private List<String> onRecover = new ArrayList<>();

    @Getter
    public static class TriggerConfig {
        private String task;
        private FilterConfig filters;
    }
}
