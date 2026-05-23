package no.ntnu.ping404.utils;

import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.model.Puck;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for goal scoring detection in CollisionDetector.
 * 
 * <p>These tests verify that goals are correctly detected when the puck
 * crosses the goal line within the goal opening (vertical range defined
 * by GOAL_WIDTH centered on the field).</p>
 * 
 * <p>Goal detection requires the puck to fully cross the line (puckX + radius <= 0
 * for left goal, puckX - radius >= fieldWidth for right).</p>
 */
@Tag("FR2.7")
class GoalScoringTest {

    private static final float DELTA_TIME = 0.016f; // ~60Hz tick
    private static final float LONG_DELTA = 0.05f;  // Longer tick for high-speed tests
    
    private GameState state;
    private Puck puck;
    private float fieldWidth;
    private float fieldHeight;
    private float goalTop;
    private float goalBottom;
    private float goalCenterY;

    @BeforeEach
    void setUp() {
        state = new GameState(Constants.DEFAULT_FIELD_WIDTH, Constants.DEFAULT_FIELD_HEIGHT);
        puck = state.getPuck();
        puck.setRadius(Constants.PUCK_RADIUS);
        
        fieldWidth = Constants.DEFAULT_FIELD_WIDTH;
        fieldHeight = Constants.DEFAULT_FIELD_HEIGHT;
        goalCenterY = fieldHeight / 2f;
        goalTop = goalCenterY + Constants.GOAL_WIDTH / 2f;
        goalBottom = goalCenterY - Constants.GOAL_WIDTH / 2f;
    }

    @Nested
    @DisplayName("Goal Opening Geometry")
    class GoalOpeningGeometry {

        @Test
        @DisplayName("Goal opening uses GOAL_WIDTH, not GOAL_HEIGHT")
        void goalOpeningUsesGoalWidth() {
            float expectedGoalTop = (fieldHeight / 2f) + (Constants.GOAL_WIDTH / 2f);
            float expectedGoalBottom = (fieldHeight / 2f) - (Constants.GOAL_WIDTH / 2f);
            
            assertEquals(expectedGoalTop, goalTop, 0.01f, 
                "Goal top should be center + GOAL_WIDTH/2");
            assertEquals(expectedGoalBottom, goalBottom, 0.01f, 
                "Goal bottom should be center - GOAL_WIDTH/2");
            assertEquals(Constants.GOAL_WIDTH, goalTop - goalBottom, 0.01f,
                "Goal opening height should equal GOAL_WIDTH (120px)");
        }

        @Test
        @DisplayName("Goal opening spans 120px centered vertically")
        void goalOpeningIs120PixelsCentered() {
            assertEquals(120f, Constants.GOAL_WIDTH, "GOAL_WIDTH should be 120");
            assertEquals(240f, goalCenterY, "Field center Y should be 240");
            assertEquals(300f, goalTop, "Goal top should be at Y=300");
            assertEquals(180f, goalBottom, "Goal bottom should be at Y=180");
        }

        @Test
        @DisplayName("Constants.goalTop returns correct value")
        void constantsGoalTopReturnsCorrectValue() {
            float actual = Constants.goalTop(fieldHeight);
            assertEquals(300f, actual, 0.01f, 
                "Constants.goalTop should return center + GOAL_WIDTH/2 = 300");
        }

        @Test
        @DisplayName("Constants.goalBottom returns correct value")
        void constantsGoalBottomReturnsCorrectValue() {
            float actual = Constants.goalBottom(fieldHeight);
            assertEquals(180f, actual, 0.01f, 
                "Constants.goalBottom should return center - GOAL_WIDTH/2 = 180");
        }
    }

    @Nested
    @DisplayName("Left Goal (Player 1 Side)")
    class LeftGoalScoring {

