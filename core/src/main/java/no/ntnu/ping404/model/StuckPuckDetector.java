package no.ntnu.ping404.model;

import no.ntnu.ping404.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects when the puck has remained on one half of the field for more
 * than {@value #MAX_HALF_DURATION_SECONDS} seconds (FR2.6).
 *
 * <p>Server-authoritative - the client does NOT run its own timer.
 * When {@link #isStuck()} returns {@code true}, the caller must reset the
 * puck to the center and call {@link #reset()}.</p>
 *
 * <p>Edge-case behaviour:</p>
 * <ul>
 *   <li>Puck exactly on center line (within {@value #CENTER_TOLERANCE} px)
 *       --> timer does NOT accumulate, half is cleared.</li>
 *   <li>Paused game --> caller simply stops calling {@link #update}.</li>
 *   <li>Goal reset delay --> caller skips physics so update is not called.</li>
 * </ul>
 */
public class StuckPuckDetector {

    private static final Logger logger = LoggerFactory.getLogger(StuckPuckDetector.class);

    /** Maximum seconds the puck may stay on one half before a reset (FR2.6). */
    static final float MAX_HALF_DURATION_SECONDS = Constants.PUCK_STALL_TIMEOUT_SECONDS;

    /**
     * Tolerance around the center line (in game-world units).
     * Puck within this band is treated as "on the line" - timer does not run.
     */
    static final float CENTER_TOLERANCE = 2.0f;

    private final float fieldCenterX;
    private float timeOnCurrentHalf;
    private Half currentHalf;
    private boolean stuck;

    /** Which half the puck is on. */
    public enum Half { LEFT, RIGHT, CENTER }

    /**
     * @param fieldCenterX X-coordinate of the center line
     */
    public StuckPuckDetector(float fieldCenterX) {
        this.fieldCenterX = fieldCenterX;
        reset();
    }

    /**
     * Call once per tick while phase == PLAYING and no goal-reset is pending.
     *
     * @param puckX     current puck X position
     * @param deltaTime seconds since last tick
     */
    public void update(float puckX, float deltaTime) {
        Half newHalf = determineHalf(puckX);

        if (newHalf == Half.CENTER) {
            timeOnCurrentHalf = 0f;
            currentHalf = Half.CENTER;
            stuck = false;
            return;
        }

        if (newHalf != currentHalf) {
            currentHalf = newHalf;
            timeOnCurrentHalf = 0f;
            stuck = false;
        }

        timeOnCurrentHalf += deltaTime;

        if (timeOnCurrentHalf > MAX_HALF_DURATION_SECONDS && !stuck) {
            stuck = true;
            logger.info("Puck stuck on {} half for >{} s - reset required",
                    currentHalf, MAX_HALF_DURATION_SECONDS);
        }
    }

    /** Returns {@code true} once per stuck event until {@link #reset()} is called. */
    public boolean isStuck() {
        return stuck;
    }

    /** Resets all state. Call after the puck has been physically moved to center. */
    public void reset() {
        timeOnCurrentHalf = 0f;
        currentHalf = Half.CENTER;
        stuck = false;
    }

    Half getCurrentHalf() {
        return currentHalf;
    }

    float getTimeOnCurrentHalf() {
        return timeOnCurrentHalf;
    }

    private Half determineHalf(float puckX) {
        float offset = puckX - fieldCenterX;
        if (Math.abs(offset) <= CENTER_TOLERANCE) {
            return Half.CENTER;
        }
        return offset < 0 ? Half.LEFT : Half.RIGHT;
    }
}
