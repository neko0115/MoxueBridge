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

class CapabilityManifestIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void discoversPluginDeclaredCapabilitiesWithoutCustomBridgeCode()
            throws Exception {

        Files.writeString(
                tempDir.resolve("moxue-capabilities.json"),
                """
                {
                  "capabilities": [
                    {
                      "id": "examplemod:crusher_processing",
                      "name": "Crusher processing",
                      "description": "Crush configured resources into processed items.",
                      "usage": {
                        "trigger": "interact",
                        "human": "Open the crusher and insert supported input items."
                      },
                      "constraints": {
                        "input_kind": "item",
                        "world_mutation": false,
                        "max_batch": 64
                      }
                    }
                  ]
                }
                """,
                StandardCharsets.UTF_8);

        var integration =
                new CapabilityManifestIntegration();

        var plugin =
                descriptor(true);

        assertTrue(integration.supports(plugin));

        var capabilities =
                integration.discoverCapabilities(plugin);

        assertEquals(1, capabilities.size());

        var capability =
                capabilities.get(0);

        assertEquals(
                "examplemod:crusher_processing",
                capability.id());
        assertEquals(
                "manifest",
                capability.source().provenance());
        assertEquals(
                "interact",
                capability.usage().trigger());
        assertEquals(
                "item",
                capability.constraints()
                        .get("input_kind"));
        assertEquals(
                false,
                capability.constraints()
                        .get("world_mutation"));
        assertEquals(
                64L,
                capability.constraints()
                        .get("max_batch"));
    }

    @Test
    void malformedNestedConstraintsFailClosed()
            throws Exception {

        Files.writeString(
                tempDir.resolve("moxue-capabilities.json"),
                """
                {
                  "capabilities": [
                    {
                      "id": "examplemod:unsafe",
                      "name": "Unsafe",
                      "description": "Unsafe nested payload.",
                      "usage": {
                        "trigger": "interact",
                        "human": "Do something."
                      },
                      "constraints": {
                        "nested": {
                          "arbitrary": "data"
                        }
                      }
                    }
                  ]
                }
                """,
                StandardCharsets.UTF_8);

        assertThrows(
                IntegrationException.class,
                () -> new CapabilityManifestIntegration()
                        .discoverCapabilities(
                                descriptor(true)));
    }

    @Test
    void absentManifestIsNotClaimed() {

        assertFalse(
                new CapabilityManifestIntegration()
                        .supports(
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
