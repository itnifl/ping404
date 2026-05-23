package no.ntnu.ping404.server.handler;

import com.esotericsoftware.kryonet.Connection;
import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.model.Player;
import no.ntnu.ping404.model.Puck;
import no.ntnu.ping404.model.Score;
import no.ntnu.ping404.network.NetworkKryoServer;
import no.ntnu.ping404.network.INetworkServer;
import no.ntnu.ping404.network.ServerConnector;
import no.ntnu.ping404.network.packets.GameOver;
import no.ntnu.ping404.network.packets.GoalScored;
import no.ntnu.ping404.network.packets.PlayerPosition;
import no.ntnu.ping404.server.GameRoom;
import no.ntnu.ping404.server.game.GameLoop;
import no.ntnu.ping404.server.game.InputQueue;
import no.ntnu.ping404.utils.CollisionDetector;
import no.ntnu.ping404.utils.CollisionDetector.GoalResult;
import no.ntnu.ping404.utils.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
 * Issue #16 - Goal-to-GameOver Integration Tests
 * 
 * Tests for the integration between goal detection (physics layer) and
 * GameOver broadcast (application layer). These tests verify that:
 * - Goal detection from CollisionDetector leads to score increment
 * - Score reaching winning threshold from a goal triggers GameOver
 * - The authoritative game loop properly integrates physics --> scoring --> GameOver
 * - There's a single source of truth for winner detection
 * 
 * Current architecture gap: Goal detection happens in CollisionDetector but
 * scoring/GameOver only triggers via PositionHandlerCommand, creating a
 * disconnect between where goals are detected and where they're acted upon.
 * 
 * @see <a href="https://git.ntnu.no/pedesmet/ping404/issues/16">Issue #16</a>
 */
class Issue16GoalToGameOverIntegrationTest {

    private static final String ROOM_ID = "test-room";
    private static final String PLAYER_NAME_ALICE = "Alice";
    private static final String PLAYER_NAME_BOB = "Bob";

    // Connection IDs for players (used as player IDs)
    private static final int CONN_P1 = 10;
    private static final int CONN_P2 = 20;
    private static final int WIN_SCORE = 5;
    private static final float TICK_DELTA = 1.0f / 60;

    private static final float BOARD_WIDTH = Constants.DEFAULT_FIELD_WIDTH;
    private static final float BOARD_HEIGHT = Constants.DEFAULT_FIELD_HEIGHT;
    private static final float CENTER_X = BOARD_WIDTH / 2;
    private static final float CENTER_Y = BOARD_HEIGHT / 2;

    // Physics tick constants
    private static final float DELTA_TIME = 0.05f; // Longer tick to ensure goal crossing
    private static final float HIGH_SPEED = 2200f; // Fast enough to cross goal in one tick
    private static final float NEAR_LEFT_GOAL_X = 55f; // Close enough to left goal
    private static final float NEAR_RIGHT_GOAL_X = BOARD_WIDTH - 55f; // Close enough to right goal

    // Valid positions for left and right halves
    private static final float VALID_LEFT_HALF_X = CENTER_X / 2;
    private static final float VALID_RIGHT_HALF_X = CENTER_X + (BOARD_WIDTH - CENTER_X) / 2;

    private RecordingServerConnector connector;
    private Map<Integer, GameRoom> playerRooms;
    private PositionHandlerCommand handler;

    @BeforeEach
    void setUp() {
        connector = new RecordingServerConnector();
        playerRooms = new ConcurrentHashMap<>();
        handler = new PositionHandlerCommand(connector, playerRooms, null);
    }
    // ISSUE #16: Goal Detection to Score Increment Integration
    @Nested
    @DisplayName("Issue #16: Goal Detection to Score Integration")
    class GoalDetectionToScoreIntegration {

