package no.ntnu.ping404.server;

import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.model.Player;
import no.ntnu.ping404.model.Score;
import no.ntnu.ping404.network.INetworkServer.PlayerConnection;
import no.ntnu.ping404.network.INetworkServer;
import no.ntnu.ping404.network.packets.GameOver;
import no.ntnu.ping404.utils.Constants;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Issue #33: Win condition flow.
 *
 * <p>Validates the complete flow from score threshold being reached
 * to GameOver packet construction with correct winner identification.</p>
 *
 * <p>Requirements covered:</p>
 * <ul>
 *   <li>FR2.9: Match ends only when a player reaches the winning score threshold</li>
 *   <li>FR3.1: Server sends GameOver packet when win condition is met</li>
 *   <li>FR3.2: GameOver packet must identify the winner by actual ID and name</li>
 * </ul>
 *
 * @see Score#hasWinner()
 * @see Score#getWinner()
 * @see GameState#finishMatch()
 * @see GameRoom#getConnectionIdForSlot(int)
 */
class WinConditionFlowTest {

    private static final int WIN_SCORE = 5;
    private static final int CONN_PLAYER_1 = 101;
    private static final int CONN_PLAYER_2 = 202;
    private static final String NAME_ALICE = "Alice";
    private static final String NAME_BOB = "Bob";
    private static final String ROOM_ID = "win-test-room";

    private static PlayerConnection conn(int id, String name) {
        return new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(id, name);
    }

    private static Player player(int id, String name) {
        return new Player(id, name);
    }

    private GameRoom createRoomWithPlayers() {
        GameRoom room = new GameRoom(ROOM_ID, CONN_PLAYER_1, WIN_SCORE);
        room.addPlayer(conn(CONN_PLAYER_1, NAME_ALICE), player(CONN_PLAYER_1, NAME_ALICE));
        room.addPlayer(conn(CONN_PLAYER_2, NAME_BOB), player(CONN_PLAYER_2, NAME_BOB));
        return room;
    }

    @Nested
    @DisplayName("FR2.9: Match ends when win score reached")
    class MatchEndTests {

        @Test
        @Tag("FR2.9")
        @DisplayName("GameState phase transitions to FINISHED when win detected")
        void phaseTransitionsToFinishedOnWin() {
            GameState state = new GameState(WIN_SCORE);
            state.addPlayer(player(1, NAME_ALICE));
            state.addPlayer(player(2, NAME_BOB));
            state.startMatch();
            state.getScore().setPlayer1Score(WIN_SCORE);

            boolean result = state.finishMatch();

            assertTrue(result, "finishMatch should succeed from PLAYING phase");
            assertEquals(GameState.Phase.FINISHED, state.getPhase(),
                    "Phase should transition to FINISHED when win condition met");
        }
    }

    @Nested
    @DisplayName("FR3.1: Server sends GameOver on win")
    class GameOverSendTests {

        @Test
        @Tag("FR3.1")
        @DisplayName("finishMatch is triggered when win score reached")
        void finishMatchTriggeredOnWin() {
            GameState state = new GameState(WIN_SCORE);
            Player p1 = player(1, NAME_ALICE);
            Player p2 = player(2, NAME_BOB);
            p1.setX(Constants.PADDLE_MARGIN + Constants.PADDLE_WIDTH / 2);
            p1.setY(Constants.boardCenterY());
            p2.setX(Constants.DEFAULT_FIELD_WIDTH - Constants.PADDLE_MARGIN - Constants.PADDLE_WIDTH / 2);
            p2.setY(Constants.boardCenterY());
            state.addPlayer(p1);
            state.addPlayer(p2);
            state.startMatch();
            state.getScore().setPlayer1Score(WIN_SCORE);

            assertTrue(state.getScore().hasWinner(), "hasWinner should be true at win score");
            boolean finished = state.finishMatch();

            assertTrue(finished, "finishMatch should succeed");
            assertEquals(GameState.Phase.FINISHED, state.getPhase(),
                    "Phase should be FINISHED after finishMatch");
        }

        @Test
        @Tag("FR3.1")
        @DisplayName("GameOver packet is created with correct scores")
        void gameOverPacketHasCorrectScores() {
            int player1FinalScore = WIN_SCORE;
            int player2FinalScore = 3;

            GameOver packet = new GameOver(CONN_PLAYER_1, NAME_ALICE, player1FinalScore, player2FinalScore);

            assertEquals(player1FinalScore, packet.player1Score, "player1Score should match final score");
            assertEquals(player2FinalScore, packet.player2Score, "player2Score should match final score");
        }
    }

