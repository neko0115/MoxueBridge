package io.github.neko0115.moxuebridge;

import io.github.neko0115.moxuebridge.api.BridgeHttpServer;
import io.github.neko0115.moxuebridge.config.BridgeConfiguration;
import io.github.neko0115.moxuebridge.config.TokenGenerator;
import io.github.neko0115.moxuebridge.discovery.BukkitPluginCatalog;
import io.github.neko0115.moxuebridge.discovery.RegistryBuilder;
import io.github.neko0115.moxuebridge.integration.CapabilityManifestIntegration;
import io.github.neko0115.moxuebridge.integration.PluginIntegration;
import io.github.neko0115.moxuebridge.integration.ResourceManifestIntegration;
import io.github.neko0115.moxuebridge.integration.VeinMinerIntegration;
import io.github.neko0115.moxuebridge.lifecycle.PluginLifecycleListener;
import io.github.neko0115.moxuebridge.model.BridgeStatus;
import io.github.neko0115.moxuebridge.registry.CapabilityRegistry;
import io.github.neko0115.moxuebridge.security.BearerTokenValidator;
import io.github.neko0115.moxuebridge.workspace.WorkspaceSelectionStore;
import io.github.neko0115.moxuebridge.workspace.WorkspaceWandListener;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class MoxueBridgePlugin extends JavaPlugin {

    private BridgeHttpServer bridgeHttpServer;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();

            String configuredBind =
                    getConfig().getString(
                            "http.bind",
                            "0.0.0.0");

            int configuredPort =
                    getConfig().getInt(
                            "http.port",
                            8766);

            String configuredToken =
                    getConfig().getString(
                            "security.token",
                            "");

            boolean tokenWasBlank =
                    configuredToken == null
                            || configuredToken.isBlank();

            var tokenGenerator =
                    new TokenGenerator();

            var bridgeConfig =
                    BridgeConfiguration.resolve(
                            configuredBind,
                            configuredPort,
                            configuredToken,
                            tokenGenerator::generate);

            if (tokenWasBlank) {
                getConfig().set(
                        "security.token",
                        bridgeConfig.token());

                saveConfig();

                getLogger().info(
                        "Generated API token; retrieve it from "
                                + "plugins/MoxueBridge/config.yml");
            }

            var catalog =
                    new BukkitPluginCatalog(
                            getServer().getPluginManager(),
                            getLogger());

            List<PluginIntegration> integrations =
                    List.of(
                            new VeinMinerIntegration(),
                            new CapabilityManifestIntegration(),
                            new ResourceManifestIntegration());

            var registryBuilder =
                    new RegistryBuilder(
                            catalog,
                            integrations,
                            () -> new BridgeStatus(
                                    "MoxueBridge",
                                    getPluginMeta()
                                            .getVersion(),
                                    Bukkit.getMinecraftVersion(),
                                    getServer().getName(),
                                    true),
                            () -> Instant.now().toString(),
                            getLogger());

            var initialSnapshot =
                    registryBuilder.build();

            var capabilityRegistry =
                    new CapabilityRegistry(
                            initialSnapshot);

            Runnable refresher = () -> {
                try {
                    capabilityRegistry.replace(
                            registryBuilder.build());

                } catch (RuntimeException ex) {
                    getLogger().log(
                            Level.SEVERE,
                            "Failed to refresh capability registry",
                            ex);
                }
            };

            var lifecycleListener =
                    new PluginLifecycleListener(
                            this,
                            refresher,
                            runnable ->
                                    Bukkit.getScheduler()
                                            .runTask(
                                                    this,
                                                    runnable));

            getServer()
                    .getPluginManager()
                    .registerEvents(
                            lifecycleListener,
                            this);

            var tokenValidator =
                    new BearerTokenValidator(
                            bridgeConfig.token());

            var workspaceSelections =
                    new WorkspaceSelectionStore();

            getServer()
                    .getPluginManager()
                    .registerEvents(
                            new WorkspaceWandListener(
                                    this,
                                    workspaceSelections),
                            this);

            bridgeHttpServer =
                    new BridgeHttpServer(
                            bridgeConfig.bindAddress(),
                            bridgeConfig.port(),
                            capabilityRegistry,
                            workspaceSelections,
                            tokenValidator,
                            getLogger());

            bridgeHttpServer.start();

            getLogger().info(
                    "MoxueBridge "
                            + getPluginMeta().getVersion()
                            + " enabled on "
                            + bridgeConfig.bindAddress()
                            + ":"
                            + bridgeHttpServer.boundPort());

        } catch (Exception ex) {
            getLogger().log(
                    Level.SEVERE,
                    "Failed to start MoxueBridge",
                    ex);

            if (bridgeHttpServer != null) {
                bridgeHttpServer.stop();
                bridgeHttpServer = null;
            }

            getServer()
                    .getPluginManager()
                    .disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (bridgeHttpServer != null) {
            bridgeHttpServer.stop();
            bridgeHttpServer = null;
        }

        getLogger().info(
                "MoxueBridge disabled");
    }
}