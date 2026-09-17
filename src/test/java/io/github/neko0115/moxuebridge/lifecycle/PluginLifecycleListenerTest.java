package io.github.neko0115.moxuebridge.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class PluginLifecycleListenerTest {

    @Test
    void schedulesRefreshAfterOtherPluginLifecycleEvents() {
        var bridgePlugin = mock(Plugin.class);
        var otherPlugin = mock(Plugin.class);

        var scheduled = new ArrayList<Runnable>();
        var refreshCount = new AtomicInteger();

        var listener = new PluginLifecycleListener(
                bridgePlugin,
                refreshCount::incrementAndGet,
                scheduled::add);

        var enableEvent = mock(PluginEnableEvent.class);
        when(enableEvent.getPlugin())
                .thenReturn(otherPlugin);

        var disableEvent = mock(PluginDisableEvent.class);
        when(disableEvent.getPlugin())
                .thenReturn(otherPlugin);

        listener.onPluginEnable(enableEvent);
        listener.onPluginDisable(disableEvent);

        assertEquals(2, scheduled.size());
        assertEquals(0, refreshCount.get());

        scheduled.forEach(Runnable::run);

        assertEquals(2, refreshCount.get());
    }

    @Test
    void ignoresMoxueBridgeOwnLifecycleEvents() {
        var bridgePlugin = mock(Plugin.class);

        var scheduled = new ArrayList<Runnable>();
        var refreshCount = new AtomicInteger();

        var listener = new PluginLifecycleListener(
                bridgePlugin,
                refreshCount::incrementAndGet,
                scheduled::add);

        var enableEvent = mock(PluginEnableEvent.class);
        when(enableEvent.getPlugin())
                .thenReturn(bridgePlugin);

        var disableEvent = mock(PluginDisableEvent.class);
        when(disableEvent.getPlugin())
                .thenReturn(bridgePlugin);

        listener.onPluginEnable(enableEvent);
        listener.onPluginDisable(disableEvent);

        assertEquals(0, scheduled.size());
        assertEquals(0, refreshCount.get());
    }
}