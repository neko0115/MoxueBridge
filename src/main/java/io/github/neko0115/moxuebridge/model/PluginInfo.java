package io.github.neko0115.moxuebridge.model;

import java.util.List;

public record PluginInfo(
        String name,
        String version,
        boolean enabled,
        boolean integrated,
        List<CommandInfo> commands) {

    public PluginInfo {
        commands = List.copyOf(commands);
    }
}