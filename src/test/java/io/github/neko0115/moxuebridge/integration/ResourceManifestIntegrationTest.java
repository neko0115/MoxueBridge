package io.github.neko0115.moxuebridge.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.neko0115.moxuebridge.model.RuntimePluginDescriptor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResourceManifestIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void discoversBoundedAuthoritativeResourceSemantics()
            throws Exception {

        Files.writeString(
                tempDir.resolve("moxue-resources.json"),
                """
                {
                  "resources": [
                    {
                      "id": "examplemod:rubber_log",
                      "kind": "log",
                      "aliases": [
                        "examplemod:rubber_wood"
                      ],
                      "block_ids": [
                        "examplemod:rubber_log"
                      ],
                      "collected_item_ids": [
                        "examplemod:rubber_log"
                      ],
                      "minimum_drop_count": 1,
                      "tool_kind": "axe",
                      "forbidden_enchantments": [],
                      "capability_id": "tree_felling",
                      "related_blocks": {
                        "leaves": [
                          "examplemod:rubber_leaves"
                        ]
                      },
                      "cleanup_policy": "natural_decay"
                    }
                  ]
                }
                """,
                StandardCharsets.UTF_8);

        var integration =
                new ResourceManifestIntegration();

        var plugin =
                descriptor(true);

        assertTrue(integration.supports(plugin));

        var resources =
                integration.discoverResources(plugin);

        assertEquals(1, resources.size());

        var resource =
                resources.get(0);

        assertEquals(
                "examplemod:rubber_log",
                resource.id());
        assertEquals(
                "log",
                resource.kind());
        assertEquals(
                List.of("examplemod:rubber_log"),
                resource.blockIds());
        assertEquals(
                List.of("examplemod:rubber_log"),
                resource.collectedItemIds());
        assertEquals(
                1,
                resource.minimumDropCount());
        assertEquals(
                "axe",
                resource.toolKind());
        assertEquals(
                "tree_felling",
                resource.capabilityId());
        assertEquals(
                List.of("examplemod:rubber_leaves"),
                resource.relatedBlocks()
                        .get("leaves"));
        assertEquals(
                "natural_decay",
                resource.cleanupPolicy());
        assertEquals(
                "authoritative",
                resource.confidence());
    }

    @Test
    void disabledPluginPublishesNoResources()
            throws Exception {

        Files.writeString(
                tempDir.resolve("moxue-resources.json"),
                """
                {
                  "resources": []
                }
                """,
                StandardCharsets.UTF_8);

        var integration =
                new ResourceManifestIntegration();

        assertTrue(
                integration
                        .discoverResources(
                                descriptor(false))
                        .isEmpty());
    }

    @Test
    void malformedOrUnsafeManifestFailsClosed()
            throws Exception {

        Files.writeString(
                tempDir.resolve("moxue-resources.json"),
                """
                {
                  "resources": [
                    {
                      "id": "not-namespaced",
                      "kind": "ore",
                      "block_ids": ["examplemod:ore"],
                      "collected_item_ids": ["examplemod:raw"],
                      "minimum_drop_count": 1
                    }
                  ]
                }
                """,
                StandardCharsets.UTF_8);

        var integration =
                new ResourceManifestIntegration();

        assertThrows(
                IntegrationException.class,
                () -> integration.discoverResources(
                        descriptor(true)));
    }

    @Test
    void absentManifestIsNotClaimedAsAnIntegration() {

        var integration =
                new ResourceManifestIntegration();

        assertFalse(
                integration.supports(
                        descriptor(true)));
    }

    private RuntimePluginDescriptor descriptor(
            boolean enabled) {

        return new RuntimePluginDescriptor(
                "ExampleMod",
                "1.0.0",
                enabled,
                tempDir,
                List.of());
    }
}
