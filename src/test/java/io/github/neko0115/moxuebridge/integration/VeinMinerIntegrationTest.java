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