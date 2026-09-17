package io.github.neko0115.moxuebridge.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.neko0115.moxuebridge.integration.IntegrationException;
import io.github.neko0115.moxuebridge.integration.PluginIntegration;
import io.github.neko0115.moxuebridge.model.BridgeStatus;
import io.github.neko0115.moxuebridge.model.Capability;
import io.github.neko0115.moxuebridge.model.CapabilitySource;
import io.github.neko0115.moxuebridge.model.CapabilityUsage;
import io.github.neko0115.moxuebridge.model.CommandInfo;
import io.github.neko0115.moxuebridge.model.RuntimePluginDescriptor;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

class RegistryBuilderTest {

    private static final BridgeStatus STATUS =
            new BridgeStatus(
                    "MoxueBridge",
                    "0.1.0",
                    "1.21.1",
                    "Paper",
                    true);

    private static final String NOW =
            "2026-09-17T10:00:00Z";

    @Test
    void enabledIntegratedPluginProducesPluginRecordAndCapabilities() {
        var catalogCalls = new AtomicInteger();

        PluginCatalog catalog = () -> {
            catalogCalls.incrementAndGet();
            return List.of(
                    descriptor(
                            "VeinMiner",
                            "2.11.2",
                            true,
                            List.of()));
        };

        var builder = builder(
                catalog,
                List.of(new FakeVeinMinerIntegration()));

        var snapshot = builder.build();

        assertEquals(1, catalogCalls.get());
        assertEquals(NOW, snapshot.generatedAt());
        assertEquals(STATUS, snapshot.status());

        assertEquals(1, snapshot.plugins().size());
        assertEquals("VeinMiner", snapshot.plugins().get(0).name());
        assertTrue(snapshot.plugins().get(0).enabled());
        assertTrue(snapshot.plugins().get(0).integrated());

        assertEquals(
                List.of("vein_mining"),
                snapshot.capabilities()
                        .stream()
                        .map(Capability::id)
                        .toList());
    }

    @Test
    void disabledIntegratedPluginDoesNotExposeCapabilities() {
        PluginCatalog catalog = () -> List.of(
                descriptor(
                        "VeinMiner",
                        "2.11.2",
                        false,
                        List.of()));

        var snapshot = builder(
                catalog,
                List.of(new FakeVeinMinerIntegration()))
                .build();

        assertEquals(1, snapshot.plugins().size());
        assertFalse(snapshot.plugins().get(0).enabled());
        assertTrue(snapshot.plugins().get(0).integrated());
        assertTrue(snapshot.capabilities().isEmpty());
    }

    @Test
    void unknownPluginKeepsCommandMetadataWithoutInventingCapability() {
        var commands = List.of(
                new CommandInfo(
                        "home",
                        "Teleport to a saved home",
                        "/home",
                        "homes.use",
                        List.of()));

        PluginCatalog catalog = () -> List.of(
                descriptor(
                        "HomesPlus",
                        "1.5.2",
                        true,
                        commands));

        var snapshot = builder(
                catalog,
                List.of(new FakeVeinMinerIntegration()))
                .build();

        assertEquals(1, snapshot.plugins().size());

        var plugin = snapshot.plugins().get(0);

        assertEquals("HomesPlus", plugin.name());
        assertTrue(plugin.enabled());
        assertFalse(plugin.integrated());
        assertEquals(commands, plugin.commands());

        assertTrue(snapshot.capabilities().isEmpty());
    }

    @Test
    void failedIntegrationDoesNotAbortSnapshot() {
        PluginCatalog catalog = () -> List.of(
                descriptor(
                        "VeinMiner",
                        "2.11.2",
                        true,
                        List.of()));

        PluginIntegration failing =
                new PluginIntegration() {
                    @Override
                    public boolean supports(
                            RuntimePluginDescriptor plugin) {
                        return "VeinMiner".equalsIgnoreCase(
                                plugin.name());
                    }

                    @Override
                    public List<Capability> discoverCapabilities(
                            RuntimePluginDescriptor plugin)
                            throws IntegrationException {

                        throw new IntegrationException(
                                "synthetic failure");
                    }
                };

        var snapshot = builder(
                catalog,
                List.of(failing))
                .build();

        assertEquals(1, snapshot.plugins().size());
        assertTrue(snapshot.plugins().get(0).integrated());
        assertTrue(snapshot.capabilities().isEmpty());
    }

    private RegistryBuilder builder(
            PluginCatalog catalog,
            List<PluginIntegration> integrations) {

        return new RegistryBuilder(
                catalog,
                integrations,
                () -> STATUS,
                () -> NOW,
                Logger.getLogger(
                        RegistryBuilderTest.class.getName()));
    }

    private RuntimePluginDescriptor descriptor(
            String name,
            String version,
            boolean enabled,
            List<CommandInfo> commands) {

        return new RuntimePluginDescriptor(
                name,
                version,
                enabled,
                Path.of("plugins", name),
                commands);
    }

    private static final class FakeVeinMinerIntegration
            implements PluginIntegration {

        @Override
        public boolean supports(
                RuntimePluginDescriptor plugin) {

            return "VeinMiner".equalsIgnoreCase(
                    plugin.name());
        }

        @Override
        public List<Capability> discoverCapabilities(
                RuntimePluginDescriptor plugin) {

            return List.of(
                    new Capability(
                            "vein_mining",
                            "連鎖挖礦",
                            "一次挖掘相連的礦物方塊",
                            true,
                            new CapabilitySource(
                                    "VeinMiner",
                                    plugin.version(),
                                    "integration"),
                            new CapabilityUsage(
                                    "sneak_and_break",
                                    "蹲下並使用十字鎬挖掘礦物"),
                            Map.of(
                                    "max_chain", 100)));
        }
    }
}