package zone.moddev.mc.ballpark.rule;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.crafting.Ingredient;

/** How a rule turns a matched item into a {@link Proposal}. Picked in JSON by {@code "type"}. */
public sealed interface Estimate {
    Codec<Long> POSITIVE_LONG = Codec.LONG.validate(v -> v > 0 ? DataResult.success(v) : DataResult.error(() -> "must be positive: " + v));
    Codec<Double> POSITIVE_DOUBLE = Codec.DOUBLE.validate(v -> v > 0 ? DataResult.success(v) : DataResult.error(() -> "must be positive: " + v));

    Map<String, MapCodec<? extends Estimate>> TYPES = Map.of(
            "skip", MapCodec.unit(new Skip()),
            "fixed", Fixed.CODEC,
            "material", Material.CODEC,
            "repair_material", RepairMaterial.CODEC,
            "from_item", FromItem.CODEC,
            "food", Food.CODEC);

    Codec<Estimate> CODEC = Codec.STRING.partialDispatch("type",
            estimate -> DataResult.success(estimate.type()),
            type -> TYPES.containsKey(type)
                    ? DataResult.success(TYPES.get(type))
                    : DataResult.error(() -> "unknown estimate type '" + type + "', expected one of " + TYPES.keySet()));

    String type();

    /** Empty when this estimate can't price the item (unknown material, no repair item, not food...). */
    Optional<Proposal> propose(Holder.Reference<Item> item, String material, Map<String, Long> materials);

    /** Don't estimate the item; ProjectE still values it from recipes or its own data if it can. */
    record Skip() implements Estimate {
        public String type() { return "skip"; }

        public Optional<Proposal> propose(Holder.Reference<Item> item, String material, Map<String, Long> materials) {
            return Optional.empty();
        }
    }

    record Fixed(long emc) implements Estimate {
        static final MapCodec<Fixed> CODEC = POSITIVE_LONG.fieldOf("emc").xmap(Fixed::new, Fixed::emc);

        public String type() { return "fixed"; }

        public Optional<Proposal> propose(Holder.Reference<Item> item, String material, Map<String, Long> materials) {
            return Optional.of(Proposal.value(emc, "fixed " + emc));
        }
    }

    /**
     * {@code units} of the material captured by the rule's {@code tag_prefix}: converted from the material's
     * {@code c:ingots/} or {@code c:gems/} tag, so ProjectE's own value for it carries over, plus the value from
     * the materials table when it has one.
     */
    record Material(double units) implements Estimate {
        static final MapCodec<Material> CODEC = POSITIVE_DOUBLE.validate(Material::wholeOrFraction).fieldOf("units").xmap(Material::new, Material::units);

        // The tag conversion needs whole numbers, so 1.5 or 0.4 would be priced two different ways.
        private static DataResult<Double> wholeOrFraction(double units) {
            double n = units >= 1 ? units : 1 / units;
            return Math.abs(n - Math.round(n)) < 0.001 ? DataResult.success(units)
                    : DataResult.error(() -> "units must be a whole number or 1/n (0.5, 0.111111): " + units);
        }
        private static final List<String> BASE_FORMS = List.of("c:ingots/", "c:gems/");

        public String type() { return "material"; }

        public Optional<Proposal> propose(Holder.Reference<Item> item, String material, Map<String, Long> materials) {
            Long each = materials.get(material);
            Optional<TagKey<Item>> base = BASE_FORMS.stream()
                    .map(form -> TagKey.create(Registries.ITEM, ResourceLocation.parse(form + material)))
                    .filter(tag -> BuiltInRegistries.ITEM.getTag(tag).filter(set -> set.size() > 0).isPresent())
                    .findFirst();
            if (each == null && base.isEmpty()) return Optional.empty();

            int amount = units >= 1 ? (int) Math.round(units) : 1;
            int makes = units >= 1 ? 1 : (int) Math.round(1 / units);
            long emc = each == null ? 0 : Math.max(1, (long) Math.ceil(each * units));
            String detail = material + " x " + units + base.map(tag -> ", from #" + tag.location()).orElse("")
                    + (each == null ? "" : ", " + each + " EMC per unit");
            return Optional.of(new Proposal(emc, Set.of(), base, amount, makes, detail));
        }
    }

    /** Worth {@code count} of another item, whatever ProjectE values that at. */
    record FromItem(Item item, int count) implements Estimate {
        static final MapCodec<FromItem> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(FromItem::item),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(FromItem::count)
        ).apply(i, FromItem::new));

        public String type() { return "from_item"; }

        public Optional<Proposal> propose(Holder.Reference<Item> item, String material, Map<String, Long> materials) {
            return Optional.of(Proposal.from(Set.of(this.item), count, count + " x " + BuiltInRegistries.ITEM.getKey(this.item)));
        }
    }

    /** {@code units} of the item's own repair ingredient (tool tier or armor material). */
    record RepairMaterial(int units) implements Estimate {
        static final MapCodec<RepairMaterial> CODEC = ExtraCodecs.POSITIVE_INT.fieldOf("units").xmap(RepairMaterial::new, RepairMaterial::units);

        public String type() { return "repair_material"; }

        public Optional<Proposal> propose(Holder.Reference<Item> item, String material, Map<String, Long> materials) {
            Ingredient repair = switch (item.value()) {
                case TieredItem tiered -> tiered.getTier().getRepairIngredient();
                case ArmorItem armor -> armor.getMaterial().value().repairIngredient().get();
                default -> Ingredient.EMPTY;
            };
            Set<Item> from = Arrays.stream(repair.getItems()).map(ItemStack::getItem).filter(i -> i != Items.AIR).collect(Collectors.toSet());
            if (from.isEmpty()) return Optional.empty();
            return Optional.of(Proposal.from(from, units, units + " x repair item"));
        }
    }

    /** {@code base + nutrition * per_nutrition + saturation * per_saturation}, from the item's default food component. */
    record Food(long base, double perNutrition, double perSaturation) implements Estimate {
        static final MapCodec<Food> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.LONG.optionalFieldOf("base", 32L).forGetter(Food::base),
                Codec.DOUBLE.optionalFieldOf("per_nutrition", 8.0).forGetter(Food::perNutrition),
                Codec.DOUBLE.optionalFieldOf("per_saturation", 4.0).forGetter(Food::perSaturation)
        ).apply(i, Food::new));

        public String type() { return "food"; }

        public Optional<Proposal> propose(Holder.Reference<Item> item, String material, Map<String, Long> materials) {
            FoodProperties food = item.value().components().get(DataComponents.FOOD);
            if (food == null) return Optional.empty();
            long emc = Math.max(1, base + (long) Math.ceil(food.nutrition() * perNutrition + food.saturation() * perSaturation));
            return Optional.of(Proposal.value(emc, String.format(Locale.ROOT, "food: nutrition %d, saturation %.1f", food.nutrition(), food.saturation())));
        }
    }
}
