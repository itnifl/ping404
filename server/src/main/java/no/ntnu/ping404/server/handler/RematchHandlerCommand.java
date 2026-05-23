package no.ntnu.ping404.server.handler;

import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.network.INetworkServer.PlayerConnection;
import no.ntnu.ping404.network.ServerConnector;
import no.ntnu.ping404.network.packets.GameStartEvent;
import no.ntnu.ping404.network.packets.RematchStart;
import no.ntnu.ping404.server.GameRoom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Handles RematchRequest packets from clients.
 * Both players in a FINISHED room must agree before the rematch begins.
 * On full agreement the room resets state, broadcasts RematchStart, transitions to PLAYING,
 * sends GameStartEvent to each player, and triggers the game loop.
 */
public class RematchHandlerCommand implements PacketHandlerCommand {

    private final ServerConnector connector;
    private final Map<Integer, GameRoom> playerRooms;
    private final Consumer<GameRoom> onGameStarted;
    private static final Logger logger = LoggerFactory.getLogger(RematchHandlerCommand.class);

    public RematchHandlerCommand(ServerConnector connector, Map<Integer, GameRoom> playerRooms,
                                 Consumer<GameRoom> onGameStarted) {
        this.connector = connector;
        this.playerRooms = playerRooms;
        this.onGameStarted = onGameStarted;
    }

    @Override
    public void handle(PlayerConnection connection, Object packet) {
        GameRoom room = playerRooms.get(connection.getId());
        if (room == null) {
            logger.warn("RematchRequest from connection {} with no assigned room", connection.getId());
            return;
        }

        if (room.getPhase() != GameState.Phase.FINISHED) {
            logger.warn("RematchRequest from {} ignored: room '{}' is not in FINISHED phase (was {})",
                    connection.getId(), room.getRoomId(), room.getPhase());
            return;
        }

        boolean allAgreed = room.requestRematch(connection.getId());
        logger.info("RematchRequest from {}: intents={}/{}", connection.getId(),
                room.getRematchIntentCount(), room.getPlayerCount());

        if (!allAgreed) {
            return;
        }

        room.clearRematchIntents();
        room.setPhase(GameState.Phase.WAITING);

        RematchStart rematchStart = new RematchStart();
        room.broadcast(rematchStart, connector);

        room.setPhase(GameState.Phase.PLAYING);

        int slot1Id = room.getConnectionIdForSlot(1);
        int slot2Id = room.getConnectionIdForSlot(2);

        PlayerConnection conn1 = room.getConnections().get(slot1Id);
        PlayerConnection conn2 = room.getConnections().get(slot2Id);

        String name1 = conn1 != null ? conn1.getPlayerName() : "Player 1";
        String name2 = conn2 != null ? conn2.getPlayerName() : "Player 2";

        if (slot1Id != -1) {
            connector.send(slot1Id, new GameStartEvent(slot1Id, slot2Id, 1, name1, name2, room.getWinScore()));
        }
        if (slot2Id != -1) {
            connector.send(slot2Id, new GameStartEvent(slot2Id, slot1Id, 2, name2, name1, room.getWinScore()));
        }

        if (onGameStarted != null) {
            onGameStarted.accept(room);
        }

        logger.info("Rematch started in room '{}': transitioned to PLAYING", room.getRoomId());
    }
}
