package io.github.neko0115.moxuebridge.discovery;

import io.github.neko0115.moxuebridge.integration.IntegrationException;
import io.github.neko0115.moxuebridge.integration.PluginIntegration;
import io.github.neko0115.moxuebridge.model.BridgeSnapshot;
import io.github.neko0115.moxuebridge.model.BridgeStatus;
import io.github.neko0115.moxuebridge.model.Capability;
import io.github.neko0115.moxuebridge.model.PluginInfo;
import io.github.neko0115.moxuebridge.model.ResourceDescriptor;
import io.github.neko0115.moxuebridge.model.RuntimePluginDescriptor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class RegistryBuilder {

    private final PluginCatalog catalog;
    private final List<PluginIntegration> integrations;
    private final Supplier<BridgeStatus> statusSupplier;
    private final Supplier<String> timestampSupplier;
    private final Logger logger;

    public RegistryBuilder(
            PluginCatalog catalog,
            List<PluginIntegration> integrations,
            Supplier<BridgeStatus> statusSupplier,
            Supplier<String> timestampSupplier,
            Logger logger) {

        this.catalog = catalog;
        this.integrations = List.copyOf(integrations);
        this.statusSupplier = statusSupplier;
        this.timestampSupplier = timestampSupplier;
        this.logger = logger;
    }

    public BridgeSnapshot build() {

        List<RuntimePluginDescriptor> descriptors =
                catalog.snapshot();

        List<PluginInfo> plugins =
                new ArrayList<>();

        List<Capability> capabilities =
                new ArrayList<>();

        List<ResourceDescriptor> resources =
                new ArrayList<>();

        for (RuntimePluginDescriptor descriptor : descriptors) {

            List<PluginIntegration> supporting =
                    integrations.stream()
                            .filter(integration ->
                                    integration.supports(descriptor))
                            .toList();

            boolean integrated =
                    !supporting.isEmpty();

            plugins.add(
                    new PluginInfo(
                            descriptor.name(),
                            descriptor.version(),
                            descriptor.enabled(),
                            integrated,
                            descriptor.commands()));

            if (!descriptor.enabled()) {
                continue;
            }

            for (PluginIntegration integration : supporting) {
                try {
                    capabilities.addAll(
                            integration.discoverCapabilities(
                                    descriptor));

                    resources.addAll(
                            integration.discoverResources(
                                    descriptor));

                } catch (IntegrationException ex) {
                    logger.warning(
                            "Integration failed for plugin "
                                    + descriptor.name()
                                    + ": "
                                    + ex.getMessage());
                }
            }
        }

        plugins.sort(
                Comparator.comparing(
                        PluginInfo::name,
                        String.CASE_INSENSITIVE_ORDER));

        capabilities.sort(
                Comparator.comparing(
                        Capability::id));

        resources.sort(
                Comparator.comparing(
                        ResourceDescriptor::id));

        return new BridgeSnapshot(
                timestampSupplier.get(),
                statusSupplier.get(),
                plugins,
                capabilities,
                resources);
    }
}