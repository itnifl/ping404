package no.ntnu.ping404.utils;

import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.model.Player;
import no.ntnu.ping404.model.Puck;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CollisionDetectorTest {

    // Test constants for collision scenarios
    private static final float GRAZING_EDGE_FACTOR = 0.6f; // Edge of paddle contact
    private static final float CORNER_OFFSET = 0.5f; // Small offset for corner-hit positioning
    private static final float PADDLE_TEST_X_LEFT = 140f; // Left-side paddle position for tests
    private static final float CORNER_TEST_X = 16f; // Corner collision test position
    private static final float CORNER_TEST_Y = 16f; // Corner collision test position
    private static final float TINY_POS = 9f; // Very close to corner for simultaneous collision
    private static final float NORMAL_DELTA_TIME = 0.02f; // Normal tick duration
    private static final float LONGER_DELTA_TIME = 0.03f; // Slightly longer tick
    private static final float LONG_DELTA_TIME = 0.05f; // Longer tick for high-speed tests

    @Test
    @Tag("FR2.4")
    void cornerHitInvertsBothVelocityComponents() {
        GameState state = new GameState(Constants.DEFAULT_FIELD_WIDTH, Constants.DEFAULT_FIELD_HEIGHT);
        Puck puck = state.getPuck();
        puck.setRadius(Constants.PUCK_RADIUS);
        puck.setPosition(Constants.PUCK_RADIUS + CORNER_OFFSET, Constants.DEFAULT_FIELD_HEIGHT - Constants.PUCK_RADIUS - CORNER_OFFSET);
        puck.setVelocityX(-220f);
        puck.setVelocityY(240f);

        CollisionDetector.TickResult result = CollisionDetector.resolveTick(puck, state, List.of(), NORMAL_DELTA_TIME);

        assertTrue(result.hasWallCollision(), "Corner impact should be registered as wall collision");
        assertEquals(CollisionDetector.GoalResult.NONE, result.getGoalResult(), "Corner wall hit must not score");
        assertTrue(puck.getVelocityX() > 0f, "X velocity should invert after left wall hit");
        assertTrue(puck.getVelocityY() < 0f, "Y velocity should invert after top wall hit");
    }

    @Test
    @Tag("FR2.5")
    void grazingPaddleHitCreatesDeflection() {
        GameState state = new GameState(Constants.DEFAULT_FIELD_WIDTH, Constants.DEFAULT_FIELD_HEIGHT);
        Puck puck = state.getPuck();
        puck.setRadius(Constants.PUCK_RADIUS);

        Player player = new Player(1, "p1");
        player.setX(PADDLE_TEST_X_LEFT);
        player.setY(Constants.DEFAULT_FIELD_HEIGHT / 2f);

        float edgeY = player.getY() + (Constants.PADDLE_HEIGHT / 2f) - (puck.getRadius() * GRAZING_EDGE_FACTOR);
        puck.setPosition(158f, edgeY);
        puck.setVelocityX(-260f);
        puck.setVelocityY(0f);

        CollisionDetector.TickResult result = CollisionDetector.resolveTick(puck, state, List.of(player), LONGER_DELTA_TIME);

        assertTrue(result.hasPaddleCollision(), "Expected paddle collision on grazing contact");
        assertTrue(Math.abs(puck.getVelocityY()) > 1f, "Grazing collision should add vertical deflection");
    }

    @Test
    @Tag("FR2.5")
    void movingPaddleTransfersMomentumToPuck() {
        float centerY = Constants.DEFAULT_FIELD_HEIGHT / 2f;

        // Baseline: stationary mallet collision.
        GameState stationaryState = new GameState(Constants.DEFAULT_FIELD_WIDTH, Constants.DEFAULT_FIELD_HEIGHT);
        Puck stationaryPuck = stationaryState.getPuck();
        stationaryPuck.setRadius(Constants.PUCK_RADIUS);
        Player stationaryPlayer = new Player(101, "stationary");
        stationaryPlayer.setX(PADDLE_TEST_X_LEFT);
        stationaryPlayer.setY(centerY);

        stationaryPuck.setPosition(157f, centerY);
        stationaryPuck.setVelocityX(-220f);
        stationaryPuck.setVelocityY(0f);
        CollisionDetector.TickResult stationaryResult = CollisionDetector.resolveTick(
                stationaryPuck, stationaryState, List.of(stationaryPlayer), NORMAL_DELTA_TIME);

        // Moving mallet collision with same setup, but mallet moved upward between ticks.
        GameState movingState = new GameState(Constants.DEFAULT_FIELD_WIDTH, Constants.DEFAULT_FIELD_HEIGHT);
        Puck movingPuck = movingState.getPuck();
        movingPuck.setRadius(Constants.PUCK_RADIUS);
        Player movingPlayer = new Player(202, "moving");
        movingPlayer.setX(PADDLE_TEST_X_LEFT);
        movingPlayer.setY(centerY);

        // Prime previous position so resolveTick can estimate player velocity.
        movingPuck.setPosition(400f, centerY);
        movingPuck.setVelocityX(0f);
        movingPuck.setVelocityY(0f);
        CollisionDetector.resolveTick(movingPuck, movingState, List.of(movingPlayer), NORMAL_DELTA_TIME);

        movingPlayer.setX(PADDLE_TEST_X_LEFT + 12f);
        movingPuck.setPosition(157f, centerY);
        movingPuck.setVelocityX(-220f);
        movingPuck.setVelocityY(0f);
        CollisionDetector.TickResult movingResult = CollisionDetector.resolveTick(
                movingPuck, movingState, List.of(movingPlayer), NORMAL_DELTA_TIME);

        assertTrue(stationaryResult.hasPaddleCollision(), "Expected baseline paddle collision");
        assertTrue(movingResult.hasPaddleCollision(), "Expected paddle collision with moving mallet");
        assertTrue(Math.abs(movingPuck.getVelocityX()) > Math.abs(stationaryPuck.getVelocityX()),
                "Mallet motion along contact normal should change rebound speed relative to stationary collision");
    }

    @Test
    @Tag("FR2.4")
    @Tag("FR2.5")
    void simultaneousWallAndPaddleContactHandledDeterministically() {
        GameState state = new GameState(Constants.DEFAULT_FIELD_WIDTH, Constants.DEFAULT_FIELD_HEIGHT);
        Puck puck = state.getPuck();
        puck.setRadius(Constants.PUCK_RADIUS);

        Player player = new Player(1, "p1");
        player.setX(CORNER_TEST_X);
        player.setY(CORNER_TEST_Y);

        puck.setPosition(TINY_POS, TINY_POS);
        puck.setVelocityX(-80f);
        puck.setVelocityY(-80f);

        CollisionDetector.TickResult result = CollisionDetector.resolveTick(puck, state, List.of(player), LONG_DELTA_TIME);

        assertTrue(result.hasWallCollision(), "Wall collision should be processed in this tick");
        assertTrue(result.hasPaddleCollision(), "Paddle collision should also be processed in same tick");
        assertEquals(CollisionDetector.GoalResult.NONE, result.getGoalResult(), "No goal expected from top-left contact");
    }

    @Test
    @Tag("FR2.7")
    void goalLinePassDetectedAtHighSpeedWithoutTunneling() {
        GameState state = new GameState(Constants.DEFAULT_FIELD_WIDTH, Constants.DEFAULT_FIELD_HEIGHT);
        Puck puck = state.getPuck();
        puck.setRadius(Constants.PUCK_RADIUS);
        puck.setPosition(55f, Constants.DEFAULT_FIELD_HEIGHT / 2f);
        puck.setVelocityX(-2200f);
        puck.setVelocityY(0f);

        CollisionDetector.TickResult result = CollisionDetector.resolveTick(puck, state, List.of(), LONG_DELTA_TIME);

        assertEquals(CollisionDetector.GoalResult.PLAYER_1_GOAL, result.getGoalResult(),
            "Puck crossing left goal line at high speed should still be detected");
    }

    @Test
    @Tag("FR2.7")
    void goalIsReportedOnlyOncePerTick() {
        GameState state = new GameState(Constants.DEFAULT_FIELD_WIDTH, Constants.DEFAULT_FIELD_HEIGHT);
        Puck puck = state.getPuck();
        puck.setRadius(Constants.PUCK_RADIUS);
        puck.setPosition(6f, Constants.DEFAULT_FIELD_HEIGHT / 2f);
        puck.setVelocityX(-1000f);
        puck.setVelocityY(0f);

        CollisionDetector.TickResult result = CollisionDetector.resolveTick(puck, state, List.of(), LONGER_DELTA_TIME);

        assertEquals(CollisionDetector.GoalResult.PLAYER_1_GOAL, result.getGoalResult());
        assertFalse(result.hasWallCollision(), "Goal lane should not be treated as side wall collision");
    }
}
