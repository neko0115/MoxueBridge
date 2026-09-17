package io.github.neko0115.moxuebridge.model;

import java.util.List;

public record CommandInfo(
        String name,
        String description,
        String usage,
        String permission,
        List<String> aliases) {

    public CommandInfo {
        aliases = List.copyOf(aliases);
    }
}