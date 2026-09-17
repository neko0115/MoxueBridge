package io.github.neko0115.moxuebridge.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class BridgeConfigurationTest {

    @Test
    void generatesTokenWhenConfiguredTokenIsBlank() {
        var config = BridgeConfiguration.resolve(
                "0.0.0.0",
                8766,
                "",
                () -> "generated-token");

        assertEquals("0.0.0.0", config.bindAddress());
        assertEquals(8766, config.port());
        assertEquals("generated-token", config.token());
    }

    @Test
    void preservesExistingTokenWithoutCallingGenerator() {
        var generatorCalled = new AtomicBoolean(false);

        var config = BridgeConfiguration.resolve(
                " 0.0.0.0 ",
                8766,
                "existing-token",
                () -> {
                    generatorCalled.set(true);
                    return "unused";
                });

        assertEquals("0.0.0.0", config.bindAddress());
        assertEquals("existing-token", config.token());
        assertEquals(false, generatorCalled.get());
    }

    @Test
    void rejectsBlankBindAddress() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BridgeConfiguration.resolve(
                        "   ",
                        8766,
                        "token",
                        () -> "unused"));
    }

    @Test
    void rejectsPortBelowValidRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BridgeConfiguration.resolve(
                        "0.0.0.0",
                        0,
                        "token",
                        () -> "unused"));
    }

    @Test
    void rejectsPortAboveValidRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BridgeConfiguration.resolve(
                        "0.0.0.0",
                        65536,
                        "token",
                        () -> "unused"));
    }
}