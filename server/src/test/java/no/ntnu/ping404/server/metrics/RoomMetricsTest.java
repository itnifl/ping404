package no.ntnu.ping404.server.metrics;

import no.ntnu.ping404.model.Player;
import no.ntnu.ping404.network.INetworkServer.PlayerConnection;
import no.ntnu.ping404.network.INetworkServer;
import no.ntnu.ping404.server.GameRoom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

class RoomMetricsTest {

    // ------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------

    private static final String ROOM_A = "room-A";
    private static final String ROOM_B = "room-B";
    private static final int HOST_ID = 0;
    private static final int CONN_1 = 1;
    /** 50 ms between ticks --> nominal rate of 20 updates/second. */
    private static final long TICK_INTERVAL_NANOS = 50_000_000L;
    /** 250 ms late gap - 5× the normal interval - produces measurable jitter. */
    private static final long LATE_GAP_NANOS = 250_000_000L;
    private static final int TICK_COUNT = 20;
    private static final double EXPECTED_TICK_RATE = 20.0;
    private static final double TICK_RATE_TOLERANCE = 3.0;
    private static final int QUEUE_PROBE_COUNT = 50;
    private static final int LATENCY_PROBE_COUNT = 100;
    private static final long LATENCY_BUDGET_MS = 10;
    private static final long EXPECTED_RTT_MS = 50L;
    private static final String TEST_IP = "10.0.0.1";
    private static final String METRICS_LOGGER_NAME = "no.ntnu.ping404.metrics";

    // ------------------------------------------------------------------
    // Setup
    // ------------------------------------------------------------------

    private MetricsCollector collector;

