package io.github.neko0115.moxuebridge.discovery;

import io.github.neko0115.moxuebridge.model.CommandInfo;
import io.github.neko0115.moxuebridge.model.RuntimePluginDescriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;

import org.bukkit.command.Command;
import org.bukkit.command.PluginCommandYamlParser;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

public final class BukkitPluginCatalog
        implements PluginCatalog {

    private final PluginManager pluginManager;
    private final Function<Plugin, List<Command>> commandParser;
    private final Logger logger;

    public BukkitPluginCatalog(
            PluginManager pluginManager,
            Logger logger) {

        this(
                pluginManager,
                PluginCommandYamlParser::parse,
                logger);
    }

    public BukkitPluginCatalog(
            PluginManager pluginManager,
            Function<Plugin, List<Command>> commandParser,
            Logger logger) {

        this.pluginManager = pluginManager;
        this.commandParser = commandParser;
        this.logger = logger;
    }

    @Override
    public List<RuntimePluginDescriptor> snapshot() {

        List<RuntimePluginDescriptor> result =
                new ArrayList<>();

        for (Plugin plugin : pluginManager.getPlugins()) {

            List<CommandInfo> commands =
                    readCommands(plugin);

            result.add(
                    new RuntimePluginDescriptor(
                            plugin.getName(),
                            plugin.getPluginMeta().getVersion(),
                            plugin.isEnabled(),
                            plugin.getDataFolder().toPath(),
                            commands));
        }

        return List.copyOf(result);
    }

    private List<CommandInfo> readCommands(
            Plugin plugin) {

        try {
            return commandParser
                    .apply(plugin)
                    .stream()
                    .map(this::toCommandInfo)
                    .toList();

        } catch (RuntimeException ex) {
            logger.warning(
                    "Failed to read command metadata for plugin "
                            + plugin.getName()
                            + ": "
                            + ex.getMessage());

            return List.of();
        }
    }

    private CommandInfo toCommandInfo(
            Command command) {

        return new CommandInfo(
                command.getName(),
                command.getDescription(),
                command.getUsage(),
                command.getPermission(),
                command.getAliases());
    }
}