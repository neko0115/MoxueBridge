package io.github.neko0115.moxuebridge.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class WorkspaceSelectionStoreTest {

    @Test
    void selectionAppearsOnlyAfterBothPointsExist() {
        var clock = Clock.fixed(
                Instant.ofEpochMilli(1_000),
                ZoneOffset.UTC);

        var store =
                new WorkspaceSelectionStore(clock);

        assertNull(
                store.setPointA(
                        "world-1",
                        "overworld",
                        "player-1",
                        "Boss",
                        new WorkspaceSelectionPoint(
                                1,
                                64,
                                2)));

        assertTrue(
                store.snapshot()
                        .selections()
                        .isEmpty());

        var completed =
                store.setPointB(
                        "world-1",
                        "overworld",
                        "player-1",
                        "Boss",
                        new WorkspaceSelectionPoint(
                                9,
                                64,
                                10));

        assertNotNull(completed);
        assertEquals(
                1,
                completed.generation());
        assertEquals(
                1_000,
                completed.selectedAt());

        var snapshot =
                store.snapshot();

        assertEquals(
                1,
                snapshot.version());
        assertEquals(
                1,
                snapshot.selections()
                        .size());

        var selection =
                snapshot.selections()
                        .getFirst();

        assertEquals(
                "player-1",
                selection.playerId());

        assertEquals(
                new WorkspaceSelectionPoint(
                        1,
                        64,
                        2),
                selection.pointA());

        assertEquals(
                new WorkspaceSelectionPoint(
                        9,
                        64,
                        10),
                selection.pointB());
    }

    @Test
    void updatingEitherCornerCreatesANewerGeneration() {
        var store =
                new WorkspaceSelectionStore(
                        Clock.fixed(
                                Instant.ofEpochMilli(
                                        2_000),
                                ZoneOffset.UTC));

        store.setPointA(
                "world-1",
                "overworld",
                "player-1",
                "Boss",
                new WorkspaceSelectionPoint(
                        0,
                        64,
                        0));

        var first =
                store.setPointB(
                        "world-1",
                        "overworld",
                        "player-1",
                        "Boss",
                        new WorkspaceSelectionPoint(
                                8,
                                64,
                                8));

        var second =
                store.setPointA(
                        "world-1",
                        "overworld",
                        "player-1",
                        "Boss",
                        new WorkspaceSelectionPoint(
                                10,
                                64,
                                10));

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(
                1,
                first.generation());
        assertEquals(
                2,
                second.generation());
        assertEquals(
                new WorkspaceSelectionPoint(
                        10,
                        64,
                        10),
                second.pointA());
    }

    @Test
    void changingWorldResetsPendingCorners() {
        var store =
                new WorkspaceSelectionStore(
                        Clock.fixed(
                                Instant.ofEpochMilli(
                                        3_000),
                                ZoneOffset.UTC));

        store.setPointA(
                "world-1",
                "overworld",
                "player-1",
                "Boss",
                new WorkspaceSelectionPoint(
                        0,
                        64,
                        0));

        assertNull(
                store.setPointB(
                        "world-2",
                        "the_nether",
                        "player-1",
                        "Boss",
                        new WorkspaceSelectionPoint(
                                5,
                                64,
                                5)));

        assertTrue(
                store.snapshot()
                        .selections()
                        .isEmpty());
    }

    @Test
    void snapshotGeneratedAtIsMonotonic() {
        var clock =
                new Clock() {
                    private long millis = 10;

                    @Override
                    public ZoneOffset getZone() {
                        return ZoneOffset.UTC;
                    }

                    @Override
                    public Clock withZone(
                            java.time.ZoneId zone) {
                        return this;
                    }

                    @Override
                    public Instant instant() {
                        return Instant.ofEpochMilli(
                                millis);
                    }

                    @Override
                    public long millis() {
                        return millis--;
                    }
                };

        var store =
                new WorkspaceSelectionStore(
                        clock);

        long first =
                store.snapshot()
                        .generatedAt();

        long second =
                store.snapshot()
                        .generatedAt();

        assertTrue(second > first);
    }
}
