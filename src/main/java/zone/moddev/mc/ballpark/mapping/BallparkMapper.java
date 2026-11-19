package zone.moddev.mc.ballpark.mapping;

import static zone.moddev.mc.ballpark.Ballpark.LOGGER;
import static zone.moddev.mc.ballpark.Ballpark.MOD_ID;

import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import moze_intel.projecte.api.mapper.EMCMapper;
import moze_intel.projecte.api.mapper.IEMCMapper;
import moze_intel.projecte.api.mapper.collector.IMappingCollector;
import moze_intel.projecte.api.nss.NSSFake;
import moze_intel.projecte.api.nss.NSSItem;
import moze_intel.projecte.api.nss.NormalizedSimpleStack;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import zone.moddev.mc.ballpark.rule.Decision;
import zone.moddev.mc.ballpark.rule.Proposal;
import zone.moddev.mc.ballpark.rule.RuleSet;

/**
 * Adds an estimate for every item a rule can price. Each estimate is one more way ProjectE can make the item,
 * so ProjectE keeps whichever is cheaper: the estimate or the item's recipes. Fixed values (ProjectE's own data,
 * custom EMC files) always win. ProjectE lists this mapper in its mapping config, so it can be switched off there.
 */
@EMCMapper
public final class BallparkMapper implements IEMCMapper<NormalizedSimpleStack, Long> {
    @Override
    public void addMappings(IMappingCollector<NormalizedSimpleStack, Long> collector, ReloadableServerResources resources,
                            RegistryAccess registries, ResourceManager resourceManager) {
        long start = System.nanoTime();
        RuleSet rules = RuleSet.load(resourceManager, registries);
        Set<Item> craftable = craftable(resources.getRecipeManager(), registries);
        Map<Item, Decision> decisions = new HashMap<>();
        int estimated = 0;

        NSSFake.setCurrentNamespace(MOD_ID);
        try {
            for (Holder.Reference<Item> holder : BuiltInRegistries.ITEM.holders().toList()) {
                Decision decision = rules.decide(holder, craftable).orElse(null);
                if (decision == null) continue;
                decisions.put(holder.value(), decision);
                if (decision.skipped()) continue;
                add(collector, holder, decision.proposal().orElseThrow());
                estimated++;
            }
        } finally {
            NSSFake.resetNamespace();
        }

        EstimateReport.publish(decisions);
        ComponentProcessor.publish(rules.components(), registries);
        LOGGER.info("Estimated EMC for {} items ({} rules, {} materials, {} component rules, {} ms)", estimated,
                rules.rules().size(), rules.materials().size(), rules.components().size(), (System.nanoTime() - start) / 1_000_000);
    }

    private static Set<Item> craftable(RecipeManager recipes, RegistryAccess registries) {
        Set<Item> craftable = new HashSet<>();
        for (RecipeHolder<?> recipe : recipes.getRecipes()) {
            try {
                craftable.add(recipe.value().getResultItem(registries).getItem());
            } catch (RuntimeException e) {
                // some modded recipes only know their result inside their own machine
            }
        }
        return craftable;
    }

    private static void add(IMappingCollector<NormalizedSimpleStack, Long> collector, Holder.Reference<Item> item, Proposal proposal) {
        NSSItem target = NSSItem.createItem(item);
        if (proposal.isValue()) {
            // A fake stack holding the value, converted 1:1 into the item. Setting the value on the item itself
            // would fix it and override its recipes.
            NSSFake estimate = NSSFake.create(item.key().location().toString());
            collector.setValueBefore(estimate, proposal.emc());
            collector.addConversion(1, target, List.of(estimate));
        }
        for (Item from : proposal.fromItems()) {
            collector.addConversion(proposal.makes(), target, Object2IntMaps.singleton(NSSItem.createItem(from), proposal.amount()));
        }
        proposal.fromTag().ifPresent(tag ->
                collector.addConversion(proposal.makes(), target, Object2IntMaps.singleton(NSSItem.createTag(tag), proposal.amount())));
    }

    @Override
    public String getName() {
        return "Ballpark";
    }

    @Override
    public String getTranslationKey() {
        return "mapper." + MOD_ID;
    }

    @Override
    public String getDescription() {
        return "Estimates EMC for modded items from the ballpark datapack rules.";
    }
}