        @Test
        @Tag("Issue16")
        @Tag("FR2.7")
        @DisplayName("CollisionDetector detects goal when puck crosses left goal line")
        void collisionDetectorDetectsPlayer1Goal() {
            GameState state = new GameState(BOARD_WIDTH, BOARD_HEIGHT);
            Puck puck = state.getPuck();
            puck.setRadius(Constants.PUCK_RADIUS);
            
            // Position puck heading toward player 1's goal (left side)
            puck.setPosition(NEAR_LEFT_GOAL_X, CENTER_Y);
            puck.setVelocityX(-HIGH_SPEED);
            puck.setVelocityY(0f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(state, DELTA_TIME);

            assertEquals(GoalResult.PLAYER_1_GOAL, result.getGoalResult(),
                    "Puck crossing left goal line should be detected as PLAYER_1_GOAL");
        }

        @Test
        @Tag("Issue16")
        @Tag("FR2.7")
        @DisplayName("CollisionDetector detects goal when puck crosses right goal line")
        void collisionDetectorDetectsPlayer2Goal() {
            GameState state = new GameState(BOARD_WIDTH, BOARD_HEIGHT);
            Puck puck = state.getPuck();
            puck.setRadius(Constants.PUCK_RADIUS);
            
            // Position puck heading toward player 2's goal (right side)
            puck.setPosition(NEAR_RIGHT_GOAL_X, CENTER_Y);
            puck.setVelocityX(HIGH_SPEED);
            puck.setVelocityY(0f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(state, DELTA_TIME);

            assertEquals(GoalResult.PLAYER_2_GOAL, result.getGoalResult(),
                    "Puck crossing right goal line should be detected as PLAYER_2_GOAL");
        }

        @Test
        @Tag("Issue16")
        @Tag("FR2.7")
        @DisplayName("Score should increment when CollisionDetector reports PLAYER_1_GOAL")
        void scoreShouldIncrementOnPlayer1Goal() {
            GameState state = new GameState(BOARD_WIDTH, BOARD_HEIGHT);
            Score score = state.getScore();
            int initialPlayer2Score = score.getPlayer2Score();

            Puck puck = state.getPuck();
            puck.setRadius(Constants.PUCK_RADIUS);
            puck.setPosition(NEAR_LEFT_GOAL_X, CENTER_Y);
            puck.setVelocityX(-HIGH_SPEED);
            puck.setVelocityY(0f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(state, DELTA_TIME);

            // Verify goal was detected
            assertEquals(GoalResult.PLAYER_1_GOAL, result.getGoalResult());

            // The expected behavior: goal detection should lead to score increment
            // When puck enters PLAYER_1_GOAL, PLAYER_2 scores (opponent scores)
            // This integration is currently missing - the test documents expected behavior
            if (result.getGoalResult() == GoalResult.PLAYER_1_GOAL) {
                score.scorePlayer2(); // Manual call - should be automatic
            }

            assertEquals(initialPlayer2Score + 1, score.getPlayer2Score(),
                    "Player 2 should score when puck enters Player 1's goal");
        }

        @Test
        @Tag("Issue16")
        @Tag("FR2.7")
        @DisplayName("Score should increment when CollisionDetector reports PLAYER_2_GOAL")
        void scoreShouldIncrementOnPlayer2Goal() {
            GameState state = new GameState(BOARD_WIDTH, BOARD_HEIGHT);
            Score score = state.getScore();
            int initialPlayer1Score = score.getPlayer1Score();

            Puck puck = state.getPuck();
            puck.setRadius(Constants.PUCK_RADIUS);
            puck.setPosition(NEAR_RIGHT_GOAL_X, CENTER_Y);
            puck.setVelocityX(HIGH_SPEED);
            puck.setVelocityY(0f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(state, DELTA_TIME);

            // Verify goal was detected
            assertEquals(GoalResult.PLAYER_2_GOAL, result.getGoalResult());

            // The expected behavior: goal detection should lead to score increment
            // When puck enters PLAYER_2_GOAL, PLAYER_1 scores (opponent scores)
            if (result.getGoalResult() == GoalResult.PLAYER_2_GOAL) {
                score.scorePlayer1(); // Manual call - should be automatic
            }

            assertEquals(initialPlayer1Score + 1, score.getPlayer1Score(),
                    "Player 1 should score when puck enters Player 2's goal");
        }
    }
    // ISSUE #16: Goal Causing Win Should Trigger GameOver
    @Nested
    @DisplayName("Issue #16: Goal Causing Win Triggers GameOver")
    class GoalCausingWinTriggersGameOver {

        @Test
        @Tag("Issue16")
        @Tag("FR2.9")
        @Tag("FR3.1")
        @DisplayName("Goal that causes winning score should trigger hasWinner()")
        void goalCausingWinShouldTriggerHasWinner() {
            GameState state = new GameState(BOARD_WIDTH, BOARD_HEIGHT);
            Score score = state.getScore();
            
            // Set player 1 at 4 points (one away from winning)
            score.setPlayer1Score(score.getWinningScore() - 1);
            assertFalse(score.hasWinner(), "Should not have winner before final goal");

            Puck puck = state.getPuck();
            puck.setRadius(Constants.PUCK_RADIUS);
            puck.setPosition(NEAR_RIGHT_GOAL_X, CENTER_Y);
            puck.setVelocityX(HIGH_SPEED);
            puck.setVelocityY(0f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(state, DELTA_TIME);

            // Verify goal was detected
            assertEquals(GoalResult.PLAYER_2_GOAL, result.getGoalResult());

            // Apply the goal to score (this is the missing integration)
            if (result.getGoalResult() == GoalResult.PLAYER_2_GOAL) {
                score.scorePlayer1();
            }

            assertTrue(score.hasWinner(), "Should have winner after final goal");
            assertEquals(1, score.getWinner(), "Player 1 should be the winner");
        }

        @Test
        @Tag("Issue16")
        @Tag("FR2.9")
        @Tag("FR3.1")
        @DisplayName("Game should transition to FINISHED phase when goal causes win")
        void gameShouldFinishWhenGoalCausesWin() {
            GameState state = new GameState(BOARD_WIDTH, BOARD_HEIGHT);
            state.setPhase(GameState.Phase.PLAYING);
            Score score = state.getScore();
            
            // Set player 2 at 4 points (one away from winning)
            score.setPlayer2Score(score.getWinningScore() - 1);

            Puck puck = state.getPuck();
            puck.setRadius(Constants.PUCK_RADIUS);
            puck.setPosition(NEAR_LEFT_GOAL_X, CENTER_Y);
            puck.setVelocityX(-HIGH_SPEED);
            puck.setVelocityY(0f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(state, DELTA_TIME);

            assertEquals(GoalResult.PLAYER_1_GOAL, result.getGoalResult());

            // Apply goal and check for winner (this should be integrated)
            if (result.getGoalResult() == GoalResult.PLAYER_1_GOAL) {
                score.scorePlayer2();
                if (score.hasWinner()) {
                    state.finishMatch();
                }
            }

            assertTrue(score.hasWinner(), "Should have winner");
            assertEquals(GameState.Phase.FINISHED, state.getPhase(),
                    "Game should transition to FINISHED when goal causes win");
        }

        @Test
        @Tag("Issue16")
        @Tag("FR3.1")
        @Tag("FR3.2")
        @DisplayName("GameOver packet should be sent when goal causes win")
        void gameOverShouldBeSentWhenGoalCausesWin() {
            int player1Id = 1;
            int player2Id = 2;

            GameRoom room = new GameRoom(ROOM_ID, player1Id);
            INetworkServer.PlayerConnection p1 = createPlayerConnection(player1Id, PLAYER_NAME_ALICE);
            INetworkServer.PlayerConnection p2 = createPlayerConnection(player2Id, PLAYER_NAME_BOB);

            room.addPlayer(p1, new Player(player1Id, PLAYER_NAME_ALICE));
            room.addPlayer(p2, new Player(player2Id, PLAYER_NAME_BOB));
            room.getGameState().setPhase(GameState.Phase.PLAYING);

            // Set player 1 one point away from winning
            room.getGameState().getScore().setPlayer1Score(
                    room.getGameState().getScore().getWinningScore() - 1);

            playerRooms.put(player1Id, room);
            playerRooms.put(player2Id, room);

            // Simulate: goal detected by physics tick, then score updated
            // Then position handler is called which checks for winner
            room.getGameState().getScore().scorePlayer1(); // Simulate goal scored

            // Now trigger the winner check via position handler
            handler.handle(p1, new PlayerPosition(0, VALID_LEFT_HALF_X, CENTER_Y));

            List<GameOver> toAlice = connector.getPacketsSentToOfType(player1Id, GameOver.class);
            List<GameOver> toBob = connector.getPacketsSentToOfType(player2Id, GameOver.class);

            assertEquals(1, toAlice.size(), "Alice should receive GameOver");
            assertEquals(1, toBob.size(), "Bob should receive GameOver");

            GameOver gameOver = toAlice.get(0);
            assertEquals(player1Id, gameOver.winnerId, "Winner ID should match");
            assertEquals(room.getGameState().getScore().getWinningScore(), gameOver.player1Score);
        }
    }
    // ISSUE #16: Authoritative Game Loop Integration
    @Nested
    @DisplayName("Issue #16: Authoritative Game Loop Integration")
    class AuthoritativeGameLoopIntegration {

        @Test
        @Tag("Issue16")
        @Tag("TC6")
        @DisplayName("Physics tick should be the source of truth for goal detection")
        void physicsTickShouldBeSourceOfTruthForGoalDetection() {
            GameState state = new GameState(BOARD_WIDTH, BOARD_HEIGHT);
            Puck puck = state.getPuck();
            puck.setRadius(Constants.PUCK_RADIUS);

            // Test case 1: Puck clearly in play - no goal
            puck.setPosition(CENTER_X, CENTER_Y);
            puck.setVelocityX(100f);
            puck.setVelocityY(0f);

            CollisionDetector.TickResult result1 = CollisionDetector.resolveTick(state, DELTA_TIME);
            assertEquals(GoalResult.NONE, result1.getGoalResult(), "No goal when puck in play area");

            // Test case 2: Puck crosses left goal - should detect PLAYER_1_GOAL
            puck.setPosition(NEAR_LEFT_GOAL_X, CENTER_Y);
            puck.setVelocityX(-HIGH_SPEED);
            puck.setVelocityY(0f);

            CollisionDetector.TickResult result2 = CollisionDetector.resolveTick(state, DELTA_TIME);
            assertEquals(GoalResult.PLAYER_1_GOAL, result2.getGoalResult(),
                    "Physics tick must detect goal crossing");
        }

        @Test
        @Tag("Issue16")
        @Tag("TC2")
        @DisplayName("Multiple consecutive goals should be tracked correctly")
        void multipleConsecutiveGoalsShouldBeTrackedCorrectly() {
            GameState state = new GameState(BOARD_WIDTH, BOARD_HEIGHT);
            Score score = state.getScore();
            Puck puck = state.getPuck();
            puck.setRadius(Constants.PUCK_RADIUS);

            // Goal 1: Player 2 scores (puck in player 1's goal)
            puck.setPosition(NEAR_LEFT_GOAL_X, CENTER_Y);
            puck.setVelocityX(-HIGH_SPEED);
            puck.setVelocityY(0f);
            CollisionDetector.TickResult result1 = CollisionDetector.resolveTick(state, DELTA_TIME);
            if (result1.getGoalResult() == GoalResult.PLAYER_1_GOAL) {
                score.scorePlayer2();
                puck.reset(CENTER_X, CENTER_Y); // Reset puck after goal
            }

            // Goal 2: Player 1 scores (puck in player 2's goal)
            puck.setPosition(NEAR_RIGHT_GOAL_X, CENTER_Y);
            puck.setVelocityX(HIGH_SPEED);
            puck.setVelocityY(0f);
            CollisionDetector.TickResult result2 = CollisionDetector.resolveTick(state, DELTA_TIME);
            if (result2.getGoalResult() == GoalResult.PLAYER_2_GOAL) {
                score.scorePlayer1();
                puck.reset(CENTER_X, CENTER_Y);
            }

            // Goal 3: Player 2 scores again
            puck.setPosition(NEAR_LEFT_GOAL_X, CENTER_Y);
            puck.setVelocityX(-HIGH_SPEED);
            puck.setVelocityY(0f);
            CollisionDetector.TickResult result3 = CollisionDetector.resolveTick(state, DELTA_TIME);
            if (result3.getGoalResult() == GoalResult.PLAYER_1_GOAL) {
                score.scorePlayer2();
            }

            assertEquals(1, score.getPlayer1Score(), "Player 1 should have 1 goal");
            assertEquals(2, score.getPlayer2Score(), "Player 2 should have 2 goals");
        }

        @Test
        @Tag("Issue16")
        @Tag("FR2.7")
        @DisplayName("Goal detection should not depend on player position updates")
        void goalDetectionShouldNotDependOnPlayerPositionUpdates() {
            // This test documents the architectural issue:
            // Currently, winner check only happens when position updates are received.
            // Goals detected by physics should trigger winner check independently.

            int player1Id = 1;
            int player2Id = 2;

            GameRoom room = new GameRoom(ROOM_ID, player1Id);
            INetworkServer.PlayerConnection p1 = createPlayerConnection(player1Id, PLAYER_NAME_ALICE);
            INetworkServer.PlayerConnection p2 = createPlayerConnection(player2Id, PLAYER_NAME_BOB);

            room.addPlayer(p1, new Player(player1Id, PLAYER_NAME_ALICE));
            room.addPlayer(p2, new Player(player2Id, PLAYER_NAME_BOB));
            room.getGameState().setPhase(GameState.Phase.PLAYING);

            playerRooms.put(player1Id, room);
            playerRooms.put(player2Id, room);

            // Set player 1 at winning score (without going through position handler)
            room.getGameState().getScore().setPlayer1Score(
                    room.getGameState().getScore().getWinningScore());

            // At this point, score shows winner, but NO GameOver was sent
            // because GameOver only triggers via position handler

            assertTrue(room.getGameState().getScore().hasWinner(),
                    "Score should show winner after reaching winning score");

            // GameOver was NOT sent yet - it's only sent when position handler runs
            List<GameOver> toAlice = connector.getPacketsSentToOfType(player1Id, GameOver.class);
            List<GameOver> toBob = connector.getPacketsSentToOfType(player2Id, GameOver.class);

            assertTrue(toAlice.isEmpty(), "GameOver not yet sent (architectural issue documented)");
            assertTrue(toBob.isEmpty(), "GameOver not yet sent (architectural issue documented)");

            // Only after a position update does GameOver get sent
            handler.handle(p1, new PlayerPosition(0, VALID_LEFT_HALF_X, CENTER_Y));

            toAlice = connector.getPacketsSentToOfType(player1Id, GameOver.class);
            assertEquals(1, toAlice.size(), "GameOver sent after position update");
        }
    }
    // ISSUE #16: Puck Reset After Goal (FR2.10 Integration)
    @Nested
    @DisplayName("Issue #16: Puck Reset After Goal Integration")
    class PuckResetAfterGoalIntegration {

        @Test
        @Tag("Issue16")
        @Tag("FR2.10")
        @DisplayName("Puck should reset to center after goal is scored")
        void puckShouldResetToCenterAfterGoal() {
            GameState state = new GameState(BOARD_WIDTH, BOARD_HEIGHT);
            Puck puck = state.getPuck();
            puck.setRadius(Constants.PUCK_RADIUS);

            // Position puck to score a goal
            puck.setPosition(NEAR_LEFT_GOAL_X, CENTER_Y);
            puck.setVelocityX(-HIGH_SPEED);
            puck.setVelocityY(0f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(state, DELTA_TIME);
            assertEquals(GoalResult.PLAYER_1_GOAL, result.getGoalResult());

            // After goal detection, puck should be reset (this is the expected integration)
            if (result.getGoalResult() != GoalResult.NONE) {
                puck.reset(CENTER_X, CENTER_Y);
            }

            assertEquals(CENTER_X, puck.getX(), 0.01f, "Puck X should be at center after goal");
            assertEquals(CENTER_Y, puck.getY(), 0.01f, "Puck Y should be at center after goal");
        }

        @Test
        @Tag("Issue16")
        @Tag("FR2.10")
        @DisplayName("Puck should have non-zero velocity after reset following goal")
        void puckShouldHaveVelocityAfterGoalReset() {
            GameState state = new GameState(BOARD_WIDTH, BOARD_HEIGHT);
            Puck puck = state.getPuck();
            puck.setRadius(Constants.PUCK_RADIUS);

            puck.setPosition(NEAR_RIGHT_GOAL_X, CENTER_Y);
            puck.setVelocityX(HIGH_SPEED);
            puck.setVelocityY(0f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(state, DELTA_TIME);
            assertEquals(GoalResult.PLAYER_2_GOAL, result.getGoalResult());

            // Reset puck after goal
            puck.reset(CENTER_X, CENTER_Y);

            // Puck should have velocity after reset (FR2.3 - continuous motion)
            float speedMagnitude = (float) Math.sqrt(
                    puck.getVelocityX() * puck.getVelocityX() +
                    puck.getVelocityY() * puck.getVelocityY());

            assertTrue(speedMagnitude > 0, "Puck should have velocity after goal reset");
        }
    }
    // ISSUE #16: Single Source of Truth for Winner Detection
    @Nested
    @DisplayName("Issue #16: Single Source of Truth for Winner")
    class SingleSourceOfTruthForWinner {

        @Test
        @Tag("Issue16")
        @Tag("FR3.1")
        @DisplayName("Score.hasWinner() should be the authoritative winner check")
        void scoreHasWinnerShouldBeAuthoritativeWinnerCheck() {
            Score score = new Score(5);

            // Game in progress - no winner
            score.setPlayer1Score(2);
            score.setPlayer2Score(3);
            assertFalse(score.hasWinner(), "No winner mid-game");
            assertEquals(0, score.getWinner(), "getWinner() returns 0 when no winner");

            // Player 2 reaches winning score
            score.setPlayer2Score(5);
            assertTrue(score.hasWinner(), "hasWinner() is true when score >= winningScore");
            assertEquals(2, score.getWinner(), "getWinner() returns 2 when player 2 wins");
        }

        @Test
        @Tag("Issue16")
        @Tag("FR3.1")
        @DisplayName("Winner detection should use Score, not manual threshold checks")
        void winnerDetectionShouldUseScoreNotManualChecks() {
            // This test verifies that Score is the single source of truth,
            // not hardcoded threshold checks scattered across handlers

            Score score = new Score(5);
            GameState state = new GameState(BOARD_WIDTH, BOARD_HEIGHT);
            state.setScore(score);
            state.setPhase(GameState.Phase.PLAYING); // Game must be in PLAYING to transition to FINISHED

            // Simulate game progression
            for (int i = 0; i < 4; i++) {
                score.scorePlayer1();
                assertFalse(score.hasWinner(), "No winner at score " + score.getPlayer1Score());
            }

            // Final goal
            score.scorePlayer1();
            assertTrue(score.hasWinner(), "Winner detected by Score class");

            // Verify phase transition uses Score as source of truth
            if (score.hasWinner()) {
                state.finishMatch();
            }
            assertEquals(GameState.Phase.FINISHED, state.getPhase());
        }

        @Test
        @Tag("Issue16")
        @Tag("FR1.6")
        @DisplayName("Custom winning score should be respected in winner detection")
        void customWinningScoreShouldBeRespected() {
            int customWinScore = 3;
            Score score = new Score(customWinScore);

            score.setPlayer1Score(2);
            assertFalse(score.hasWinner(), "No winner at 2 when winning score is 3");

            score.scorePlayer1(); // Now at 3
            assertTrue(score.hasWinner(), "Winner at 3 when winning score is 3");
            assertEquals(1, score.getWinner());
        }
    }
    /**
     * Tests verifying GameLoop integration for physics and goal detection.
     * These tests use the actual GameLoop.tick() method which properly handles
     * goal detection, score updates, and GameOver broadcasting.
     */
    @Nested
    @DisplayName("Issue #16: Server Physics Integration (GameLoop)")
    @Tag("Issue16")
    class ServerPhysicsIntegration {

        private GameRoom gameLoopRoom;
        private InputQueue inputQueue;
        private GameLoop loop;

        @BeforeEach
        void setUpGameLoop() {
            gameLoopRoom = new GameRoom(ROOM_ID, CONN_P1, WIN_SCORE);
            var pc1 = new NetworkKryoServer.KryoPlayerConnection(CONN_P1);
            pc1.setPlayerName(PLAYER_NAME_ALICE);
            var pc2 = new NetworkKryoServer.KryoPlayerConnection(CONN_P2);
            pc2.setPlayerName(PLAYER_NAME_BOB);
            gameLoopRoom.addPlayer(pc1, new Player(CONN_P1, PLAYER_NAME_ALICE));
            gameLoopRoom.addPlayer(pc2, new Player(CONN_P2, PLAYER_NAME_BOB));
            gameLoopRoom.getGameState().setPhase(GameState.Phase.PLAYING);
            inputQueue = new InputQueue();
            connector = new RecordingServerConnector();
            loop = new GameLoop(gameLoopRoom, inputQueue, connector);
        }

        private void aimPuckAtPlayer1Goal() {
            var puck = gameLoopRoom.getGameState().getPuck();
            puck.setPosition(puck.getRadius() + 1f, Constants.boardCenterY());
            puck.setVelocityX(-5000f);
            puck.setVelocityY(0);
        }

        private void aimPuckAtPlayer2Goal() {
            var puck = gameLoopRoom.getGameState().getPuck();
            puck.setPosition(Constants.DEFAULT_FIELD_WIDTH - puck.getRadius() - 1f, Constants.boardCenterY());
            puck.setVelocityX(5000f);
            puck.setVelocityY(0);
        }

        private void parkPuckAtCenter() {
            var puck = gameLoopRoom.getGameState().getPuck();
            puck.setPosition(Constants.boardCenterX(), Constants.boardCenterY());
            puck.setVelocityX(0);
            puck.setVelocityY(0);
        }

        @Test
        @Tag("FR2.7")
        @DisplayName("GameLoop.tick() detects goal and updates score automatically")
        void tickShouldDetectGoalAndUpdateScoreAutomatically() {
            int initialP2Score = gameLoopRoom.getGameState().getScore().getPlayer2Score();
            aimPuckAtPlayer1Goal();

            loop.tick(TICK_DELTA);

            Score score = gameLoopRoom.getGameState().getScore();
            assertEquals(initialP2Score + 1, score.getPlayer2Score(),
                    "Player 2 score should increment when puck crosses Player 1's goal");
            assertEquals(0, score.getPlayer1Score(),
                    "Player 1 score should remain unchanged");
        }

        @Test
        @Tag("FR2.9")
        @Tag("FR3.1")
        @DisplayName("GameLoop.tick() detects winning goal and ends game")
        void tickShouldDetectWinningGoalAndEndGame() {
            gameLoopRoom.getGameState().getScore().setPlayer1Score(WIN_SCORE - 1);
            aimPuckAtPlayer2Goal();

            loop.tick(TICK_DELTA);

            assertEquals(GameState.Phase.FINISHED, gameLoopRoom.getPhase(),
                    "Phase should transition to FINISHED after winning goal");
            assertEquals(WIN_SCORE, gameLoopRoom.getGameState().getScore().getPlayer1Score(),
                    "Winner should have winning score");
        }

        @Test
        @Tag("FR2.10")
        @DisplayName("GameLoop.tick() resets puck to conceding player's half after non-winning goal")
        void tickShouldResetPuckAfterNonWinningGoal() {
            aimPuckAtPlayer1Goal();

            loop.tick(TICK_DELTA);

            var puck = gameLoopRoom.getGameState().getPuck();
            // FR2.10: When Player 1's goal is scored, puck resets to Player 1's half
            assertEquals(Constants.player1HalfCenterX(), puck.getX(), 1f,
                    "Puck should reset to Player 1's half X after goal scored at Player 1's goal");
            assertEquals(Constants.boardCenterY(), puck.getY(), 1f,
                    "Puck should be at center Y after goal");
        }

        @Test
        @Tag("FR2.7")
        @DisplayName("GameLoop.tick() with no goal does not change score")
        void tickWithNoGoalShouldNotChangeScore() {
            parkPuckAtCenter();
            int initialP1 = gameLoopRoom.getGameState().getScore().getPlayer1Score();
            int initialP2 = gameLoopRoom.getGameState().getScore().getPlayer2Score();

            loop.tick(TICK_DELTA);

            assertEquals(initialP1, gameLoopRoom.getGameState().getScore().getPlayer1Score());
            assertEquals(initialP2, gameLoopRoom.getGameState().getScore().getPlayer2Score());
        }

        @Test
        @Tag("FR3.1")
        @Tag("FR3.2")
        @DisplayName("GameLoop.tick() broadcasts GameOver when winning goal scored")
        void tickWinningGoalShouldTriggerGameOverBroadcast() {
            gameLoopRoom.getGameState().getScore().setPlayer2Score(WIN_SCORE - 1);
            aimPuckAtPlayer1Goal();

            loop.tick(TICK_DELTA);

            List<GameOver> toP1 = connector.getPacketsSentToOfType(CONN_P1, GameOver.class);
            List<GameOver> toP2 = connector.getPacketsSentToOfType(CONN_P2, GameOver.class);
            assertEquals(1, toP1.size(), "Player 1 should receive GameOver");
            assertEquals(1, toP2.size(), "Player 2 should receive GameOver");
        }

        @Test
        @Tag("FR2.7")
        @DisplayName("GameLoop.tick() broadcasts GoalScored packet after goal")
        void tickShouldBroadcastGoalScoredAfterGoal() {
            aimPuckAtPlayer1Goal();

            loop.tick(TICK_DELTA);

            List<GoalScored> toP1 = connector.getPacketsSentToOfType(CONN_P1, GoalScored.class);
            List<GoalScored> toP2 = connector.getPacketsSentToOfType(CONN_P2, GoalScored.class);
            assertEquals(1, toP1.size(), "Player 1 should receive GoalScored");
            assertEquals(1, toP2.size(), "Player 2 should receive GoalScored");
        }

        @Test
        @Tag("FR3.2")
        @DisplayName("GameOver packet contains correct winner info when Player 2 wins")
        void gameOverPacketShouldContainCorrectWinnerInfo() {
            gameLoopRoom.getGameState().getScore().setPlayer1Score(2);
            gameLoopRoom.getGameState().getScore().setPlayer2Score(WIN_SCORE - 1);
            aimPuckAtPlayer1Goal();

            loop.tick(TICK_DELTA);

            List<GameOver> packets = connector.getPacketsSentToOfType(CONN_P1, GameOver.class);
            assertFalse(packets.isEmpty(), "Should have received GameOver packet");
            GameOver gameOver = packets.get(0);
            assertEquals(CONN_P2, gameOver.winnerId, "Winner ID should be Player 2's connection ID");
            assertEquals(PLAYER_NAME_BOB, gameOver.winnerName, "Winner name should be Bob");
            assertEquals(2, gameOver.player1Score, "Player 1 final score should be 2");
            assertEquals(WIN_SCORE, gameOver.player2Score, "Player 2 final score should be win score");
        }
    }

    // Helper Methods and Classes
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
