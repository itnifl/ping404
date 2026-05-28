package no.ntnu.ping404.network;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link PuckInterpolator}.
 *
 * <p>Covers QAS-P2 (smooth rendering) and the laggy-puck-at-high-speed defect
 * where the exponential smoother used to lag the authoritative target by
 * {@code v / BLEND_RATE} pixels at high velocities.</p>
 */
class PuckInterpolatorTest {

    /** Render frame delta used by the tests (~60 FPS). */
    private static final float FRAME_DELTA = 1f / 60f;

    /**
     * Simulates a constant-velocity puck for {@code seconds} of render time,
     * injecting an authoritative snapshot every {@code snapshotIntervalSeconds}
     * to mimic the real server broadcast cadence. Returns the last authoritative
     * X coordinate that was pushed into the interpolator.
     */
    private static float simulate(PuckInterpolator interpolator,
                                  Vector2 startPosition,
                                  Vector2 velocity,
                                  float seconds,
                                  float snapshotIntervalSeconds) {
        float elapsed = 0f;
        float timeSinceSnapshot = 0f;
        float lastAuthoritativeX = startPosition.x;
        interpolator.onAuthoritativeUpdate(startPosition, velocity);

        while (elapsed < seconds) {
            interpolator.update(FRAME_DELTA);
            elapsed += FRAME_DELTA;
            timeSinceSnapshot += FRAME_DELTA;

            if (timeSinceSnapshot >= snapshotIntervalSeconds) {
                Vector2 newPosition = new Vector2(
                    startPosition.x + velocity.x * elapsed,
                    startPosition.y + velocity.y * elapsed
                );
                interpolator.onAuthoritativeUpdate(newPosition, velocity);
                lastAuthoritativeX = newPosition.x;
                timeSinceSnapshot = 0f;
            }
        }
        return lastAuthoritativeX;
    }

    @Test
    @Tag("P2")
    @DisplayName("Render position does not trail authoritative when puck moves fast")
    void renderPositionDoesNotTrailAuthoritativeAtHighSpeed() {
        PuckInterpolator interpolator = new PuckInterpolator();
        Vector2 startPosition = new Vector2(0f, 0f);
        Vector2 velocity = new Vector2(1500f, 0f); // fast shot

        float totalSeconds = 1f;
        float snapshotIntervalSeconds = 1f / 30f; // 30 Hz server broadcast
        float lastAuthoritativeX = simulate(
            interpolator, startPosition, velocity, totalSeconds, snapshotIntervalSeconds);

        float renderedX = interpolator.getRenderX();

        // Before the fix the render position trailed the authoritative position by
        // roughly v / BLEND_RATE pixels (150 px at v=1500). Now it must sit at or
        // ahead of the last authoritative position so the puck no longer appears
        // visibly behind where the server says it is.
        assertTrue(renderedX >= lastAuthoritativeX - 5f,
            "Render position must not trail authoritative at high speed. "
                + "render=" + renderedX + " auth=" + lastAuthoritativeX);

        // And it must not run unbounded ahead either: lead compensation is bounded
        // by velocity * (1/BLEND_RATE) plus one snapshot interval of extrapolation.
        float lead = velocity.x * (1f / 10f) + velocity.x * snapshotIntervalSeconds;
        assertTrue(renderedX <= lastAuthoritativeX + lead + 10f,
            "Render position should not run unbounded ahead of authoritative. "
                + "render=" + renderedX + " auth=" + lastAuthoritativeX + " maxLead=" + lead);
    }

    @Test
    @Tag("P2")
    @DisplayName("Render position does not drift when puck is stationary")
    void renderPositionMatchesAuthoritativeWhenStationary() {
        PuckInterpolator interpolator = new PuckInterpolator();
        Vector2 position = new Vector2(500f, 300f);
        Vector2 velocity = new Vector2(0f, 0f);

        simulate(interpolator, position, velocity, 0.5f, 1f / 30f);

        assertEquals(position.x, interpolator.getRenderX(), 0.5f,
            "Stationary puck should not drift on X");
        assertEquals(position.y, interpolator.getRenderY(), 0.5f,
            "Stationary puck should not drift on Y");
    }

    @Test
    @Tag("P2")
    @DisplayName("Large authoritative jump snaps render position")
    void largeAuthoritativeJumpSnapsRenderPosition() {
        PuckInterpolator interpolator = new PuckInterpolator();
        Vector2 zeroVelocity = new Vector2(0f, 0f);

        interpolator.onAuthoritativeUpdate(new Vector2(0f, 0f), zeroVelocity);
        interpolator.update(FRAME_DELTA);

        Vector2 farAway = new Vector2(1000f, 1000f);
        interpolator.onAuthoritativeUpdate(farAway, zeroVelocity);

        assertEquals(farAway.x, interpolator.getRenderX(), 0.01f,
            "Render X should snap to authoritative on large jump");
        assertEquals(farAway.y, interpolator.getRenderY(), 0.01f,
            "Render Y should snap to authoritative on large jump");
    }

    @Test
    @Tag("P2")
    @DisplayName("Reset clears state and initialization flag")
    void resetClearsState() {
        PuckInterpolator interpolator = new PuckInterpolator();
        interpolator.onAuthoritativeUpdate(new Vector2(100f, 200f), new Vector2(0f, 0f));
        assertTrue(interpolator.isInitialized(), "Should be initialized after first update");

        interpolator.reset();

        assertFalse(interpolator.isInitialized(), "Reset should clear initialized flag");
        assertEquals(0f, interpolator.getRenderX(), 0.01f, "Reset should clear X");
        assertEquals(0f, interpolator.getRenderY(), 0.01f, "Reset should clear Y");
    }
}
