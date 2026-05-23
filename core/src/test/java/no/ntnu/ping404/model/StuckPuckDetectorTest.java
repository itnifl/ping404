package no.ntnu.ping404.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StuckPuckDetector - 7-second rule (FR2.6)")
class StuckPuckDetectorTest {

    private static final float CENTER_X = 400f;
    private static final float LEFT_X = 100f;
    private static final float RIGHT_X = 700f;
    private static final float DT = 1.0f / 60f;

    private StuckPuckDetector detector;

    @BeforeEach
    void setUp() {
        detector = new StuckPuckDetector(CENTER_X);
    }

    @Nested
    @DisplayName("Stuck detection")
    class StuckDetection {

        @Test
        @Tag("FR2.6")
        @DisplayName("Puck on left half for >7 seconds triggers stuck")
        void stuckOnLeftHalf() {
            simulateTicks(LEFT_X, 7.1f);
            assertTrue(detector.isStuck());
            assertEquals(StuckPuckDetector.Half.LEFT, detector.getCurrentHalf());
        }

        @Test
        @Tag("FR2.6")
        @DisplayName("Puck on right half for >7 seconds triggers stuck")
        void stuckOnRightHalf() {
            simulateTicks(RIGHT_X, 7.1f);
            assertTrue(detector.isStuck());
            assertEquals(StuckPuckDetector.Half.RIGHT, detector.getCurrentHalf());
        }

        @Test
        @Tag("FR2.6")
        @DisplayName("Puck at exactly 7.0 seconds is NOT yet stuck")
        void exactlySevenSecondsNotStuck() {
            // Inject exactly MAX_HALF_DURATION_SECONDS in a single update to avoid
            // floating-point accumulation from tick simulation.
            detector.update(LEFT_X, StuckPuckDetector.MAX_HALF_DURATION_SECONDS);
            assertFalse(detector.isStuck(), "Must exceed 7 s, not equal");
        }

        @Test
        @Tag("FR2.6")
        @DisplayName("Puck under 7 seconds is not stuck")
        void underSevenSecondsNotStuck() {
            simulateTicks(LEFT_X, 6.9f);
            assertFalse(detector.isStuck());
        }
    }

    @Nested
    @DisplayName("Center-line handling")
    class CenterLine {

        @Test
        @Tag("FR2.6")
        @DisplayName("Puck exactly on center line never triggers stuck")
        void onCenterLineNeverStuck() {
            simulateTicks(CENTER_X, 20f);
            assertFalse(detector.isStuck());
            assertEquals(StuckPuckDetector.Half.CENTER, detector.getCurrentHalf());
        }

        @Test
        @Tag("FR2.6")
        @DisplayName("Puck within center tolerance never triggers stuck")
        void withinToleranceNotStuck() {
            simulateTicks(CENTER_X + 1.5f, 20f);
            assertFalse(detector.isStuck());
        }
    }

    @Nested
    @DisplayName("Timer resets")
    class TimerResets {

        @Test
        @Tag("FR2.6")
        @DisplayName("Crossing center resets timer - no stuck")
        void crossingCenterResetsTimer() {
            simulateTicks(LEFT_X, 6f);
            assertFalse(detector.isStuck());

            detector.update(RIGHT_X, DT);

            simulateTicks(RIGHT_X, 6f);
            assertFalse(detector.isStuck(), "Timer must have reset when puck crossed center");
        }

        @Test
        @Tag("FR2.6")
        @DisplayName("Reset clears stuck flag, timer, and half")
        void resetClearsEverything() {
            simulateTicks(LEFT_X, 7.1f);
            assertTrue(detector.isStuck());

            detector.reset();

            assertFalse(detector.isStuck());
            assertEquals(0f, detector.getTimeOnCurrentHalf());
            assertEquals(StuckPuckDetector.Half.CENTER, detector.getCurrentHalf());
        }

        @Test
        @Tag("FR2.6")
        @DisplayName("Puck can get stuck again after reset")
        void stuckAgainAfterReset() {
            simulateTicks(LEFT_X, 7.1f);
            assertTrue(detector.isStuck());
            detector.reset();

            simulateTicks(RIGHT_X, 7.1f);
            assertTrue(detector.isStuck());
            assertEquals(StuckPuckDetector.Half.RIGHT, detector.getCurrentHalf());
        }
    }

    @Nested
    @DisplayName("Pause behaviour")
    class PauseBehaviour {

        @Test
        @Tag("FR2.6")
        @DisplayName("Zero delta-time does not accumulate (simulates paused game)")
        void zeroDeltaTimeDoesNotAccumulate() {
            for (int i = 0; i < 1000; i++) {
                detector.update(LEFT_X, 0f);
            }
            assertFalse(detector.isStuck());
            assertEquals(0f, detector.getTimeOnCurrentHalf());
        }

        @Test
        @Tag("FR2.6")
        @DisplayName("Timer survives pause gap and resumes correctly")
        void timerSurvivesPauseAndResumes() {
            simulateTicks(LEFT_X, 4f);
            simulateTicks(LEFT_X, 4f);
            assertTrue(detector.isStuck(), "Accumulated time across pause should exceed 7 s");
        }
    }

    private void simulateTicks(float puckX, float durationSeconds) {
        int ticks = Math.round(durationSeconds / DT);
        for (int i = 0; i < ticks; i++) {
            detector.update(puckX, DT);
        }
    }
}
