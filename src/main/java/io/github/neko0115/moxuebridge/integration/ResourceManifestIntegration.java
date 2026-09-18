package io.github.neko0115.moxuebridge.integration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.neko0115.moxuebridge.model.Capability;
import io.github.neko0115.moxuebridge.model.ResourceDescriptor;
import io.github.neko0115.moxuebridge.model.RuntimePluginDescriptor;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ResourceManifestIntegration
        implements PluginIntegration {

    private static final String MANIFEST_NAME =
            "moxue-resources.json";

    private static final long MAX_MANIFEST_BYTES =
            256L * 1024L;

    private static final int MAX_RESOURCES = 512;
    private static final int MAX_LIST_ITEMS = 64;

    private static final Pattern IDENTIFIER =
            Pattern.compile(
                    "^[a-z0-9_.-]+:[a-z0-9_./-]+$");

    private static final Pattern SIMPLE_NAME =
            Pattern.compile(
                    "^[a-z0-9_.:-]+$");

    private static final Set<String> CLEANUP_POLICIES =
            Set.of(
                    "natural_decay",
                    "remove_after_felling",
                    "preserve");

    private static final Set<String> CONFIDENCE =
            Set.of(
                    "authoritative",
                    "inferred");

    @Override
    public boolean supports(
            RuntimePluginDescriptor plugin) {

        return Files.isRegularFile(
                manifestPath(plugin));
    }

    @Override
    public List<Capability> discoverCapabilities(
            RuntimePluginDescriptor plugin) {

        return List.of();
    }

    @Override
    public List<ResourceDescriptor> discoverResources(
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
                        "Resource manifest size is invalid");
            }

            JsonObject root = readObject(manifest);

            if (!root.has("resources")
                    || !root.get("resources").isJsonArray()) {
                throw new IntegrationException(
                        "Resource manifest requires resources array");
            }

            JsonArray resources =
                    root.getAsJsonArray("resources");

            if (resources.size() > MAX_RESOURCES) {
                throw new IntegrationException(
                        "Resource manifest has too many resources");
            }

            List<ResourceDescriptor> descriptors =
                    new ArrayList<>();

            for (JsonElement element : resources) {
                if (!element.isJsonObject()) {
                    throw new IntegrationException(
                            "Resource entries must be objects");
                }

                descriptors.add(
                        parseResource(
                                element.getAsJsonObject()));
            }

            return List.copyOf(descriptors);

        } catch (IOException | RuntimeException ex) {
            if (ex instanceof IntegrationException integration) {
                throw integration;
            }

            throw new IntegrationException(
                    "Failed to read resource manifest",
                    ex);
        }
    }

    private ResourceDescriptor parseResource(
            JsonObject object)
            throws IntegrationException {

        String id =
                requiredIdentifier(
                        object,
                        "id");

        String kind =
                requiredSimpleName(
                        object,
                        "kind");

        List<String> aliases =
                optionalIdentifiers(
                        object,
                        "aliases");

        List<String> blockIds =
                requiredIdentifiers(
                        object,
                        "block_ids");

        List<String> collectedItemIds =
                requiredIdentifiers(
                        object,
                        "collected_item_ids");

        int minimumDropCount =
                requiredPositiveInt(
                        object,
                        "minimum_drop_count");

        String toolKind =
                optionalSimpleName(
                        object,
                        "tool_kind");

        List<String> forbiddenEnchantments =
                optionalSimpleNames(
                        object,
                        "forbidden_enchantments");

        String capabilityId =
                optionalSimpleName(
                        object,
                        "capability_id");

        Map<String, List<String>> relatedBlocks =
                relatedBlocks(object);

        String cleanupPolicy =
                optionalSimpleName(
                        object,
                        "cleanup_policy");

        if (cleanupPolicy != null
                && !CLEANUP_POLICIES.contains(
                        cleanupPolicy)) {
            throw new IntegrationException(
                    "Unsupported cleanup_policy");
        }

        String confidence =
                optionalSimpleName(
                        object,
                        "confidence");

        if (confidence == null) {
            confidence = "authoritative";
        }

        if (!CONFIDENCE.contains(confidence)) {
            throw new IntegrationException(
                    "Unsupported confidence");
        }

        return new ResourceDescriptor(
                id,
                kind,
                aliases,
                blockIds,
                collectedItemIds,
                minimumDropCount,
                toolKind,
                forbiddenEnchantments,
                capabilityId,
                relatedBlocks,
                cleanupPolicy,
                confidence);
    }

    private Map<String, List<String>> relatedBlocks(
            JsonObject object)
            throws IntegrationException {

        if (!object.has("related_blocks")
                || object.get("related_blocks").isJsonNull()) {
            return Map.of();
        }

        if (!object.get("related_blocks").isJsonObject()) {
            throw new IntegrationException(
                    "related_blocks must be an object");
        }

        JsonObject related =
                object.getAsJsonObject(
                        "related_blocks");

        if (related.size() > 32) {
            throw new IntegrationException(
                    "Too many related block groups");
        }

        Map<String, List<String>> result =
                new HashMap<>();

        for (var entry : related.entrySet()) {
            if (!SIMPLE_NAME.matcher(
                    entry.getKey()).matches()) {
                throw new IntegrationException(
                        "Invalid related block key");
            }

            if (!entry.getValue().isJsonArray()) {
                throw new IntegrationException(
                        "Related block values must be arrays");
            }

            result.put(
                    entry.getKey(),
                    identifiers(
                            entry.getValue()
                                    .getAsJsonArray()));
        }

        return Map.copyOf(result);
    }

    private List<String> requiredIdentifiers(
            JsonObject object,
            String key)
            throws IntegrationException {

        if (!object.has(key)
                || !object.get(key).isJsonArray()) {
            throw new IntegrationException(
                    "Missing identifier array: "
                            + key);
        }

        List<String> values =
                identifiers(
                        object.getAsJsonArray(key));

        if (values.isEmpty()) {
            throw new IntegrationException(
                    "Identifier array must not be empty: "
                            + key);
        }

        return values;
    }

    private List<String> optionalIdentifiers(
            JsonObject object,
            String key)
            throws IntegrationException {

        if (!object.has(key)
                || object.get(key).isJsonNull()) {
            return List.of();
        }

        if (!object.get(key).isJsonArray()) {
            throw new IntegrationException(
                    "Expected identifier array: "
                            + key);
        }

        return identifiers(
                object.getAsJsonArray(key));
    }

    private List<String> identifiers(
            JsonArray array)
            throws IntegrationException {

        if (array.size() > MAX_LIST_ITEMS) {
            throw new IntegrationException(
                    "Identifier list is too large");
        }

        List<String> values =
                new ArrayList<>();

        for (JsonElement element : array) {
            if (!element.isJsonPrimitive()
                    || !element.getAsJsonPrimitive()
                            .isString()) {
                throw new IntegrationException(
                        "Identifier values must be strings");
            }

            String value =
                    element.getAsString()
                            .trim()
                            .toLowerCase();

            if (!IDENTIFIER.matcher(value).matches()) {
                throw new IntegrationException(
                        "Invalid namespaced identifier");
            }

            values.add(value);
        }

        return List.copyOf(values);
    }

    private List<String> optionalSimpleNames(
            JsonObject object,
            String key)
            throws IntegrationException {

        if (!object.has(key)
                || object.get(key).isJsonNull()) {
            return List.of();
        }

        if (!object.get(key).isJsonArray()) {
            throw new IntegrationException(
                    "Expected string array: "
                            + key);
        }

        JsonArray array =
                object.getAsJsonArray(key);

        if (array.size() > MAX_LIST_ITEMS) {
            throw new IntegrationException(
                    "String list is too large");
        }

        List<String> values =
                new ArrayList<>();

        for (JsonElement element : array) {
            if (!element.isJsonPrimitive()
                    || !element.getAsJsonPrimitive()
                            .isString()) {
                throw new IntegrationException(
                        "String list values must be strings");
            }

            String value =
                    element.getAsString()
                            .trim()
                            .toLowerCase();

            if (!SIMPLE_NAME.matcher(value).matches()) {
                throw new IntegrationException(
                        "Invalid simple name");
            }

            values.add(value);
        }

        return List.copyOf(values);
    }

    private String requiredIdentifier(
            JsonObject object,
            String key)
            throws IntegrationException {

        String value =
                requiredString(
                        object,
                        key)
                        .toLowerCase();

        if (!IDENTIFIER.matcher(value).matches()) {
            throw new IntegrationException(
                    "Invalid namespaced identifier: "
                            + key);
        }

        return value;
    }

    private String requiredSimpleName(
            JsonObject object,
            String key)
            throws IntegrationException {

        String value =
                requiredString(
                        object,
                        key)
                        .toLowerCase();

        if (!SIMPLE_NAME.matcher(value).matches()) {
            throw new IntegrationException(
                    "Invalid simple name: "
                            + key);
        }

        return value;
    }

    private String optionalSimpleName(
            JsonObject object,
            String key)
            throws IntegrationException {

        if (!object.has(key)
                || object.get(key).isJsonNull()) {
            return null;
        }

        if (!object.get(key).isJsonPrimitive()
                || !object.getAsJsonPrimitive(key)
                        .isString()) {
            throw new IntegrationException(
                    "Expected string: "
                            + key);
        }

        String value =
                object.get(key)
                        .getAsString()
                        .trim()
                        .toLowerCase();

        if (value.isEmpty()
                || value.length() > 128
                || !SIMPLE_NAME.matcher(value).matches()) {
            throw new IntegrationException(
                    "Invalid simple name: "
                            + key);
        }

        return value;
    }

    private int requiredPositiveInt(
            JsonObject object,
            String key)
            throws IntegrationException {

        if (!object.has(key)
                || !object.get(key).isJsonPrimitive()) {
            throw new IntegrationException(
                    "Missing integer: "
                            + key);
        }

        int value =
                object.get(key).getAsInt();

        if (value < 1 || value > 2304) {
            throw new IntegrationException(
                    "Integer out of range: "
                            + key);
        }

        return value;
    }

    private String requiredString(
            JsonObject object,
            String key)
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
                || value.length() > 128) {
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
