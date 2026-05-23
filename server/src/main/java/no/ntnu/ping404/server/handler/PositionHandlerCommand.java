package no.ntnu.ping404.server.handler;

import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.model.Player;
import no.ntnu.ping404.network.INetworkServer.PlayerConnection;
import no.ntnu.ping404.network.ServerConnector;
import no.ntnu.ping404.network.packets.GameOver;
import no.ntnu.ping404.network.packets.PlayerPosition;
import no.ntnu.ping404.server.GameRoom;
import no.ntnu.ping404.server.game.InputEvent;
import no.ntnu.ping404.server.game.InputEventDispatcher;
import no.ntnu.ping404.server.metrics.MetricEvent;
import no.ntnu.ping404.server.metrics.MetricsCollector;
import no.ntnu.ping404.utils.Constants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Handles position packets from clients (Producer side of Producer-Consumer pattern).
 * Validates and clamps positions, then broadcasts to opponents and enqueues a
 * PADDLE_MOVE InputEvent for the GameLoop to consume.
 */
public class PositionHandlerCommand implements PacketHandlerCommand {

    private final ServerConnector connector;
    private final Map<Integer, GameRoom> playerRooms;
    private final InputEventDispatcher dispatcher;
    private final MetricsCollector metricsCollector;
    private static final Logger logger = LoggerFactory.getLogger(PositionHandlerCommand.class);

    public PositionHandlerCommand(ServerConnector connector, Map<Integer, GameRoom> playerRooms,
            InputEventDispatcher dispatcher) {
        this(connector, playerRooms, dispatcher, null);
    }

    public PositionHandlerCommand(ServerConnector connector, Map<Integer, GameRoom> playerRooms,
            InputEventDispatcher dispatcher, MetricsCollector metricsCollector) {
        this.connector = connector;
        this.playerRooms = playerRooms;
        this.dispatcher = dispatcher;
        this.metricsCollector = metricsCollector;
    }

    @Override
    public void handle(PlayerConnection connection, Object packet) {
        PlayerPosition position = (PlayerPosition) packet;
        GameRoom room = playerRooms.get(connection.getId());
        if (room == null) return;

        Player player = room.getGameState().getPlayer(connection.getId());
        if (player == null) {
            logger.warn("Received position update from unknown player (ID: {}) in room '{}'. Ignoring.",
                    connection.getId(), room.getRoomId());
            return;
        }

        if (room.getPhase() != GameState.Phase.PLAYING) {
            logger.warn("Received position update from player '{}' (ID: {}) in room '{}', but the game is not active (phase: {}). Ignoring.",
                    player.getName(), connection.getId(), room.getRoomId(), room.getPhase());
            recordMetric(room.getRoomId(), connection.getId(), true, false);
            return;
        }

        boolean adjusted = !verifyPositionMovement(room, player, position);
        if (adjusted) {
            clampPosition(room, player, position);
        }
        recordMetric(room.getRoomId(), connection.getId(), false, adjusted);

        player.setX(position.x);
        player.setY(position.y);
        connection.setPosition(position.x, position.y);
        position.playerId = connection.getId();

        room.broadcastExcept(connection.getId(), position, connector);

        if (dispatcher != null) {
            dispatcher.dispatch(room, InputEvent.paddleMove(connection.getId(), position.x, position.y));
        }

        checkAndBroadcastWinner(room);
    }

    /**
     * Verifies if a position is within valid boundaries.
     *
     * @return true if position is valid, false if it needs clamping
     */
    private boolean verifyPositionMovement(GameRoom room, Player player, PlayerPosition newPosition) {
        int playerSlot = room.getSlotByPlayerId(player.getId());
        float malletRadius = Constants.PADDLE_WIDTH / 2;
        float centerX = Constants.boardCenterX();

        if (newPosition.x < malletRadius || newPosition.x > Constants.DEFAULT_FIELD_WIDTH - malletRadius ||
                newPosition.y < malletRadius || newPosition.y > Constants.DEFAULT_FIELD_HEIGHT - malletRadius) {
            return false;
        }

        if (playerSlot == 1 && newPosition.x > centerX - malletRadius) {
            return false;
        }
        if (playerSlot == 2 && newPosition.x < centerX + malletRadius) {
            return false;
        }

        return true;
    }

    /**
     * Clamps the player position to the nearest valid point within game boundaries and the center line.
     */
    private void clampPosition(GameRoom room, Player player, PlayerPosition position) {
        int playerSlot = room.getSlotByPlayerId(player.getId());
        float malletRadius = Constants.PADDLE_WIDTH / 2;
        float centerX = Constants.boardCenterX();

        position.y = Math.max(malletRadius, Math.min(position.y, Constants.DEFAULT_FIELD_HEIGHT - malletRadius));

        if (playerSlot == 1) {
            position.x = Math.max(malletRadius, Math.min(position.x, centerX - malletRadius));
        } else if (playerSlot == 2) {
            position.x = Math.max(centerX + malletRadius, Math.min(position.x, Constants.DEFAULT_FIELD_WIDTH - malletRadius));
        }

        logger.debug("Clamped position for player '{}' (ID: {}) in room '{}': ({}, {})",
                player.getName(), player.getId(), room.getRoomId(), position.x, position.y);
    }

    private void recordMetric(String roomId, int connectionId, boolean dropped, boolean adjusted) {
        if (metricsCollector != null) {
            metricsCollector.record(
                    new MetricEvent.PositionUpdateEvent(roomId, connectionId, System.nanoTime(), dropped, adjusted));
        }
    }

    private void checkAndBroadcastWinner(GameRoom room) {
        if (room.getGameState().getPhase() != GameState.Phase.PLAYING) {
            return;
        }

        if (room.getGameState().getScore().hasWinner()) {
            int winnerSlot = room.getGameState().getScore().getWinner();
            int player1Score = room.getGameState().getScore().getPlayer1Score();
            int player2Score = room.getGameState().getScore().getPlayer2Score();

            var winnerId = room.getConnectionIdForSlot(winnerSlot);
            var winnerPlayer = room.getGameState().getPlayer(winnerId);
            if (winnerPlayer == null) {
                logger.warn("Winner lookup failed for room '{}': slot={}, connectionId={}. Skipping GameOver broadcast.",
                        room.getRoomId(), winnerSlot, winnerId);
                return;
            }

            room.getGameState().finishMatch();
            room.setPhase(GameState.Phase.FINISHED);

            // Issue #15: Notify listeners via Domain Event before broadcasting (Observer pattern)
            room.notifyMatchEnded(winnerId, winnerPlayer.getName());

            GameOver gameOverPacket = new GameOver(winnerId, winnerPlayer.getName(), player1Score, player2Score);
            for (Integer playerId : room.getConnections().keySet()) {
                connector.send(playerId, gameOverPacket);
            }

            logger.info("Game finished! Winner: {}, Score: {}-{}", winnerPlayer.getName(), player1Score, player2Score);
        }
    }
}
