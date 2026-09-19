package io.github.neko0115.moxuebridge.workspace;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class WorkspaceSelectionStore {

    private static final int SNAPSHOT_VERSION = 1;

    private final Clock clock;
    private final Map<String, SelectionState> states =
            new HashMap<>();

    private long lastSnapshotAt;

    public WorkspaceSelectionStore() {
        this(Clock.systemUTC());
    }

    public WorkspaceSelectionStore(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    public synchronized WorkspaceSelection setPointA(
            String worldIdentity,
            String dimension,
            String playerId,
            String playerName,
            WorkspaceSelectionPoint point) {

        return setPoint(
                worldIdentity,
                dimension,
                playerId,
                playerName,
                point,
                true);
    }

    public synchronized WorkspaceSelection setPointB(
            String worldIdentity,
            String dimension,
            String playerId,
            String playerName,
            WorkspaceSelectionPoint point) {

        return setPoint(
                worldIdentity,
                dimension,
                playerId,
                playerName,
                point,
                false);
    }

    public synchronized WorkspaceSelectionSnapshot snapshot() {
        var selections =
                new ArrayList<WorkspaceSelection>();

        for (var state : states.values()) {
            if (state.latest != null) {
                selections.add(state.latest);
            }
        }

        selections.sort(
                Comparator.comparing(
                        WorkspaceSelection::playerId)
                        .thenComparing(
                                WorkspaceSelection::dimension));

        long now = clock.millis();
        long generatedAt =
                Math.max(now, lastSnapshotAt + 1);
        lastSnapshotAt = generatedAt;

        return new WorkspaceSelectionSnapshot(
                SNAPSHOT_VERSION,
                generatedAt,
                selections);
    }

    private WorkspaceSelection setPoint(
            String worldIdentity,
            String dimension,
            String playerId,
            String playerName,
            WorkspaceSelectionPoint point,
            boolean pointA) {

        String normalizedWorld =
                requireText(
                        worldIdentity,
                        "worldIdentity");

        String normalizedDimension =
                requireText(
                        dimension,
                        "dimension");

        String normalizedPlayerId =
                requireText(
                        playerId,
                        "playerId");

        String normalizedPlayerName =
                requireText(
                        playerName,
                        "playerName");

        Objects.requireNonNull(point);

        var state =
                states.computeIfAbsent(
                        normalizedPlayerId,
                        ignored ->
                                new SelectionState());

        if (!normalizedWorld.equals(
                state.worldIdentity)
                || !normalizedDimension.equals(
                        state.dimension)) {

            state.resetForWorld(
                    normalizedWorld,
                    normalizedDimension);
        }

        state.playerName =
                normalizedPlayerName;

        if (pointA) {
            state.pointA = point;
        } else {
            state.pointB = point;
        }

        if (
                state.pointA == null
                || state.pointB == null) {

            return null;
        }

        long selectedAt = clock.millis();
        state.generation += 1;

        var completed =
                new WorkspaceSelection(
                        UUID.randomUUID()
                                .toString(),
                        state.generation,
                        normalizedDimension,
                        normalizedPlayerId,
                        normalizedPlayerName,
                        state.pointA,
                        state.pointB,
                        selectedAt);

        state.latest = completed;
        return completed;
    }

    private static String requireText(
            String value,
            String field) {

        if (value == null
                || value.isBlank()) {

            throw new IllegalArgumentException(
                    field
                            + " must be non-empty");
        }

        return value.trim();
    }

    private static final class SelectionState {
        private String worldIdentity;
        private String dimension;
        private String playerName;
        private WorkspaceSelectionPoint pointA;
        private WorkspaceSelectionPoint pointB;
        private WorkspaceSelection latest;
        private long generation;

        private void resetForWorld(
                String nextWorld,
                String nextDimension) {

            worldIdentity = nextWorld;
            dimension = nextDimension;
            pointA = null;
            pointB = null;
            latest = null;
        }
    }
}