        @Test
        @DisplayName("Puck entering left goal at center scores for Player 2")
        void puckAtCenterScoresLeftGoal() {
            // Position puck close to goal line, high velocity to cross completely
            puck.setPosition(20f, goalCenterY);
            puck.setVelocityX(-1500f);
            puck.setVelocityY(0f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(
                puck, state, List.of(), LONG_DELTA);

            assertEquals(CollisionDetector.GoalResult.PLAYER_1_GOAL, result.getGoalResult(),
                "Puck crossing left goal line at center should score PLAYER_1_GOAL");
        }

        @Test
        @DisplayName("Puck entering left goal at top edge scores")
        void puckAtTopEdgeScoresLeftGoal() {
            float nearTopEdge = goalTop - puck.getRadius() - 5f;
            puck.setPosition(20f, nearTopEdge);
            puck.setVelocityX(-1500f);
            puck.setVelocityY(0f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(
                puck, state, List.of(), LONG_DELTA);

            assertEquals(CollisionDetector.GoalResult.PLAYER_1_GOAL, result.getGoalResult(),
                "Puck near top of goal opening should still score");
        }

        @Test
        @DisplayName("Puck entering left goal at bottom edge scores")
        void puckAtBottomEdgeScoresLeftGoal() {
            float nearBottomEdge = goalBottom + puck.getRadius() + 5f;
            puck.setPosition(20f, nearBottomEdge);
            puck.setVelocityX(-1500f);
            puck.setVelocityY(0f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(
                puck, state, List.of(), LONG_DELTA);

            assertEquals(CollisionDetector.GoalResult.PLAYER_1_GOAL, result.getGoalResult(),
                "Puck near bottom of goal opening should still score");
        }

        @Test
        @DisplayName("Puck above goal opening bounces off wall")
        void puckAboveGoalDoesNotScore() {
            float aboveGoal = goalTop + 20f;
            puck.setPosition(20f, aboveGoal);
            puck.setVelocityX(-1500f);
            puck.setVelocityY(0f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(
                puck, state, List.of(), LONG_DELTA);

            assertEquals(CollisionDetector.GoalResult.NONE, result.getGoalResult(),
                "Puck above goal opening should not score");
            assertTrue(result.hasWallCollision(),
                "Puck above goal should hit side wall");
        }

        @Test
        @DisplayName("Puck below goal opening bounces off wall")
        void puckBelowGoalDoesNotScore() {
            float belowGoal = goalBottom - 20f;
            puck.setPosition(20f, belowGoal);
            puck.setVelocityX(-1500f);
            puck.setVelocityY(0f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(
                puck, state, List.of(), LONG_DELTA);

            assertEquals(CollisionDetector.GoalResult.NONE, result.getGoalResult(),
                "Puck below goal opening should not score");
            assertTrue(result.hasWallCollision(),
                "Puck below goal should hit side wall");
        }
    }

    @Nested
    @DisplayName("Right Goal (Player 2 Side)")
    class RightGoalScoring {

        @Test
        @DisplayName("Puck entering right goal at center scores for Player 1")
        void puckAtCenterScoresRightGoal() {
            puck.setPosition(fieldWidth - 20f, goalCenterY);
            puck.setVelocityX(1500f);
            puck.setVelocityY(0f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(
                puck, state, List.of(), LONG_DELTA);

            assertEquals(CollisionDetector.GoalResult.PLAYER_2_GOAL, result.getGoalResult(),
                "Puck crossing right goal line at center should score PLAYER_2_GOAL");
        }

        @Test
        @DisplayName("Puck entering right goal at top edge scores")
        void puckAtTopEdgeScoresRightGoal() {
            float nearTopEdge = goalTop - puck.getRadius() - 5f;
            puck.setPosition(fieldWidth - 20f, nearTopEdge);
            puck.setVelocityX(1500f);
            puck.setVelocityY(0f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(
                puck, state, List.of(), LONG_DELTA);

            assertEquals(CollisionDetector.GoalResult.PLAYER_2_GOAL, result.getGoalResult(),
                "Puck near top of right goal opening should still score");
        }

        @Test
        @DisplayName("Puck entering right goal at bottom edge scores")
        void puckAtBottomEdgeScoresRightGoal() {
            float nearBottomEdge = goalBottom + puck.getRadius() + 5f;
            puck.setPosition(fieldWidth - 20f, nearBottomEdge);
            puck.setVelocityX(1500f);
            puck.setVelocityY(0f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(
                puck, state, List.of(), LONG_DELTA);

            assertEquals(CollisionDetector.GoalResult.PLAYER_2_GOAL, result.getGoalResult(),
                "Puck near bottom of right goal opening should still score");
        }

        @Test
        @DisplayName("Puck above right goal opening bounces off wall")
        void puckAboveRightGoalDoesNotScore() {
            float aboveGoal = goalTop + 20f;
            puck.setPosition(fieldWidth - 20f, aboveGoal);
            puck.setVelocityX(1500f);
            puck.setVelocityY(0f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(
                puck, state, List.of(), LONG_DELTA);

            assertEquals(CollisionDetector.GoalResult.NONE, result.getGoalResult(),
                "Puck above right goal opening should not score");
        }
    }

    @Nested
    @DisplayName("Diagonal Goal Entry")
    class DiagonalGoalEntry {

        @Test
        @DisplayName("Puck entering left goal diagonally from top scores")
        void diagonalEntryTopLeftScores() {
            puck.setPosition(40f, goalCenterY + 20f);
            puck.setVelocityX(-1200f);
            puck.setVelocityY(-100f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(
                puck, state, List.of(), LONG_DELTA);

            assertEquals(CollisionDetector.GoalResult.PLAYER_1_GOAL, result.getGoalResult(),
                "Diagonal entry into left goal should score");
        }

        @Test
        @DisplayName("Puck entering left goal diagonally from bottom scores")
        void diagonalEntryBottomLeftScores() {
            puck.setPosition(40f, goalCenterY - 20f);
            puck.setVelocityX(-1200f);
            puck.setVelocityY(100f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(
                puck, state, List.of(), LONG_DELTA);

            assertEquals(CollisionDetector.GoalResult.PLAYER_1_GOAL, result.getGoalResult(),
                "Diagonal entry into left goal from bottom should score");
        }

        @Test
        @DisplayName("Puck entering right goal diagonally scores")
        void diagonalEntryRightScores() {
            puck.setPosition(fieldWidth - 40f, goalCenterY + 20f);
            puck.setVelocityX(1200f);
            puck.setVelocityY(-50f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(
                puck, state, List.of(), LONG_DELTA);

            assertEquals(CollisionDetector.GoalResult.PLAYER_2_GOAL, result.getGoalResult(),
                "Diagonal entry into right goal should score");
        }
    }

    @Nested
    @DisplayName("High-Speed Goal Detection")
    class HighSpeedGoalDetection {

        @Test
        @DisplayName("Very fast puck crossing left goal is detected (no tunneling)")
        void fastPuckLeftGoalNoTunneling() {
            puck.setPosition(60f, goalCenterY);
            puck.setVelocityX(-3000f);
            puck.setVelocityY(0f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(
                puck, state, List.of(), LONG_DELTA);

            assertEquals(CollisionDetector.GoalResult.PLAYER_1_GOAL, result.getGoalResult(),
                "Fast puck should not tunnel through left goal");
        }

        @Test
        @DisplayName("Very fast puck crossing right goal is detected (no tunneling)")
        void fastPuckRightGoalNoTunneling() {
            puck.setPosition(fieldWidth - 60f, goalCenterY);
            puck.setVelocityX(3000f);
            puck.setVelocityY(0f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(
                puck, state, List.of(), LONG_DELTA);

            assertEquals(CollisionDetector.GoalResult.PLAYER_2_GOAL, result.getGoalResult(),
                "Fast puck should not tunnel through right goal");
        }

        @Test
        @DisplayName("Fast diagonal puck entering goal is detected")
        void fastDiagonalPuckDetected() {
            puck.setPosition(100f, goalCenterY + 30f);
            puck.setVelocityX(-2500f);
            puck.setVelocityY(-100f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(
                puck, state, List.of(), LONG_DELTA);

            assertEquals(CollisionDetector.GoalResult.PLAYER_1_GOAL, result.getGoalResult(),
                "Fast diagonal puck into goal should be detected");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Puck crossing goal line center scores")
        void puckCrossingGoalLine() {
            // Position puck to fully cross the goal line
            puck.setPosition(15f, goalCenterY);
            puck.setVelocityX(-1000f);
            puck.setVelocityY(0f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(
                puck, state, List.of(), LONG_DELTA);

            assertEquals(CollisionDetector.GoalResult.PLAYER_1_GOAL, result.getGoalResult(),
                "Puck fully crossing goal line should score");
        }

        @Test
        @DisplayName("Puck grazing goal edge boundary still scores")
        void puckGrazingGoalEdgeBoundary() {
            float justInsideGoal = goalBottom + puck.getRadius() + 2f;
            puck.setPosition(30f, justInsideGoal);
            puck.setVelocityX(-1500f);
            puck.setVelocityY(0f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(
                puck, state, List.of(), LONG_DELTA);

            assertEquals(CollisionDetector.GoalResult.PLAYER_1_GOAL, result.getGoalResult(),
                "Puck just inside goal boundary should still score");
        }

        @Test
        @DisplayName("Puck just outside goal boundary does not score")
        void puckJustOutsideGoalBoundary() {
            float justOutsideGoal = goalTop + puck.getRadius() + 10f;
            puck.setPosition(50f, justOutsideGoal);
            puck.setVelocityX(-1500f);
            puck.setVelocityY(0f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(
                puck, state, List.of(), LONG_DELTA);

            assertEquals(CollisionDetector.GoalResult.NONE, result.getGoalResult(),
                "Puck just outside goal boundary should not score");
        }

        @Test
        @DisplayName("Stationary puck in goal area does not immediately score")
        void stationaryPuckInGoalArea() {
            puck.setPosition(puck.getRadius() + 5f, goalCenterY);
            puck.setVelocityX(0f);
            puck.setVelocityY(0f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(
                puck, state, List.of(), DELTA_TIME);

            assertEquals(CollisionDetector.GoalResult.NONE, result.getGoalResult(),
                "Stationary puck near goal should not score until it crosses");
        }

        @Test
        @DisplayName("Puck moving away from goal does not score")
        void puckMovingAwayFromGoal() {
            puck.setPosition(10f, goalCenterY);
            puck.setVelocityX(500f); // Moving right, away from left goal
            puck.setVelocityY(0f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(
                puck, state, List.of(), DELTA_TIME);

            assertEquals(CollisionDetector.GoalResult.NONE, result.getGoalResult(),
                "Puck moving away from goal should not score");
        }
    }

    @Nested
    @DisplayName("Y-Position Boundary Tests")
    class YPositionBoundaryTests {

        @Test
        @DisplayName("Puck at Y=240 (center) entering left goal scores")
        void puckAtY240Scores() {
            puck.setPosition(30f, 240f);
            puck.setVelocityX(-1500f);
            puck.setVelocityY(0f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(
                puck, state, List.of(), LONG_DELTA);

            assertEquals(CollisionDetector.GoalResult.PLAYER_1_GOAL, result.getGoalResult(),
                "Puck at Y=240 (center) should score");
        }

        @Test
        @DisplayName("Puck at Y=200 (inside goal opening) entering left goal scores")
        void puckAtY200Scores() {
            puck.setPosition(30f, 200f);
            puck.setVelocityX(-1500f);
            puck.setVelocityY(0f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(
                puck, state, List.of(), LONG_DELTA);

            assertEquals(CollisionDetector.GoalResult.PLAYER_1_GOAL, result.getGoalResult(),
                "Puck at Y=200 should be inside 120px goal opening and score");
        }

        @Test
        @DisplayName("Puck at Y=280 (inside goal opening) entering left goal scores")
        void puckAtY280Scores() {
            puck.setPosition(30f, 280f);
            puck.setVelocityX(-1500f);
            puck.setVelocityY(0f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(
                puck, state, List.of(), LONG_DELTA);

            assertEquals(CollisionDetector.GoalResult.PLAYER_1_GOAL, result.getGoalResult(),
                "Puck at Y=280 should be inside 120px goal opening and score");
        }

        @Test
        @DisplayName("Puck fully below goal opening does not score")
        void puckFullyBelowGoalDoesNotScore() {
            // Puck must be fully below goal: puckY + radius < goalBottom
            // goalBottom = 180, radius = 8, so puckY < 180 - 8 = 172
            float fullyBelowGoal = goalBottom - puck.getRadius() - 5f;
            puck.setPosition(50f, fullyBelowGoal);
            puck.setVelocityX(-1500f);
            puck.setVelocityY(0f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(
                puck, state, List.of(), LONG_DELTA);

            assertEquals(CollisionDetector.GoalResult.NONE, result.getGoalResult(),
                "Puck fully below goal opening should not score");
        }

        @Test
        @DisplayName("Puck fully above goal opening does not score")
        void puckFullyAboveGoalDoesNotScore() {
            // Puck must be fully above goal: puckY - radius > goalTop
            // goalTop = 300, radius = 8, so puckY > 300 + 8 = 308
            float fullyAboveGoal = goalTop + puck.getRadius() + 5f;
            puck.setPosition(50f, fullyAboveGoal);
            puck.setVelocityX(-1500f);
            puck.setVelocityY(0f);

            CollisionDetector.TickResult result = CollisionDetector.resolveTick(
                puck, state, List.of(), LONG_DELTA);

            assertEquals(CollisionDetector.GoalResult.NONE, result.getGoalResult(),
                "Puck fully above goal opening should not score");
        }
    }
}
