package no.ntnu.ping404.server.game;

import no.ntnu.ping404.network.ServerConnector;
import no.ntnu.ping404.network.packets.RoomMetricsSnapshot;
import no.ntnu.ping404.server.GameRoom;
import no.ntnu.ping404.server.metrics.MetricsCollector;
import no.ntnu.ping404.server.metrics.RoomMetrics;

/**
 * Tracks state snapshot broadcast events and periodically sends a {@link RoomMetricsSnapshot} to all clients.
 */
public class MetricsBroadcaster {

    private static final float BROADCAST_INTERVAL_SECONDS = 1.0f;

    private final MetricsCollector metricsCollector;
    private final GameRoom room;
    private final ServerConnector connector;
    private final int simulationTickHz;
    private final int maxStateBroadcastHz;
    private final long maxLoopJitterNs;

    private float secondsSinceLastBroadcast = BROADCAST_INTERVAL_SECONDS;
    private float stateBroadcastEventsInWindow;
    private float effectiveStateBroadcastHz;

    MetricsBroadcaster(MetricsCollector metricsCollector, GameRoom room, ServerConnector connector,
                       int simulationTickHz, int maxStateBroadcastHz, long maxLoopJitterNs) {
        this.metricsCollector = metricsCollector;
        this.room = room;
        this.connector = connector;
        this.simulationTickHz = simulationTickHz;
        this.maxStateBroadcastHz = maxStateBroadcastHz;
        this.maxLoopJitterNs = maxLoopJitterNs;
    }

    void recordStateBroadcast() {
        stateBroadcastEventsInWindow += 1f;
    }

    /**
     * Sends a metrics snapshot if the broadcast interval has elapsed.
     *
     * @param deltaTime        elapsed time since last tick in seconds
     * @param latestLoopJitterMs the most recent loop jitter measurement in milliseconds
     */
    void maybeBroadcast(float deltaTime, float latestLoopJitterMs) {
        if (deltaTime <= 0f) return;
        secondsSinceLastBroadcast += deltaTime;
        if (secondsSinceLastBroadcast < BROADCAST_INTERVAL_SECONDS) return;

        effectiveStateBroadcastHz = stateBroadcastEventsInWindow / secondsSinceLastBroadcast;
        stateBroadcastEventsInWindow = 0f;
        secondsSinceLastBroadcast = 0f;

        RoomMetrics metrics = metricsCollector != null
            ? metricsCollector.getMetrics(room.getRoomId())
            : null;

        RoomMetricsSnapshot snapshot = new RoomMetricsSnapshot(
            room.getRoomId(),
            simulationTickHz,
            maxStateBroadcastHz,
            effectiveStateBroadcastHz,
            latestLoopJitterMs,
            (float) (maxLoopJitterNs / 1_000_000.0),
            metrics != null ? (float) metrics.getTickRatePerSecond() : 0f,
            metrics != null ? (float) metrics.getDropRate() : 0f,
            metrics != null ? (float) metrics.getOutgoingDropRate() : 0f,
            metrics != null ? (float) metrics.getOutgoingBandwidthBps() : 0f,
            metricsCollector != null ? metricsCollector.getQueueDepth() : 0
        );
        room.broadcast(snapshot, connector);
    }
}
