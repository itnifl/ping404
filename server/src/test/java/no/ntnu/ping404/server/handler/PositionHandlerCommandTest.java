package no.ntnu.ping404.server.handler;

import com.esotericsoftware.kryonet.Connection;
import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.model.Player;
import no.ntnu.ping404.network.NetworkKryoServer;
import no.ntnu.ping404.network.INetworkServer;
import no.ntnu.ping404.network.ServerConnector;
import no.ntnu.ping404.network.packets.GameOver;
import no.ntnu.ping404.network.packets.PlayerPosition;
import no.ntnu.ping404.server.GameRoom;
import no.ntnu.ping404.utils.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class PositionHandlerCommandTest {

    private static final String ROOM_ID_1 = "room-1";
    private static final String ROOM_ID_2 = "room-2";
    private static final String PLAYER_NAME_ALICE = "Alice";
    private static final String PLAYER_NAME_BOB = "Bob";
    private static final int PLAYER_2_SCORE_BEFORE_WIN = 3;
    private static final float BOARD_WIDTH = Constants.DEFAULT_FIELD_WIDTH;
    private static final float BOARD_HEIGHT = Constants.DEFAULT_FIELD_HEIGHT;
    private static final float CENTER_X = BOARD_WIDTH / 2;
    private static final float MALLET_RADIUS = Constants.PADDLE_WIDTH / 2;

    // Derived valid positions for each half
    private static final float VALID_LEFT_HALF_X = CENTER_X / 2;
    private static final float VALID_RIGHT_HALF_X = CENTER_X + (BOARD_WIDTH - CENTER_X) / 2;
    private static final float VALID_CENTER_Y = BOARD_HEIGHT / 2;

    private RecordingServerConnector connector;
    private Map<Integer, GameRoom> playerRooms;
    private PositionHandlerCommand handler;

    @BeforeEach
    void setUp() {
        connector = new RecordingServerConnector();
        playerRooms = new ConcurrentHashMap<>();
        handler = new PositionHandlerCommand(connector, playerRooms, null, null);
    }

    @Test
    @Tag("TC6")
    @DisplayName("Position update is sent only to other players in same room")
    void positionUpdateRoomScoped() {
        GameRoom room1 = new GameRoom(ROOM_ID_1, 1);
        GameRoom room2 = new GameRoom(ROOM_ID_2, 3);

        INetworkServer.PlayerConnection p1 = createPlayerConnection(1, PLAYER_NAME_ALICE);
        INetworkServer.PlayerConnection p2 = createPlayerConnection(2, PLAYER_NAME_BOB);
        INetworkServer.PlayerConnection p3 = createPlayerConnection(3, "Charlie");
        INetworkServer.PlayerConnection p4 = createPlayerConnection(4, "Diana");

        room1.addPlayer(p1, new Player(1, PLAYER_NAME_ALICE));
        room1.addPlayer(p2, new Player(2, PLAYER_NAME_BOB));
        room2.addPlayer(p3, new Player(3, "Charlie"));
        room2.addPlayer(p4, new Player(4, "Diana"));
        room1.setPhase(GameState.Phase.PLAYING);
        room1.setPhase(GameState.Phase.PLAYING);
        room2.setPhase(GameState.Phase.PLAYING);
        room2.setPhase(GameState.Phase.PLAYING);

        playerRooms.put(1, room1);
        playerRooms.put(2, room1);
        playerRooms.put(3, room2);
        playerRooms.put(4, room2);

        PlayerPosition update = new PlayerPosition(0, VALID_LEFT_HALF_X, VALID_CENTER_Y);
        handler.handle(p1, update);

        List<PlayerPosition> toBob = connector.getPacketsSentToOfType(2, PlayerPosition.class);
        List<PlayerPosition> toAlice = connector.getPacketsSentToOfType(1, PlayerPosition.class);
        List<PlayerPosition> toCharlie = connector.getPacketsSentToOfType(3, PlayerPosition.class);
        List<PlayerPosition> toDiana = connector.getPacketsSentToOfType(4, PlayerPosition.class);

        assertEquals(1, toBob.size());
        assertTrue(toAlice.isEmpty());
        assertTrue(toCharlie.isEmpty());
        assertTrue(toDiana.isEmpty());

        assertEquals(1, toBob.get(0).playerId);
        assertEquals(VALID_LEFT_HALF_X, room1.getGameState().getPlayer(1).getX());
        assertEquals(VALID_CENTER_Y, room1.getGameState().getPlayer(1).getY());
        assertEquals(VALID_LEFT_HALF_X, p1.getX());
        assertEquals(VALID_CENTER_Y, p1.getY());
    }

    @Test
    @Tag("FR1.2")
    @DisplayName("Player in GameState is retrievable by connection ID and has matching playerId")
    void playerIdMatchesConnectionId() {
        GameRoom room = new GameRoom(ROOM_ID_1, 1);

        // Create connections with arbitrary IDs (not 1 or 2)
        INetworkServer.PlayerConnection conn1 = createPlayerConnection(42, PLAYER_NAME_ALICE);
        INetworkServer.PlayerConnection conn2 = createPlayerConnection(99, PLAYER_NAME_BOB);

        // Add players - the Player's ID should match the connection ID
        room.addPlayer(conn1, new Player(conn1.getId(), PLAYER_NAME_ALICE));
        room.addPlayer(conn2, new Player(conn2.getId(), PLAYER_NAME_BOB));

        // Verify: Player retrieved by connection ID has the same ID
        Player retrievedPlayer1 = room.getGameState().getPlayer(conn1.getId());
        Player retrievedPlayer2 = room.getGameState().getPlayer(conn2.getId());

        assertNotNull(retrievedPlayer1, "Player should be retrievable by connection ID");
        assertNotNull(retrievedPlayer2, "Player should be retrievable by connection ID");
        
        assertEquals(conn1.getId(), retrievedPlayer1.getId(),
                "Player's ID should equal the connection ID used to retrieve it");
        assertEquals(conn2.getId(), retrievedPlayer2.getId(),
                "Player's ID should equal the connection ID used to retrieve it");
    }    

    @Test
    @Tag("FR3.1")
    @DisplayName("GameOver winnerId identifies correct player when player1 slot wins")
    void gameOverWinnerIdMatchesScoreWinnerPlayer1() {
        GameRoom room = new GameRoom(ROOM_ID_1, 1);

        // Player IDs are connection IDs, not slot numbers
        var player1ConnectionId = 11;
        var player2ConnectionId = 22;
        INetworkServer.PlayerConnection p1 = createPlayerConnection(player1ConnectionId, PLAYER_NAME_ALICE);
        INetworkServer.PlayerConnection p2 = createPlayerConnection(player2ConnectionId, PLAYER_NAME_BOB);

        // First player added = "player 1 slot", second = "player 2 slot"
        room.addPlayer(p1, new Player(player1ConnectionId, PLAYER_NAME_ALICE));
        room.addPlayer(p2, new Player(player2ConnectionId, PLAYER_NAME_BOB));
        room.setPhase(GameState.Phase.PLAYING);
        room.getGameState().getPuck().setPosition(CENTER_X, BOARD_HEIGHT / 2);
        room.setPhase(GameState.Phase.PLAYING);
        
        // Player 1 slot wins
        var theWinnerScore = room.getGameState().getScore().getWinningScore();
        room.getGameState().getScore().setPlayer1Score(theWinnerScore);

        playerRooms.put(player1ConnectionId, room);
        playerRooms.put(player2ConnectionId, room);

        handler.handle(p1, new PlayerPosition(0, VALID_LEFT_HALF_X, VALID_CENTER_Y));

        List<GameOver> toAlice = connector.getPacketsSentToOfType(player1ConnectionId, GameOver.class);
        assertEquals(1, toAlice.size());

        // winnerId should be the actual connection ID of the winner, not just slot "1"
        assertEquals(player1ConnectionId, toAlice.get(0).winnerId,
                "GameOver winnerId should be the actual player connection ID (11), not the slot number (1)");
    }

    @Test
    @Tag("FR3.1")
    @DisplayName("GameOver winnerId identifies correct player when player2 slot wins")
    void gameOverWinnerIdMatchesScoreWinnerPlayer2() {
        GameRoom room = new GameRoom(ROOM_ID_1, 1);

        // Player IDs are connection IDs, not slot numbers
        var player1ConnectionId = 11;
        var player2ConnectionId = 22;
        INetworkServer.PlayerConnection p1 = createPlayerConnection(player1ConnectionId, PLAYER_NAME_ALICE);
        INetworkServer.PlayerConnection p2 = createPlayerConnection(player2ConnectionId, PLAYER_NAME_BOB);

        // First player added = "player 1 slot", second = "player 2 slot"
        room.addPlayer(p1, new Player(player1ConnectionId, PLAYER_NAME_ALICE));
        room.addPlayer(p2, new Player(player2ConnectionId, PLAYER_NAME_BOB));
        room.setPhase(GameState.Phase.PLAYING);
        room.getGameState().getPuck().setPosition(CENTER_X, BOARD_HEIGHT / 2);
        room.setPhase(GameState.Phase.PLAYING);
        
        // Player 2 slot wins
        var theWinnerScore = room.getGameState().getScore().getWinningScore();
        room.getGameState().getScore().setPlayer2Score(theWinnerScore);

        playerRooms.put(player1ConnectionId, room);
        playerRooms.put(player2ConnectionId, room);

        // Player 2 is on right half, so x must be >= 400
        handler.handle(p2, new PlayerPosition(0, VALID_RIGHT_HALF_X, VALID_CENTER_Y));

        List<GameOver> toBob = connector.getPacketsSentToOfType(player2ConnectionId, GameOver.class);
        assertEquals(1, toBob.size());

        // winnerId should be the actual connection ID of the winner, not just slot "2"
        assertEquals(player2ConnectionId, toBob.get(0).winnerId,
                "GameOver winnerId should be the actual player connection ID (22), not the slot number (2)");
    }

    @Test
    @Tag("FR3.1")
    @DisplayName("Winner GameOver should map winner to actual room player id")
    void winnerGameOverUsesActualPlayerId() {
        // Player IDs are connection IDs, not slot numbers
        var player1ConnectionId = 41;
        var player2ConnectionId = 77;

        GameRoom room = new GameRoom(ROOM_ID_1, player1ConnectionId);

        INetworkServer.PlayerConnection p1 = createPlayerConnection(player1ConnectionId, PLAYER_NAME_ALICE);
        INetworkServer.PlayerConnection p2 = createPlayerConnection(player2ConnectionId, PLAYER_NAME_BOB);

        // First player added = "player 1 slot", second = "player 2 slot"
        room.addPlayer(p1, new Player(player1ConnectionId, PLAYER_NAME_ALICE));
        room.addPlayer(p2, new Player(player2ConnectionId, PLAYER_NAME_BOB));
        room.setPhase(GameState.Phase.PLAYING);
        room.getGameState().getPuck().setPosition(CENTER_X, BOARD_HEIGHT / 2);
        room.setPhase(GameState.Phase.PLAYING);
        
        // Player 1 slot wins
        room.getGameState().getScore().setPlayer1Score(room.getGameState().getScore().getWinningScore());

        playerRooms.put(player1ConnectionId, room);
        playerRooms.put(player2ConnectionId, room);

        // Player 1 is on left half, x must be 0-400
        handler.handle(p1, new PlayerPosition(0, VALID_LEFT_HALF_X, VALID_CENTER_Y));

        List<GameOver> toAlice = connector.getPacketsSentToOfType(player1ConnectionId, GameOver.class);
        assertEquals(1, toAlice.size());

        GameOver gameOver = toAlice.get(0);
        // winnerId should be the actual connection ID of the winner (41), not the slot number (1)
        assertEquals(player1ConnectionId, gameOver.winnerId,
                "GameOver winnerId should be the actual player connection ID (41), not the slot number (1)");
    }

    @Test
    @Tag("FR3.1")
    @DisplayName("Winner GameOver should use actual winner name from room player")
    void winnerGameOverUsesActualPlayerName() {
        GameRoom room = new GameRoom(ROOM_ID_1, 1001);

        INetworkServer.PlayerConnection p1001 = createPlayerConnection(1001, "Neo");
        INetworkServer.PlayerConnection p2002 = createPlayerConnection(2002, "Trinity");

        room.addPlayer(p1001, new Player(1001, "Neo"));
        room.addPlayer(p2002, new Player(2002, "Trinity"));
        room.setPhase(GameState.Phase.PLAYING);
        room.getGameState().getPuck().setPosition(CENTER_X, BOARD_HEIGHT / 2);
        room.setPhase(GameState.Phase.PLAYING);
        room.getGameState().getScore().setPlayer2Score(room.getGameState().getScore().getWinningScore());

        playerRooms.put(1001, room);
        playerRooms.put(2002, room);

        // Player 2 (Trinity) is on right half, x must be >= 400
        handler.handle(p2002, new PlayerPosition(0, VALID_RIGHT_HALF_X, VALID_CENTER_Y));

        List<GameOver> toTrinity = connector.getPacketsSentToOfType(2002, GameOver.class);
        assertEquals(1, toTrinity.size());

        GameOver gameOver = toTrinity.get(0);
        assertTrue("Neo".equals(gameOver.winnerName) || "Trinity".equals(gameOver.winnerName),
                "Winner name should be an actual room player name, not a hardcoded value");
    }

    @Test
    @Tag("FR4.3")
    @DisplayName("No GameOver is sent when game is already finished")
    void finishedGameDoesNotBroadcastGameOverAgain() {
        GameRoom room = new GameRoom(ROOM_ID_1, 1);

        INetworkServer.PlayerConnection p1 = createPlayerConnection(1, PLAYER_NAME_ALICE);
        INetworkServer.PlayerConnection p2 = createPlayerConnection(2, PLAYER_NAME_BOB);

        room.addPlayer(p1, new Player(1, PLAYER_NAME_ALICE));
        room.addPlayer(p2, new Player(2, PLAYER_NAME_BOB));

        room.setPhase(GameState.Phase.PLAYING);
        room.setPhase(GameState.Phase.PLAYING);
        room.setPhase(GameState.Phase.FINISHED);
        room.getGameState().getScore().setPlayer1Score(room.getGameState().getScore().getWinningScore());

        playerRooms.put(1, room);
        playerRooms.put(2, room);

        handler.handle(p1, new PlayerPosition(0, MALLET_RADIUS + 1, MALLET_RADIUS + 1));

        assertTrue(connector.getPacketsSentToOfType(1, GameOver.class).isEmpty());
        assertTrue(connector.getPacketsSentToOfType(2, GameOver.class).isEmpty());
    }

    // === Position Handling Edge Cases ===

    @Test
    @Tag("TC6")
    @DisplayName("Position update for player not in any room does not throw")
    void nullRoomDoesNotThrow() {
        INetworkServer.PlayerConnection orphanPlayer = createPlayerConnection(999, "Orphan");
        // Player not added to playerRooms

        assertDoesNotThrow(() -> handler.handle(orphanPlayer, new PlayerPosition(0, MALLET_RADIUS + 1, MALLET_RADIUS + 1)));
        assertTrue(connector.getPacketsSentToOfType(999, PlayerPosition.class).isEmpty());
    }

    @Test
    @Tag("TC6")
    @DisplayName("Position update when player missing from GameState does not throw")
    void nullPlayerInGameStateDoesNotThrow() {
        GameRoom room = new GameRoom(ROOM_ID_1, 1);
        INetworkServer.PlayerConnection p1 = createPlayerConnection(1, PLAYER_NAME_ALICE);
        
        // Add to room connections but NOT to GameState players
        room.getConnections().put(1, p1);
        playerRooms.put(1, room);

        assertDoesNotThrow(() -> handler.handle(p1, new PlayerPosition(0, VALID_LEFT_HALF_X, VALID_CENTER_Y)));
    }

    // === Phase Gating ===

    @Test
    @Tag("FR4.3")
    @DisplayName("Position packet is not forwarded during WAITING phase")
    void waitingPhaseBlocksPositionUpdate() {
        GameRoom room = new GameRoom(ROOM_ID_1, 1);
        INetworkServer.PlayerConnection p1 = createPlayerConnection(1, PLAYER_NAME_ALICE);
        INetworkServer.PlayerConnection p2 = createPlayerConnection(2, PLAYER_NAME_BOB);

        room.addPlayer(p1, new Player(1, PLAYER_NAME_ALICE));
        room.addPlayer(p2, new Player(2, PLAYER_NAME_BOB));
        room.setPhase(GameState.Phase.WAITING);

        playerRooms.put(1, room);
        playerRooms.put(2, room);

        handler.handle(p1, new PlayerPosition(0, VALID_LEFT_HALF_X, VALID_CENTER_Y));

        assertTrue(connector.getPacketsSentToOfType(2, PlayerPosition.class).isEmpty(),
                "Position should not be forwarded during WAITING phase");
    }

    @Test
    @Tag("FR4.3")
    @DisplayName("Position packet is not forwarded during FINISHED phase")
    void finishedPhaseBlocksPositionUpdate() {
        GameRoom room = new GameRoom(ROOM_ID_1, 1);
        INetworkServer.PlayerConnection p1 = createPlayerConnection(1, PLAYER_NAME_ALICE);
        INetworkServer.PlayerConnection p2 = createPlayerConnection(2, PLAYER_NAME_BOB);

        room.addPlayer(p1, new Player(1, PLAYER_NAME_ALICE));
        room.addPlayer(p2, new Player(2, PLAYER_NAME_BOB));
        room.setPhase(GameState.Phase.FINISHED);

        playerRooms.put(1, room);
        playerRooms.put(2, room);

        handler.handle(p1, new PlayerPosition(0, VALID_LEFT_HALF_X, VALID_CENTER_Y));

        assertTrue(connector.getPacketsSentToOfType(2, PlayerPosition.class).isEmpty(),
            "Position should not be forwarded during FINISHED phase");
    }

    @Test
    @Tag("FR4.1")
    @Tag("FR4.3")
    @DisplayName("Position packet is not forwarded during PAUSED phase")
    void pausedPhaseBlocksPositionUpdate() {
        GameRoom room = new GameRoom(ROOM_ID_1, 1);
        INetworkServer.PlayerConnection p1 = createPlayerConnection(1, PLAYER_NAME_ALICE);
        INetworkServer.PlayerConnection p2 = createPlayerConnection(2, PLAYER_NAME_BOB);

        room.addPlayer(p1, new Player(1, PLAYER_NAME_ALICE));
        room.addPlayer(p2, new Player(2, PLAYER_NAME_BOB));
        room.setPhase(GameState.Phase.PAUSED);

        playerRooms.put(1, room);
        playerRooms.put(2, room);

        handler.handle(p1, new PlayerPosition(0, VALID_LEFT_HALF_X, VALID_CENTER_Y));

        assertTrue(connector.getPacketsSentToOfType(2, PlayerPosition.class).isEmpty(),
                "Position should not be forwarded during PAUSED phase");
    }


    @Test
    @Tag("TC6")
    @DisplayName("Zero connection ID handled correctly")
    void zeroConnectionIdHandledCorrectly() {
        GameRoom room = new GameRoom(ROOM_ID_1, 0);
        INetworkServer.PlayerConnection p0 = createPlayerConnection(0, "Zero");
        INetworkServer.PlayerConnection p1 = createPlayerConnection(1, "One");

        room.addPlayer(p0, new Player(0, "Zero"));
        room.addPlayer(p1, new Player(1, "One"));
        room.setPhase(GameState.Phase.PLAYING);
        room.setPhase(GameState.Phase.PLAYING);

        playerRooms.put(0, room);
        playerRooms.put(1, room);

        // Player 0 is slot 1 (left half), x must be 0-400
        handler.handle(p0, new PlayerPosition(0, VALID_LEFT_HALF_X, VALID_CENTER_Y));

        List<PlayerPosition> toOne = connector.getPacketsSentToOfType(1, PlayerPosition.class);
        List<PlayerPosition> toZero = connector.getPacketsSentToOfType(0, PlayerPosition.class);

        assertEquals(1, toOne.size(), "Player 1 should receive position from player 0");
        assertTrue(toZero.isEmpty(), "Sender should not receive their own position");
        assertEquals(0, toOne.get(0).playerId, "playerId should be 0");
    }

    @Test
    @Tag("TC6")
    @DisplayName("Large connection IDs handled correctly")
    void largeConnectionIdHandledCorrectly() {
        int largeId1 = Integer.MAX_VALUE - 1;
        int largeId2 = Integer.MAX_VALUE;
        
        GameRoom room = new GameRoom(ROOM_ID_1, largeId1);
        INetworkServer.PlayerConnection p1 = createPlayerConnection(largeId1, "Large1");
        INetworkServer.PlayerConnection p2 = createPlayerConnection(largeId2, "Large2");

        room.addPlayer(p1, new Player(largeId1, "Large1"));
        room.addPlayer(p2, new Player(largeId2, "Large2"));
        room.setPhase(GameState.Phase.PLAYING);
        room.setPhase(GameState.Phase.PLAYING);

        playerRooms.put(largeId1, room);
        playerRooms.put(largeId2, room);

        // Large1 is slot 1 (left half), x must be 0-400
        handler.handle(p1, new PlayerPosition(0, VALID_LEFT_HALF_X, VALID_CENTER_Y));

        List<PlayerPosition> toLarge2 = connector.getPacketsSentToOfType(largeId2, PlayerPosition.class);
        assertEquals(1, toLarge2.size());
        assertEquals(largeId1, toLarge2.get(0).playerId);
    }

    // === Winner resolution edge case tests (PR #120 scenarios) ===

    @Test
    @Tag("FR3.1")
    @DisplayName("No crash when winner player is null due to stale slot mapping (PR #120 fix)")
    void nullWinnerPlayerDoesNotCrash() {
        // Scenario: winner slot maps to a connection ID that has no Player in GameState.
        // This can happen if a player disconnects mid-game and slot mapping becomes stale.
        // PR #120 adds a null guard to prevent NPE crash.
        // The SENDER (p2) must exist in GameState, but the WINNER (p1) is removed.
        GameRoom room = new GameRoom(ROOM_ID_1, 1);
        INetworkServer.PlayerConnection p1 = createPlayerConnection(1, PLAYER_NAME_ALICE);
        INetworkServer.PlayerConnection p2 = createPlayerConnection(2, PLAYER_NAME_BOB);

        // Add both players normally - p1 in slot 1, p2 in slot 2
        room.addPlayer(p1, new Player(1, PLAYER_NAME_ALICE));
        room.addPlayer(p2, new Player(2, PLAYER_NAME_BOB));
        room.setPhase(GameState.Phase.PLAYING);
        room.getGameState().getPuck().setPosition(CENTER_X, BOARD_HEIGHT / 2);
        room.setPhase(GameState.Phase.PLAYING);

        // Set winning score for player 1 slot (Alice wins)
        room.getGameState().getScore().setPlayer1Score(room.getGameState().getScore().getWinningScore());

        // Simulate stale state: remove WINNER (player 1) from GameState but keep slot mapping
        // Player 2 (sender) still exists
        room.getGameState().removePlayer(1);

        playerRooms.put(1, room);
        playerRooms.put(2, room);

        // Player 2 sends a position update - this triggers winner check
        // Winner (player 1) is null in GameState, but slot mapping says slot 1 won
        // Should not throw NPE (PR #120 fix) - main would crash here
        assertDoesNotThrow(() -> handler.handle(p2, new PlayerPosition(0, VALID_RIGHT_HALF_X, VALID_CENTER_Y)),
                "Handler should not crash when winner player is null");
    }

    @Test
    @Tag("FR3.1")
    @DisplayName("Disconnected winner still produces GameOver for connected clients")
    void disconnectedWinnerStillBroadcastsGameOver() {
        // Simulate disconnect by removing only the winner's active connection.
        // Slot mapping and winner Player remain in GameState so winner info is still resolvable.
        GameRoom room = new GameRoom(ROOM_ID_1, 1);
        INetworkServer.PlayerConnection p1 = createPlayerConnection(1, PLAYER_NAME_ALICE);
        INetworkServer.PlayerConnection p2 = createPlayerConnection(2, PLAYER_NAME_BOB);

        room.addPlayer(p1, new Player(1, PLAYER_NAME_ALICE));
        room.addPlayer(p2, new Player(2, PLAYER_NAME_BOB));
        room.setPhase(GameState.Phase.PLAYING);
        room.setPhase(GameState.Phase.PLAYING);
        room.getGameState().getScore().setPlayer1Score(room.getGameState().getScore().getWinningScore());

        room.getConnections().remove(1);
        playerRooms.put(2, room);

        handler.handle(p2, new PlayerPosition(0, VALID_RIGHT_HALF_X, VALID_CENTER_Y));

        List<GameOver> toBob = connector.getPacketsSentToOfType(2, GameOver.class);
        assertEquals(1, toBob.size(), "Connected player should still receive GameOver");
        assertEquals(1, toBob.get(0).winnerId, "Winner should keep original connection ID");
        assertEquals(PLAYER_NAME_ALICE, toBob.get(0).winnerName, "Winner should keep original player name");
    }

    @Test
    @Tag("FR3.1")
    @DisplayName("Room must not be FINISHED if GameOver was not sent (PR #120 failure mode)")
    void roomMustNotBeFinishedIfGameOverNotSent() {
        // Winner slot exists, but winner Player is missing from GameState.
        // Handler should skip GameOver broadcast and keep the room out of FINISHED.
        GameRoom room = new GameRoom(ROOM_ID_1, 1);
        INetworkServer.PlayerConnection p1 = createPlayerConnection(1, PLAYER_NAME_ALICE);
        INetworkServer.PlayerConnection p2 = createPlayerConnection(2, PLAYER_NAME_BOB);

        room.addPlayer(p1, new Player(1, PLAYER_NAME_ALICE));
        room.addPlayer(p2, new Player(2, PLAYER_NAME_BOB));
        room.setPhase(GameState.Phase.PLAYING);
        room.getGameState().getPuck().setPosition(CENTER_X, BOARD_HEIGHT / 2);
        room.setPhase(GameState.Phase.PLAYING);
        room.getGameState().getScore().setPlayer1Score(room.getGameState().getScore().getWinningScore());

        room.getGameState().removePlayer(1);
        playerRooms.put(1, room);
        playerRooms.put(2, room);

        handler.handle(p2, new PlayerPosition(0, VALID_RIGHT_HALF_X, VALID_CENTER_Y));

        List<GameOver> toAlice = connector.getPacketsSentToOfType(1, GameOver.class);
        List<GameOver> toBob = connector.getPacketsSentToOfType(2, GameOver.class);

        assertTrue(toAlice.isEmpty(), "No GameOver should be sent when winner lookup fails");
        assertTrue(toBob.isEmpty(), "No GameOver should be sent when winner lookup fails");
        assertNotEquals(GameState.Phase.FINISHED, room.getPhase(),
                "Room should not be FINISHED if GameOver could not be sent");
    }

    private static INetworkServer.PlayerConnection createPlayerConnection(int id, String name) {
        INetworkServer.PlayerConnection connection = new NetworkKryoServer.KryoPlayerConnection(new TestConnection(id));
        connection.setPlayerName(name);
        return connection;
    }

    private static class TestConnection extends Connection {
        private final int id;

        TestConnection(int id) {
            this.id = id;
        }

        @Override
        public int getID() {
            return id;
        }
    }

    private static class RecordingServerConnector extends ServerConnector {
        private final List<SentPacket> sentPackets = new ArrayList<>();

        RecordingServerConnector() {
            super(new NetworkKryoServer());
        }

        @Override
        public void send(int connectionId, Object packet) {
            sentPackets.add(new SentPacket(connectionId, packet));
        }

        <T> List<T> getPacketsSentToOfType(int connectionId, Class<T> type) {
            return sentPackets.stream()
                    .filter(p -> p.connectionId == connectionId)
                    .map(SentPacket::packet)
                    .filter(type::isInstance)
                    .map(type::cast)
                    .toList();
        }

        private record SentPacket(int connectionId, Object packet) {}
    }
}

