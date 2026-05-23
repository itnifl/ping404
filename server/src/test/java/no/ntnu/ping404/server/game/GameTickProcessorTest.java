package no.ntnu.ping404.server.game;

import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.model.GameState.Phase;
import no.ntnu.ping404.model.Player;
import no.ntnu.ping404.model.Puck;
import no.ntnu.ping404.model.Score;
import no.ntnu.ping404.utils.CollisionDetector.GoalResult;
import no.ntnu.ping404.utils.Constants;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GameTickProcessor} - the server-side game tick orchestrator (issue #18).
 *
 * <p>Verifies that the orchestrator correctly sequences the four tick steps:</p>
 * <ol>
 *   <li>{@code updatePuckPosition} - position += velocity * deltaTime</li>
 *   <li>{@code applyRules} - delegates to Core collision detection</li>
 *   <li>{@code updateScore} - increments score and resets puck on goal</li>
 *   <li>{@code checkGameOver} - sets phase to FINISHED when max score reached</li>
 * </ol>
 *
 * <p>These tests focus on orchestration correctness, not physics.
 * Physics/collision accuracy is tested in {@code CollisionDetectorTest}.</p>
 */
class GameTickProcessorTest {

    private static final int WIN_SCORE = 5;
    private static final float TICK_DELTA = 1.0f / 60;

    private GameState gameState;
    private GameTickProcessor processor;

    @BeforeEach
    void setUp() {
        gameState = new GameState(WIN_SCORE);
        // Add two players so CollisionDetector has paddles to check
        Player p1 = new Player(1, "Alice");
        p1.setX(Constants.PADDLE_MARGIN + Constants.PADDLE_WIDTH / 2);
        p1.setY(Constants.boardCenterY());
        Player p2 = new Player(2, "Bob");
        p2.setX(Constants.DEFAULT_FIELD_WIDTH - Constants.PADDLE_MARGIN - Constants.PADDLE_WIDTH / 2);
        p2.setY(Constants.boardCenterY());
        gameState.addPlayer(p1);
        gameState.addPlayer(p2);
        gameState.startMatch();

        processor = new GameTickProcessor(gameState);
    }

    /** Parks the puck at center with zero velocity (no movement, no goals). */
    private void parkPuckAtCenter() {
        Puck puck = gameState.getPuck();
        puck.setPosition(Constants.boardCenterX(), Constants.boardCenterY());
        puck.setVelocityX(0);
        puck.setVelocityY(0);
    }

    /** Aims the puck to cross Player 1's goal line (left side --> Player 2 scores). */
    private void aimPuckAtPlayer1Goal() {
        Puck puck = gameState.getPuck();
        puck.setPosition(puck.getRadius() + 1f, Constants.boardCenterY());
        puck.setVelocityX(-5000f);
        puck.setVelocityY(0);
    }

    /** Aims the puck to cross Player 2's goal line (right side --> Player 1 scores). */
    private void aimPuckAtPlayer2Goal() {
        Puck puck = gameState.getPuck();
        puck.setPosition(Constants.DEFAULT_FIELD_WIDTH - puck.getRadius() - 1f, Constants.boardCenterY());
        puck.setVelocityX(5000f);
        puck.setVelocityY(0);
    }

    // ==================================================================
    // 1. updatePuckPosition - position += velocity * deltaTime
    // ==================================================================

    @Nested
    @DisplayName("Step 1: updatePuckPosition")
    class UpdatePuckPositionTests {

        @Test
        @DisplayName("Puck position advances by velocity * deltaTime")
        void puckPositionAdvancesByVelocity() {
            Puck puck = gameState.getPuck();
            puck.setPosition(100f, 200f);
            puck.setVelocityX(300f);
            puck.setVelocityY(-150f);

            float dt = 0.1f;
            processor.updatePuckPosition(dt);

            assertEquals(130f, puck.getX(), 0.01f, "X should advance by vX * dt");
            assertEquals(185f, puck.getY(), 0.01f, "Y should advance by vY * dt");
        }

        @Test
        @DisplayName("Zero velocity means no position change")
        void zeroVelocityNoMovement() {
            Puck puck = gameState.getPuck();
            puck.setPosition(200f, 200f);
            puck.setVelocityX(0);
            puck.setVelocityY(0);

            processor.updatePuckPosition(TICK_DELTA);

            assertEquals(200f, puck.getX(), 0.001f);
            assertEquals(200f, puck.getY(), 0.001f);
        }

        @Test
        @DisplayName("Zero deltaTime means no position change")
        void zeroDeltaTimeNoMovement() {
            Puck puck = gameState.getPuck();
            puck.setPosition(200f, 200f);
            puck.setVelocityX(500f);
            puck.setVelocityY(500f);

            processor.updatePuckPosition(0f);

            assertEquals(200f, puck.getX(), 0.001f);
            assertEquals(200f, puck.getY(), 0.001f);
        }
    }

    // ==================================================================
    // 2. applyRules - delegates to Core CollisionDetector
    // ==================================================================

    @Nested
    @DisplayName("Step 2: applyRules")
    class ApplyRulesTests {

        @Test
        @DisplayName("Returns NONE when puck is at center with no velocity")
        void returnsNoneWhenNoCollision() {
            parkPuckAtCenter();

            GoalResult result = processor.applyRules(TICK_DELTA);

            assertEquals(GoalResult.NONE, result);
        }

        @Test
        @DisplayName("Returns PLAYER_1_GOAL when puck crosses left goal line")
        void returnsPlayer1GoalOnLeftCross() {
            aimPuckAtPlayer1Goal();

            GoalResult result = processor.applyRules(TICK_DELTA);

            assertEquals(GoalResult.PLAYER_1_GOAL, result);
        }

        @Test
        @DisplayName("Returns PLAYER_2_GOAL when puck crosses right goal line")
        void returnsPlayer2GoalOnRightCross() {
            aimPuckAtPlayer2Goal();

            GoalResult result = processor.applyRules(TICK_DELTA);

            assertEquals(GoalResult.PLAYER_2_GOAL, result);
        }

        @Test
        @DisplayName("Wall collision reverses puck velocity (top/bottom)")
        void wallCollisionReversesVelocity() {
            Puck puck = gameState.getPuck();
            puck.setPosition(Constants.boardCenterX(), puck.getRadius() + 0.5f);
            puck.setVelocityX(0);
            puck.setVelocityY(-500f);

            processor.applyRules(TICK_DELTA);

            assertTrue(puck.getVelocityY() > 0, "Y velocity should be positive after bottom wall bounce");
        }
    }

    // ==================================================================
    // 3. updateScore - increment score and reset puck on goal
    // ==================================================================

    @Nested
    @DisplayName("Step 3: updateScore")
    class UpdateScoreTests {

        @Test
        @DisplayName("PLAYER_1_GOAL increments Player 2 score")
        void player1GoalIncrementsPlayer2() {
            processor.updateScore(GoalResult.PLAYER_1_GOAL);

            Score score = gameState.getScore();
            assertEquals(0, score.getPlayer1Score());
            assertEquals(1, score.getPlayer2Score());
        }

        @Test
        @DisplayName("PLAYER_2_GOAL increments Player 1 score")
        void player2GoalIncrementsPlayer1() {
            processor.updateScore(GoalResult.PLAYER_2_GOAL);

            Score score = gameState.getScore();
            assertEquals(1, score.getPlayer1Score());
            assertEquals(0, score.getPlayer2Score());
        }

        @Test
        @DisplayName("NONE does not change score")
        void noneDoesNotChangeScore() {
            processor.updateScore(GoalResult.NONE);

            Score score = gameState.getScore();
            assertEquals(0, score.getPlayer1Score());
            assertEquals(0, score.getPlayer2Score());
        }

        @Test
        @DisplayName("Puck is stopped at center after goal")
        void puckStoppedAtCenterAfterGoal() {
            Puck puck = gameState.getPuck();
            puck.setPosition(50f, 50f);
            puck.setVelocityX(300f);
            puck.setVelocityY(-200f);

            processor.updateScore(GoalResult.PLAYER_1_GOAL);

            assertEquals(Constants.boardCenterX(), puck.getX(), 0.01f, "Puck X should be at center");
            assertEquals(Constants.boardCenterY(), puck.getY(), 0.01f, "Puck Y should be at center");
            assertEquals(0f, puck.getVelocityX(), 0.001f, "Puck vX should be 0");
            assertEquals(0f, puck.getVelocityY(), 0.001f, "Puck vY should be 0");
        }

        @Test
        @DisplayName("Puck is NOT reset when no goal scored")
        void puckNotResetWhenNoGoal() {
            Puck puck = gameState.getPuck();
            puck.setPosition(100f, 200f);
            puck.setVelocityX(300f);
            puck.setVelocityY(-150f);

            processor.updateScore(GoalResult.NONE);

            assertEquals(100f, puck.getX(), 0.001f);
            assertEquals(200f, puck.getY(), 0.001f);
            assertEquals(300f, puck.getVelocityX(), 0.001f);
            assertEquals(-150f, puck.getVelocityY(), 0.001f);
        }

        @Test
        @DisplayName("Multiple goals accumulate score correctly")
        void multipleGoalsAccumulate() {
            processor.updateScore(GoalResult.PLAYER_1_GOAL);
            processor.updateScore(GoalResult.PLAYER_1_GOAL);
            processor.updateScore(GoalResult.PLAYER_2_GOAL);

            Score score = gameState.getScore();
            assertEquals(1, score.getPlayer1Score());
            assertEquals(2, score.getPlayer2Score());
        }
    }

    // ==================================================================
    // 4. checkGameOver - set phase to FINISHED
    // ==================================================================

    @Nested
    @DisplayName("Step 4: checkGameOver")
    class CheckGameOverTests {

        @Test
        @DisplayName("Phase stays PLAYING when no winner")
        void phaseStaysPlayingWhenNoWinner() {
            gameState.getScore().setPlayer1Score(0);
            gameState.getScore().setPlayer2Score(0);

            processor.checkGameOver();

            assertEquals(Phase.PLAYING, gameState.getPhase());
        }

        @Test
        @DisplayName("Phase transitions to FINISHED when Player 1 reaches win score")
        void finishedWhenPlayer1Wins() {
            gameState.getScore().setPlayer1Score(WIN_SCORE);

            processor.checkGameOver();

            assertEquals(Phase.FINISHED, gameState.getPhase());
        }

        @Test
        @DisplayName("Phase transitions to FINISHED when Player 2 reaches win score")
        void finishedWhenPlayer2Wins() {
            gameState.getScore().setPlayer2Score(WIN_SCORE);

            processor.checkGameOver();

            assertEquals(Phase.FINISHED, gameState.getPhase());
        }

        @Test
        @DisplayName("Phase stays PLAYING when score is one below winning")
        void staysPlayingBelowWinScore() {
            gameState.getScore().setPlayer1Score(WIN_SCORE - 1);
            gameState.getScore().setPlayer2Score(WIN_SCORE - 1);

            processor.checkGameOver();

            assertEquals(Phase.PLAYING, gameState.getPhase());
        }

        @Test
        @DisplayName("checkGameOver is idempotent - calling twice stays FINISHED")
        void idempotentWhenAlreadyFinished() {
            gameState.getScore().setPlayer1Score(WIN_SCORE);

            processor.checkGameOver();
            processor.checkGameOver();

            assertEquals(Phase.FINISHED, gameState.getPhase());
        }
    }

    // ==================================================================
    // 5. processTick - full orchestration integration
    // ==================================================================

    @Nested
    @DisplayName("processTick - full tick orchestration")
    class ProcessTickTests {

        @Test
        @DisplayName("Returns NONE when puck is stationary at center")
        void returnsNoneWhenStationary() {
            parkPuckAtCenter();

            GoalResult result = processor.processTick(TICK_DELTA);

            assertEquals(GoalResult.NONE, result);
        }

        @Test
        @DisplayName("Full tick: goal --> score incremented --> puck reset")
        void fullTickGoalScoresAndResets() {
            aimPuckAtPlayer1Goal();

            GoalResult result = processor.processTick(TICK_DELTA);

            assertEquals(GoalResult.PLAYER_1_GOAL, result);
            assertEquals(1, gameState.getScore().getPlayer2Score(), "Player 2 should have scored");
            assertEquals(0f, gameState.getPuck().getVelocityX(), 0.001f, "Puck should be stopped");
        }

        @Test
        @DisplayName("Full tick: winning goal --> score incremented --> phase FINISHED")
        void fullTickWinningGoalEndsGame() {
            gameState.getScore().setPlayer2Score(WIN_SCORE - 1);
            aimPuckAtPlayer1Goal();

            processor.processTick(TICK_DELTA);

            assertEquals(WIN_SCORE, gameState.getScore().getPlayer2Score());
            assertEquals(Phase.FINISHED, gameState.getPhase());
        }

        @Test
        @DisplayName("Full tick: non-winning goal keeps phase PLAYING")
        void fullTickNonWinningGoalKeepsPlaying() {
            aimPuckAtPlayer1Goal();

            processor.processTick(TICK_DELTA);

            assertEquals(Phase.PLAYING, gameState.getPhase());
        }

        @Test
        @DisplayName("getGameState returns the state managed by the processor")
        void getGameStateReturnsManagedState() {
            assertSame(gameState, processor.getGameState());
        }
    }
}
