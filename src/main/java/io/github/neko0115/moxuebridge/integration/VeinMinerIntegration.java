package io.github.neko0115.moxuebridge.integration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.neko0115.moxuebridge.model.Capability;
import io.github.neko0115.moxuebridge.model.CapabilitySource;
import io.github.neko0115.moxuebridge.model.CapabilityUsage;
import io.github.neko0115.moxuebridge.model.RuntimePluginDescriptor;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class VeinMinerIntegration
        implements PluginIntegration {

    private static final String PLUGIN_NAME = "VeinMiner";

    @Override
    public boolean supports(
            RuntimePluginDescriptor plugin) {

        return PLUGIN_NAME.equalsIgnoreCase(plugin.name());
    }

    @Override
    public List<Capability> discoverCapabilities(
            RuntimePluginDescriptor plugin)
            throws IntegrationException {

        if (!supports(plugin) || !plugin.enabled()) {
            return List.of();
        }

        try {
            JsonObject settings = readObject(
                    plugin.dataFolder()
                            .resolve("settings.json"));

            JsonArray groups = readArray(
                    plugin.dataFolder()
                            .resolve("groups.json"));

            boolean mustSneak =
                    settings.get("mustSneak").getAsBoolean();

            int maxChain =
                    settings.get("maxChain").getAsInt();

            boolean needCorrectTool =
                    settings.get("needCorrectTool")
                            .getAsBoolean();

            boolean separateGroupMining =
                    settings.has("separateGroupMining")
                            && settings.get("separateGroupMining")
                                    .getAsBoolean();

            boolean mergeItemDrops =
                    settings.has("mergeItemDrops")
                            && settings.get("mergeItemDrops")
                                    .getAsBoolean();

            List<Capability> capabilities =
                    new ArrayList<>();

            for (JsonElement element : groups) {
                JsonObject group =
                        element.getAsJsonObject();

                String name =
                        group.get("name").getAsString();

                JsonObject override =
                        group.has("override")
                                && group.get("override").isJsonObject()
                                ? group.getAsJsonObject("override")
                                : new JsonObject();

                boolean effectiveMustSneak =
                        booleanOverride(
                                override,
                                "mustSneak",
                                mustSneak);

                int effectiveMaxChain =
                        intOverride(
                                override,
                                "maxChain",
                                maxChain);

                boolean effectiveNeedCorrectTool =
                        booleanOverride(
                                override,
                                "needCorrectTool",
                                needCorrectTool);

                boolean effectiveSeparateGroupMining =
                        booleanOverride(
                                override,
                                "separateGroupMining",
                                separateGroupMining);

                boolean effectiveSameBlockOnly =
                        effectiveSeparateGroupMining
                                || hasSingleExplicitBlock(group);

                if ("Ores".equalsIgnoreCase(name)) {
                    capabilities.add(
                            veinMiningCapability(
                                    plugin,
                                    effectiveMustSneak,
                                    effectiveMaxChain,
                                    effectiveNeedCorrectTool,
                                    effectiveSameBlockOnly,
                                    mergeItemDrops));
                }

                if ("Logs".equalsIgnoreCase(name)) {
                    capabilities.add(
                            treeFellingCapability(
                                    plugin,
                                    effectiveMustSneak,
                                    effectiveMaxChain,
                                    effectiveNeedCorrectTool,
                                    effectiveSameBlockOnly,
                                    mergeItemDrops));
                }
            }

            return List.copyOf(capabilities);

        } catch (IOException | RuntimeException ex) {
            throw new IntegrationException(
                    "Failed to read VeinMiner configuration",
                    ex);
        }
    }

    private Capability veinMiningCapability(
            RuntimePluginDescriptor plugin,
            boolean mustSneak,
            int maxChain,
            boolean needCorrectTool,
            boolean sameBlockOnly,
            boolean mergeItemDrops) {

        String trigger =
                mustSneak
                        ? "sneak_and_break"
                        : "break";

        String human =
                mustSneak
                        ? "蹲下並使用正確的十字鎬挖掘相連礦物"
                        : "使用正確的十字鎬挖掘相連礦物";

        return new Capability(
                "vein_mining",
                "連鎖挖礦",
                "一次挖掘相連的礦物方塊",
                true,
                source(plugin),
                new CapabilityUsage(
                        trigger,
                        human),
                commonConstraints(
                        maxChain,
                        needCorrectTool,
                        mustSneak,
                        sameBlockOnly,
                        mergeItemDrops,
                        "pickaxe"));
    }

    private Capability treeFellingCapability(
            RuntimePluginDescriptor plugin,
            boolean mustSneak,
            int maxChain,
            boolean needCorrectTool,
            boolean sameBlockOnly,
            boolean mergeItemDrops) {

        String trigger =
                mustSneak
                        ? "sneak_and_break"
                        : "break";

        String human =
                mustSneak
                        ? "蹲下並使用斧頭砍伐相連原木"
                        : "使用斧頭砍伐相連原木";

        return new Capability(
                "tree_felling",
                "連鎖伐木",
                "一次砍伐相連的原木方塊",
                true,
                source(plugin),
                new CapabilityUsage(
                        trigger,
                        human),
                commonConstraints(
                        maxChain,
                        needCorrectTool,
                        mustSneak,
                        sameBlockOnly,
                        mergeItemDrops,
                        "axe"));
    }

    private CapabilitySource source(
            RuntimePluginDescriptor plugin) {

        return new CapabilitySource(
                PLUGIN_NAME,
                plugin.version(),
                "integration");
    }

    private Map<String, Object> commonConstraints(
            int maxChain,
            boolean needCorrectTool,
            boolean mustSneak,
            boolean sameBlockOnly,
            boolean mergeItemDrops,
            String toolKind) {

        return Map.<String, Object>of(
                "max_chain", maxChain,
                "correct_tool_required", needCorrectTool,
                "must_sneak", mustSneak,
                "same_block_only", sameBlockOnly,
                "merge_item_drops", mergeItemDrops,
                "tool_kind", toolKind);
    }

    private boolean hasSingleExplicitBlock(
            JsonObject group) {

        if (!group.has("blocks")
                || !group.get("blocks").isJsonArray()) {
            return false;
        }

        JsonArray blocks =
                group.getAsJsonArray("blocks");

        if (blocks.size() != 1) {
            return false;
        }

        JsonElement only =
                blocks.get(0);

        if (!only.isJsonPrimitive()
                || !only.getAsJsonPrimitive().isString()) {
            return false;
        }

        String selector =
                only.getAsString().trim();

        return !selector.isEmpty()
                && !selector.startsWith("#");
    }

    private boolean booleanOverride(
            JsonObject override,
            String key,
            boolean fallback) {

        return override.has(key)
                && !override.get(key).isJsonNull()
                ? override.get(key).getAsBoolean()
                : fallback;
    }

    private int intOverride(
            JsonObject override,
            String key,
            int fallback) {

        return override.has(key)
                && !override.get(key).isJsonNull()
                ? override.get(key).getAsInt()
                : fallback;
    }

    private JsonObject readObject(Path path)
            throws IOException {

        try (Reader reader = Files.newBufferedReader(
                path,
                StandardCharsets.UTF_8)) {

            return JsonParser
                    .parseReader(reader)
                    .getAsJsonObject();
        }
    }

    private JsonArray readArray(Path path)
            throws IOException {

        try (Reader reader = Files.newBufferedReader(
                path,
                StandardCharsets.UTF_8)) {

            return JsonParser
                    .parseReader(reader)
                    .getAsJsonArray();
        }
    }
}