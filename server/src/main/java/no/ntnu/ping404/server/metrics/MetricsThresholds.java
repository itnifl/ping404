package no.ntnu.ping404.server.metrics;

/**
 * Configurable thresholds for metrics-based alerting.
 * When any threshold is exceeded, the {@link MetricsCollector} emits a WARN-level log
 * so that degraded operation can be detected early.
 * 
 * <p>Default values are tuned for a typical 20 Hz game tick with 2-player rooms:</p>
 * <ul>
 *   <li>Tick rate should stay between 15–25 Hz</li>
 *   <li>Jitter should remain below 20 ms</li>
 *   <li>Queue depth should not exceed 100 pending events</li>
 *   <li>Drop rate should stay below 5%</li>
 *   <li>RTT should remain below 150 ms for playable latency</li>
 * </ul>
 * 
 * <p>Thresholds can be customized via the builder pattern for different deployment scenarios.</p>
 */
public final class MetricsThresholds {

    // Default threshold values

    /** Default minimum acceptable tick rate (updates/second). */
    public static final double DEFAULT_MIN_TICK_RATE = 15.0;

    /** Default maximum acceptable tick rate (updates/second). */
    public static final double DEFAULT_MAX_TICK_RATE = 25.0;

    /** Default maximum acceptable jitter (milliseconds). */
    public static final double DEFAULT_MAX_JITTER_MS = 20.0;

    /** Default maximum acceptable incoming queue depth. */
    public static final int DEFAULT_MAX_INCOMING_QUEUE_DEPTH = 100;

    /** Default maximum acceptable outgoing queue depth per room. */
    public static final int DEFAULT_MAX_OUTGOING_QUEUE_DEPTH = 50;

    /** Default maximum acceptable drop rate (0.0 to 1.0). */
    public static final double DEFAULT_MAX_DROP_RATE = 0.05;

    /** Default maximum acceptable round-trip time (milliseconds). */
    public static final long DEFAULT_MAX_RTT_MS = 150;

    /** Default maximum acceptable outgoing bandwidth per room (bytes/second). */
    public static final long DEFAULT_MAX_OUTGOING_BANDWIDTH_BPS = 50_000;

    // Instance fields (immutable)

    private final double minTickRate;
    private final double maxTickRate;
    private final double maxJitterMs;
    private final int maxIncomingQueueDepth;
    private final int maxOutgoingQueueDepth;
    private final double maxDropRate;
    private final long maxRttMs;
    private final long maxOutgoingBandwidthBps;

    private MetricsThresholds(Builder builder) {
        this.minTickRate = builder.minTickRate;
        this.maxTickRate = builder.maxTickRate;
        this.maxJitterMs = builder.maxJitterMs;
        this.maxIncomingQueueDepth = builder.maxIncomingQueueDepth;
        this.maxOutgoingQueueDepth = builder.maxOutgoingQueueDepth;
        this.maxDropRate = builder.maxDropRate;
        this.maxRttMs = builder.maxRttMs;
        this.maxOutgoingBandwidthBps = builder.maxOutgoingBandwidthBps;
    }

    /** Returns a new instance with all default thresholds. */
    public static MetricsThresholds defaults() {
        return new Builder().build();
    }

    /** Returns a new builder for customizing thresholds. */
    public static Builder builder() {
        return new Builder();
    }

    // Threshold accessors

    public double getMinTickRate() {
        return minTickRate;
    }

    public double getMaxTickRate() {
        return maxTickRate;
    }

    public double getMaxJitterMs() {
        return maxJitterMs;
    }

    public int getMaxIncomingQueueDepth() {
        return maxIncomingQueueDepth;
    }

    public int getMaxOutgoingQueueDepth() {
        return maxOutgoingQueueDepth;
    }

    public double getMaxDropRate() {
        return maxDropRate;
    }

    public long getMaxRttMs() {
        return maxRttMs;
    }

    public long getMaxOutgoingBandwidthBps() {
        return maxOutgoingBandwidthBps;
    }

    // Threshold violation checks

    /** Returns true if tick rate is outside the acceptable range. */
    public boolean isTickRateViolation(double tickRate) {
        return tickRate > 0 && (tickRate < minTickRate || tickRate > maxTickRate);
    }

    /** Returns true if jitter exceeds the threshold. */
    public boolean isJitterViolation(double jitterMs) {
        return jitterMs > maxJitterMs;
    }

    /** Returns true if incoming queue depth exceeds the threshold. */
    public boolean isIncomingQueueViolation(int queueDepth) {
        return queueDepth > maxIncomingQueueDepth;
    }

    /** Returns true if outgoing queue depth exceeds the threshold. */
    public boolean isOutgoingQueueViolation(long queueDepth) {
        return queueDepth > maxOutgoingQueueDepth;
    }

    /** Returns true if drop rate exceeds the threshold. */
    public boolean isDropRateViolation(double dropRate) {
        return dropRate > maxDropRate;
    }

    /** Returns true if RTT exceeds the threshold. */
    public boolean isRttViolation(long rttMs) {
        return rttMs > maxRttMs;
    }

    /** Returns true if outgoing bandwidth exceeds the threshold. */
    public boolean isBandwidthViolation(long bandwidthBps) {
        return bandwidthBps > maxOutgoingBandwidthBps;
    }

    // Builder

    public static final class Builder {
        private double minTickRate = DEFAULT_MIN_TICK_RATE;
        private double maxTickRate = DEFAULT_MAX_TICK_RATE;
        private double maxJitterMs = DEFAULT_MAX_JITTER_MS;
        private int maxIncomingQueueDepth = DEFAULT_MAX_INCOMING_QUEUE_DEPTH;
        private int maxOutgoingQueueDepth = DEFAULT_MAX_OUTGOING_QUEUE_DEPTH;
        private double maxDropRate = DEFAULT_MAX_DROP_RATE;
        private long maxRttMs = DEFAULT_MAX_RTT_MS;
        private long maxOutgoingBandwidthBps = DEFAULT_MAX_OUTGOING_BANDWIDTH_BPS;

        public Builder minTickRate(double minTickRate) {
            this.minTickRate = minTickRate;
            return this;
        }

        public Builder maxTickRate(double maxTickRate) {
            this.maxTickRate = maxTickRate;
            return this;
        }

        public Builder maxJitterMs(double maxJitterMs) {
            this.maxJitterMs = maxJitterMs;
            return this;
        }

        public Builder maxIncomingQueueDepth(int maxIncomingQueueDepth) {
            this.maxIncomingQueueDepth = maxIncomingQueueDepth;
            return this;
        }

        public Builder maxOutgoingQueueDepth(int maxOutgoingQueueDepth) {
            this.maxOutgoingQueueDepth = maxOutgoingQueueDepth;
            return this;
        }

        public Builder maxDropRate(double maxDropRate) {
            this.maxDropRate = maxDropRate;
            return this;
        }

        public Builder maxRttMs(long maxRttMs) {
            this.maxRttMs = maxRttMs;
            return this;
        }

        public Builder maxOutgoingBandwidthBps(long maxOutgoingBandwidthBps) {
            this.maxOutgoingBandwidthBps = maxOutgoingBandwidthBps;
            return this;
        }

        public MetricsThresholds build() {
            return new MetricsThresholds(this);
        }
    }
}
