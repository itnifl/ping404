package no.ntnu.ping404.server.handler;

import no.ntnu.ping404.network.INetworkServer.PlayerConnection;
import no.ntnu.ping404.network.packets.Pong;
import no.ntnu.ping404.server.GameRoom;
import no.ntnu.ping404.server.metrics.MetricEvent;
import no.ntnu.ping404.server.metrics.MetricsCollector;

import java.util.Map;

/**
 * Handles incoming Pong packets from clients.
 * Updates the connection's last heartbeat timestamp so the server
 * knows the client is still alive, and records the round-trip latency.
 */
public class PongHandlerCommand implements PacketHandlerCommand {

    private final MetricsCollector metricsCollector;
    private final Map<Integer, GameRoom> playerRooms;

    public PongHandlerCommand() {
        this(null, null);
    }

    public PongHandlerCommand(MetricsCollector metricsCollector, Map<Integer, GameRoom> playerRooms) {
        this.metricsCollector = metricsCollector;
        this.playerRooms = playerRooms;
    }

    @Override
    public void handle(PlayerConnection connection, Object packet) {
        connection.updateLastHeartbeat();
        if (metricsCollector != null && playerRooms != null && packet instanceof Pong pong) {
            GameRoom room = playerRooms.get(connection.getId());
            if (room != null) {
                metricsCollector.record(
                    new MetricEvent.PingLatencyEvent(room.getRoomId(), connection.getId(), pong.getRoundTripTime()));
            }
        }
    }
}
