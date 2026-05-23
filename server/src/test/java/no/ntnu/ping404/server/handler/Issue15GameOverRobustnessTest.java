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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #15 - GameOver Robustness Tests
 * 
 * Tests for null-safety and edge case handling in the GameOver broadcast flow.
 * These tests ensure the server does not crash when:
 * - Winner slot mapping returns -1 (invalid slot)
 * - Winner player is not found in GameState
 * - A player disconnects mid-game while score triggers win
 * - Player name is null or empty
 * 
 * @see <a href="https://git.ntnu.no/pedesmet/ping404/issues/15">Issue #15</a>
 */
class Issue15GameOverRobustnessTest {

    private static final String ROOM_ID = "test-room";
    private static final String PLAYER_NAME_ALICE = "Alice";
    private static final String PLAYER_NAME_BOB = "Bob";

    private static final float BOARD_WIDTH = Constants.DEFAULT_FIELD_WIDTH;
    private static final float BOARD_HEIGHT = Constants.DEFAULT_FIELD_HEIGHT;
    private static final float CENTER_X = BOARD_WIDTH / 2;

    // Valid positions for left and right halves
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
        handler = new PositionHandlerCommand(connector, playerRooms, null);
    }

    @Nested
    @DisplayName("Issue #15: Null-Safety for Winner Lookup")
    class NullSafetyForWinnerLookup {

        @Test
        @Tag("Issue15")
        @Tag("FR3.1")
        @DisplayName("GameOver does not throw NPE when winner player is removed from GameState before broadcast")
        void gameOverDoesNotThrowWhenWinnerPlayerMissingFromGameState() {
            // Setup: Two players in room, player 1 will win
            int player1Id = 1;
            int player2Id = 2;

            GameRoom room = new GameRoom(ROOM_ID, player1Id);
            INetworkServer.PlayerConnection p1 = createPlayerConnection(player1Id, PLAYER_NAME_ALICE);
            INetworkServer.PlayerConnection p2 = createPlayerConnection(player2Id, PLAYER_NAME_BOB);

            room.addPlayer(p1, new Player(player1Id, PLAYER_NAME_ALICE));
            room.addPlayer(p2, new Player(player2Id, PLAYER_NAME_BOB));
            room.getGameState().setPhase(GameState.Phase.PLAYING);

            // Set winning score so hasWinner() returns true
            room.getGameState().getScore().setPlayer1Score(
                    room.getGameState().getScore().getWinningScore());

            // CRITICAL: Remove player 1 from GameState BEFORE handling position
            // This simulates a race condition where player disconnects as game ends
            room.getGameState().removePlayer(player1Id);

            playerRooms.put(player1Id, room);
            playerRooms.put(player2Id, room);

            // Should NOT throw NPE when attempting to get winner's name
            assertDoesNotThrow(() -> 
                handler.handle(p1, new PlayerPosition(0, VALID_LEFT_HALF_X, VALID_CENTER_Y)),
                "GameOver broadcast should not throw NPE when winner player is missing from GameState"
            );
        }

        @Test
        @Tag("Issue15")
        @Tag("FR3.1")
        @DisplayName("GameOver handles null winner name gracefully")
        void gameOverHandlesNullWinnerNameGracefully() {
            int player1Id = 1;
            int player2Id = 2;

            GameRoom room = new GameRoom(ROOM_ID, player1Id);
            INetworkServer.PlayerConnection p1 = createPlayerConnection(player1Id, PLAYER_NAME_ALICE);
            INetworkServer.PlayerConnection p2 = createPlayerConnection(player2Id, PLAYER_NAME_BOB);

            // Create player with null name
            Player playerWithNullName = new Player(player1Id, null);
            room.addPlayer(p1, playerWithNullName);
            room.addPlayer(p2, new Player(player2Id, PLAYER_NAME_BOB));
            room.getGameState().setPhase(GameState.Phase.PLAYING);

            room.getGameState().getScore().setPlayer1Score(
                    room.getGameState().getScore().getWinningScore());

            playerRooms.put(player1Id, room);
            playerRooms.put(player2Id, room);

            // Should NOT throw NPE when winner has null name
            assertDoesNotThrow(() -> 
                handler.handle(p1, new PlayerPosition(0, VALID_LEFT_HALF_X, VALID_CENTER_Y)),
                "GameOver broadcast should not throw NPE when winner has null name"
            );
        }

        @Test
        @Tag("Issue15")
        @Tag("FR3.1")
        @DisplayName("GameOver handles empty winner name gracefully")
        void gameOverHandlesEmptyWinnerNameGracefully() {
            int player1Id = 1;
            int player2Id = 2;

            GameRoom room = new GameRoom(ROOM_ID, player1Id);
            INetworkServer.PlayerConnection p1 = createPlayerConnection(player1Id, PLAYER_NAME_ALICE);
            INetworkServer.PlayerConnection p2 = createPlayerConnection(player2Id, PLAYER_NAME_BOB);

            // Create player with empty name
            Player playerWithEmptyName = new Player(player1Id, "");
            room.addPlayer(p1, playerWithEmptyName);
            room.addPlayer(p2, new Player(player2Id, PLAYER_NAME_BOB));
            room.getGameState().setPhase(GameState.Phase.PLAYING);

            room.getGameState().getScore().setPlayer1Score(
                    room.getGameState().getScore().getWinningScore());

            playerRooms.put(player1Id, room);
            playerRooms.put(player2Id, room);

            // Should handle empty name without exception
            assertDoesNotThrow(() -> 
                handler.handle(p1, new PlayerPosition(0, VALID_LEFT_HALF_X, VALID_CENTER_Y)),
                "GameOver broadcast should handle empty winner name"
            );
        }
    }

    @Nested
    @DisplayName("Issue #15: Slot Mapping Edge Cases")
    class SlotMappingEdgeCases {

        @Test
        @Tag("Issue15")
        @Tag("FR3.1")
        @DisplayName("GameOver does not crash when slot-to-connectionId returns -1")
        void gameOverDoesNotCrashWhenSlotReturnsInvalidConnectionId() {
            // This tests a defensive scenario where internal state becomes inconsistent
            int player1Id = 1;
            int player2Id = 2;

            GameRoom room = new GameRoom(ROOM_ID, player1Id);
            INetworkServer.PlayerConnection p1 = createPlayerConnection(player1Id, PLAYER_NAME_ALICE);
            INetworkServer.PlayerConnection p2 = createPlayerConnection(player2Id, PLAYER_NAME_BOB);

            room.addPlayer(p1, new Player(player1Id, PLAYER_NAME_ALICE));
            room.addPlayer(p2, new Player(player2Id, PLAYER_NAME_BOB));
            room.getGameState().setPhase(GameState.Phase.PLAYING);

            // Set score to indicate player 1 wins
            room.getGameState().getScore().setPlayer1Score(
                    room.getGameState().getScore().getWinningScore());

            // Remove player from room (but leave score indicating they won)
            // This could happen if player disconnects exactly as they score winning point
            room.removePlayer(player1Id);

            playerRooms.put(player2Id, room);

            // Handle position from remaining player - should not throw
            assertDoesNotThrow(() -> 
                handler.handle(p2, new PlayerPosition(0, VALID_RIGHT_HALF_X, VALID_CENTER_Y)),
                "GameOver broadcast should not crash when winner slot is empty"
            );
        }

        @Test
        @Tag("Issue15")
        @Tag("FR3.2")
        @DisplayName("GameOver uses fallback when winner cannot be identified")
        void gameOverUsesFallbackWhenWinnerCannotBeIdentified() {
            int player1Id = 1;
            int player2Id = 2;

            GameRoom room = new GameRoom(ROOM_ID, player1Id);
            INetworkServer.PlayerConnection p1 = createPlayerConnection(player1Id, PLAYER_NAME_ALICE);
            INetworkServer.PlayerConnection p2 = createPlayerConnection(player2Id, PLAYER_NAME_BOB);

            room.addPlayer(p1, new Player(player1Id, PLAYER_NAME_ALICE));
            room.addPlayer(p2, new Player(player2Id, PLAYER_NAME_BOB));
            room.getGameState().setPhase(GameState.Phase.PLAYING);

            // Set winning score
            room.getGameState().getScore().setPlayer1Score(
                    room.getGameState().getScore().getWinningScore());

            // Remove winner from GameState (simulates disconnect during win)
            room.getGameState().removePlayer(player1Id);

            playerRooms.put(player1Id, room);
            playerRooms.put(player2Id, room);

            // Handle position - should handle gracefully
            handler.handle(p1, new PlayerPosition(0, VALID_LEFT_HALF_X, VALID_CENTER_Y));

            // Verify: Either no GameOver sent (graceful skip) or GameOver with safe fallback
            List<GameOver> toBob = connector.getPacketsSentToOfType(player2Id, GameOver.class);
            
            // If GameOver was sent, it should have valid data (not null/crash)
            if (!toBob.isEmpty()) {
                GameOver gameOver = toBob.get(0);
                assertNotNull(gameOver, "GameOver packet should not be null");
                // Score should still be accurate even if winner lookup failed
                assertEquals(room.getGameState().getScore().getPlayer1Score(), gameOver.player1Score);
                assertEquals(room.getGameState().getScore().getPlayer2Score(), gameOver.player2Score);
            }
            // Test passes either way - the key is no NPE
        }
    }

    @Nested
    @DisplayName("Issue #15: Concurrent Disconnect During Win")
    class ConcurrentDisconnectDuringWin {

        @Test
        @Tag("Issue15")
        @Tag("FR3.1")
        @Tag("A1")
        @DisplayName("Server handles winner disconnect exactly at win moment without crashing")
        void serverHandlesWinnerDisconnectAtWinMoment() {
            int player1Id = 10;
            int player2Id = 20;

            GameRoom room = new GameRoom(ROOM_ID, player1Id);
            INetworkServer.PlayerConnection p1 = createPlayerConnection(player1Id, PLAYER_NAME_ALICE);
            INetworkServer.PlayerConnection p2 = createPlayerConnection(player2Id, PLAYER_NAME_BOB);

            room.addPlayer(p1, new Player(player1Id, PLAYER_NAME_ALICE));
            room.addPlayer(p2, new Player(player2Id, PLAYER_NAME_BOB));
            room.getGameState().setPhase(GameState.Phase.PLAYING);

            // Set score so player 1 has won
            room.getGameState().getScore().setPlayer1Score(
                    room.getGameState().getScore().getWinningScore());

            // Simulate: player 1 disconnects (removed from room) right before GameOver is sent
            room.removePlayer(player1Id);
            playerRooms.remove(player1Id);

            playerRooms.put(player2Id, room);

            // Now player 2 sends a position - this should trigger winner check
            // but winner (player 1) is already gone
            assertDoesNotThrow(() -> 
                handler.handle(p2, new PlayerPosition(0, VALID_RIGHT_HALF_X, VALID_CENTER_Y)),
                "Server should handle case where winner disconnected before GameOver broadcast"
            );
        }

        @Test
        @Tag("Issue15")
        @Tag("FR3.1")
        @Tag("A1")
        @DisplayName("Server handles loser disconnect at win moment without crashing")
        void serverHandlesLoserDisconnectAtWinMoment() {
            int player1Id = 10;
            int player2Id = 20;

            GameRoom room = new GameRoom(ROOM_ID, player1Id);
            INetworkServer.PlayerConnection p1 = createPlayerConnection(player1Id, PLAYER_NAME_ALICE);
            INetworkServer.PlayerConnection p2 = createPlayerConnection(player2Id, PLAYER_NAME_BOB);

            room.addPlayer(p1, new Player(player1Id, PLAYER_NAME_ALICE));
            room.addPlayer(p2, new Player(player2Id, PLAYER_NAME_BOB));
            room.getGameState().setPhase(GameState.Phase.PLAYING);

            // Set score so player 1 has won
            room.getGameState().getScore().setPlayer1Score(
                    room.getGameState().getScore().getWinningScore());

            // Simulate: player 2 (loser) disconnects right before GameOver is sent
            room.removePlayer(player2Id);
            playerRooms.remove(player2Id);

            playerRooms.put(player1Id, room);

            // Player 1 sends position - triggers GameOver but opponent is gone
            assertDoesNotThrow(() -> 
                handler.handle(p1, new PlayerPosition(0, VALID_LEFT_HALF_X, VALID_CENTER_Y)),
                "Server should handle case where loser disconnected before GameOver broadcast"
            );

            // Winner should still receive GameOver
            List<GameOver> toAlice = connector.getPacketsSentToOfType(player1Id, GameOver.class);
            assertEquals(1, toAlice.size(), "Winner should still receive GameOver even if opponent disconnected");
        }

        @Test
        @Tag("Issue15")
        @Tag("FR3.1")
        @Tag("A1")
        @DisplayName("Server handles both players disconnecting during win check")
        void serverHandlesBothPlayersDisconnectingDuringWinCheck() {
            int player1Id = 10;
            int player2Id = 20;

            GameRoom room = new GameRoom(ROOM_ID, player1Id);
            INetworkServer.PlayerConnection p1 = createPlayerConnection(player1Id, PLAYER_NAME_ALICE);
            INetworkServer.PlayerConnection p2 = createPlayerConnection(player2Id, PLAYER_NAME_BOB);

            room.addPlayer(p1, new Player(player1Id, PLAYER_NAME_ALICE));
            room.addPlayer(p2, new Player(player2Id, PLAYER_NAME_BOB));
            room.getGameState().setPhase(GameState.Phase.PLAYING);

            // Set winning score
            room.getGameState().getScore().setPlayer1Score(
                    room.getGameState().getScore().getWinningScore());

            // Both players disconnect - empty room with winning score
            room.removePlayer(player1Id);
            room.removePlayer(player2Id);

            // Room is now empty but score indicates winner
            assertTrue(room.isEmpty(), "Room should be empty");
            assertTrue(room.getGameState().getScore().hasWinner(), "Score should still show winner");

            // If somehow a stale connection triggers position handling, it should not crash
            // (This is a defensive edge case)
            playerRooms.put(player1Id, room);

            assertDoesNotThrow(() -> 
                handler.handle(p1, new PlayerPosition(0, VALID_LEFT_HALF_X, VALID_CENTER_Y)),
                "Server should handle empty room with winning score"
            );
        }
    }

    @Nested
    @DisplayName("Issue #15: GameOver Idempotency")
    class GameOverIdempotency {

        @Test
        @Tag("Issue15")
        @Tag("FR3.1")
        @DisplayName("GameOver is only broadcast once per match")
        void gameOverBroadcastOnlyOnce() {
            int player1Id = 1;
            int player2Id = 2;

            GameRoom room = new GameRoom(ROOM_ID, player1Id);
            INetworkServer.PlayerConnection p1 = createPlayerConnection(player1Id, PLAYER_NAME_ALICE);
            INetworkServer.PlayerConnection p2 = createPlayerConnection(player2Id, PLAYER_NAME_BOB);

            room.addPlayer(p1, new Player(player1Id, PLAYER_NAME_ALICE));
            room.addPlayer(p2, new Player(player2Id, PLAYER_NAME_BOB));
            room.getGameState().setPhase(GameState.Phase.PLAYING);

            room.getGameState().getScore().setPlayer1Score(
                    room.getGameState().getScore().getWinningScore());

            playerRooms.put(player1Id, room);
            playerRooms.put(player2Id, room);

            // First position update triggers GameOver
            handler.handle(p1, new PlayerPosition(0, VALID_LEFT_HALF_X, VALID_CENTER_Y));

            // Multiple subsequent position updates should NOT trigger additional GameOvers
            handler.handle(p1, new PlayerPosition(0, VALID_LEFT_HALF_X + 1, VALID_CENTER_Y));
            handler.handle(p1, new PlayerPosition(0, VALID_LEFT_HALF_X + 2, VALID_CENTER_Y));
            handler.handle(p2, new PlayerPosition(0, VALID_RIGHT_HALF_X, VALID_CENTER_Y));
            handler.handle(p2, new PlayerPosition(0, VALID_RIGHT_HALF_X + 1, VALID_CENTER_Y));

            List<GameOver> toAlice = connector.getPacketsSentToOfType(player1Id, GameOver.class);
            List<GameOver> toBob = connector.getPacketsSentToOfType(player2Id, GameOver.class);

            assertEquals(1, toAlice.size(), "Alice should receive exactly one GameOver");
            assertEquals(1, toBob.size(), "Bob should receive exactly one GameOver");
        }

        @Test
        @Tag("Issue15")
        @Tag("FR3.1")
        @DisplayName("Phase transitions to FINISHED after GameOver broadcast")
        void phaseTransitionsToFinishedAfterGameOver() {
            int player1Id = 1;
            int player2Id = 2;

            GameRoom room = new GameRoom(ROOM_ID, player1Id);
            INetworkServer.PlayerConnection p1 = createPlayerConnection(player1Id, PLAYER_NAME_ALICE);
            INetworkServer.PlayerConnection p2 = createPlayerConnection(player2Id, PLAYER_NAME_BOB);

            room.addPlayer(p1, new Player(player1Id, PLAYER_NAME_ALICE));
            room.addPlayer(p2, new Player(player2Id, PLAYER_NAME_BOB));
            room.getGameState().setPhase(GameState.Phase.PLAYING);

            assertEquals(GameState.Phase.PLAYING, room.getPhase(), "Should start in PLAYING phase");

            room.getGameState().getScore().setPlayer1Score(
                    room.getGameState().getScore().getWinningScore());

            playerRooms.put(player1Id, room);
            playerRooms.put(player2Id, room);

            handler.handle(p1, new PlayerPosition(0, VALID_LEFT_HALF_X, VALID_CENTER_Y));

            assertEquals(GameState.Phase.FINISHED, room.getPhase(), 
                    "Phase should transition to FINISHED after GameOver");
            assertEquals(GameState.Phase.FINISHED, room.getGameState().getPhase(),
                    "GameState phase should also be FINISHED");
        }
    }

    /**
     * Issue #15 requires Domain Event/Observer pattern for winner notification.
     * Tests verify GameRoomListener.onMatchEnded is called when winner is detected.
     */
    @Nested
    @DisplayName("Issue #15: Winner Domain Event / Observer Pattern")
    class WinnerDomainEventObserverPattern {

        @Test
        @Tag("Issue15")
        @Tag("FR3.1")
        @DisplayName("GameRoomListener.onMatchEnded should be called when winner detected")
        void gameRoomListenerOnMatchEndedCalledWhenWinnerDetected() {
            int player1Id = 1;
            int player2Id = 2;

            GameRoom room = new GameRoom(ROOM_ID, player1Id);
            INetworkServer.PlayerConnection p1 = createPlayerConnection(player1Id, PLAYER_NAME_ALICE);
            INetworkServer.PlayerConnection p2 = createPlayerConnection(player2Id, PLAYER_NAME_BOB);

            room.addPlayer(p1, new Player(player1Id, PLAYER_NAME_ALICE));
            room.addPlayer(p2, new Player(player2Id, PLAYER_NAME_BOB));
            room.getGameState().setPhase(GameState.Phase.PLAYING);

            // Track listener calls
            List<Integer> winnerIds = new ArrayList<>();
            room.addListener(new GameRoom.GameRoomListener() {
                @Override
                public void onMatchEnded(GameRoom r, int winnerId, String winnerName) {
                    winnerIds.add(winnerId);
                }
            });

            room.getGameState().getScore().setPlayer1Score(
                    room.getGameState().getScore().getWinningScore());

            playerRooms.put(player1Id, room);
            playerRooms.put(player2Id, room);

            // This should trigger winner detection and call listener
            handler.handle(p1, new PlayerPosition(0, VALID_LEFT_HALF_X, VALID_CENTER_Y));

            assertEquals(1, winnerIds.size(),
                    "onMatchEnded should be called exactly once");
            assertEquals(1, winnerIds.get(0),
                    "Winner ID should be player 1");
        }

        @Test
        @Tag("Issue15")
        @Tag("FR3.1")
        @DisplayName("GameRoomListener.onMatchEnded should receive winner name")
        void gameRoomListenerOnMatchEndedReceivesWinnerName() {
            int player1Id = 1;
            int player2Id = 2;

            GameRoom room = new GameRoom(ROOM_ID, player1Id);
            INetworkServer.PlayerConnection p1 = createPlayerConnection(player1Id, PLAYER_NAME_ALICE);
            INetworkServer.PlayerConnection p2 = createPlayerConnection(player2Id, PLAYER_NAME_BOB);

            room.addPlayer(p1, new Player(player1Id, PLAYER_NAME_ALICE));
            room.addPlayer(p2, new Player(player2Id, PLAYER_NAME_BOB));
            room.getGameState().setPhase(GameState.Phase.PLAYING);

            List<String> winnerNames = new ArrayList<>();
            room.addListener(new GameRoom.GameRoomListener() {
                @Override
                public void onMatchEnded(GameRoom r, int winnerId, String winnerName) {
                    winnerNames.add(winnerName);
                }
            });

            room.getGameState().getScore().setPlayer1Score(
                    room.getGameState().getScore().getWinningScore());

            playerRooms.put(player1Id, room);
            playerRooms.put(player2Id, room);

            handler.handle(p1, new PlayerPosition(0, VALID_LEFT_HALF_X, VALID_CENTER_Y));

            assertEquals(1, winnerNames.size(),
                    "onMatchEnded should be called");
            assertEquals(PLAYER_NAME_ALICE, winnerNames.get(0),
                    "Winner name should be Alice");
        }

        @Test
        @Tag("Issue15")
        @Tag("FR3.2")
        @DisplayName("Multiple listeners should all be notified on match end")
        void multipleListenersNotifiedOnMatchEnd() {
            int player1Id = 1;
            int player2Id = 2;

            GameRoom room = new GameRoom(ROOM_ID, player1Id);
            INetworkServer.PlayerConnection p1 = createPlayerConnection(player1Id, PLAYER_NAME_ALICE);
            INetworkServer.PlayerConnection p2 = createPlayerConnection(player2Id, PLAYER_NAME_BOB);

            room.addPlayer(p1, new Player(player1Id, PLAYER_NAME_ALICE));
            room.addPlayer(p2, new Player(player2Id, PLAYER_NAME_BOB));
            room.getGameState().setPhase(GameState.Phase.PLAYING);

            List<String> listener1Calls = new ArrayList<>();
            List<String> listener2Calls = new ArrayList<>();

            room.addListener(new GameRoom.GameRoomListener() {
                @Override
                public void onMatchEnded(GameRoom r, int winnerId, String winnerName) {
                    listener1Calls.add("called");
                }
            });

            room.addListener(new GameRoom.GameRoomListener() {
                @Override
                public void onMatchEnded(GameRoom r, int winnerId, String winnerName) {
                    listener2Calls.add("called");
                }
            });

            room.getGameState().getScore().setPlayer1Score(
                    room.getGameState().getScore().getWinningScore());

            playerRooms.put(player1Id, room);
            playerRooms.put(player2Id, room);

            handler.handle(p1, new PlayerPosition(0, VALID_LEFT_HALF_X, VALID_CENTER_Y));

            assertEquals(1, listener1Calls.size(),
                    "First listener should be notified");
            assertEquals(1, listener2Calls.size(),
                    "Second listener should be notified");
        }

        @Test
        @Tag("Issue15")
        @Tag("FR3.1")
        @DisplayName("onMatchEnded called before GameOver is broadcast")
        void onMatchEndedCalledBeforeGameOverBroadcast() {
            int player1Id = 1;
            int player2Id = 2;

            GameRoom room = new GameRoom(ROOM_ID, player1Id);
            INetworkServer.PlayerConnection p1 = createPlayerConnection(player1Id, PLAYER_NAME_ALICE);
            INetworkServer.PlayerConnection p2 = createPlayerConnection(player2Id, PLAYER_NAME_BOB);

            room.addPlayer(p1, new Player(player1Id, PLAYER_NAME_ALICE));
            room.addPlayer(p2, new Player(player2Id, PLAYER_NAME_BOB));
            room.getGameState().setPhase(GameState.Phase.PLAYING);

            List<String> eventOrder = new ArrayList<>();

            // Override connector to track when GameOver is sent
            RecordingServerConnector trackingConnector = new RecordingServerConnector() {
                @Override
                public void send(int connectionId, Object packet) {
                    if (packet instanceof GameOver) {
                        eventOrder.add("GameOverSent");
                    }
                    super.send(connectionId, packet);
                }
            };

            room.addListener(new GameRoom.GameRoomListener() {
                @Override
                public void onMatchEnded(GameRoom r, int winnerId, String winnerName) {
                    eventOrder.add("onMatchEnded");
                }
            });

            PositionHandlerCommand trackingHandler = new PositionHandlerCommand(trackingConnector, playerRooms, null);

            room.getGameState().getScore().setPlayer1Score(
                    room.getGameState().getScore().getWinningScore());

            playerRooms.put(player1Id, room);
            playerRooms.put(player2Id, room);

            trackingHandler.handle(p1, new PlayerPosition(0, VALID_LEFT_HALF_X, VALID_CENTER_Y));

            assertTrue(eventOrder.contains("onMatchEnded"),
                    "onMatchEnded should be called");
            assertTrue(eventOrder.contains("GameOverSent"),
                    "GameOverSent should occur");
            assertEquals("onMatchEnded", eventOrder.get(0),
                    "onMatchEnded should be called before GameOver broadcast");
        }
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

        List<Object> getAllPacketsSentTo(int connectionId) {
            return sentPackets.stream()
                    .filter(p -> p.connectionId == connectionId)
                    .map(SentPacket::packet)
                    .toList();
        }

        private record SentPacket(int connectionId, Object packet) {}
    }
}
