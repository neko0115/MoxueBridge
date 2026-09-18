package io.github.neko0115.moxuebridge.integration;

import io.github.neko0115.moxuebridge.model.Capability;
import io.github.neko0115.moxuebridge.model.ResourceDescriptor;
import io.github.neko0115.moxuebridge.model.RuntimePluginDescriptor;

import java.util.List;

public interface PluginIntegration {

    boolean supports(RuntimePluginDescriptor plugin);

    List<Capability> discoverCapabilities(
            RuntimePluginDescriptor plugin)
            throws IntegrationException;

    default List<ResourceDescriptor> discoverResources(
            RuntimePluginDescriptor plugin)
            throws IntegrationException {

        return List.of();
    }
}