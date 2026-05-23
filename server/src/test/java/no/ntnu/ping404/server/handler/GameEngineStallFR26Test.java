package no.ntnu.ping404.server.handler;

import no.ntnu.ping404.model.GameEngine;
import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.model.Player;
import no.ntnu.ping404.model.Puck;
import no.ntnu.ping404.utils.Constants;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acceptance tests for the anti-stall 7-second rule (FR2.6) as implemented in
 * {@link GameEngine}. Verifies that {@link GameEngine.TickOutcome#hasPuckStallReset()}
 * is signalled correctly and that the puck is repositioned at board centre.
 */
@DisplayName("GameEngine - anti-stall 7-second rule (FR2.6)")
class GameEngineStallFR26Test {

    private static final float CENTER_X = Constants.boardCenterX();
    private static final float CENTER_Y = Constants.boardCenterY();
    private static final float LEFT_X   = CENTER_X / 2f;
    private static final float RIGHT_X  = CENTER_X + (Constants.DEFAULT_FIELD_WIDTH - CENTER_X) / 2f;
    private static final float DT       = 1.0f / 60f;

    private GameEngine engine;
    private Puck puck;

    @BeforeEach
    void setUp() {
        GameState state = new GameState();
        state.addPlayer(new Player(1, "P1"));
        state.addPlayer(new Player(2, "P2"));
        state.startMatch();
        engine = new GameEngine(state);
        puck = state.getPuck();
    }

    /**
     * Pins the puck at {@code puckX} with zero velocity and advances the engine for
     * {@code durationSeconds}. Returns immediately if a stall reset is signalled,
     * otherwise returns the last outcome after the full duration.
     */
    private GameEngine.TickOutcome simulateTicks(float puckX, float durationSeconds) {
        int ticks = Math.round(durationSeconds / DT);
        GameEngine.TickOutcome last = null;
        for (int i = 0; i < ticks; i++) {
            puck.setPosition(puckX, CENTER_Y);
            puck.setVelocityX(0f);
            puck.setVelocityY(0f);
            last = engine.tick(DT);
            if (last.hasPuckStallReset()) {
                return last;
            }
        }
        return last;
    }

    @Nested
    @DisplayName("Stall detection via GameEngine.tick()")
    class StallDetectionViaTick {

        @Test
        @Tag("FR2.6")
        @DisplayName("hasPuckStallReset() is false when puck is stuck under 7 seconds")
        void underSevenSecondsNeverTriggersStall() {
            GameEngine.TickOutcome outcome = simulateTicks(LEFT_X, 6.9f);
            assertFalse(outcome.hasPuckStallReset(), "No stall reset should be triggered before 7 seconds");
        }

        @Test
        @Tag("FR2.6")
        @DisplayName("hasPuckStallReset() is true after puck is stuck for more than 7 seconds")
        void afterSevenSecondsTickReturnsStallReset() {
            GameEngine.TickOutcome outcome = simulateTicks(LEFT_X, 7.1f);
            assertTrue(outcome.hasPuckStallReset(), "Stall reset must be signalled after more than 7 seconds");
        }

        @Test
        @Tag("FR2.6")
        @DisplayName("Puck is repositioned at board centre after stall reset")
        void puckIsAtCentreAfterStallReset() {
            simulateTicks(LEFT_X, 7.1f);
            assertEquals(CENTER_X, puck.getX(), 1f, "Puck X must be at board centre after stall reset");
            assertEquals(CENTER_Y, puck.getY(), 1f, "Puck Y must be at board centre after stall reset");
        }

        @Test
        @Tag("FR2.6")
        @DisplayName("Crossing to the opposite half resets the timer - 6 s left + 6 s right does not trigger stall")
        void halfCrossingResetsTimer() {
            simulateTicks(LEFT_X, 6f);
            simulateTicks(RIGHT_X, 6f);
            puck.setPosition(RIGHT_X, CENTER_Y);
            puck.setVelocityX(0f);
            puck.setVelocityY(0f);
            GameEngine.TickOutcome outcome = engine.tick(DT);
            assertFalse(outcome.hasPuckStallReset(), "Timer must reset when puck crosses to the other half");
        }

        @Test
        @Tag("FR2.6")
        @DisplayName("Stall timer resets after a stall event - a second stall also triggers")
        void stuckAgainAfterStallReset() {
            simulateTicks(LEFT_X, 7.1f);
            GameEngine.TickOutcome secondStall = simulateTicks(RIGHT_X, 7.1f);
            assertTrue(secondStall.hasPuckStallReset(), "A second stall must also trigger a reset");
        }

        @Test
        @Tag("FR2.6")
        @DisplayName("No stall reset is triggered when phase is not PLAYING")
        void noStallResetWhenNotPlaying() {
            simulateTicks(LEFT_X, 6f);
            engine.getState().pauseMatch();
            puck.setPosition(LEFT_X, CENTER_Y);
            puck.setVelocityX(0f);
            puck.setVelocityY(0f);
            GameEngine.TickOutcome outcome = engine.tick(DT);
            assertFalse(outcome.hasPuckStallReset(), "Stall check must not run when the match is paused");
        }
    }

    @Nested
    @DisplayName("Stall timer and goal interaction")
    class StallAndGoal {

        @Test
        @Tag("FR2.6")
        @DisplayName("resetStallTimer() clears accumulated time - 6 s + reset + 6 s does not trigger stall")
        void resetStallTimerClearsAccumulatedTime() {
            simulateTicks(LEFT_X, 6f);
            engine.resetStallTimer();
            GameEngine.TickOutcome outcome = simulateTicks(LEFT_X, 6f);
            assertFalse(outcome.hasPuckStallReset(),
                "6 seconds after an explicit timer reset should not trigger stall");
        }
    }
}
