package io.github.neko0115.moxuebridge.integration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.neko0115.moxuebridge.model.Capability;
import io.github.neko0115.moxuebridge.model.CapabilitySource;
import io.github.neko0115.moxuebridge.model.CapabilityUsage;
import io.github.neko0115.moxuebridge.model.RuntimePluginDescriptor;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.regex.Pattern;

public final class CapabilityManifestIntegration
        implements PluginIntegration {

    private static final String MANIFEST_NAME =
            "moxue-capabilities.json";

    private static final long MAX_MANIFEST_BYTES =
            256L * 1024L;

    private static final int MAX_CAPABILITIES = 256;
    private static final int MAX_CONSTRAINTS = 32;

    private static final Pattern SIMPLE_NAME =
            Pattern.compile(
                    "^[a-z0-9_.:-]+$");

    @Override
    public boolean supports(
            RuntimePluginDescriptor plugin) {

        return Files.isRegularFile(
                manifestPath(plugin));
    }

    @Override
    public List<Capability> discoverCapabilities(
            RuntimePluginDescriptor plugin)
            throws IntegrationException {

        Path manifest = manifestPath(plugin);

        if (!plugin.enabled()
                || !Files.isRegularFile(manifest)) {
            return List.of();
        }

        try {
            long size = Files.size(manifest);

            if (size < 1 || size > MAX_MANIFEST_BYTES) {
                throw new IntegrationException(
                        "Capability manifest size is invalid");
            }

            JsonObject root = readObject(manifest);

            if (!root.has("capabilities")
                    || !root.get("capabilities").isJsonArray()) {
                throw new IntegrationException(
                        "Capability manifest requires capabilities array");
            }

            JsonArray array =
                    root.getAsJsonArray("capabilities");

            if (array.size() > MAX_CAPABILITIES) {
                throw new IntegrationException(
                        "Capability manifest has too many entries");
            }

            List<Capability> capabilities =
                    new ArrayList<>();

            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    throw new IntegrationException(
                            "Capability entries must be objects");
                }

                capabilities.add(
                        parseCapability(
                                plugin,
                                element.getAsJsonObject()));
            }

            return List.copyOf(capabilities);

        } catch (IOException | RuntimeException ex) {
            throw new IntegrationException(
                    "Failed to read capability manifest",
                    ex);
        }
    }

    private Capability parseCapability(
            RuntimePluginDescriptor plugin,
            JsonObject object)
            throws IntegrationException {

        String id =
                requiredSimpleName(
                        object,
                        "id");

        String name =
                requiredText(
                        object,
                        "name",
                        128);

        String description =
                requiredText(
                        object,
                        "description",
                        500);

        if (!object.has("usage")
                || !object.get("usage").isJsonObject()) {
            throw new IntegrationException(
                    "Capability requires usage object");
        }

        JsonObject usage =
                object.getAsJsonObject("usage");

        String trigger =
                requiredSimpleName(
                        usage,
                        "trigger");

        String human =
                requiredText(
                        usage,
                        "human",
                        500);

        Map<String, Object> constraints =
                constraints(object);

        return new Capability(
                id,
                name,
                description,
                true,
                new CapabilitySource(
                        plugin.name(),
                        plugin.version(),
                        "manifest"),
                new CapabilityUsage(
                        trigger,
                        human),
                constraints);
    }

    private Map<String, Object> constraints(
            JsonObject object)
            throws IntegrationException {

        if (!object.has("constraints")
                || object.get("constraints").isJsonNull()) {
            return Map.of();
        }

        if (!object.get("constraints").isJsonObject()) {
            throw new IntegrationException(
                    "constraints must be an object");
        }

        JsonObject constraints =
                object.getAsJsonObject("constraints");

        if (constraints.size() > MAX_CONSTRAINTS) {
            throw new IntegrationException(
                    "Too many capability constraints");
        }

        Map<String, Object> result =
                new HashMap<>();

        for (var entry : constraints.entrySet()) {
            String key =
                    entry.getKey()
                            .trim()
                            .toLowerCase();

            if (key.length() > 128
                    || !SIMPLE_NAME.matcher(key).matches()) {
                throw new IntegrationException(
                        "Invalid capability constraint key");
            }

            JsonElement value =
                    entry.getValue();

            if (value.isJsonNull()) {
                continue;
            }

            if (!value.isJsonPrimitive()) {
                throw new IntegrationException(
                        "Capability constraints must be primitive");
            }

            var primitive =
                    value.getAsJsonPrimitive();

            if (primitive.isBoolean()) {
                result.put(
                        key,
                        primitive.getAsBoolean());

            } else if (primitive.isNumber()) {
                double number =
                        primitive.getAsDouble();

                if (!Double.isFinite(number)) {
                    throw new IntegrationException(
                            "Capability constraint number must be finite");
                }

                long integral =
                        primitive.getAsLong();

                result.put(
                        key,
                        number == integral
                                ? integral
                                : number);

            } else if (primitive.isString()) {
                String text =
                        primitive.getAsString()
                                .trim();

                if (text.length() > 500) {
                    throw new IntegrationException(
                            "Capability constraint string is too long");
                }

                result.put(
                        key,
                        text);
            }
        }

        return Map.copyOf(result);
    }

    private String requiredSimpleName(
            JsonObject object,
            String key)
            throws IntegrationException {

        String value =
                requiredText(
                        object,
                        key,
                        128)
                        .toLowerCase();

        if (!SIMPLE_NAME.matcher(value).matches()) {
            throw new IntegrationException(
                    "Invalid simple name: "
                            + key);
        }

        return value;
    }

    private String requiredText(
            JsonObject object,
            String key,
            int maximum)
            throws IntegrationException {

        if (!object.has(key)
                || !object.get(key).isJsonPrimitive()
                || !object.getAsJsonPrimitive(key)
                        .isString()) {
            throw new IntegrationException(
                    "Missing string: "
                            + key);
        }

        String value =
                object.get(key)
                        .getAsString()
                        .trim();

        if (value.isEmpty()
                || value.length() > maximum) {
            throw new IntegrationException(
                    "Invalid string: "
                            + key);
        }

        return value;
    }

    private Path manifestPath(
            RuntimePluginDescriptor plugin) {

        return plugin.dataFolder()
                .resolve(MANIFEST_NAME);
    }

    private JsonObject readObject(
            Path path)
            throws IOException {

        try (Reader reader =
                Files.newBufferedReader(
                        path,
                        StandardCharsets.UTF_8)) {

            return JsonParser
                    .parseReader(reader)
                    .getAsJsonObject();
        }
    }
}
