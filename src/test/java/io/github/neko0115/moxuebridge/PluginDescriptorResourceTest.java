package io.github.neko0115.moxuebridge;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PluginDescriptorResourceTest {

    @Test
    void pluginYmlDeclaresExpectedEntrypointAndApiVersion() throws IOException {
        var stream = getClass()
                .getClassLoader()
                .getResourceAsStream("plugin.yml");

        assertNotNull(stream);

        var text = new String(
                stream.readAllBytes(),
                StandardCharsets.UTF_8);

        assertTrue(text.contains(
                "main: io.github.neko0115.moxuebridge.MoxueBridgePlugin"));

        assertTrue(text.contains(
                "api-version: '1.21'"));
    }
}
