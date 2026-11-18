package zone.moddev.mc.emcballpark.rule;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One file in {@code data/<namespace>/emcballpark/rules/}. Rules are tried from the highest priority down;
 * the first that matches an item and can price it (or skips it) wins.
 *
 * @param estimate defaults to {@code skip}, so {@code {"enabled": false}} alone switches a shipped rule off
 * @param enabled  lets a datapack switch off a shipped rule by overriding its file
 */
public record EstimateRule(int priority, RuleMatch match, Estimate estimate, boolean enabled) {
    public static final Codec<EstimateRule> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("priority", 0).forGetter(EstimateRule::priority),
            RuleMatch.CODEC.optionalFieldOf("match", RuleMatch.ANY).forGetter(EstimateRule::match),
            Estimate.CODEC.optionalFieldOf("estimate", new Estimate.Skip()).forGetter(EstimateRule::estimate),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(EstimateRule::enabled)
    ).apply(i, EstimateRule::new));
}
