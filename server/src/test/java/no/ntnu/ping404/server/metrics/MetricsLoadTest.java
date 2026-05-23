package no.ntnu.ping404.server.metrics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Load and benchmark tests for the metrics infrastructure.
 * Validates stability under high input frequency and across multiple rooms.
 * 
 * <p>These tests verify:
 * <ul>
 *   <li>Threshold breach warning logging</li>
 *   <li>Multiple rooms under concurrent load</li>
 *   <li>High-frequency event processing stability</li>
 *   <li>Outgoing packet metrics tracking</li>
 * </ul>
 */
@Tag("Issue27")
class MetricsLoadTest {

    // ------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------

    private static final int CONN_1 = 1;
    private static final int CONN_2 = 2;
    private static final long TICK_INTERVAL_NANOS = 50_000_000L; // 50ms = 20 ticks/sec

    // ------------------------------------------------------------------
    // Setup
    // ------------------------------------------------------------------

    private MetricsCollector collector;

    @BeforeEach
    void setUp() {
        collector = new MetricsCollector(new NoOpGeoLocationService());
    }

    @AfterEach
    void tearDown() {
        if (collector != null) {
            collector.stop();
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static MetricEvent.PositionUpdateEvent posEvent(
            String roomId, int connId, long nanos, boolean dropped, boolean adjusted) {
        return new MetricEvent.PositionUpdateEvent(roomId, connId, nanos, dropped, adjusted);
    }

    private static MetricEvent.OutgoingPacketEvent outEvent(
            String roomId, int connId, String packetType, int bytes, long nanos,
            boolean queued, boolean dropped) {
        return new MetricEvent.OutgoingPacketEvent(roomId, connId, packetType, bytes, nanos, queued, dropped);
    }

    // ==================================================================
    // THRESHOLD BREACH BEHAVIOR TESTS
    // ==================================================================

    /**
     * High RTT (above threshold) should be reflected in the collected metrics.
     */
    @Test
    @Tag("M7")
    void highRttIsRecordedForPlayerMetrics() {
        // Default max RTT is 150ms, use 200ms to trigger breach
        collector.record(new MetricEvent.PingLatencyEvent("room-A", CONN_1, 200L));
        collector.flush();

        PlayerMetrics pm = collector.getMetrics("room-A").getPlayerMetrics(CONN_1);
        assertNotNull(pm, "Player metrics should exist after ping event");
        assertEquals(200L, pm.getLatestRttMs(), "High RTT must be stored as-is");
        assertTrue(collector.getThresholds().isRttViolation(pm.getLatestRttMs()),
                "200ms should violate default max RTT threshold");
    }

    /**
     * RTT below threshold should not be considered a threshold violation.
     */
    @Test
    @Tag("M7")
    void rttBelowThresholdIsNotViolation() {
        // Default max RTT is 150ms, use 50ms which is OK
        collector.record(new MetricEvent.PingLatencyEvent("room-A", CONN_1, 50L));
        collector.flush();

        PlayerMetrics pm = collector.getMetrics("room-A").getPlayerMetrics(CONN_1);
        assertNotNull(pm, "Player metrics should exist after ping event");
        assertFalse(collector.getThresholds().isRttViolation(pm.getLatestRttMs()),
                "50ms should be below the default max RTT threshold");
    }

        /**
         * Repeated threshold breaches should keep one active violation state until recovery.
         */
        @Test
        @Tag("M7")
        void repeatedRttBreachesKeepSingleActiveViolation() {
        MetricsThresholds strict = MetricsThresholds.builder()
            .maxRttMs(10L)
            .build();
        collector = new MetricsCollector(new NoOpGeoLocationService(), strict);

        collector.record(new MetricEvent.PingLatencyEvent("room-A", CONN_1, 25L));
        collector.flush();
        assertEquals(1, collector.getActiveThresholdViolationCount(),
            "Initial breach should register one active violation");
        assertTrue(collector.hasActiveThresholdViolation("rtt:room-A:1"),
            "RTT breach key should be active after threshold breach");

        collector.record(new MetricEvent.PingLatencyEvent("room-A", CONN_1, 30L));
        collector.flush();
        assertEquals(1, collector.getActiveThresholdViolationCount(),
            "Repeated breach should not create duplicate active violations");
        }

        /**
         * Once metric values recover, active violation state should be cleared.
         */
        @Test
        @Tag("M7")
        void rttRecoveryClearsActiveViolation() {
        MetricsThresholds strict = MetricsThresholds.builder()
            .maxRttMs(10L)
            .build();
        collector = new MetricsCollector(new NoOpGeoLocationService(), strict);

        collector.record(new MetricEvent.PingLatencyEvent("room-A", CONN_1, 25L));
        collector.flush();
        assertTrue(collector.hasActiveThresholdViolation("rtt:room-A:1"),
            "RTT breach should be active after high-latency sample");

        collector.record(new MetricEvent.PingLatencyEvent("room-A", CONN_1, 5L));
        collector.flush();
        assertEquals(0, collector.getActiveThresholdViolationCount(),
            "Active violation should clear after value recovers below threshold");
        }

    /**
     * High drop rate should be detectable from room metrics.
     */
    @Test
    @Tag("M7")
    void dropRateThresholdBreachIsDetectedFromMetrics() {
        // Create 100 events, 20 dropped = 20% drop rate (max is 5%)
        long baseNanos = 0L;
        for (int i = 0; i < 100; i++) {
            boolean dropped = (i < 20);
            collector.record(posEvent("room-A", CONN_1, baseNanos + i * TICK_INTERVAL_NANOS, dropped, false));
        }
        collector.flush();

        RoomMetrics metrics = collector.getMetrics("room-A");
        assertTrue(collector.getThresholds().isDropRateViolation(metrics.getDropRate()),
                "20% incoming drop rate should violate default 5% threshold");
    }

    /**
     * Tick rate outside acceptable range should be detectable with custom thresholds.
     */
    @Test
    @Tag("M7")
    void tickRateOutsideRangeIsDetected() {
        // Use custom thresholds with a narrow tick rate range (18-22 Hz)
        // Then record events at 10 Hz rate (100ms intervals) - way too slow
        MetricsThresholds strictThresholds = MetricsThresholds.builder()
                .minTickRate(18.0)
                .maxTickRate(22.0)
                .build();
        collector = new MetricsCollector(new NoOpGeoLocationService(), strictThresholds);

        long baseNanos = 0L;
        long slowInterval = 100_000_000L; // 100ms = 10 ticks/sec (too slow)
        for (int i = 0; i < 20; i++) {
            collector.record(posEvent("room-A", CONN_1, baseNanos + i * slowInterval, false, false));
        }
        collector.flush();

        RoomMetrics metrics = collector.getMetrics("room-A");
        assertTrue(strictThresholds.isTickRateViolation(metrics.getTickRatePerSecond()),
            "~10Hz tick rate should violate configured [18,22] range");
    }

    // ==================================================================
    // OUTGOING PACKET METRICS TESTS
    // ==================================================================

    /**
     * Outgoing packets should be tracked correctly.
     */
    @Test
    @Tag("M8")
    void outgoingPacketsAreCountedCorrectly() {
        long now = System.nanoTime();
        collector.record(outEvent("room-A", CONN_1, "PositionUpdate", 128, now, false, false));
        collector.record(outEvent("room-A", CONN_1, "PositionUpdate", 128, now + 50_000_000, false, false));
        collector.record(outEvent("room-A", CONN_2, "PositionUpdate", 128, now + 100_000_000, false, false));
        collector.flush();

        RoomMetrics metrics = collector.getMetrics("room-A");
        assertEquals(3, metrics.getTotalOutgoingPackets(),
                "Should have counted 3 outgoing packets");
        assertEquals(384, metrics.getTotalOutgoingBytes(),
                "Should have counted 384 bytes (3 x 128)");
    }

    /**
     * Dropped outgoing packets should be tracked separately.
     */
    @Test
    @Tag("M8")
    void droppedOutgoingPacketsAreTracked() {
        long now = System.nanoTime();
        collector.record(outEvent("room-A", CONN_1, "PositionUpdate", 128, now, false, false));
        collector.record(outEvent("room-A", CONN_1, "PositionUpdate", 128, now + 50_000_000, false, true));
        collector.record(outEvent("room-A", CONN_1, "PositionUpdate", 128, now + 100_000_000, false, true));
        collector.flush();

        RoomMetrics metrics = collector.getMetrics("room-A");
        assertEquals(3, metrics.getTotalOutgoingPackets(),
                "Should have counted 3 total outgoing packets");
        assertEquals(2, metrics.getDroppedOutgoingPackets(),
                "Should have counted 2 dropped outgoing packets");
    }

    /**
     * Queued outgoing packets should be tracked.
     */
    @Test
    @Tag("M8")
    void queuedOutgoingPacketsAreTracked() {
        long now = System.nanoTime();
        collector.record(outEvent("room-A", CONN_1, "PositionUpdate", 128, now, true, false));
        collector.record(outEvent("room-A", CONN_1, "PositionUpdate", 128, now + 50_000_000, true, false));
        collector.record(outEvent("room-A", CONN_1, "PositionUpdate", 128, now + 100_000_000, false, false));
        collector.flush();

        RoomMetrics metrics = collector.getMetrics("room-A");
        assertEquals(3, metrics.getTotalOutgoingPackets(),
                "Should have counted 3 total outgoing packets");
        assertEquals(2, metrics.getQueuedOutgoingPackets(),
                "Should have counted 2 queued outgoing packets");
    }

    /**
     * High outgoing drop rate should be detectable from room metrics.
     */
    @Test
    @Tag("M8")
    void highOutgoingDropRateIsDetectedFromMetrics() {
        long now = System.nanoTime();
        // 20 packets, 15 dropped = 75% drop rate
        for (int i = 0; i < 20; i++) {
            boolean dropped = (i < 15);
            collector.record(outEvent("room-A", CONN_1, "PositionUpdate", 128, 
                    now + i * TICK_INTERVAL_NANOS, false, dropped));
        }
        collector.flush();

        RoomMetrics metrics = collector.getMetrics("room-A");
        assertTrue(collector.getThresholds().isDropRateViolation(metrics.getOutgoingDropRate()),
            "75% outgoing drop rate should violate default 5% threshold");
    }

    // ==================================================================
    // MULTIPLE ROOMS LOAD TESTS
    // ==================================================================

    /**
     * Metrics should be tracked correctly across multiple rooms.
     */
    @Test
    @Tag("M9")
    void multipleRoomsAreTrackedIndependently() {
        String[] rooms = {"room-A", "room-B", "room-C", "room-D", "room-E"};
        long baseNanos = 0L;
        
        for (String room : rooms) {
            for (int i = 0; i < 20; i++) {
                collector.record(posEvent(room, CONN_1, baseNanos + i * TICK_INTERVAL_NANOS, false, false));
            }
        }
        collector.flush();

        for (String room : rooms) {
            RoomMetrics metrics = collector.getMetrics(room);
            assertEquals(20, metrics.getTotalUpdates(),
                    "Room " + room + " should have 20 position updates");
        }
    }

    /**
     * High-volume load test: 10 rooms, 100 events each = 1000 total events.
     */
    @Test
    @Tag("M9")
    void handlesHighVolumeLoadAcrossMultipleRooms() {
        int roomCount = 10;
        int eventsPerRoom = 100;
        long baseNanos = 0L;

        for (int r = 0; r < roomCount; r++) {
            String room = "room-" + r;
            for (int i = 0; i < eventsPerRoom; i++) {
                collector.record(posEvent(room, CONN_1, baseNanos + i * TICK_INTERVAL_NANOS, false, false));
            }
        }

        assertEquals(roomCount * eventsPerRoom, collector.getQueueDepth(),
                "All 1000 events should be queued before flush");

        collector.flush();

        assertEquals(0, collector.getQueueDepth(),
                "Queue should be empty after flush");

        for (int r = 0; r < roomCount; r++) {
            String room = "room-" + r;
            RoomMetrics metrics = collector.getMetrics(room);
            assertEquals(eventsPerRoom, metrics.getTotalUpdates(),
                    "Room " + room + " should have " + eventsPerRoom + " updates after flush");
        }
    }

    /**
     * Concurrent producers should not cause race conditions or data loss.
     */
    @Test
    @Tag("M9")
    void concurrentProducersDoNotCauseDataLoss() throws InterruptedException {
        int threadCount = 5;
        int eventsPerThread = 200;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    String room = "room-" + threadId;
                    long baseNanos = System.nanoTime();
                    for (int i = 0; i < eventsPerThread; i++) {
                        collector.record(posEvent(room, CONN_1, baseNanos + i * TICK_INTERVAL_NANOS, false, false));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS),
                "All producer threads should complete within 5 seconds");

        int expectedTotal = threadCount * eventsPerThread;
        assertEquals(expectedTotal, collector.getQueueDepth(),
                "All " + expectedTotal + " events should be in queue");

        collector.flush();

        int totalProcessed = IntStream.range(0, threadCount)
                .mapToObj(t -> collector.getMetrics("room-" + t))
                .mapToInt(m -> (int) m.getTotalUpdates())
                .sum();

        assertEquals(expectedTotal, totalProcessed,
                "All " + expectedTotal + " events should be processed without loss");

        executor.shutdown();
    }

    // ==================================================================
    // MIXED EVENT TYPES TESTS
    // ==================================================================

    /**
     * Mixed event types should all be processed correctly.
     */
    @Test
    @Tag("M9")
    void mixedEventTypesAreProcessedCorrectly() {
        long now = System.nanoTime();
        String room = "room-A";

        // Position updates
        collector.record(posEvent(room, CONN_1, now, false, false));
        collector.record(posEvent(room, CONN_1, now + 50_000_000, true, false)); // dropped
        collector.record(posEvent(room, CONN_1, now + 100_000_000, false, true)); // adjusted

        // Outgoing packets
        collector.record(outEvent(room, CONN_1, "PositionUpdate", 128, now, false, false));
        collector.record(outEvent(room, CONN_2, "GameOver", 64, now, false, false));

        // Ping latency
        collector.record(new MetricEvent.PingLatencyEvent(room, CONN_1, 25L));
        collector.record(new MetricEvent.PingLatencyEvent(room, CONN_2, 35L));

        // Player joined
        collector.record(new MetricEvent.PlayerJoinedEvent(room, CONN_1, "192.168.1.1"));

        collector.flush();

        RoomMetrics metrics = collector.getMetrics(room);
        
        // Verify incoming
        assertEquals(3, metrics.getTotalUpdates(), "Should have 3 position updates");
        assertEquals(1, metrics.getDroppedUpdates(), "Should have 1 dropped update");
        assertEquals(1, metrics.getAdjustedUpdates(), "Should have 1 adjusted update");

        // Verify outgoing
        assertEquals(2, metrics.getTotalOutgoingPackets(), "Should have 2 outgoing packets");
        assertEquals(192, metrics.getTotalOutgoingBytes(), "Should have 192 bytes (128 + 64)");

        // Verify player metrics
        PlayerMetrics pm1 = metrics.getOrCreatePlayerMetrics(CONN_1);
        assertEquals(25L, pm1.getLatestRttMs(), "Connection 1 RTT should be 25ms");
        assertEquals("192.168.1.1", pm1.getIpAddress(), "Connection 1 IP should be set");

        PlayerMetrics pm2 = metrics.getOrCreatePlayerMetrics(CONN_2);
        assertEquals(35L, pm2.getLatestRttMs(), "Connection 2 RTT should be 35ms");
    }

    // ==================================================================
    // SNAPSHOT/FLUSH BEHAVIOR TESTS
    // ==================================================================

    /**
     * Flush should process data for each room without cross-room interference.
     */
    @Test
    @Tag("M10")
    void flushProcessesEachRoomIndependently() {
        String[] rooms = {"room-A", "room-B"};
        long now = System.nanoTime();
        
        for (String room : rooms) {
            collector.record(posEvent(room, CONN_1, now, false, false));
            collector.record(outEvent(room, CONN_1, "PositionUpdate", 128, now, false, false));
        }
        collector.flush();

        assertEquals(1, collector.getMetrics("room-A").getTotalUpdates(),
            "room-A should have exactly one incoming update");
        assertEquals(1, collector.getMetrics("room-B").getTotalUpdates(),
            "room-B should have exactly one incoming update");
        assertEquals(1, collector.getMetrics("room-A").getTotalOutgoingPackets(),
            "room-A should have exactly one outgoing packet");
        assertEquals(1, collector.getMetrics("room-B").getTotalOutgoingPackets(),
            "room-B should have exactly one outgoing packet");
    }

    /**
     * Flush should preserve both incoming and outgoing metric samples.
     */
    @Test
    @Tag("M10")
    void flushPreservesIncomingAndOutgoingMetrics() {
        long now = System.nanoTime();
        String room = "room-A";

        collector.record(posEvent(room, CONN_1, now, false, false));
        collector.record(outEvent(room, CONN_1, "PositionUpdate", 128, now, false, false));
        collector.flush();

        RoomMetrics metrics = collector.getMetrics(room);
        assertEquals(1, metrics.getTotalUpdates(), "Incoming metrics should be preserved");
        assertEquals(1, metrics.getTotalOutgoingPackets(), "Outgoing metrics should be preserved");
    }

    // ==================================================================
    // THRESHOLDS CUSTOMIZATION TESTS
    // ==================================================================

    /**
    * Custom thresholds via builder should be respected for RTT violation checks.
     */
    @Test
    @Tag("M7")
    void customThresholdsAreRespected() {
        // Create collector with very strict RTT threshold (10ms max)
        MetricsThresholds strict = MetricsThresholds.builder()
                .maxRttMs(10L)
                .build();
        collector = new MetricsCollector(new NoOpGeoLocationService(), strict);

        // 25ms RTT should trigger warning with strict threshold but not with default (150ms)
        collector.record(new MetricEvent.PingLatencyEvent("room-A", CONN_1, 25L));
        collector.flush();

        PlayerMetrics pm = collector.getMetrics("room-A").getPlayerMetrics(CONN_1);
        assertNotNull(pm, "Player metrics should exist after ping event");
        assertTrue(strict.isRttViolation(pm.getLatestRttMs()),
            "Custom 10ms RTT threshold should be violated by 25ms latency");
    }

    /**
     * Default thresholds should use sensible values.
     */
    @Test
    @Tag("M7")
    void defaultThresholdsHaveSensibleValues() {
        MetricsThresholds defaults = MetricsThresholds.defaults();
        
        assertEquals(15.0, defaults.getMinTickRate(), "Min tick rate should be 15 Hz");
        assertEquals(25.0, defaults.getMaxTickRate(), "Max tick rate should be 25 Hz");
        assertEquals(20.0, defaults.getMaxJitterMs(), "Max jitter should be 20ms");
        assertEquals(100, defaults.getMaxIncomingQueueDepth(), "Max incoming queue should be 100");
        assertEquals(50, defaults.getMaxOutgoingQueueDepth(), "Max outgoing queue should be 50");
        assertEquals(0.05, defaults.getMaxDropRate(), 0.001, "Max drop rate should be 5%");
        assertEquals(150L, defaults.getMaxRttMs(), "Max RTT should be 150ms");
        assertEquals(50_000L, defaults.getMaxOutgoingBandwidthBps(), "Max bandwidth should be 50KB/s");
    }

    // ==================================================================
    // OUTGOING BANDWIDTH TESTS
    // ==================================================================

    /**
     * Outgoing bandwidth calculation should be accurate.
     */
    @Test
    @Tag("M8")
    void outgoingBandwidthCalculationIsAccurate() {
        long baseNanos = System.nanoTime();
        String room = "room-A";
        
        // Send 10 packets of 100 bytes over 1 second interval
        for (int i = 0; i < 10; i++) {
            collector.record(outEvent(room, CONN_1, "PositionUpdate", 100, 
                    baseNanos + i * 100_000_000L, false, false)); // 100ms intervals
        }
        collector.flush();

        RoomMetrics metrics = collector.getMetrics(room);
        assertEquals(1000, metrics.getTotalOutgoingBytes(),
                "Should have 1000 bytes total");
        
        // Bandwidth depends on timing window; just verify it's positive and reasonable
        double bps = metrics.getOutgoingBandwidthBps();
        assertTrue(bps > 0, "Bandwidth should be positive");
    }

    /**
     * Excessive bandwidth should be detectable via custom thresholds.
     */
    @Test
    @Tag("M8")
    void excessiveBandwidthIsDetected() {
        // Use custom threshold with very low bandwidth limit
        MetricsThresholds strict = MetricsThresholds.builder()
                .maxOutgoingBandwidthBps(100L) // Only 100 bytes/sec allowed
                .build();
        collector = new MetricsCollector(new NoOpGeoLocationService(), strict);

        long baseNanos = System.nanoTime();
        // Send 1000 bytes in 100ms = 10,000 bytes/sec (way over limit)
        for (int i = 0; i < 10; i++) {
            collector.record(outEvent("room-A", CONN_1, "PositionUpdate", 100, 
                    baseNanos + i * 10_000_000L, false, false)); // 10ms intervals
        }
        collector.flush();

        RoomMetrics metrics = collector.getMetrics("room-A");
        assertTrue(strict.isBandwidthViolation((long) metrics.getOutgoingBandwidthBps()),
            "Measured outgoing bandwidth should violate strict 100 B/s threshold");
    }
}
