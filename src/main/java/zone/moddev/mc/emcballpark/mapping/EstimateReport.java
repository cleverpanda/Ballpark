package zone.moddev.mc.emcballpark.mapping;

import java.util.Map;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;
import zone.moddev.mc.emcballpark.rule.Decision;

/** The decisions from the most recent mapping pass. Written on the reload thread, read by commands. */
public final class EstimateReport {
    private static volatile @Nullable Map<Item, Decision> decisions;

    private EstimateReport() {}

    static void publish(Map<Item, Decision> latest) {
        decisions = Map.copyOf(latest);
    }

    /** False until ProjectE has run its mappers (it skips them when using pregenerated values). */
    public static boolean ready() {
        return decisions != null;
    }

    /** Null when no rule matched the item. */
    public static @Nullable Decision get(Item item) {
        Map<Item, Decision> current = decisions;
        return current == null ? null : current.get(item);
    }
}
