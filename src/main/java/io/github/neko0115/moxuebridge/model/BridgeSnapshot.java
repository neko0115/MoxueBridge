package io.github.neko0115.moxuebridge.model;

import java.util.List;

public record BridgeSnapshot(
        String generatedAt,
        BridgeStatus status,
        List<PluginInfo> plugins,
        List<Capability> capabilities) {

    public BridgeSnapshot {
        plugins = List.copyOf(plugins);
        capabilities = List.copyOf(capabilities);
    }
}