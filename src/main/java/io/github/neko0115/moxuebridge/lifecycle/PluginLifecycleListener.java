package io.github.neko0115.moxuebridge.lifecycle;

import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;

public final class PluginLifecycleListener
        implements Listener {

    private final Plugin bridgePlugin;
    private final Runnable refresher;
    private final Consumer<Runnable> scheduler;

    public PluginLifecycleListener(
            Plugin bridgePlugin,
            Runnable refresher,
            Consumer<Runnable> scheduler) {

        this.bridgePlugin =
                Objects.requireNonNull(
                        bridgePlugin,
                        "bridgePlugin");

        this.refresher =
                Objects.requireNonNull(
                        refresher,
                        "refresher");

        this.scheduler =
                Objects.requireNonNull(
                        scheduler,
                        "scheduler");
    }

    @EventHandler
    public void onPluginEnable(
            PluginEnableEvent event) {

        scheduleRefreshFor(
                event.getPlugin());
    }

    @EventHandler
    public void onPluginDisable(
            PluginDisableEvent event) {

        scheduleRefreshFor(
                event.getPlugin());
    }

    private void scheduleRefreshFor(
            Plugin changedPlugin) {

        if (changedPlugin == bridgePlugin) {
            return;
        }

        scheduler.accept(refresher);
    }
}