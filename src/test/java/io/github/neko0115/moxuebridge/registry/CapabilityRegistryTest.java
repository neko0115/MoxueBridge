package io.github.neko0115.moxuebridge.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.neko0115.moxuebridge.model.BridgeSnapshot;
import io.github.neko0115.moxuebridge.model.BridgeStatus;
import io.github.neko0115.moxuebridge.model.Capability;
import io.github.neko0115.moxuebridge.model.CapabilitySource;
import io.github.neko0115.moxuebridge.model.CapabilityUsage;
import io.github.neko0115.moxuebridge.model.CommandInfo;
import io.github.neko0115.moxuebridge.model.PluginInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CapabilityRegistryTest {

    @Test
    void snapshotDefensivelyCopiesDataAndRegistrySwapsAtomically() {
        var commands = new ArrayList<CommandInfo>();

        var plugin = new PluginInfo(
                "VeinMiner",
                "2.11.2",
                true,
                true,
                commands);

        var plugins = new ArrayList<PluginInfo>();
        plugins.add(plugin);

        var constraints = new HashMap<String, Object>();
        constraints.put("max_chain", 100);

        var capability = new Capability(
                "vein_mining",
                "連鎖挖礦",
                "一次挖掘相連的礦物方塊",
                true,
                new CapabilitySource(
                        "VeinMiner",
                        "2.11.2",
                        "integration"),
                new CapabilityUsage(
                        "sneak_and_break",
                        "蹲下並使用正確的十字鎬挖掘礦物"),
                constraints);

        var capabilities = new ArrayList<Capability>();
        capabilities.add(capability);

        var first = new BridgeSnapshot(
                "2026-09-17T09:00:00Z",
                new BridgeStatus(
                        "MoxueBridge",
                        "0.1.0",
                        "1.21.1",
                        "Paper",
                        true),
                plugins,
                capabilities);

        var registry = new CapabilityRegistry(first);

        // Mutating the caller's collections must not mutate the snapshot.
        commands.add(new CommandInfo(
                "fake",
                "fake",
                "/fake",
                "",
                List.of()));

        plugins.clear();
        capabilities.clear();
        constraints.put("correct_tool_required", true);

        assertEquals(1, registry.snapshot().plugins().size());
        assertEquals(0, registry.snapshot().plugins().get(0).commands().size());

        assertEquals(1, registry.snapshot().capabilities().size());
        assertEquals(
                Map.of("max_chain", 100),
                registry.snapshot().capabilities().get(0).constraints());

        assertThrows(
                UnsupportedOperationException.class,
                () -> registry.snapshot().plugins().add(null));

        assertThrows(
                UnsupportedOperationException.class,
                () -> registry.snapshot()
                        .capabilities()
                        .get(0)
                        .constraints()
                        .put("x", 1));

        var replacement = new BridgeSnapshot(
                "2026-09-17T09:01:00Z",
                new BridgeStatus(
                        "MoxueBridge",
                        "0.1.0",
                        "1.21.1",
                        "Paper",
                        true),
                List.of(),
                List.of());

        registry.replace(replacement);

        assertSame(replacement, registry.snapshot());
    }
}