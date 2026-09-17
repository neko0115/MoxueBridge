package io.github.neko0115.moxuebridge.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class RuntimePluginDescriptorTest {

    @Test
    void descriptorDefensivelyCopiesCommandList() {
        var commands = new ArrayList<CommandInfo>();

        var descriptor = new RuntimePluginDescriptor(
                "VeinMiner",
                "2.11.2",
                true,
                Path.of("plugins", "Veinminer"),
                commands);

        commands.add(new CommandInfo(
                "fake",
                "fake",
                "/fake",
                "",
                List.of()));

        assertEquals(0, descriptor.commands().size());
    }
}