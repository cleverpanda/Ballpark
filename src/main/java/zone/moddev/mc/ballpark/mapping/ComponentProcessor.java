package zone.moddev.mc.ballpark.mapping;

import static zone.moddev.mc.ballpark.Ballpark.MOD_ID;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Optional;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.components.DataComponentProcessor;
import moze_intel.projecte.api.components.IDataComponentProcessor;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.ballpark.rule.ComponentRule;

/**
 * Applies the {@code ballpark/components} datapack files: keeps the listed components when an item is learned in
 * a transmutation table or copied by a condenser, and scales the item's EMC by a component's value. Without this,
 * an item such as an Apotheosis gem is learned as a blank item, which can't be taken back out.
 */
@DataComponentProcessor
public final class ComponentProcessor implements IDataComponentProcessor {
    // Filled when the server maps. Clients of a dedicated server never get these, so their tooltips show unscaled EMC;
    // transmutation uses the server's value.
    private static volatile List<ComponentRule> rules = List.of();
    private static volatile DynamicOps<JsonElement> ops = JsonOps.INSTANCE;

    static void publish(List<ComponentRule> latest, RegistryAccess registries) {
        rules = latest;
        ops = RegistryOps.create(JsonOps.INSTANCE, registries);
    }

    @Override
    public String getName() {
        return "Ballpark Components";
    }

    @Override
    public String getTranslationKey() {
        return "processor." + MOD_ID + ".components";
    }

    @Override
    public String getDescription() {
        return "Keeps and prices the data components named in the ballpark/components datapack files.";
    }

    @Override
    public long recalculateEMC(ItemInfo info, long currentEMC) throws ArithmeticException {
        long emc = currentEMC;
        for (ComponentRule rule : rules) {
            if (rule.scale().isEmpty() || !applies(rule, info)) continue;
            ComponentRule.Scale scale = rule.scale().get();
            Double multiplier = type(scale.component()).flatMap(type -> written(info, type)).map(scale.values()::get).orElse(null);
            if (multiplier == null) continue;
            double scaled = emc * multiplier;
            if (scaled >= Long.MAX_VALUE) throw new ArithmeticException("EMC overflow");
            emc = Math.round(scaled);
        }
        return emc;
    }

    @Override
    public boolean hasPersistentComponents() {
        return true;
    }

    @Override
    public void collectPersistentComponents(ItemInfo info, DataComponentPatch.Builder builder) {
        for (ComponentRule rule : rules) {
            if (!applies(rule, info)) continue;
            for (ResourceLocation id : rule.keep()) {
                type(id).ifPresent(type -> copy(info, type, builder));
            }
        }
    }

    private static boolean applies(ComponentRule rule, ItemInfo info) {
        return rule.items().map(items -> items.contains(info.getItem())).orElse(true);
    }

    private static Optional<DataComponentType<?>> type(ResourceLocation id) {
        return BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(id);
    }

    private static <T> void copy(ItemInfo info, DataComponentType<T> type, DataComponentPatch.Builder builder) {
        T value = info.getOrNull(type);
        if (value != null) builder.set(type, value);
    }

    /** The component's value as written in a command, when it's a single string or number. */
    private static <T> Optional<String> written(ItemInfo info, DataComponentType<T> type) {
        T value = info.getOrNull(type);
        Codec<T> codec = type.codec();
        if (value == null || codec == null) return Optional.empty();
        return codec.encodeStart(ops, value).result().filter(JsonElement::isJsonPrimitive).map(JsonElement::getAsString);
    }
}
