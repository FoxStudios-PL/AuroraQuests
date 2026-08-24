package gg.auroramc.quests.reward;

import java.util.function.Consumer;

/**
 * Logging indirection for the reward-scale classes. They run in unit tests too, where
 * the {@code AuroraQuests} class cannot be loaded (its bytecode references Quartz, a
 * compile-only dependency absent from the test runtime) — so the plugin installs the
 * real logger at startup and tests keep the no-op default.
 */
public final class ScaleLog {
    static volatile Consumer<String> severe = message -> {
    };
    static volatile Consumer<String> warning = message -> {
    };

    private ScaleLog() {
    }

    public static void install(Consumer<String> severeSink, Consumer<String> warningSink) {
        severe = severeSink;
        warning = warningSink;
    }
}
