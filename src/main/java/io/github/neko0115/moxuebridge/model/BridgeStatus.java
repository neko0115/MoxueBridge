package io.github.neko0115.moxuebridge.model;

public record BridgeStatus(
        String bridge,
        String bridgeVersion,
        String minecraft,
        String serverSoftware,
        boolean online) {
}