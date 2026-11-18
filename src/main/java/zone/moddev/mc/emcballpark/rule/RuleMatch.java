package zone.moddev.mc.emcballpark.rule;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;

/**
 * Which items a rule applies to. Every present condition must hold.
 *
 * @param items      item ids or a {@code #tag}
 * @param namespaces item id namespaces (mod ids)
 * @param tagPrefix  the item must have a tag starting with this, e.g. {@code c:ingots/}; the rest of the tag is the material
 * @param components    data components the item must have by default, e.g. {@code minecraft:food}
 * @param withoutRecipe only items no recipe makes; keeps a rough estimate off crafted items, where it can come out
 *                      below the recipe and, with a recycling recipe, let players make EMC from nothing
 */
public record RuleMatch(Optional<HolderSet<Item>> items, List<String> namespaces, Optional<String> tagPrefix,
                        List<DataComponentType<?>> components, boolean withoutRecipe) {
    public static final RuleMatch ANY = new RuleMatch(Optional.empty(), List.of(), Optional.empty(), List.of(), false);

    public static final Codec<RuleMatch> CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryCodecs.homogeneousList(Registries.ITEM).optionalFieldOf("items").forGetter(RuleMatch::items),
            Codec.STRING.listOf().optionalFieldOf("namespaces", List.of()).forGetter(RuleMatch::namespaces),
            Codec.STRING.optionalFieldOf("tag_prefix").forGetter(RuleMatch::tagPrefix),
            BuiltInRegistries.DATA_COMPONENT_TYPE.byNameCodec().listOf().optionalFieldOf("components", List.of()).forGetter(RuleMatch::components),
            Codec.BOOL.optionalFieldOf("without_recipe", false).forGetter(RuleMatch::withoutRecipe)
    ).apply(i, RuleMatch::new));

    /** The material captured by {@link #tagPrefix} ("" without one), or empty when the item doesn't match. */
    public Optional<String> test(Holder.Reference<Item> item, Set<Item> craftable) {
        if (withoutRecipe && craftable.contains(item.value())) return Optional.empty();
        if (items.isPresent() && !items.get().contains(item)) return Optional.empty();
        if (!namespaces.isEmpty() && !namespaces.contains(item.key().location().getNamespace())) return Optional.empty();
        for (DataComponentType<?> component : components) {
            if (!item.value().components().has(component)) return Optional.empty();
        }
        if (tagPrefix.isEmpty()) return Optional.of("");
        String prefix = tagPrefix.get();
        return item.tags()
                .map(tag -> tag.location().toString())
                .filter(tag -> tag.startsWith(prefix) && tag.length() > prefix.length())
                .map(tag -> tag.substring(prefix.length()))
                .sorted()
                .findFirst();
    }
}
