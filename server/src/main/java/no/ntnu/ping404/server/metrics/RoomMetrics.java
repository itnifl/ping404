package no.ntnu.ping404.server.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Aggregates runtime metrics for a single {@link no.ntnu.ping404.server.GameRoom}.
 * 
 * <p>Tracks both <strong>incoming</strong> (position updates) and <strong>outgoing</strong>
 * (broadcast packets) traffic, along with per-connection {@link PlayerMetrics}.</p>
 * 
 * <p><b>Incoming Metrics</b></p>
 * <ul>
 *   <li>{@link #getTotalUpdates()} - total position updates received</li>
 *   <li>{@link #getDroppedUpdates()} - updates discarded (wrong phase, etc.)</li>
 *   <li>{@link #getAdjustedUpdates()} - updates with clamped positions</li>
 *   <li>{@link #getTickRatePerSecond()} - computed incoming update rate</li>
 *   <li>{@link #getJitterMs(int)} - per-connection inter-arrival jitter</li>
 * </ul>
 * 
 * <p><b>Outgoing Metrics</b></p>
 * <ul>
 *   <li>{@link #getTotalOutgoingPackets()} - total packets sent</li>
 *   <li>{@link #getDroppedOutgoingPackets()} - packets dropped due to backpressure</li>
 *   <li>{@link #getQueuedOutgoingPackets()} - packets that entered a queue</li>
 *   <li>{@link #getTotalOutgoingBytes()} - cumulative bandwidth consumed</li>
 *   <li>{@link #getOutgoingBandwidthBps()} - computed outgoing bandwidth</li>
 * </ul>
 * 
 * <p>All counter fields are thread-safe; computed statistics are updated by
 * the {@link MetricsCollector} consumer thread.</p>
 */
public class RoomMetrics {

    private final String roomId;
    
    // Incoming (position update) counters
    private final AtomicLong droppedUpdates;
    private final AtomicLong adjustedUpdates;
    private final AtomicLong totalUpdates;
    
    // Outgoing (broadcast) counters
    private final AtomicLong totalOutgoingPackets;
    private final AtomicLong droppedOutgoingPackets;
    private final AtomicLong queuedOutgoingPackets;
    private final AtomicLong totalOutgoingBytes;
    
    // Per-connection state
    private final ConcurrentHashMap<Integer, PlayerMetrics> playerMetrics;

    /** Arrival nanos for all non-dropped incoming updates; used for tick-rate computation. */
    private final List<Long> allArrivalNanos;
    /** Per-connection arrival nanos for non-dropped updates; used for jitter computation. */
    private final ConcurrentHashMap<Integer, List<Long>> connectionArrivalNanos;
    
    /** Timestamps for outgoing packets; used for bandwidth computation. */
    private final List<OutgoingPacketSample> outgoingPacketSamples;

    public RoomMetrics(String roomId) {
        this.roomId = roomId;
        // Incoming counters
        this.droppedUpdates = new AtomicLong(0);
        this.adjustedUpdates = new AtomicLong(0);
        this.totalUpdates = new AtomicLong(0);
        // Outgoing counters
        this.totalOutgoingPackets = new AtomicLong(0);
        this.droppedOutgoingPackets = new AtomicLong(0);
        this.queuedOutgoingPackets = new AtomicLong(0);
        this.totalOutgoingBytes = new AtomicLong(0);
        // Collections
        this.playerMetrics = new ConcurrentHashMap<>();
        this.allArrivalNanos = new ArrayList<>();
        this.connectionArrivalNanos = new ConcurrentHashMap<>();
        this.outgoingPacketSamples = new ArrayList<>();
    }

    // =========================================================================
    // Event recording (called by MetricsCollector consumer thread)
    // =========================================================================

    /** Called by {@link MetricsCollector#processEvent} for every PositionUpdateEvent. */
    void recordPositionUpdate(MetricEvent.PositionUpdateEvent e) {
        totalUpdates.incrementAndGet();
        if (e.dropped()) {
            droppedUpdates.incrementAndGet();
        }
        if (e.adjusted()) {
            adjustedUpdates.incrementAndGet();
        }
        // Only non-dropped updates contribute to timing statistics.
        if (!e.dropped()) {
            synchronized (allArrivalNanos) {
                allArrivalNanos.add(e.arrivalNanos());
            }
            List<Long> arrivals = connectionArrivalNanos.computeIfAbsent(
                e.connectionId(), k -> Collections.synchronizedList(new ArrayList<>()));
            arrivals.add(e.arrivalNanos());
        }
    }

    /** Called by {@link MetricsCollector#processEvent} for every OutgoingPacketEvent. */
    void recordOutgoingPacket(MetricEvent.OutgoingPacketEvent e) {
        totalOutgoingPackets.incrementAndGet();
        totalOutgoingBytes.addAndGet(e.packetSizeBytes());
        if (e.dropped()) {
            droppedOutgoingPackets.incrementAndGet();
        }
        if (e.queued()) {
            queuedOutgoingPackets.incrementAndGet();
        }
        // Track for bandwidth computation
        if (!e.dropped()) {
            synchronized (outgoingPacketSamples) {
                outgoingPacketSamples.add(new OutgoingPacketSample(e.timestampNanos(), e.packetSizeBytes()));
            }
        }
    }

    // =========================================================================
    // Basic accessors
    // =========================================================================

    public String getRoomId() {
        return roomId;
    }

    // Incoming metrics

    public long getDroppedUpdates() {
        return droppedUpdates.get();
    }

    public long getAdjustedUpdates() {
        return adjustedUpdates.get();
    }

    public long getTotalUpdates() {
        return totalUpdates.get();
    }

    /**
     * Returns the drop rate for incoming updates (0.0 to 1.0).
     * Returns 0.0 if no updates have been received.
     */
    public double getDropRate() {
        long total = totalUpdates.get();
        if (total == 0) return 0.0;
        return (double) droppedUpdates.get() / total;
    }

    // Outgoing metrics

    public long getTotalOutgoingPackets() {
        return totalOutgoingPackets.get();
    }

    public long getDroppedOutgoingPackets() {
        return droppedOutgoingPackets.get();
    }

    public long getQueuedOutgoingPackets() {
        return queuedOutgoingPackets.get();
    }

    public long getTotalOutgoingBytes() {
        return totalOutgoingBytes.get();
    }

    /**
     * Returns the outgoing drop rate (0.0 to 1.0).
     * Returns 0.0 if no packets have been sent.
     */
    public double getOutgoingDropRate() {
        long total = totalOutgoingPackets.get();
        if (total == 0) return 0.0;
        return (double) droppedOutgoingPackets.get() / total;
    }

    // =========================================================================
    // Computed statistics
    // =========================================================================

    /**
     * Returns the overall position-update rate for this room in updates per second,
     * computed as {@code count / (last_arrival - first_arrival)} over all non-dropped events.
     */
    public double getTickRatePerSecond() {
        synchronized (allArrivalNanos) {
            if (allArrivalNanos.size() < 2) return 0.0;
            long first = allArrivalNanos.get(0);
            long last = allArrivalNanos.get(allArrivalNanos.size() - 1);
            double spanSeconds = (last - first) / 1_000_000_000.0;
            if (spanSeconds <= 0.0) return 0.0;
            return allArrivalNanos.size() / spanSeconds;
        }
    }

    /**
     * Returns the inter-arrival jitter for a specific connection in milliseconds,
     * computed as the mean absolute deviation of inter-arrival gaps from their own mean
     * (analogous to the RFC 3550 jitter estimator).
     */
    public double getJitterMs(int connectionId) {
        List<Long> nanos = connectionArrivalNanos.get(connectionId);
        if (nanos == null) return 0.0;

        // Snapshot the list under synchronization to avoid concurrent modification
        List<Long> snapshot;
        synchronized (nanos) {
            if (nanos.size() < 2) return 0.0;
            snapshot = new ArrayList<>(nanos);
        }

        List<Double> gaps = new ArrayList<>(snapshot.size() - 1);
        for (int i = 1; i < snapshot.size(); i++) {
            gaps.add((snapshot.get(i) - snapshot.get(i - 1)) / 1_000_000.0);
        }
        double meanGap = gaps.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        return gaps.stream().mapToDouble(g -> Math.abs(g - meanGap)).average().orElse(0.0);
    }

    /**
     * Returns the outgoing bandwidth in bytes per second,
     * computed from the most recent packet samples.
     */
    public double getOutgoingBandwidthBps() {
        synchronized (outgoingPacketSamples) {
            if (outgoingPacketSamples.size() < 2) return 0.0;
            OutgoingPacketSample first = outgoingPacketSamples.get(0);
            OutgoingPacketSample last = outgoingPacketSamples.get(outgoingPacketSamples.size() - 1);
            double spanSeconds = (last.timestampNanos() - first.timestampNanos()) / 1_000_000_000.0;
            if (spanSeconds <= 0.0) return 0.0;
            long totalBytes = outgoingPacketSamples.stream().mapToLong(OutgoingPacketSample::sizeBytes).sum();
            return totalBytes / spanSeconds;
        }
    }

    // =========================================================================
    // Player metrics
    // =========================================================================

    public PlayerMetrics getOrCreatePlayerMetrics(int connectionId) {
        return playerMetrics.computeIfAbsent(connectionId, PlayerMetrics::new);
    }

    public PlayerMetrics getPlayerMetrics(int connectionId) {
        return playerMetrics.get(connectionId);
    }

    public List<Integer> getConnectionIds() {
        return new ArrayList<>(connectionArrivalNanos.keySet());
    }

    // =========================================================================
    // Internal types
    // =========================================================================

    private record OutgoingPacketSample(long timestampNanos, int sizeBytes) {}
}
