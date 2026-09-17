package io.github.neko0115.moxuebridge.model;

import java.nio.file.Path;
import java.util.List;

public record RuntimePluginDescriptor(
        String name,
        String version,
        boolean enabled,
        Path dataFolder,
        List<CommandInfo> commands) {

    public RuntimePluginDescriptor {
        commands = List.copyOf(commands);
    }
}