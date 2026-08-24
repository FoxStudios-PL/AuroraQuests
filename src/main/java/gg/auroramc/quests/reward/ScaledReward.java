package gg.auroramc.quests.reward;

import gg.auroramc.aurora.api.AuroraAPI;
import gg.auroramc.aurora.api.command.CommandDispatcher;
import gg.auroramc.aurora.api.message.Placeholder;
import gg.auroramc.aurora.api.reward.CommandReward;
import gg.auroramc.aurora.api.reward.NumberReward;
import gg.auroramc.aurora.api.reward.Reward;
import gg.auroramc.aurora.api.util.ThreadSafety;
import gg.auroramc.quests.AuroraQuests;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a numeric reward so its amount is resolved from a {@code reward-scales} table at
 * the moment the reward is displayed or paid — never at roll time, so a player who
 * levels up mid-day is paid at the new tier.
 * <p>
 * One resolution path for everything (the spec's trap #1): {@link #execute} and
 * {@link #getDisplay} both go through {@link RewardScale#resolve}, so the number the
 * player sees is the number the player gets. The wrapped reward's own amount was
 * replaced at parse time by a formula reading {@link #VALUE_TOKEN}, which this class
 * injects into the placeholder list; command rewards get their commands filled here,
 * before the Aurora dispatcher (trap #2), with a clean integer-safe {@code {value}}.
 * <p>
 * Tokens usable in {@code display} and {@code command}: {@code {value}} (clean raw:
 * {@code 1700}, command-safe), {@code {value_raw}} (alias), {@code {value_int}},
 * {@code {value_formatted}} (AuroraAPI.formatNumber — pretty, for displays only),
 * {@code {scale_source}} (the value read on the player), {@code {scale_tier}} (1-based
 * tier index, for debugging). The scale itself is looked up per call, so
 * {@code /quests reload} changes apply immediately.
 */
public class ScaledReward implements Reward {
    /** Formula token the wrapped reward's {@code getValue} resolves through. */
    public static final String VALUE_TOKEN = "{aq_scale_value}";

    private final String scaleId;
    private final NumberReward delegate;
    private String display = "";

    public ScaledReward(String scaleId, NumberReward delegate) {
        this.scaleId = scaleId;
        this.delegate = delegate;
    }

    @Override
    public void init(ConfigurationSection section) {
        // The delegate was initialised by the factory on the rewritten section; only the
        // display template is owned here (rendered with the scale tokens).
        this.display = section.getString("display", "");
    }

    @Override
    public ThreadSafety getThreadSafety() {
        return delegate.getThreadSafety();
    }

    @Override
    public void execute(Player player, long level, List<Placeholder<?>> placeholders) {
        var scale = lookup();
        if (scale == null) {
            AuroraQuests.logger().severe("[reward-scales] Reward scale '" + scaleId
                    + "' no longer exists (removed by a reload?) - the reward was NOT paid.");
            return;
        }
        var resolution = scale.resolve(player);
        var extended = extend(placeholders, resolution);

        if (delegate instanceof CommandReward commandReward) {
            // Fill the value tokens ourselves so {value} stays command-safe ("1700",
            // never "1700.0" or a pretty-formatted number), then hand the command to
            // the dispatcher with the full placeholder list (PAPI etc. happen there).
            for (var command : commandReward.getCommands()) {
                CommandDispatcher.dispatch(player, Placeholder.execute(command, valueTokens(resolution)), extended);
            }
            return;
        }

        delegate.execute(player, level, extended);
    }

    @Override
    public String getDisplay(Player player, List<Placeholder<?>> placeholders) {
        var scale = lookup();
        if (scale == null) return display;
        var resolution = scale.resolve(player);
        return Placeholder.execute(display, valueTokens(resolution));
    }

    private RewardScale lookup() {
        var configManager = AuroraQuests.getInstance().getConfigManager();
        var scales = configManager != null ? configManager.getRewardScales() : null;
        return scales != null ? scales.get(scaleId) : null;
    }

    private List<Placeholder<?>> extend(List<Placeholder<?>> placeholders, RewardScale.Resolution resolution) {
        var extended = new ArrayList<Placeholder<?>>(placeholders.size() + 6);
        extended.addAll(placeholders);
        // The delegate's rewritten formula reads this one (Number: exact value).
        extended.add(Placeholder.of(VALUE_TOKEN, (Number) resolution.value()));
        for (var token : valueTokens(resolution)) {
            extended.add(token);
        }
        return extended;
    }

    private static Placeholder<?>[] valueTokens(RewardScale.Resolution resolution) {
        var clean = RewardScale.formatClean(resolution.value());
        return new Placeholder<?>[]{
                Placeholder.of("{value}", clean),
                Placeholder.of("{value_raw}", clean),
                Placeholder.of("{value_int}", (Number) (long) resolution.value()),
                Placeholder.of("{value_formatted}", AuroraAPI.formatNumber(resolution.value())),
                Placeholder.of("{scale_source}", RewardScale.formatClean(resolution.source())),
                Placeholder.of("{scale_tier}", (Number) resolution.tier())
        };
    }
}
