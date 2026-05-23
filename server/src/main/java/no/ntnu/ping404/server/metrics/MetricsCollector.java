package no.ntnu.ping404.server.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central Producer-Consumer hub for per-room metrics.
 * 
 * <p>Producers (e.g. {@link RoomMetricsListener}, packet handlers) call {@link #record(MetricEvent)}
 * which enqueues the event without blocking the calling thread. A dedicated consumer daemon
 * thread drains the queue, updates {@link RoomMetrics} aggregates, checks for threshold violations,
 * and emits periodic log snapshots.</p>
 * 
 * <p><b>Logging Strategy</b></p>
 * <ul>
 *   <li><strong>DEBUG</strong>: Periodic snapshots every 5 seconds on {@code no.ntnu.ping404.metrics}</li>
 *   <li><strong>WARN</strong>: Threshold violations logged immediately for early detection of degraded operation</li>
 * </ul>
 * 
 * <p><b>Thread Safety</b></p>
 * <p>The {@link #record(MetricEvent)} method uses non-blocking {@code offer()} so it never stalls
 * the game thread. The consumer thread processes events sequentially.</p>
 */
public class MetricsCollector {

    private static final Logger metricsLogger = LoggerFactory.getLogger("no.ntnu.ping404.metrics");
    private static final long SNAPSHOT_INTERVAL_MS = 5_000L;
    /** Maximum queue capacity to prevent unbounded memory growth under load. */
    private static final int MAX_QUEUE_CAPACITY = 10_000;

    private final LinkedBlockingQueue<MetricEvent> queue;
    private final AtomicLong droppedEvents = new AtomicLong(0);
    private final ConcurrentHashMap<String, RoomMetrics> roomStats;
    private final ConcurrentHashMap<String, Boolean> activeThresholdViolations;
    private final GeoLocationService geoLocationService;
    private final MetricsThresholds thresholds;
    private final AtomicBoolean running;
    private Thread consumerThread;

    /**
     * Constructs a collector with default thresholds.
     * @param geoLocationService service for IP-to-location lookups
     */
    public MetricsCollector(GeoLocationService geoLocationService) {
        this(geoLocationService, MetricsThresholds.defaults());
    }

    /**
     * Constructs a collector with custom thresholds.
     * @param geoLocationService service for IP-to-location lookups
     * @param thresholds configurable threshold values for alerting
     */
    public MetricsCollector(GeoLocationService geoLocationService, MetricsThresholds thresholds) {
        this.queue = new LinkedBlockingQueue<>(MAX_QUEUE_CAPACITY);
        this.roomStats = new ConcurrentHashMap<>();
        this.activeThresholdViolations = new ConcurrentHashMap<>();
        this.geoLocationService = geoLocationService;
        this.thresholds = thresholds;
        this.running = new AtomicBoolean(false);
    }

    // =========================================================================
    // Producer API
    // =========================================================================

    /** 
     * Non-blocking enqueue; never stalls the calling (game) thread.
     * If the queue is full, the event is dropped and counted.
     * @param event the metric event to record
     */
    public void record(MetricEvent event) {
        if (!queue.offer(event)) {
            droppedEvents.incrementAndGet();
        }
    }

    /** Returns the number of events dropped due to queue backpressure. */
    public long getDroppedEvents() {
        return droppedEvents.get();
    }

    // =========================================================================
    // Consumer API (for testing)
    // =========================================================================

    /**
     * Synchronously drains the entire queue and processes every pending event.
     * Intended for use in tests to make assertions deterministic without Thread.sleep.
     * Also emits a metrics snapshot and checks thresholds after processing.
     */
    public void flush() {
        List<MetricEvent> events = new ArrayList<>();
        queue.drainTo(events);
        for (MetricEvent event : events) {
            processEvent(event);
        }
        checkThresholdsAndEmitSnapshot();
    }

    // =========================================================================
    // Event processing
    // =========================================================================

    private void processEvent(MetricEvent event) {
        if (event instanceof MetricEvent.PositionUpdateEvent e) {
            RoomMetrics metrics = roomStats.computeIfAbsent(e.roomId(), RoomMetrics::new);
            metrics.recordPositionUpdate(e);
        } else if (event instanceof MetricEvent.PingLatencyEvent e) {
            RoomMetrics metrics = roomStats.computeIfAbsent(e.roomId(), RoomMetrics::new);
            metrics.getOrCreatePlayerMetrics(e.connectionId()).setLatestRttMs(e.rttMs());
            evaluateThreshold(
                "rtt:" + e.roomId() + ":" + e.connectionId(),
                "rttMs",
                "room=" + e.roomId() + " connection=" + e.connectionId(),
                thresholds.isRttViolation(e.rttMs()),
                "value=" + e.rttMs() + " max=" + thresholds.getMaxRttMs());
        } else if (event instanceof MetricEvent.PlayerJoinedEvent e) {
            RoomMetrics metrics = roomStats.computeIfAbsent(e.roomId(), RoomMetrics::new);
            PlayerMetrics pm = metrics.getOrCreatePlayerMetrics(e.connectionId());
            if (e.ipAddress() != null) {
                pm.setIpAddress(e.ipAddress());
                try {
                    String geo = geoLocationService.lookup(e.ipAddress());
                    pm.setGeoLocation(geo);
                } catch (Exception ex) {
                    // Geo-lookup failure must never crash the metrics pipeline.
                }
            }
        } else if (event instanceof MetricEvent.OutgoingPacketEvent e) {
            RoomMetrics metrics = roomStats.computeIfAbsent(e.roomId(), RoomMetrics::new);
            metrics.recordOutgoingPacket(e);
        }
    }

    // =========================================================================
    // Threshold checking and logging
    // =========================================================================

    private void checkThresholdsAndEmitSnapshot() {
        int incomingQueueDepth = queue.size();

        evaluateThreshold(
            "incomingQueueDepth:global",
            "incomingQueueDepth",
            "global",
            thresholds.isIncomingQueueViolation(incomingQueueDepth),
            "value=" + incomingQueueDepth + " max=" + thresholds.getMaxIncomingQueueDepth());

        // Check per-room thresholds
        roomStats.forEach((roomId, metrics) -> {
            checkRoomThresholds(roomId, metrics);
            emitRoomSnapshot(roomId, metrics, incomingQueueDepth);
        });
    }

    private void checkRoomThresholds(String roomId, RoomMetrics metrics) {
        // Tick rate
        double tickRate = metrics.getTickRatePerSecond();
        evaluateThreshold(
            "tickRate:" + roomId,
            "tickRateHz",
            "room=" + roomId,
            thresholds.isTickRateViolation(tickRate),
            "value=" + String.format("%.2f", tickRate) +
                " range=[" + thresholds.getMinTickRate() + "," + thresholds.getMaxTickRate() + "]");

        // Per-connection jitter
        for (int connectionId : metrics.getConnectionIds()) {
            double jitterMs = metrics.getJitterMs(connectionId);
            evaluateThreshold(
                "jitter:" + roomId + ":" + connectionId,
                "jitterMs",
                "room=" + roomId + " connection=" + connectionId,
                thresholds.isJitterViolation(jitterMs),
                "value=" + String.format("%.2f", jitterMs) + " max=" + thresholds.getMaxJitterMs());
        }

        // Drop rate (incoming)
        double dropRate = metrics.getDropRate();
        evaluateThreshold(
            "incomingDropRate:" + roomId,
            "incomingDropRate",
            "room=" + roomId,
            thresholds.isDropRateViolation(dropRate),
            "value=" + String.format("%.2f%%", dropRate * 100) +
                " max=" + String.format("%.2f%%", thresholds.getMaxDropRate() * 100));

        // Outgoing drop rate
        double outgoingDropRate = metrics.getOutgoingDropRate();
        evaluateThreshold(
            "outgoingDropRate:" + roomId,
            "outgoingDropRate",
            "room=" + roomId,
            thresholds.isDropRateViolation(outgoingDropRate),
            "value=" + String.format("%.2f%%", outgoingDropRate * 100) +
                " max=" + String.format("%.2f%%", thresholds.getMaxDropRate() * 100));

        // Outgoing bandwidth
        double bandwidthBps = metrics.getOutgoingBandwidthBps();
        evaluateThreshold(
            "outgoingBandwidth:" + roomId,
            "outgoingBandwidthBps",
            "room=" + roomId,
            thresholds.isBandwidthViolation((long) bandwidthBps),
            "value=" + String.format("%.0f", bandwidthBps) +
                " max=" + thresholds.getMaxOutgoingBandwidthBps());
    }

    private void evaluateThreshold(String key, String metric, String scope, boolean violating, String detail) {
        boolean wasViolating = activeThresholdViolations.containsKey(key);
        if (violating) {
            if (!wasViolating) {
                activeThresholdViolations.put(key, Boolean.TRUE);
                metricsLogger.warn("THRESHOLD BREACH: metric={} scope={} {}", metric, scope, detail);
            }
            return;
        }
        if (wasViolating) {
            activeThresholdViolations.remove(key);
            metricsLogger.info("THRESHOLD RECOVERED: metric={} scope={} {}", metric, scope, detail);
        }
    }

    private void emitRoomSnapshot(String roomId, RoomMetrics metrics, int incomingQueueDepth) {
        metricsLogger.debug(
            "room={} in[total={} dropped={} adjusted={} tickRate={}] " +
            "out[total={} dropped={} queued={} bytes={} bps={}] queueDepth={}",
            roomId,
            metrics.getTotalUpdates(),
            metrics.getDroppedUpdates(),
            metrics.getAdjustedUpdates(),
            String.format("%.2f", metrics.getTickRatePerSecond()),
            metrics.getTotalOutgoingPackets(),
            metrics.getDroppedOutgoingPackets(),
            metrics.getQueuedOutgoingPackets(),
            metrics.getTotalOutgoingBytes(),
            String.format("%.0f", metrics.getOutgoingBandwidthBps()),
            incomingQueueDepth);
    }

    // =========================================================================
    // Lifecycle management
    // =========================================================================

    /** Starts the background consumer daemon thread. */
    public void start() {
        if (running.compareAndSet(false, true)) {
            consumerThread = new Thread(() -> {
                long lastSnapshot = System.currentTimeMillis();
                while (running.get()) {
                    try {
                        MetricEvent event = queue.poll(100, TimeUnit.MILLISECONDS);
                        if (event != null) {
                            processEvent(event);
                        }
                        long now = System.currentTimeMillis();
                        if (now - lastSnapshot >= SNAPSHOT_INTERVAL_MS) {
                            checkThresholdsAndEmitSnapshot();
                            lastSnapshot = now;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }, "metrics-consumer");
            consumerThread.setDaemon(true);
            consumerThread.start();
        }
    }

    /** Stops the background consumer daemon thread. */
    public void stop() {
        running.set(false);
        if (consumerThread != null) {
            consumerThread.interrupt();
        }
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    /** Returns the current depth of the incoming event queue. */
    public int getQueueDepth() {
        return queue.size();
    }

    /** Returns (or lazily creates) the metrics for the specified room. */
    public RoomMetrics getMetrics(String roomId) {
        return roomStats.computeIfAbsent(roomId, RoomMetrics::new);
    }

    /** Returns the geo-location service used by this collector. */
    public GeoLocationService getGeoLocationService() {
        return geoLocationService;
    }

    /** Returns the thresholds configuration. */
    public MetricsThresholds getThresholds() {
        return thresholds;
    }

    int getActiveThresholdViolationCount() {
        return activeThresholdViolations.size();
    }

    boolean hasActiveThresholdViolation(String key) {
        return activeThresholdViolations.containsKey(key);
    }
}
