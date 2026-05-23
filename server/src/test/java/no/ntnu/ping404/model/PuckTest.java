package no.ntnu.ping404.model;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import no.ntnu.ping404.utils.CollisionDetector;
import no.ntnu.ping404.utils.Constants;

import static org.junit.jupiter.api.Assertions.*;

class PuckTest {

    private static final float CENTER_X = 400f;
    private static final float CENTER_Y = 240f;
    private static final float SAMPLE_POSITIVE_VELOCITY = 10f;
    private static final float UPDATE_VELOCITY_X = 100f;
    private static final float UPDATE_VELOCITY_Y = 50f;
    private static final float UPDATE_DELTA_TIME = 0.1f;
    private static final float EXPECTED_UPDATED_X = 10f;
    private static final float EXPECTED_UPDATED_Y = 5f;
    private static final float ASSERT_EPSILON = 0.001f;
    private static final float SPEED_ASSERT_EPSILON = 0.01f;
    private static final float COLLISION_DELTA_TIME = 0.016f;
    private static final int MALLET_PLAYER_ID = 1;
    private static final String MALLET_PLAYER_NAME = "Mallet";
    private static final float MALLET_X_OFFSET = 0.5f;
    private static final float POSITIVE_X_THRESHOLD = 0f;

    @Test
    @Tag("FR2.3")
    void puckHasNonZeroVelocityAfterReset() {
        // FR2.3: Puck must be in continuous motion between goals.
        // After reset(), velocityX or velocityY (or both) must be non-zero.
        Puck puck = new Puck();
        puck.reset(CENTER_X, CENTER_Y);
        assertTrue(puck.getVelocityX() != 0 || puck.getVelocityY() != 0,
            "Puck must have non-zero velocity after reset");
    }

    @Test
    @Tag("FR2.4")
    void puckVelocityXIsReversedAfterBounceX() {
        Puck puck = new Puck();
        puck.setVelocityX(SAMPLE_POSITIVE_VELOCITY);
        puck.bounceX();
        assertEquals(-SAMPLE_POSITIVE_VELOCITY, puck.getVelocityX(), ASSERT_EPSILON, "bounceX must negate velocityX");
    }

    @Test
    @Tag("FR2.4")
    void puckVelocityYIsReversedAfterBounceY() {
        Puck puck = new Puck();
        puck.setVelocityY(SAMPLE_POSITIVE_VELOCITY);
        puck.bounceY();
        assertEquals(-SAMPLE_POSITIVE_VELOCITY, puck.getVelocityY(), ASSERT_EPSILON, "bounceY must negate velocityY");
    }

    @Test
    @Tag("FR2.3")
    void puckPositionUpdatesBasedOnVelocityAndDeltaTime() {
        Puck puck = new Puck(0f, 0f);
        puck.setVelocityX(UPDATE_VELOCITY_X);
        puck.setVelocityY(UPDATE_VELOCITY_Y);
        puck.update(UPDATE_DELTA_TIME);
        assertEquals(EXPECTED_UPDATED_X, puck.getX(), ASSERT_EPSILON, "x must advance by velocityX * dt");
        assertEquals(EXPECTED_UPDATED_Y, puck.getY(), ASSERT_EPSILON, "y must advance by velocityY * dt");
    }

    @Test
    @Tag("FR2.10")
    void puckResetsToGivenCenterCoordinates() {
        Puck puck = new Puck();
        puck.reset(CENTER_X, CENTER_Y);
        assertEquals(CENTER_X, puck.getX(), ASSERT_EPSILON, "puck.getX() must equal centerX after reset");
        assertEquals(CENTER_Y, puck.getY(), ASSERT_EPSILON, "puck.getY() must equal centerY after reset");
    }

    @Test
    @Tag("FR2.3")
    void puckSpeedMagnitudeIsPreservedAfterReset() {
        Puck puck = new Puck();
        puck.reset(CENTER_X, CENTER_Y);
        float magnitude = (float) Math.sqrt(
                puck.getVelocityX() * puck.getVelocityX() + puck.getVelocityY() * puck.getVelocityY());
        assertEquals(puck.getSpeed(), magnitude, SPEED_ASSERT_EPSILON, "Speed magnitude must equal configured speed after reset");
    }

    @Disabled("Comprehensive tests in StuckPuckDetectorTest")
    @Test
    @Tag("FR2.6")
    void puckIsResetsAfterSevenSecondsOnOneHalf() {
        // FR2.6: If the puck remains on one player's half for 7 consecutive seconds,
        // the server resets the puck to center to prevent stalling.
        // See: StuckPuckDetectorTest for comprehensive coverage
    }

    @Test
    @Tag("FR2.5")
    void puckVelocityReversesOnMalletCollision() {
        GameState state = new GameState();
        Puck puck = state.getPuck();
        puck.setPosition(CENTER_X, CENTER_Y);
        puck.setVelocityX(-Constants.INITIAL_PUCK_SPEED);
        puck.setVelocityY(0f);

        Player mallet = new Player(MALLET_PLAYER_ID, MALLET_PLAYER_NAME);
        mallet.setX(CENTER_X - Constants.PUCK_RADIUS - Constants.PADDLE_WIDTH / 2f - MALLET_X_OFFSET);
        mallet.setY(CENTER_Y);
        state.addPlayer(mallet);

        CollisionDetector.TickResult result = CollisionDetector.resolveTick(state, COLLISION_DELTA_TIME);

        assertTrue(result.hasPaddleCollision(), "Expected a paddle collision");
        assertTrue(puck.getVelocityX() > POSITIVE_X_THRESHOLD, "Puck X velocity must reverse to positive after left-side mallet collision");
    }