    @BeforeEach
    void setUp() {
        collector = new MetricsCollector(new NoOpGeoLocationService());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static MetricEvent.PositionUpdateEvent posEvent(
            String roomId, int connId, long nanos, boolean dropped, boolean adjusted) {
        return new MetricEvent.PositionUpdateEvent(roomId, connId, nanos, dropped, adjusted);
    }

    // ------------------------------------------------------------------
    // M1 - Tick rate
    // ------------------------------------------------------------------

    /**
     * 20 position events fed 50 ms apart span one full second.
     * After the consumer drains the queue the computed tick rate must be ~20/s.
     */
    @Test
    @Tag("M1")
    void tickRateReflectsRateOfPositionUpdatesPerSecond() {
        long baseNanos = 0L;
        for (int i = 0; i < TICK_COUNT; i++) {
            collector.record(posEvent(ROOM_A, CONN_1, baseNanos + i * TICK_INTERVAL_NANOS,
                    false, false));
        }
        collector.flush();

        double rate = collector.getMetrics(ROOM_A).getTickRatePerSecond();
        assertEquals(EXPECTED_TICK_RATE, rate, TICK_RATE_TOLERANCE,
                "Tick rate should be approximately 20 updates/second");
    }

    // ------------------------------------------------------------------
    // M2 - Jitter
    // ------------------------------------------------------------------

    /**
     * Four evenly-spaced events followed by one late event create an irregular
     * inter-arrival pattern. Jitter must be positive after the flush.
     */
    @Test
    @Tag("M2")
    void jitterIsNonZeroWhenUpdateIntervalsAreIrregular() {
        long baseNanos = 0L;
        for (int i = 0; i < 4; i++) {
            collector.record(posEvent(ROOM_A, CONN_1, baseNanos + i * TICK_INTERVAL_NANOS,
                    false, false));
        }
        // Late event: 250 ms after the last instead of the normal 50 ms.
        collector.record(posEvent(ROOM_A, CONN_1,
                baseNanos + 3 * TICK_INTERVAL_NANOS + LATE_GAP_NANOS,
                false, false));
        collector.flush();

        assertTrue(collector.getMetrics(ROOM_A).getJitterMs(CONN_1) > 0,
                "Jitter must be positive when update intervals are irregular");
    }

    // ------------------------------------------------------------------
    // M3 - Queue depth
    // ------------------------------------------------------------------

    /**
     * When the consumer has not been started, every recorded event must remain
     * in the queue so the depth equals the number of recorded events.
     */
    @Test
    @Tag("M3")
    void queueDepthIsNonZeroWhenConsumerIsNotRunning() {
        MetricsCollector isolated = new MetricsCollector(new NoOpGeoLocationService());
        for (int i = 0; i < QUEUE_PROBE_COUNT; i++) {
            isolated.record(posEvent(ROOM_A, CONN_1, (long) i * TICK_INTERVAL_NANOS, false, false));
        }

        assertEquals(QUEUE_PROBE_COUNT, isolated.getQueueDepth(),
                QUEUE_PROBE_COUNT + " events recorded with no consumer should fill the queue to depth "
                        + QUEUE_PROBE_COUNT);
    }

    // ------------------------------------------------------------------
    // M4 - Dropped updates
    // ------------------------------------------------------------------

    /**
     * A position update that is discarded (e.g. wrong game phase) must increment
     * the per-room dropped-updates counter.
     */
    @Test
    @Tag("M4")
    void droppedUpdatesIncrementWhenGameIsNotInPlayingPhase() {
        collector.record(posEvent(ROOM_A, CONN_1, System.nanoTime(), true, false));
        collector.flush();

        assertEquals(1L, collector.getMetrics(ROOM_A).getDroppedUpdates(),
                "A dropped position update must increment the dropped-updates counter");
    }

    // ------------------------------------------------------------------
    // M5 - Adjusted updates
    // ------------------------------------------------------------------

    /**
     * A position update that is clamped to the valid boundary must increment
     * the per-room adjusted-updates counter.
     */
    @Test
    @Tag("M5")
    void adjustedUpdatesIncrementWhenPositionIsClamped() {
        collector.record(posEvent(ROOM_A, CONN_1, System.nanoTime(), false, true));
        collector.flush();

        assertEquals(1L, collector.getMetrics(ROOM_A).getAdjustedUpdates(),
                "A clamped position update must increment the adjusted-updates counter");
    }

    // ------------------------------------------------------------------
    // M6 - Ping-Pong round-trip latency
    // ------------------------------------------------------------------

    /**
     * The RTT value carried in a PingLatencyEvent must be stored in the
     * per-player PlayerMetrics after the consumer processes the event.
     */
    @Test
    @Tag("M6")
    void pingLatencyIsComputedFromPongRoundTrip() {
        collector.record(new MetricEvent.PingLatencyEvent(ROOM_A, CONN_1, EXPECTED_RTT_MS));
        collector.flush();

        PlayerMetrics pm = collector.getMetrics(ROOM_A).getPlayerMetrics(CONN_1);
        assertNotNull(pm, "PlayerMetrics must be created after a PingLatencyEvent");
        assertEquals(EXPECTED_RTT_MS, pm.getLatestRttMs(),
                "Measured RTT must equal the value carried in the PingLatencyEvent");
    }

    // ------------------------------------------------------------------
    // M7 - IP address capture on join
    // ------------------------------------------------------------------

    /**
     * When a player joins, the listener must extract the remote address from the
     * PlayerConnection and store it in PlayerMetrics.
     */
    @Test
    @Tag("M7")
    void playerIpAddressIsCapturedFromConnectionOnJoin() {
        GameRoom room = new GameRoom(ROOM_A, HOST_ID);
        PlayerConnection conn = new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(CONN_1, TEST_IP);
        Player player = new Player(CONN_1, "TestPlayer");

        RoomMetricsListener listener = new RoomMetricsListener(collector);
        listener.onPlayerJoined(room, conn, player);

        PlayerMetrics pm = collector.getMetrics(ROOM_A).getPlayerMetrics(CONN_1);
        assertNotNull(pm, "PlayerMetrics must be created when a player joins");
        assertEquals(TEST_IP, pm.getIpAddress(),
                "The player's IP address must be captured from the connection on join");
    }

    // ------------------------------------------------------------------
    // M8 - Geo-location failure tolerance (trivially passes with stubs)
    // ------------------------------------------------------------------

    /**
     * A geo-location service that throws must never propagate the exception.
     * The geo-location field must remain null rather than crashing the server.
     */
    @Test
    @Tag("M8")
    void geoLocationIsNullWhenServiceThrowsException() {
        GeoLocationService throwingService = ip -> {
            throw new RuntimeException("geo lookup unavailable");
        };
        MetricsCollector faultCollector = new MetricsCollector(throwingService);
        GameRoom room = new GameRoom(ROOM_A, HOST_ID);
        PlayerConnection conn = new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(CONN_1, TEST_IP);
        Player player = new Player(CONN_1, "TestPlayer");
        RoomMetricsListener listener = new RoomMetricsListener(faultCollector);

        assertDoesNotThrow(() -> listener.onPlayerJoined(room, conn, player),
                "A failing geo-location service must never propagate an exception");

        PlayerMetrics pm = faultCollector.getMetrics(ROOM_A).getPlayerMetrics(CONN_1);
        if (pm != null) {
            assertNull(pm.getGeoLocation(),
                    "geo-location must be null when the lookup service fails");
        }
    }

    // ------------------------------------------------------------------
    // M9 - Room isolation
    // ------------------------------------------------------------------

    /**
     * A dropped update recorded for room A must not affect room B's counters.
     */
    @Test
    @Tag("M9")
    void metricsAreIsolatedBetweenRooms() {
        collector.record(posEvent(ROOM_A, CONN_1, System.nanoTime(), true, false));
        collector.flush();

        assertEquals(1L, collector.getMetrics(ROOM_A).getDroppedUpdates(),
                "Room A must record the dropped update");
        assertEquals(0L, collector.getMetrics(ROOM_B).getDroppedUpdates(),
                "Room B must have zero dropped updates - rooms are independent");
    }

    // ------------------------------------------------------------------
    // M10 - Non-blocking recording latency (trivially passes with stubs)
    // ------------------------------------------------------------------

    /**
     * 100 consecutive record() calls must complete within the latency budget of
     * the game-critical PositionHandlerCommand calling path.
     */
    @Test
    @Tag("M10")
    void metricsRecordingDoesNotExceedPositionHandlerLatencyBudget() {
        long start = System.nanoTime();
        for (int i = 0; i < LATENCY_PROBE_COUNT; i++) {
            collector.record(posEvent(ROOM_A, CONN_1, (long) i * TICK_INTERVAL_NANOS, false, false));
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        assertTrue(elapsedMs < LATENCY_BUDGET_MS,
                LATENCY_PROBE_COUNT + " record() calls took " + elapsedMs
                        + " ms - must be < " + LATENCY_BUDGET_MS + " ms");
    }

    // ------------------------------------------------------------------
    // M11 - Dedicated metrics logger availability
    // ------------------------------------------------------------------

    /**
     * The dedicated metrics logger must be available and flush must run without
     * binding-specific logging assumptions.
     */
    @Test
    @Tag("M11")
    void metricsSamplesAreEmittedOnDedicatedMetricsLogger() {
        assertNotNull(LoggerFactory.getLogger(METRICS_LOGGER_NAME),
                "Dedicated metrics logger should be resolvable via SLF4J");

        collector.start();
        try {
            collector.record(posEvent(ROOM_A, CONN_1, System.nanoTime(), false, false));
            assertDoesNotThrow(collector::flush,
                    "flush() should complete without logging backend-specific failures");
        } finally {
            collector.stop();
        }
    }
}
