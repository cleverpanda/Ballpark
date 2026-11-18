package zone.moddev.mc.emcballpark.rule;

import static zone.moddev.mc.emcballpark.EMCBallpark.LOGGER;
import static zone.moddev.mc.emcballpark.EMCBallpark.MOD_ID;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
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

/**
 * The rules and material values from the loaded datapacks, read fresh each time ProjectE maps.
 * Materials live in {@code data/<namespace>/emcballpark/materials/<material>.json} as {@code {"emc": 256}}.
 */
public record RuleSet(List<Entry> rules, Map<String, Long> materials) {
    private static final FileToIdConverter RULES = FileToIdConverter.json(MOD_ID + "/rules");
    private static final FileToIdConverter MATERIALS = FileToIdConverter.json(MOD_ID + "/materials");
    private static final Codec<Long> MATERIAL = Estimate.POSITIVE_LONG.fieldOf("emc").codec();

    public record Entry(ResourceLocation id, EstimateRule rule) {}

    /**
     * The first rule that matches the item and either skips it or can price it; empty when none does.
     *
     * @param craftable every item some recipe makes
     */
    public Optional<Decision> decide(Holder.Reference<Item> item, Set<Item> craftable) {
        for (Entry entry : rules) {
            Optional<String> material = entry.rule().match().test(item, craftable);
            if (material.isEmpty()) continue;
            Estimate estimate = entry.rule().estimate();
            if (estimate instanceof Estimate.Skip) return Optional.of(new Decision(entry.id(), Optional.empty()));
            Optional<Proposal> proposal = estimate.propose(item, material.get(), materials);
            if (proposal.isPresent()) return Optional.of(new Decision(entry.id(), proposal));
        }
        return Optional.empty();
    }

    public static RuleSet load(ResourceManager resources, RegistryAccess registries) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);

        List<Entry> rules = new ArrayList<>();
        read(resources, RULES, ops, EstimateRule.CODEC, (id, rule) -> {
            if (rule.enabled()) rules.add(new Entry(id, rule));
        });
        rules.sort(Comparator.comparingInt((Entry e) -> -e.rule().priority()).thenComparing(Entry::id));

        // The same material from several namespaces: anyone's beats the shipped default, otherwise first wins.
        Map<String, Long> materials = new HashMap<>();
        Map<String, ResourceLocation> sources = new HashMap<>();
        read(resources, MATERIALS, ops, MATERIAL, (id, emc) -> {
            ResourceLocation previous = sources.get(id.getPath());
            if (previous == null || previous.getNamespace().equals(MOD_ID)) {
                materials.put(id.getPath(), emc);
                sources.put(id.getPath(), id);
            } else {
                LOGGER.warn("Material '{}' is defined by both {} and {}; keeping {}", id.getPath(), previous, id, previous);
            }
        });
        return new RuleSet(List.copyOf(rules), Map.copyOf(materials));
    }

    private static <T> void read(ResourceManager resources, FileToIdConverter files, DynamicOps<JsonElement> ops,
                                 Codec<T> codec, BiConsumer<ResourceLocation, T> sink) {
        for (Map.Entry<ResourceLocation, Resource> file : new TreeMap<>(files.listMatchingResources(resources)).entrySet()) {
            ResourceLocation id = files.fileToId(file.getKey());
            try (Reader reader = file.getValue().openAsReader()) {
                codec.parse(ops, JsonParser.parseReader(reader))
                        .resultOrPartial(error -> LOGGER.error("Skipping {} {}: {}", files == RULES ? "rule" : "material", id, error))
                        .ifPresent(value -> sink.accept(id, value));
            } catch (IOException | RuntimeException e) {
                LOGGER.error("Couldn't read {}", file.getKey(), e);
            }
        }
    }
}
