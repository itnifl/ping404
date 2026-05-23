package no.ntnu.ping404.server.game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Measures per-tick loop jitter and logs a warning when the configured threshold is breached.
 */
public class LoopJitterMonitor {

    private static final Logger logger = LoggerFactory.getLogger(LoopJitterMonitor.class);

    private static final long WARN_COOLDOWN_MS = 5000;
    private static final String DECIMAL_FORMAT = "%.2f";

    private final long simulationTickDurationNs;
    private final long maxLoopJitterNs;
    private final String roomId;

    private long lastTickStartNs;
    private long nextWarnTimeMs;

    LoopJitterMonitor(long simulationTickDurationNs, long maxLoopJitterNs, String roomId) {
        this.simulationTickDurationNs = simulationTickDurationNs;
        this.maxLoopJitterNs = maxLoopJitterNs;
        this.roomId = roomId;
    }

    /**
     * Measures jitter for the current tick and logs a warning if the threshold is breached.
     * Returns 0 on the first call (no previous tick to compare against).
     *
     * @param tickStartNs current tick start time from {@link System#nanoTime()}
     * @return jitter in milliseconds
     */
    float measure(long tickStartNs) {
        if (lastTickStartNs == 0L) {
            lastTickStartNs = tickStartNs;
            return 0f;
        }

        long actualIntervalNs = tickStartNs - lastTickStartNs;
        long jitterNs = Math.abs(actualIntervalNs - simulationTickDurationNs);
        float jitterMs = (float) (jitterNs / 1_000_000.0);
        lastTickStartNs = tickStartNs;

        long nowMs = System.currentTimeMillis();
        if (jitterNs > maxLoopJitterNs && nowMs >= nextWarnTimeMs) {
            logger.warn(
                "Loop jitter threshold breached in room '{}': jitterMs={} targetTickMs={} maxAllowedMs={}",
                roomId,
                String.format(DECIMAL_FORMAT, jitterMs),
                String.format(DECIMAL_FORMAT, simulationTickDurationNs / 1_000_000.0),
                String.format(DECIMAL_FORMAT, maxLoopJitterNs / 1_000_000.0));
            nextWarnTimeMs = nowMs + WARN_COOLDOWN_MS;
        }

        return jitterMs;
    }
}
