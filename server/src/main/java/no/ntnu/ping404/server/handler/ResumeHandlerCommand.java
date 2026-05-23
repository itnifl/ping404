package no.ntnu.ping404.server.handler;

import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.network.INetworkServer.PlayerConnection;
import no.ntnu.ping404.network.ServerConnector;
import no.ntnu.ping404.network.packets.ErrorPacket;
import no.ntnu.ping404.network.packets.ResumeEvent;
import no.ntnu.ping404.server.GameRoom;
import no.ntnu.ping404.server.game.InputEvent;
import no.ntnu.ping404.server.game.InputEventDispatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Handles resume requests during paused gameplay.
 * Validates host permission, broadcasts ResumeEvent, and enqueues to the GameLoop's InputQueue.
 */
public class ResumeHandlerCommand implements PacketHandlerCommand {

    private final ServerConnector connector;
    private final Map<Integer, GameRoom> playerRooms;
    private final InputEventDispatcher dispatcher;
    private static final Logger logger = LoggerFactory.getLogger(ResumeHandlerCommand.class);

    public ResumeHandlerCommand(ServerConnector connector, Map<Integer, GameRoom> playerRooms, InputEventDispatcher dispatcher) {
        this.connector = connector;
        this.playerRooms = playerRooms;
        this.dispatcher = dispatcher;
    }

    @Override
    public void handle(PlayerConnection connection, Object packet) {
        GameRoom room = playerRooms.get(connection.getId());
        if (room == null) return;

        // Only resume a match that is currently paused.
        if (room.getPhase() != GameState.Phase.PAUSED) return;

        if (room.getHostConnectionId() == connection.getId()) {
            // 1. Set phase FIRST so other handlers see PLAYING immediately.
            room.setPhase(GameState.Phase.PLAYING);

            // 2. Broadcast ResumeEvent to all clients.
            ResumeEvent event = new ResumeEvent(connection.getId());
            broadcastEventToRoom(room, event);

            // 3. Enqueue for GameLoop acknowledgment.
            if (dispatcher != null) {
                dispatcher.dispatch(room, InputEvent.resumeRequest(connection.getId()));
            }

            logger.info("Room '{}' resumed by connection {}", room.getRoomId(), connection.getId());
        } else {
            var errorMessage = "Connection " + connection.getId() + " attempted to resume room '"
                    + room.getRoomId() + "' but is not the host. Ignoring.";
            ErrorPacket error = new ErrorPacket(connection.getId(), errorMessage);
            connector.send(connection.getId(), error);

            logger.warn(errorMessage);
        }
    }

    private void broadcastEventToRoom(GameRoom room, Object packet) {
        for (Integer connectionId : room.getConnections().keySet()) {
            connector.send(connectionId, packet);
        }
    }
}
