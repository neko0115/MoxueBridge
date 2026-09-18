package io.github.neko0115.moxuebridge.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record ResourceDescriptor(
        String id,
        String kind,
        List<String> aliases,
        List<String> blockIds,
        List<String> collectedItemIds,
        int minimumDropCount,
        String toolKind,
        List<String> forbiddenEnchantments,
        String capabilityId,
        Map<String, List<String>> relatedBlocks,
        String cleanupPolicy,
        String confidence) {

    public ResourceDescriptor {
        aliases = List.copyOf(aliases);
        blockIds = List.copyOf(blockIds);
        collectedItemIds =
                List.copyOf(collectedItemIds);
        forbiddenEnchantments =
                List.copyOf(forbiddenEnchantments);

        Map<String, List<String>> copied =
                new HashMap<>();

        for (var entry : relatedBlocks.entrySet()) {
            copied.put(
                    entry.getKey(),
                    List.copyOf(entry.getValue()));
        }

        relatedBlocks = Map.copyOf(copied);
    }
}
