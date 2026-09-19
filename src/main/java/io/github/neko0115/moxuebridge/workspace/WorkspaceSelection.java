package io.github.neko0115.moxuebridge.workspace;

public record WorkspaceSelection(
        String id,
        long generation,
        String dimension,
        String playerId,
        String playerName,
        WorkspaceSelectionPoint pointA,
        WorkspaceSelectionPoint pointB,
        long selectedAt) {
}
