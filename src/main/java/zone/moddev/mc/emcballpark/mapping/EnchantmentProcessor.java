package zone.moddev.mc.emcballpark.mapping;

import static zone.moddev.mc.emcballpark.EMCBallpark.MOD_ID;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.List;
import java.util.function.LongSupplier;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.components.DataComponentProcessor;
import moze_intel.projecte.api.components.IDataComponentProcessor;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Adds EMC for the enchantments on enchanted books and enchanted gear, and keeps them when the item is learned in
 * a transmutation table or copied by a condenser. ProjectE has its own version of this, but it is switched off in
 * code. ProjectE lists this one in its mapping config, where it can be turned off or made non-persistent.
 */
@DataComponentProcessor
public final class EnchantmentProcessor implements IDataComponentProcessor {
    private static final List<DataComponentType<ItemEnchantments>> TYPES = List.of(DataComponents.ENCHANTMENTS, DataComponents.STORED_ENCHANTMENTS);

    private LongSupplier emcPerLevel = () -> 5000;

    @Override
    public String getName() {
        return "EMC Ballpark Enchantments";
    }

    @Override
    public String getTranslationKey() {
        return "processor." + MOD_ID + ".enchantments";
    }

    @Override
    public String getDescription() {
        return "Adds EMC for enchantments on books and gear, and keeps them in transmutation knowledge.";
    }

    @Override
    public void addConfigOptions(ModConfigSpec.Builder builder) {
        ModConfigSpec.LongValue value = builder
                .comment("EMC per enchantment level, divided by the enchantment's weight, so rare enchantments are worth more.")
                .translation("processor." + MOD_ID + ".enchantments.emc_per_level")
                .defineInRange("emc_per_level", 5000L, 0L, Long.MAX_VALUE);
        emcPerLevel = value::getAsLong;
    }

    @Override
    public long recalculateEMC(ItemInfo info, long currentEMC) throws ArithmeticException {
        long emc = currentEMC;
        for (DataComponentType<ItemEnchantments> type : TYPES) {
            ItemEnchantments enchantments = info.getOrNull(type);
            if (enchantments == null) continue;
            for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
                int weight = entry.getKey().value().definition().weight();
                if (weight > 0) emc = Math.addExact(emc, Math.multiplyExact(emcPerLevel.getAsLong() / weight, entry.getIntValue()));
            }
        }
        return emc;
    }

    @Override
    public boolean hasPersistentComponents() {
        return true;
    }

    @Override
    public void collectPersistentComponents(ItemInfo info, DataComponentPatch.Builder builder) {
        for (DataComponentType<ItemEnchantments> type : TYPES) {
            ItemEnchantments enchantments = info.getOrNull(type);
            if (enchantments != null && !enchantments.isEmpty()) builder.set(type, enchantments);
        }
    }
}
