package zone.moddev.mc.ballpark.rule;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Data components ProjectE should keep when an item carrying them is learned or condensed, and an optional value
 * multiplier keyed on one component's value. Component ids are only looked up when an item is valued, so a file
 * can name components from mods that aren't installed; item ids can't, so guard those with {@code neoforge:conditions}.
 *
 * @param items the items this applies to, ids or a {@code #tag}; every item when absent. Name them when a component
 *              can hold contents, like {@code minecraft:block_entity_data}, or a machine could be copied full
 * @param keep  component ids kept in transmutation knowledge
 * @param scale multiplies the item's EMC by the entry matching the component's value
 */
public record ComponentRule(Optional<HolderSet<Item>> items, List<ResourceLocation> keep, Optional<Scale> scale) {
    public static final Codec<ComponentRule> CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryCodecs.homogeneousList(Registries.ITEM).optionalFieldOf("items").forGetter(ComponentRule::items),
            ResourceLocation.CODEC.listOf().optionalFieldOf("keep", List.of()).forGetter(ComponentRule::keep),
            Scale.CODEC.optionalFieldOf("scale").forGetter(ComponentRule::scale)
    ).apply(i, ComponentRule::new));

    /** @param values multiplier per component value, the value written as it is in a command or loot table */
    public record Scale(ResourceLocation component, Map<String, Double> values) {
        static final Codec<Scale> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("component").forGetter(Scale::component),
                Codec.unboundedMap(Codec.STRING, Estimate.POSITIVE_DOUBLE).fieldOf("values").forGetter(Scale::values)
        ).apply(i, Scale::new));
    }
}
