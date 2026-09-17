package io.github.neko0115.moxuebridge.model;

import java.util.Map;

public record Capability(
        String id,
        String name,
        String description,
        boolean available,
        CapabilitySource source,
        CapabilityUsage usage,
        Map<String, Object> constraints) {

    public Capability {
        constraints = Map.copyOf(constraints);
    }
}