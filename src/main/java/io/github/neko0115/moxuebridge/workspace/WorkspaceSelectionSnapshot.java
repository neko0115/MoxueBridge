package io.github.neko0115.moxuebridge.workspace;

import java.util.List;

public record WorkspaceSelectionSnapshot(
        int version,
        long generatedAt,
        List<WorkspaceSelection> selections) {

    public WorkspaceSelectionSnapshot {
        selections = List.copyOf(selections);
    }
}
