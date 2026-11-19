package zone.moddev.mc.ballpark.rule;

import java.util.Optional;
import java.util.Set;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * What a rule proposes for one item: a flat EMC value, and/or "{@code amount} of any one of {@code fromItems}
 * (or of {@code fromTag}) makes {@code makes} of it", which ProjectE prices once those have EMC.
 */
public record Proposal(long emc, Set<Item> fromItems, Optional<TagKey<Item>> fromTag, int amount, int makes, String detail) {
    public static Proposal value(long emc, String detail) {
        return new Proposal(emc, Set.of(), Optional.empty(), 0, 1, detail);
    }

    public static Proposal from(Set<Item> fromItems, int amount, String detail) {
        return new Proposal(0, Set.copyOf(fromItems), Optional.empty(), amount, 1, detail);
    }

    public boolean isValue() {
        return emc > 0;
    }
}
