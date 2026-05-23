package no.ntnu.ping404.server.handler;

import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.network.INetworkServer.PlayerConnection;
import no.ntnu.ping404.network.ServerConnector;
import no.ntnu.ping404.network.packets.ErrorPacket;
import no.ntnu.ping404.network.packets.PauseEvent;
import no.ntnu.ping404.server.GameRoom;
import no.ntnu.ping404.server.game.InputEvent;
import no.ntnu.ping404.server.game.InputEventDispatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Handles pause requests during active gameplay.
 * Validates host permission, broadcasts PauseEvent, and enqueues to the GameLoop's InputQueue
 * (Producer side of Producer-Consumer pattern, issue #14).
 */
public class PauseHandlerCommand implements PacketHandlerCommand {

   private final ServerConnector connector;
    private final Map<Integer, GameRoom> playerRooms;
    private final InputEventDispatcher dispatcher;
    private static final Logger logger = LoggerFactory.getLogger(PauseHandlerCommand.class);

    public PauseHandlerCommand(ServerConnector connector, Map<Integer, GameRoom> playerRooms, InputEventDispatcher dispatcher) {
        this.connector = connector;
        this.playerRooms = playerRooms;
        this.dispatcher = dispatcher;
    }


    @Override
    public void handle(PlayerConnection connection, Object packet) {
        GameRoom room = playerRooms.get(connection.getId());
        if (room == null) return;

        // Only pause a match that is actively playing.
        if (room.getPhase() != GameState.Phase.PLAYING) return;

        if (room.getHostConnectionId() == connection.getId()) {
            // 1. Set phase FIRST so other handlers see PAUSED immediately.
            //    Fixes race window where position updates leak through after
            //    pause broadcast. (Defect #2)
            room.setPhase(GameState.Phase.PAUSED);

            // 2. Broadcast PauseEvent to all clients.
            PauseEvent event = new PauseEvent(connection.getId());
            broadcastEventToRoom(room, event);

            // 3. Enqueue for GameLoop acknowledgment (becomes a no-op since
            //    phase is already PAUSED).
            if (dispatcher != null) {
                dispatcher.dispatch(room, InputEvent.pauseRequest(connection.getId()));
            }

            logger.info("Room '{}' paused by connection {}", room.getRoomId(), connection.getId());
        } else {
            var errorMessage = "Connection " + connection.getId() + " attempted to pause room '"
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
