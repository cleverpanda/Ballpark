package zone.moddev.mc.ballpark.rule;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** The rule that decided an item, and its estimate; no estimate means the rule skips the item. */
public record Decision(ResourceLocation rule, Optional<Proposal> proposal) {
    public boolean skipped() {
        return proposal.isEmpty();
    }

    public String describe() {
        return proposal.map(p -> "estimated by " + rule + ": " + (p.isValue() ? p.emc() + " EMC, " : "") + p.detail())
                .orElse("skipped by " + rule);
    }
}
