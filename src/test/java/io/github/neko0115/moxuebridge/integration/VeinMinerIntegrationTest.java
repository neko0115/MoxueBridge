package io.github.neko0115.moxuebridge.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.neko0115.moxuebridge.model.Capability;
import io.github.neko0115.moxuebridge.model.RuntimePluginDescriptor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VeinMinerIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void supportsVeinMinerCaseInsensitively() {
        var integration = new VeinMinerIntegration();

        assertTrue(integration.supports(descriptor("VeinMiner", true)));
        assertTrue(integration.supports(descriptor("Veinminer", true)));
        assertFalse(integration.supports(descriptor("SomethingElse", true)));
    }

    @Test
    void discoversOnlyVeinMiningFromCurrentOresConfiguration()
            throws Exception {

        installResource(
                "veinminer/ores-only/settings.json",
                "settings.json");

        installResource(
                "veinminer/ores-only/groups.json",
                "groups.json");

        var integration = new VeinMinerIntegration();

        var capabilities = integration.discoverCapabilities(
                descriptor("VeinMiner", true));

        assertEquals(List.of("vein_mining"), sortedIds(capabilities));

        var capability = capabilities.get(0);

        assertEquals("連鎖挖礦", capability.name());
        assertEquals("integration", capability.source().provenance());
        assertEquals("VeinMiner", capability.source().plugin());

        assertEquals(
                "sneak_and_break",
                capability.usage().trigger());

        assertEquals(
                100,
                capability.constraints().get("max_chain"));

        assertEquals(
                true,
                capability.constraints().get("correct_tool_required"));

        assertEquals(
                true,
                capability.constraints().get("must_sneak"));

        assertEquals(
                false,
                capability.constraints().get("same_block_only"));

        assertEquals(
                false,
                capability.constraints().get("merge_item_drops"));

        assertEquals(
                "pickaxe",
                capability.constraints().get("tool_kind"));

        assertFalse(
                capability.constraints()
                        .containsKey("exact_block"));
    }

    @Test
    void discoversTreeFellingWhenLogsGroupExists()
            throws Exception {

        installResource(
                "veinminer/ores-only/settings.json",
                "settings.json");

        installResource(
                "veinminer/ores-and-logs/groups.json",
                "groups.json");

        var integration = new VeinMinerIntegration();

        var capabilities = integration.discoverCapabilities(
                descriptor("VeinMiner", true));

        assertEquals(
                List.of("tree_felling", "vein_mining"),
                sortedIds(capabilities));
    }



    @Test
    void resolvesSeparateGroupMiningAndGroupOverrides()
            throws Exception {

        Files.writeString(
                tempDir.resolve("settings.json"),
                """
                {
                  "mustSneak": true,
                  "maxChain": 8,
                  "needCorrectTool": true,
                  "mergeItemDrops": true,
                  "separateGroupMining": true
                }
                """,
                StandardCharsets.UTF_8);

        Files.writeString(
                tempDir.resolve("groups.json"),
                """
                [
                  {
                    "name": "Ores",
                    "blocks": ["#c:ores"],
                    "tools": ["#minecraft:pickaxes"],
                    "override": {}
                  },
                  {
                    "name": "Logs",
                    "blocks": ["#minecraft:logs"],
                    "tools": ["#minecraft:axes"],
                    "override": {
                      "mustSneak": false,
                      "maxChain": 3,
                      "needCorrectTool": false,
                      "separateGroupMining": false
                    }
                  }
                ]
                """,
                StandardCharsets.UTF_8);

        var integration = new VeinMinerIntegration();

        var capabilities = integration.discoverCapabilities(
                descriptor("VeinMiner", true));

        var veinMining = capabilities.stream()
                .filter(capability ->
                        capability.id().equals("vein_mining"))
                .findFirst()
                .orElseThrow();

        assertEquals(
                "sneak_and_break",
                veinMining.usage().trigger());
        assertEquals(
                8,
                veinMining.constraints().get("max_chain"));
        assertEquals(
                true,
                veinMining.constraints().get("correct_tool_required"));
        assertEquals(
                true,
                veinMining.constraints().get("must_sneak"));
        assertEquals(
                true,
                veinMining.constraints().get("same_block_only"));
        assertEquals(
                true,
                veinMining.constraints().get("merge_item_drops"));
        assertEquals(
                "pickaxe",
                veinMining.constraints().get("tool_kind"));
        assertFalse(
                veinMining.constraints()
                        .containsKey("exact_block"));

        var treeFelling = capabilities.stream()
                .filter(capability ->
                        capability.id().equals("tree_felling"))
                .findFirst()
                .orElseThrow();

        assertEquals(
                "break",
                treeFelling.usage().trigger());
        assertEquals(
                3,
                treeFelling.constraints().get("max_chain"));
        assertEquals(
                false,
                treeFelling.constraints().get("correct_tool_required"));
        assertEquals(
                false,
                treeFelling.constraints().get("must_sneak"));
        assertEquals(
                false,
                treeFelling.constraints().get("same_block_only"));
        assertEquals(
                true,
                treeFelling.constraints().get("merge_item_drops"));
        assertEquals(
                "axe",
                treeFelling.constraints().get("tool_kind"));
    }



    @Test
    void singleExplicitBlockGroupIsSameBlockOnlyWithoutSeparateGroupMining()
            throws Exception {

        Files.writeString(
                tempDir.resolve("settings.json"),
                """
                {
                  "mustSneak": true,
                  "maxChain": 100,
                  "needCorrectTool": true,
                  "mergeItemDrops": false
                }
                """,
                StandardCharsets.UTF_8);

        Files.writeString(
                tempDir.resolve("groups.json"),
                """
                [
                  {
                    "name": "Logs",
                    "blocks": ["minecraft:oak_log"],
                    "tools": ["#minecraft:axes"],
                    "override": {
                      "maxChain": 4
                    }
                  }
                ]
                """,
                StandardCharsets.UTF_8);

        var integration = new VeinMinerIntegration();

        var capabilities = integration.discoverCapabilities(
                descriptor("VeinMiner", true));

        assertEquals(
                List.of("tree_felling"),
                sortedIds(capabilities));

        var capability = capabilities.get(0);

        assertEquals(
                true,
                capability.constraints().get("same_block_only"));
        assertEquals(
                4,
                capability.constraints().get("max_chain"));
        assertEquals(
                "axe",
                capability.constraints().get("tool_kind"));
        assertEquals(
                "minecraft:oak_log",
                capability.constraints().get("exact_block"));
    }

    @Test
    void singleExplicitOreGroupPublishesExactBlockScope()
            throws Exception {

        Files.writeString(
                tempDir.resolve("settings.json"),
                """
                {
                  "mustSneak": true,
                  "maxChain": 100,
                  "needCorrectTool": true,
                  "mergeItemDrops": false
                }
                """,
                StandardCharsets.UTF_8);

        Files.writeString(
                tempDir.resolve("groups.json"),
                """
                [
                  {
                    "name": "Ores",
                    "blocks": ["minecraft:iron_ore"],
                    "tools": ["#minecraft:pickaxes"],
                    "override": {
                      "maxChain": 4
                    }
                  }
                ]
                """,
                StandardCharsets.UTF_8);

        var integration = new VeinMinerIntegration();

        var capability = integration
                .discoverCapabilities(
                        descriptor("VeinMiner", true))
                .get(0);

        assertEquals(
                "vein_mining",
                capability.id());
        assertEquals(
                true,
                capability.constraints().get("same_block_only"));
        assertEquals(
                4,
                capability.constraints().get("max_chain"));
        assertEquals(
                "minecraft:iron_ore",
                capability.constraints().get("exact_block"));
    }

    @Test
    void disabledPluginProducesNoCapabilities()
            throws Exception {

        installResource(
                "veinminer/ores-only/settings.json",
                "settings.json");

        installResource(
                "veinminer/ores-only/groups.json",
                "groups.json");

        var integration = new VeinMinerIntegration();

        assertTrue(
                integration.discoverCapabilities(
                        descriptor("VeinMiner", false))
                        .isEmpty());
    }

    @Test
    void malformedConfigurationFailsClosed()
            throws Exception {

        installResource(
                "veinminer/ores-only/settings.json",
                "settings.json");

        Files.writeString(
                tempDir.resolve("groups.json"),
                "{ definitely-not-valid-json",
                StandardCharsets.UTF_8);

        var integration = new VeinMinerIntegration();

        assertThrows(
                IntegrationException.class,
                () -> integration.discoverCapabilities(
                        descriptor("VeinMiner", true)));
    }

    private RuntimePluginDescriptor descriptor(
            String name,
            boolean enabled) {

        return new RuntimePluginDescriptor(
                name,
                "2.11.2",
                enabled,
                tempDir,
                List.of());
    }

    private List<String> sortedIds(
            List<Capability> capabilities) {

        return capabilities.stream()
                .map(Capability::id)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private void installResource(
            String resource,
            String targetFileName)
            throws IOException {

        try (InputStream input = getClass()
                .getClassLoader()
                .getResourceAsStream(resource)) {

            if (input == null) {
                throw new IOException(
                        "Missing test resource: " + resource);
            }

            Files.copy(
                    input,
                    tempDir.resolve(targetFileName),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }
}