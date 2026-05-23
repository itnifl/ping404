package no.ntnu.ping404.server.handler;

import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.network.INetworkServer.PlayerConnection;
import no.ntnu.ping404.network.ServerConnector;
import no.ntnu.ping404.network.packets.ErrorPacket;
import no.ntnu.ping404.network.packets.GameStartEvent;
import no.ntnu.ping404.server.GameRoom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Handles game start requests from the host.
 * Validates that the room is full, phase is WAITING, and requester is the host.
 * Broadcasts GameStartEvent to all clients with their assigned slot and opponent info.
 */
public class GameStartHandlerCommand implements PacketHandlerCommand {

    private final ServerConnector connector;
    private final Map<Integer, GameRoom> playerRooms;
    private final Consumer<GameRoom> onGameStarted;
    private static final Logger logger = LoggerFactory.getLogger(GameStartHandlerCommand.class);

    public GameStartHandlerCommand(ServerConnector connector,
                                   Map<Integer, GameRoom> playerRooms,
                                   Consumer<GameRoom> onGameStarted) {
        this.connector = connector;
        this.playerRooms = playerRooms;
        this.onGameStarted = onGameStarted;
    }

    @Override
    public void handle(PlayerConnection connection, Object packet) {
        System.out.println("[GameStartHandler] Received GameStartRequest from connection " + connection.getId());
        GameRoom room = playerRooms.get(connection.getId());
        if (room == null) {
            System.out.println("[GameStartHandler] No room found for connection " + connection.getId());
            logger.warn("GameStartRequest from connection {} with no assigned room", connection.getId());
            return;
        }

        System.out.println("[GameStartHandler] Room: " + room.getRoomId() + 
                           ", canStart=" + room.canStart() + 
                           ", isFull=" + room.isFull() + 
                           ", phase=" + room.getPhase() +
                           ", playerCount=" + room.getPlayerCount() +
                           ", hostId=" + room.getHostConnectionId() +
                           ", requesterId=" + connection.getId());

        // Validate: must be host to start the game
        if (room.getHostConnectionId() != connection.getId()) {
            String errorMessage = "Only the host can start the game";
            connector.send(connection.getId(), new ErrorPacket(connection.getId(), errorMessage));
            logger.warn("Non-host {} attempted to start game in room '{}'",
                    connection.getId(), room.getRoomId());
            return;
        }

        // Validate: room must be startable
        if (!room.canStart()) {
            String errorMessage = room.isFull()
                    ? "Game cannot start: not in WAITING phase"
                    : "Game cannot start: waiting for opponent";
            connector.send(connection.getId(), new ErrorPacket(connection.getId(), errorMessage));
            logger.warn("Cannot start room '{}': canStart=false", room.getRoomId());
            return;
        }

        // Transition to PLAYING phase
        boolean transitioned = room.setPhase(GameState.Phase.PLAYING);
        if (!transitioned) {
            connector.send(connection.getId(),
                    new ErrorPacket(connection.getId(), "Failed to start game"));
            logger.error("Failed to transition room '{}' to PLAYING", room.getRoomId());
            return;
        }

        // Broadcast GameStartEvent to each player with their perspective
        int slot1Id = room.getConnectionIdForSlot(1);
        int slot2Id = room.getConnectionIdForSlot(2);
        System.out.println("[GameStartHandler] Sending to slot1=" + slot1Id + ", slot2=" + slot2Id);

        PlayerConnection conn1 = room.getConnections().get(slot1Id);
        PlayerConnection conn2 = room.getConnections().get(slot2Id);

        String name1 = conn1 != null ? conn1.getPlayerName() : "Player 1";
        String name2 = conn2 != null ? conn2.getPlayerName() : "Player 2";

        // Send to slot 1 player
        if (slot1Id != -1) {
            GameStartEvent event1 = new GameStartEvent(
                    slot1Id, slot2Id, 1, name1, name2, room.getWinScore());
            System.out.println("[GameStartHandler] Sending GameStartEvent to slot1 (" + slot1Id + ")");
            connector.send(slot1Id, event1);
        }

        // Send to slot 2 player
        if (slot2Id != -1) {
            GameStartEvent event2 = new GameStartEvent(
                    slot2Id, slot1Id, 2, name2, name1, room.getWinScore());
            System.out.println("[GameStartHandler] Sending GameStartEvent to slot2 (" + slot2Id + ")");
            connector.send(slot2Id, event2);
        }

        // Notify GameServer to start the game loop
        if (onGameStarted != null) {
            onGameStarted.accept(room);
        }

        logger.info("Room '{}' game started by host {}", room.getRoomId(), connection.getId());
    }
}