    // ============ FR2.4: Wall Bounce ============

    @Test
    @Tag("FR2.4")
    void puckBouncesOffTopWall() {
        Puck puck = new Puck(400, 5);
        puck.setVelocityY(-200);

        boolean bounced = puck.bounceOffWalls(480);

        assertTrue(bounced);
        assertTrue(puck.getVelocityY() > 0, "VelocityY should reverse after top wall bounce");
        assertEquals(puck.getRadius(), puck.getY(), "Puck should be clamped to top edge");
    }

    @Test
    @Tag("FR2.4")
    void puckBouncesOffBottomWall() {
        Puck puck = new Puck(400, 475);
        puck.setVelocityY(200);

        boolean bounced = puck.bounceOffWalls(480);

        assertTrue(bounced);
        assertTrue(puck.getVelocityY() < 0, "VelocityY should reverse after bottom wall bounce");
        assertEquals(480 - puck.getRadius(), puck.getY(), "Puck should be clamped to bottom edge");
    }

    @Test
    @Tag("FR2.4")
    void puckDoesNotBounceInMiddle() {
        Puck puck = new Puck(400, 240);
        puck.setVelocityY(200);

        boolean bounced = puck.bounceOffWalls(480);

        assertFalse(bounced);
        assertEquals(200, puck.getVelocityY());
    }

    // ============ FR2.5: Mallet Bounce ============

    @Test
    @Tag("FR2.5")
    void puckBouncesOffMallet() {
        Puck puck = new Puck(105, 240);
        puck.setVelocityX(-200);
        puck.setVelocityY(0);

        Player mallet = new Player(1, "P1");
        mallet.setX(80);
        mallet.setY(240);

        boolean bounced = puck.bounceOffMallet(mallet, 20f);

        assertTrue(bounced);
        assertTrue(puck.getVelocityX() > 0, "VelocityX should reverse after mallet bounce");
    }

    @Test
    @Tag("FR2.5")
    void puckDoesNotBounceWhenFarFromMallet() {
        Puck puck = new Puck(400, 240);
        puck.setVelocityX(-200);

        Player mallet = new Player(1, "P1");
        mallet.setX(80);
        mallet.setY(240);

        assertFalse(puck.bounceOffMallet(mallet, 20f));
    }

    @Test
    @Tag("FR2.5")
    void puckSpeedPreservedAfterMalletBounce() {
        Puck puck = new Puck(105, 245);
        puck.setVelocityX(-200);
        puck.setVelocityY(100);
        puck.setSpeed(300);

        Player mallet = new Player(1, "P1");
        mallet.setX(80);
        mallet.setY(240);

        puck.bounceOffMallet(mallet, 20f);

        float speed = (float) Math.sqrt(
            puck.getVelocityX() * puck.getVelocityX() +
            puck.getVelocityY() * puck.getVelocityY()
        );
        assertEquals(300f, speed, 1f, "Speed should be preserved after mallet bounce");
    }

    // ============ FR2.9: Goal Detection ============

    @Test
    @Tag("FR2.9")
    void detectsGoalOnLeftEdge() {
        Puck puck = new Puck(3, 240);
        assertEquals(2, puck.checkGoal(800), "Player 2 scores when puck passes left edge");
    }

    @Test
    @Tag("FR2.9")
    void detectsGoalOnRightEdge() {
        Puck puck = new Puck(797, 240);
        assertEquals(1, puck.checkGoal(800), "Player 1 scores when puck passes right edge");
    }

    @Test
    @Tag("FR2.9")
    void noGoalInCenter() {
        Puck puck = new Puck(400, 240);
        assertEquals(0, puck.checkGoal(800));
    }

        @Test
    @Disabled("Not yet implemented")
    @Tag("FR2.6")
    void puckIsInFactResetsAfterSevenSecondsOnOneHalf() {
        // FR2.6: If the puck remains on one player's half for 7 consecutive seconds,
        // the server should reset the puck to center to prevent stalling.
        // This is a contract test and will fail until the anti-stall behavior is implemented.
        Puck puck = new Puck(Constants.player1HalfCenterX(), CENTER_Y);
        puck.setVelocityX(0f);
        puck.setVelocityY(0f);

        float elapsedSeconds = 0f;
        while (elapsedSeconds < 7.0f) {
            puck.update(UPDATE_DELTA_TIME);
            elapsedSeconds += UPDATE_DELTA_TIME;
        }

        assertEquals(Constants.boardCenterX(), puck.getX(), ASSERT_EPSILON,
            "Puck should reset to center X after 7 seconds on one half");
        assertEquals(Constants.boardCenterY(), puck.getY(), ASSERT_EPSILON,
            "Puck should reset to center Y after 7 seconds on one half");
    }

}
