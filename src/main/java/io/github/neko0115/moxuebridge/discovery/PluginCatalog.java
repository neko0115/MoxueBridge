package io.github.neko0115.moxuebridge.discovery;

import io.github.neko0115.moxuebridge.model.RuntimePluginDescriptor;

import java.util.List;

public interface PluginCatalog {

    List<RuntimePluginDescriptor> snapshot();
}