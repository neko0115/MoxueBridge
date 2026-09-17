package io.github.neko0115.moxuebridge.registry;

import io.github.neko0115.moxuebridge.model.BridgeSnapshot;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class CapabilityRegistry {

    private final AtomicReference<BridgeSnapshot> snapshot;

    public CapabilityRegistry(BridgeSnapshot initial) {
        this.snapshot = new AtomicReference<>(
                Objects.requireNonNull(initial));
    }

    public BridgeSnapshot snapshot() {
        return snapshot.get();
    }

    public void replace(BridgeSnapshot next) {
        snapshot.set(Objects.requireNonNull(next));
    }
}