    @Nested
    @DisplayName("FR3.2: GameOver identifies winner correctly")
    class WinnerIdentificationTests {

        @Test
        @Tag("FR3.2")
        @DisplayName("Winner slot 1 maps to correct connection ID")
        void winnerSlot1MapsToConnectionId() {
            GameRoom room = createRoomWithPlayers();

            int connectionId = room.getConnectionIdForSlot(1);

            assertEquals(CONN_PLAYER_1, connectionId,
                    "Slot 1 should map to first player's connection ID");
        }

        @Test
        @Tag("FR3.2")
        @DisplayName("Winner slot 2 maps to correct connection ID")
        void winnerSlot2MapsToConnectionId() {
            GameRoom room = createRoomWithPlayers();

            int connectionId = room.getConnectionIdForSlot(2);

            assertEquals(CONN_PLAYER_2, connectionId,
                    "Slot 2 should map to second player's connection ID");
        }

        @Test
        @Tag("FR3.2")
        @DisplayName("GameOver packet contains winner's actual name from room")
        void gameOverContainsWinnerName() {
            GameRoom room = createRoomWithPlayers();
            room.getGameState().getScore().setPlayer1Score(WIN_SCORE);

            int winnerSlot = room.getGameState().getScore().getWinner();
            int winnerId = room.getConnectionIdForSlot(winnerSlot);
            Player winner = room.getGameState().getPlayer(winnerId);

            assertEquals(NAME_ALICE, winner.getName(),
                    "Winner name should match the player in the winning slot");
        }

        @Test
        @Tag("FR3.2")
        @DisplayName("GameOver winnerId is connection ID, not slot number")
        void gameOverWinnerIdIsConnectionId() {
            GameRoom room = createRoomWithPlayers();
            room.getGameState().getScore().setPlayer1Score(WIN_SCORE);

            int winnerSlot = room.getGameState().getScore().getWinner();
            int winnerId = room.getConnectionIdForSlot(winnerSlot);

            assertEquals(1, winnerSlot, "Winner slot should be 1");
            assertEquals(CONN_PLAYER_1, winnerId,
                    "Winner ID should be connection ID (101), not slot number (1)");
            assertNotEquals(winnerSlot, winnerId,
                    "Winner ID must not equal slot number when connection IDs differ");
        }
    }

    @Nested
    @DisplayName("Integration: Full win condition flow")
    class IntegrationTests {

        @Test
        @Tag("FR2.9")
        @Tag("FR3.1")
        @Tag("FR3.2")
        @DisplayName("End-to-end: Score threshold --> win detected --> GameOver with correct winner")
        void endToEndWinConditionFlow() {
            GameRoom room = createRoomWithPlayers();
            room.setPhase(GameState.Phase.PLAYING);
            Score score = room.getGameState().getScore();
            score.setPlayer1Score(WIN_SCORE - 1);

            score.incrementScore(1);

            assertTrue(score.hasWinner(), "hasWinner should return true after winning score reached");
            assertEquals(1, score.getWinner(), "getWinner should return winning slot");

            room.getGameState().finishMatch();
            assertEquals(GameState.Phase.FINISHED, room.getGameState().getPhase(),
                    "Phase should be FINISHED");

            int winnerSlot = score.getWinner();
            int winnerId = room.getConnectionIdForSlot(winnerSlot);
            Player winner = room.getGameState().getPlayer(winnerId);

            GameOver packet = new GameOver(winnerId, winner.getName(),
                    score.getPlayer1Score(), score.getPlayer2Score());

            assertEquals(CONN_PLAYER_1, packet.winnerId, "winnerId should be connection ID");
            assertEquals(NAME_ALICE, packet.winnerName, "winnerName should be player name");
            assertEquals(WIN_SCORE, packet.player1Score, "player1Score should be win score");
            assertEquals(0, packet.player2Score, "player2Score should be 0");
        }

        @Test
        @Tag("FR3.2")
        @DisplayName("Winner identification works with non-sequential connection IDs")
        void winnerIdentificationWithNonSequentialIds() {
            GameRoom room = createRoomWithPlayers();
            room.setPhase(GameState.Phase.PLAYING);
            room.getGameState().getScore().setPlayer2Score(WIN_SCORE);

            int winnerSlot = room.getGameState().getScore().getWinner();
            int winnerId = room.getConnectionIdForSlot(winnerSlot);
            Player winner = room.getGameState().getPlayer(winnerId);

            assertEquals(2, winnerSlot, "Winner slot should be 2");
            assertEquals(CONN_PLAYER_2, winnerId, "Winner ID should be 202");
            assertEquals(NAME_BOB, winner.getName(), "Winner name should be Bob");
        }
    }
}
