package no.ntnu.ping404.server.metrics;

/**
 * Sealed event hierarchy representing observable metrics events produced by game components.
 * Implementations are posted by producers onto the {@link MetricsCollector} queue and
 * consumed asynchronously by the metrics consumer thread without blocking the game thread.
 * 
 * <p>Event types cover both <strong>incoming</strong> (position updates, pong responses)
 * and <strong>outgoing</strong> (state broadcasts, game packets) traffic for complete
 * bidirectional metrics visibility.</p>
 */
public sealed interface MetricEvent permits 
        MetricEvent.PositionUpdateEvent, 
        MetricEvent.PingLatencyEvent, 
        MetricEvent.PlayerJoinedEvent,
        MetricEvent.OutgoingPacketEvent {

    /**
     * Emitted each time a player position packet is processed (incoming).
     * {@code dropped} is true when the update was discarded (e.g. wrong phase);
     * {@code adjusted} is true when the position was clamped to the nearest valid boundary.
     */
    record PositionUpdateEvent(
            String roomId, 
            int connectionId, 
            long arrivalNanos, 
            boolean dropped, 
            boolean adjusted
    ) implements MetricEvent {}

    /**
     * Emitted when a Pong response is received from a client (incoming),
     * carrying the measured round-trip time in milliseconds.
     */
    record PingLatencyEvent(
            String roomId, 
            int connectionId, 
            long rttMs
    ) implements MetricEvent {}

    /**
     * Emitted when a player joins a room, carrying the player's IP address
     * for connection-level diagnostics. The IP address may be {@code null}
     * if it cannot be determined from the underlying connection.
     */
    record PlayerJoinedEvent(
            String roomId, 
            int connectionId, 
            String ipAddress
    ) implements MetricEvent {}

    /**
     * Emitted each time an outgoing packet is sent to a client.
     * Tracks the packet type and size for bandwidth/load analysis.
     * {@code queued} indicates if the packet was placed in a queue (potential backpressure).
     * {@code dropped} indicates if the packet was dropped due to queue overflow.
     */
    record OutgoingPacketEvent(
            String roomId,
            int connectionId,
            String packetType,
            int packetSizeBytes,
            long timestampNanos,
            boolean queued,
            boolean dropped
    ) implements MetricEvent {}
}
