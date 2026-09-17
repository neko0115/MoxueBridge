package io.github.neko0115.moxuebridge.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.papermc.paper.plugin.configuration.PluginMeta;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.command.Command;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

class BukkitPluginCatalogTest {

    @Test
    void snapshotsAuthoritativeRuntimePluginMetadata() {
        var manager = mock(PluginManager.class);
        var plugin = mock(Plugin.class);
        var meta = mock(PluginMeta.class);

        when(manager.getPlugins())
                .thenReturn(new Plugin[] { plugin });

        when(plugin.getName())
                .thenReturn("ExamplePlugin");

        when(plugin.getPluginMeta())
                .thenReturn(meta);

        when(meta.getVersion())
                .thenReturn("1.2.3");

        when(plugin.isEnabled())
                .thenReturn(true);

        when(plugin.getDataFolder())
                .thenReturn(new File(
                        "plugins",
                        "ExamplePlugin"));

        var command = mock(Command.class);

        when(command.getName())
                .thenReturn("example");

        when(command.getDescription())
                .thenReturn("Example command");

        when(command.getUsage())
                .thenReturn("/example");

        when(command.getPermission())
                .thenReturn("example.use");

        when(command.getAliases())
                .thenReturn(List.of("ex"));

        var catalog = new BukkitPluginCatalog(
                manager,
                ignored -> List.of(command),
                Logger.getLogger(
                        BukkitPluginCatalogTest.class.getName()));

        var snapshot = catalog.snapshot();

        assertEquals(1, snapshot.size());

        var descriptor = snapshot.get(0);

        assertEquals("ExamplePlugin", descriptor.name());
        assertEquals("1.2.3", descriptor.version());
        assertTrue(descriptor.enabled());

        assertEquals(
                new File(
                        "plugins",
                        "ExamplePlugin")
                        .toPath(),
                descriptor.dataFolder());

        assertEquals(1, descriptor.commands().size());

        var info = descriptor.commands().get(0);

        assertEquals("example", info.name());
        assertEquals(
                "Example command",
                info.description());
        assertEquals("/example", info.usage());
        assertEquals(
                "example.use",
                info.permission());
        assertEquals(
                List.of("ex"),
                info.aliases());
    }

    @Test
    void commandParserFailureDoesNotLosePluginRecord() {
        var manager = mock(PluginManager.class);
        var plugin = mock(Plugin.class);
        var meta = mock(PluginMeta.class);

        when(manager.getPlugins())
                .thenReturn(new Plugin[] { plugin });

        when(plugin.getName())
                .thenReturn("BrokenCommands");

        when(plugin.getPluginMeta())
                .thenReturn(meta);

        when(meta.getVersion())
                .thenReturn("9.9.9");

        when(plugin.isEnabled())
                .thenReturn(true);

        when(plugin.getDataFolder())
                .thenReturn(new File(
                        "plugins",
                        "BrokenCommands"));

        var catalog = new BukkitPluginCatalog(
                manager,
                ignored -> {
                    throw new IllegalStateException(
                            "synthetic parser failure");
                },
                Logger.getLogger(
                        BukkitPluginCatalogTest.class.getName()));

        var snapshot = catalog.snapshot();

        assertEquals(1, snapshot.size());
        assertEquals(
                "BrokenCommands",
                snapshot.get(0).name());

        assertTrue(
                snapshot.get(0)
                        .commands()
                        .isEmpty());
    }
}