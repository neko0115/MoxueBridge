package io.github.neko0115.moxuebridge.model;

import java.util.List;

public record BridgeSnapshot(
        String generatedAt,
        BridgeStatus status,
        List<PluginInfo> plugins,
        List<Capability> capabilities,
        List<ResourceDescriptor> resources) {

    public BridgeSnapshot {
        plugins = List.copyOf(plugins);
        capabilities = List.copyOf(capabilities);
        resources = List.copyOf(resources);
    }

    public BridgeSnapshot(
            String generatedAt,
            BridgeStatus status,
            List<PluginInfo> plugins,
            List<Capability> capabilities) {

        this(
                generatedAt,
                status,
                plugins,
                capabilities,
                List.of());
    }
}
