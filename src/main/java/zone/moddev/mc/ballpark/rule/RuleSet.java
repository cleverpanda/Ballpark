package zone.moddev.mc.ballpark.rule;

import static zone.moddev.mc.ballpark.Ballpark.LOGGER;
import static zone.moddev.mc.ballpark.Ballpark.MOD_ID;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * The rules and material values from the loaded datapacks, read fresh each time ProjectE maps.
 * Materials live in {@code data/<namespace>/ballpark/materials/<material>.json} as {@code {"emc": 256}}, component
 * rules in {@code ballpark/components/}. Every file can carry {@code neoforge:conditions}, such as {@code mod_loaded}.
 */
public record RuleSet(List<Entry> rules, Map<String, Long> materials, List<ComponentRule> components) {
    private static final FileToIdConverter RULES = FileToIdConverter.json(MOD_ID + "/rules");
    private static final FileToIdConverter MATERIALS = FileToIdConverter.json(MOD_ID + "/materials");
    private static final FileToIdConverter COMPONENTS = FileToIdConverter.json(MOD_ID + "/components");
    private static final Codec<Long> MATERIAL = Estimate.POSITIVE_LONG.fieldOf("emc").codec();

    public record Entry(ResourceLocation id, EstimateRule rule) {}

    /**
     * The first rule that matches the item and either skips it or can price it; empty when none does.
     *
     * @param craftable every item some recipe makes
     */
    public Optional<Decision> decide(Holder.Reference<Item> item, Set<Item> craftable) {
        for (Entry entry : rules) {
            List<String> candidates = entry.rule().match().test(item, craftable);
            if (candidates.isEmpty()) continue;
            Estimate estimate = entry.rule().estimate();
            if (estimate instanceof Estimate.Skip) return Optional.of(new Decision(entry.id(), Optional.empty()));
            for (String material : candidates) {
                Optional<Proposal> proposal = estimate.propose(item, material, materials);
                if (proposal.isPresent()) return Optional.of(new Decision(entry.id(), proposal));
            }
        }
        return Optional.empty();
    }

    public static RuleSet load(ResourceManager resources, RegistryAccess registries) {
        RegistryOps<JsonElement> ops = new ConditionalOps<>(RegistryOps.create(JsonOps.INSTANCE, registries), ICondition.IContext.EMPTY);

        List<Entry> rules = new ArrayList<>();
        read(resources, RULES, "rule", ops, EstimateRule.CODEC, (id, rule) -> {
            if (rule.enabled()) rules.add(new Entry(id, rule));
        });
        rules.sort(Comparator.comparingInt((Entry e) -> -e.rule().priority()).thenComparing(Entry::id));

        // A material defined outside our namespace replaces the shipped default; between two others, the first id wins.
        Map<String, Long> materials = new HashMap<>();
        Map<String, ResourceLocation> sources = new HashMap<>();
        read(resources, MATERIALS, "material", ops, MATERIAL, (id, emc) -> {
            ResourceLocation previous = sources.get(id.getPath());
            if (previous != null && id.getNamespace().equals(MOD_ID)) return; // a pack's file was read first and wins
            if (previous == null || previous.getNamespace().equals(MOD_ID)) {
                materials.put(id.getPath(), emc);
                sources.put(id.getPath(), id);
            } else {
                LOGGER.warn("Material '{}' is defined by both {} and {}; keeping {}", id.getPath(), previous, id, previous);
            }
        });

        List<ComponentRule> components = new ArrayList<>();
        read(resources, COMPONENTS, "component rule", ops, ComponentRule.CODEC, (id, rule) -> components.add(rule));
        return new RuleSet(List.copyOf(rules), Map.copyOf(materials), List.copyOf(components));
    }

    private static <T> void read(ResourceManager resources, FileToIdConverter files, String kind, DynamicOps<JsonElement> ops,
                                 Codec<T> codec, BiConsumer<ResourceLocation, T> sink) {
        for (Map.Entry<ResourceLocation, Resource> file : new TreeMap<>(files.listMatchingResources(resources)).entrySet()) {
            ResourceLocation id = files.fileToId(file.getKey());
            try (Reader reader = file.getValue().openAsReader()) {
                // empty when the file's neoforge:conditions don't hold
                DataResult<Optional<T>> result = ConditionalOps.createConditionalCodec(codec).parse(ops, JsonParser.parseReader(reader));
                result.error().ifPresent(error -> LOGGER.error("Skipping {} {}: {}", kind, id, error.message()));
                result.result().flatMap(value -> value).ifPresent(value -> sink.accept(id, value));
            } catch (IOException | RuntimeException e) {
                LOGGER.error("Couldn't read {}", file.getKey(), e);
            }
        }
    }
}
