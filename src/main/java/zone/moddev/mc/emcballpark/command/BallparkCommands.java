package zone.moddev.mc.emcballpark.command;

import static zone.moddev.mc.emcballpark.EMCBallpark.MOD_ID;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import moze_intel.projecte.api.proxy.IEMCProxy;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;
import zone.moddev.mc.emcballpark.mapping.EstimateReport;
import zone.moddev.mc.emcballpark.rule.Decision;

/**
 * {@code /emcballpark explain [item]}: final EMC and which rule estimated it.
 * {@code /emcballpark gaps [namespace]}: items still without EMC that no rule skips.
 * {@code /emcballpark export}: every item's EMC and rule as a CSV in {@code logs/}.
 */
public final class BallparkCommands {
    private static final int GAPS_SHOWN = 10;
    private static final String NOT_READY = "ProjectE hasn't run its mappers yet (or it's using pregenerated values)";

    private BallparkCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(MOD_ID)
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("explain")
                        .executes(c -> explain(c.getSource(), c.getSource().getPlayerOrException().getMainHandItem().getItem()))
                        .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                                .executes(c -> explain(c.getSource(), ItemArgument.getItem(c, "item").getItem()))))
                .then(Commands.literal("gaps")
                        .executes(c -> gaps(c.getSource(), null))
                        .then(Commands.argument("namespace", StringArgumentType.word())
                                .executes(c -> gaps(c.getSource(), StringArgumentType.getString(c, "namespace")))))
                .then(Commands.literal("export").executes(c -> export(c.getSource()))));
    }

    private static int explain(CommandSourceStack source, Item item) throws CommandSyntaxException {
        long emc = IEMCProxy.INSTANCE.getValue(item);
        Decision decision = EstimateReport.get(item);
        String line = BuiltInRegistries.ITEM.getKey(item) + ": " + (emc > 0 ? String.format("%,d EMC", emc) : "no EMC");
        String why = !EstimateReport.ready() ? NOT_READY : decision == null ? "no rule matched" : decision.describe();
        source.sendSuccess(() -> Component.literal(line), false);
        source.sendSuccess(() -> Component.literal("  " + why), false);
        return emc > 0 ? 1 : 0;
    }

    private static int gaps(CommandSourceStack source, @Nullable String namespace) {
        if (!EstimateReport.ready()) {
            source.sendFailure(Component.literal(NOT_READY));
            return 0;
        }
        List<ResourceLocation> gaps = new ArrayList<>();
        Map<String, Integer> perNamespace = new TreeMap<>();
        for (Holder.Reference<Item> item : BuiltInRegistries.ITEM.holders().toList()) {
            ResourceLocation id = item.key().location();
            Decision decision = EstimateReport.get(item.value());
            if ((decision != null && decision.skipped()) || IEMCProxy.INSTANCE.hasValue(item)) continue;
            if (namespace != null && !id.getNamespace().equals(namespace)) continue;
            gaps.add(id);
            perNamespace.merge(id.getNamespace(), 1, Integer::sum);
        }
        gaps.sort(null);

        source.sendSuccess(() -> Component.literal(gaps.size() + " items without EMC " + perNamespace), false);
        gaps.stream().limit(GAPS_SHOWN).forEach(id -> source.sendSuccess(() -> Component.literal("  " + id), false));
        if (gaps.size() > GAPS_SHOWN) source.sendSuccess(() -> Component.literal("  ... full list: /" + MOD_ID + " export"), false);
        return gaps.size();
    }

    private static int export(CommandSourceStack source) {
        Path file = source.getServer().getServerDirectory().resolve("logs").resolve(MOD_ID + "-report.csv");
        List<String> lines = new ArrayList<>(List.of("item,emc,rule,estimate,detail"));
        for (Holder.Reference<Item> item : BuiltInRegistries.ITEM.holders().toList()) {
            Decision decision = EstimateReport.get(item.value());
            lines.add(String.join(",", item.key().location().toString(),
                    Long.toString(IEMCProxy.INSTANCE.getValue(item)),
                    decision == null ? "" : decision.rule().toString(),
                    decision == null ? "" : decision.proposal().map(p -> p.isValue() ? Long.toString(p.emc()) : "conversion").orElse("skip"),
                    csv(decision == null ? "" : decision.proposal().map(p -> p.detail()).orElse(""))));
        }
        lines.subList(1, lines.size()).sort(null);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, lines);
        } catch (IOException e) {
            source.sendFailure(Component.literal("Couldn't write " + file + ": " + e.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Wrote " + (lines.size() - 1) + " items to " + file.toAbsolutePath().normalize()), false);
        return lines.size() - 1;
    }

    private static String csv(String text) {
        return text.contains(",") || text.contains("\"") ? '"' + text.replace("\"", "\"\"") + '"' : text;
    }
}
