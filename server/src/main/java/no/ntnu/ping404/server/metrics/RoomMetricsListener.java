package no.ntnu.ping404.server.metrics;

import no.ntnu.ping404.model.Player;
import no.ntnu.ping404.network.INetworkServer.PlayerConnection;
import no.ntnu.ping404.server.GameRoom;

/**
 * {@link GameRoom.GameRoomListener} that acts as the metrics producer for a single room.
 * Reacts to room lifecycle events (player joins, leaves) and posts the corresponding
 * {@link MetricEvent}s onto the shared {@link MetricsCollector} queue so that
 * IP capture and geo-location resolution never block the game thread.
 */
public class RoomMetricsListener implements GameRoom.GameRoomListener {

    private final MetricsCollector collector;

    public RoomMetricsListener(MetricsCollector collector) {
        this.collector = collector;
    }

    @Override
    public void onPlayerJoined(GameRoom room, PlayerConnection connection, Player player) {
        String ip = connection.getRemoteAddress();
        // Synchronously capture IP so callers see it immediately (IP capture is cheap).
        collector.getMetrics(room.getRoomId())
                 .getOrCreatePlayerMetrics(connection.getId())
                 .setIpAddress(ip);
        // Enqueue for async geo-lookup in the consumer thread.
        collector.record(new MetricEvent.PlayerJoinedEvent(room.getRoomId(), connection.getId(), ip));
    }

    @Override
    public void onPlayerLeft(GameRoom room, int connectionId) {
        // Stub: no event recording yet.
    }

    public MetricsCollector getCollector() {
        return collector;
    }
}
