package no.ntnu.ping404.server.game;

import no.ntnu.ping404.server.metrics.MetricsCollector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controls broadcast frequency under load by applying a multiplier to the base broadcast interval.
 * Detects overload via loop jitter and metrics queue depth, and logs level transitions.
 */
public class BroadcastRateController {

    private static final Logger logger = LoggerFactory.getLogger(BroadcastRateController.class);

    private static final int NO_MULTIPLIER = 1;
    private static final int MODERATE_MULTIPLIER = 2;
    private static final int SEVERE_MULTIPLIER = 4;
    private static final float MODERATE_THRESHOLD = 1.0f;
    private static final float SEVERE_THRESHOLD = 2.0f;

    private final MetricsCollector metricsCollector;
    private final long maxLoopJitterNs;
    private final float baseBroadcastIntervalSeconds;
    private final int maxBroadcastHz;
    private final String roomId;

    private int multiplier = NO_MULTIPLIER;
    private float latestJitterMs;

    BroadcastRateController(MetricsCollector metricsCollector, long maxLoopJitterNs,
                          float baseBroadcastIntervalSeconds, int maxBroadcastHz, String roomId) {
        this.metricsCollector = metricsCollector;
        this.maxLoopJitterNs = maxLoopJitterNs;
        this.baseBroadcastIntervalSeconds = baseBroadcastIntervalSeconds;
        this.maxBroadcastHz = maxBroadcastHz;
        this.roomId = roomId;
    }

    /**
     * Updates the backpressure level based on current load signals, logging on transitions.
     *
     * @param latestJitterMs the most recent loop jitter measurement in milliseconds
     */
    void update(float latestJitterMs) {
        this.latestJitterMs = latestJitterMs;

        int nextMultiplier = switch (detectLevel()) {
            case SEVERE -> SEVERE_MULTIPLIER;
            case MODERATE -> MODERATE_MULTIPLIER;
            case NONE -> NO_MULTIPLIER;
        };

        if (nextMultiplier == multiplier) return;
        multiplier = nextMultiplier;

        if (multiplier == NO_MULTIPLIER) {
            logger.info("State broadcast backpressure recovered in room '{}' (maxBroadcast={}Hz)",
                roomId, maxBroadcastHz);
        } else {
            logger.info("State broadcast backpressure active in room '{}' (maxBroadcast={}Hz -> effective\u2248{}Hz)",
                roomId, maxBroadcastHz, Math.max(1, maxBroadcastHz / multiplier));
        }
    }

    /**
     * Returns the effective broadcast interval in seconds after applying the backpressure multiplier.
     */
    float effectiveIntervalSeconds() {
        return baseBroadcastIntervalSeconds * multiplier;
    }

    /**
     * Detects load level using both loop jitter and metrics queue depth.
     * Severe level is triggered when either signal exceeds twice the configured threshold.
     */
    private Level detectLevel() {
        if (isQueueOverloaded(SEVERE_THRESHOLD) || isJitterOverloaded(SEVERE_THRESHOLD)) return Level.SEVERE;
        if (isQueueOverloaded(MODERATE_THRESHOLD) || isJitterOverloaded(MODERATE_THRESHOLD)) return Level.MODERATE;
        return Level.NONE;
    }

    private boolean isQueueOverloaded(float thresholdFactor) {
        if (metricsCollector == null) return false;
        int base = metricsCollector.getThresholds().getMaxIncomingQueueDepth();
        int scaled = Math.max(1, Math.round(base * thresholdFactor));
        return metricsCollector.getQueueDepth() > scaled;
    }

    private boolean isJitterOverloaded(float thresholdFactor) {
        return latestJitterMs > (float) ((maxLoopJitterNs / 1_000_000.0) * thresholdFactor);
    }

    private enum Level { NONE, MODERATE, SEVERE }
}
