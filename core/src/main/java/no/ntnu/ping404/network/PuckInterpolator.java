package no.ntnu.ping404.network;

import com.badlogic.gdx.math.Vector2;

/**
 * Handles client-side puck interpolation and prediction between server snapshots.
 *
 * <p>The server sends {@code GameStateSnapshot} at approximately 20-30 Hz. Without
 * interpolation, the puck would teleport between updates, causing jerky visuals.
 * This class provides smooth rendering by:</p>
 * <ul>
 *   <li><b>Interpolation:</b> Smoothly blends between the last two authoritative positions</li>
 *   <li><b>Extrapolation:</b> Predicts forward using velocity when no new snapshot has arrived</li>
 *   <li><b>Bounded correction:</b> Snaps to authoritative state when deviation is too large</li>
 * </ul>
 *
 * <p>Requirements covered: P2 (smooth rendering), FR2.3 (continuous puck motion), FR1.5 (sync).</p>
 *
 * @see GameScreenState
 */
public class PuckInterpolator {

    /**
     * Maximum position deviation (pixels) before snapping to authoritative state.
     * Tuned so large network jumps snap immediately while normal interpolation blends smoothly.
     * Set conservatively to ~3% of table width (typical 1500px width) to catch only server-driven
     * corrections or lag-induced jumps, not normal extrapolation drift.
     */
    private static final float SNAP_THRESHOLD = 50f;

    /**
     * Exponential decay constant for frame-rate independent smoothing.
     * Determines how quickly render position converges to target position.
     * Value of 10f gives ~95% convergence in ~0.3s at 60 FPS, providing responsive yet smooth motion.
     * Formula: blendFactor = 1 - exp(-BLEND_RATE * deltaTime) ensures constant perceived smoothing
     * regardless of frame rate.
     */
    private static final float BLEND_RATE = 10f;

    /**
     * Lead-compensation time used to cancel the steady-state lag of a first-order
     * exponential filter when tracking a target moving at constant velocity.
     *
     * <p>For an exponential filter with rate {@code k}, the render position lags a
     * constantly moving target by {@code v / k} pixels at steady state. Adding
     * {@code v * (1 / k)} to the target makes the steady-state error zero, which
     * removes the visible trailing effect at high puck speeds while preserving the
     * smoothing for direction changes and network jitter.</p>
     */
    private static final float LEAD_COMPENSATION_TIME = 1f / BLEND_RATE;

    /**
     * Maximum extrapolation time (seconds) before stopping velocity-based prediction.
     * Servers send snapshots at ~20-30 Hz (33-50ms interval). Capping extrapolation at 200ms
     * (6-7 snapshots) prevents unbounded position drift if network stalls or becomes unidirectional.
     */
    private static final float MAX_EXTRAPOLATION_TIME = 0.2f;

    /** Current interpolated/predicted position for rendering. */
    private final Vector2 renderPosition = new Vector2();

    /** Last authoritative position from server. */
    private final Vector2 authoritativePosition = new Vector2();

    /** Last authoritative velocity from server. */
    private final Vector2 authoritativeVelocity = new Vector2();

    /** Time since last authoritative update (seconds). */
    private float timeSinceUpdate = 0f;

    /** Whether an authoritative update has been received. */
    private boolean initialized = false;

    /**
     * Updates the interpolator with a new authoritative snapshot from the server.
     *
     * @param position the authoritative puck position
     * @param velocity the authoritative puck velocity
     */
    public void onAuthoritativeUpdate(Vector2 position, Vector2 velocity) {
        if (position == null) return;

        authoritativePosition.set(position);
        if (velocity != null) {
            authoritativeVelocity.set(velocity);
        }

        if (!initialized) {
            renderPosition.set(position);
            initialized = true;
        } else {
            // Snap when the render position deviates from where it would naturally sit at
            // this instant (authoritative position adjusted for lead compensation). Comparing
            // against the lead-compensated reference avoids spurious snaps at high speeds
            // where the render position is intentionally ahead of the raw authoritative one.
            float leadX = authoritativePosition.x + authoritativeVelocity.x * LEAD_COMPENSATION_TIME;
            float leadY = authoritativePosition.y + authoritativeVelocity.y * LEAD_COMPENSATION_TIME;
            float dx = renderPosition.x - leadX;
            float dy = renderPosition.y - leadY;
            float deviation = (float) Math.sqrt(dx * dx + dy * dy);
            if (deviation > SNAP_THRESHOLD) {
                renderPosition.set(leadX, leadY);
            }
            // Deviations within threshold are blended smoothly in update().
        }

        timeSinceUpdate = 0f;
    }

    /**
     * Updates the interpolated position for the current frame.
     * Call this every render frame with the delta time.
     *
     * @param deltaTime time since last frame (seconds)
     */
    public void update(float deltaTime) {
        if (!initialized || deltaTime <= 0) return;

        timeSinceUpdate += deltaTime;

        float extrapolationTime = Math.min(timeSinceUpdate, MAX_EXTRAPOLATION_TIME);
        // Lead-compensated target removes steady-state lag of the exponential smoother
        // at high constant velocities (e.g. fast puck shots), while the smoother still
        // dampens jitter and direction changes.
        float leadTime = extrapolationTime + LEAD_COMPENSATION_TIME;
        float targetX = authoritativePosition.x + authoritativeVelocity.x * leadTime;
        float targetY = authoritativePosition.y + authoritativeVelocity.y * leadTime;

        float blendFactor = 1f - (float) Math.exp(-BLEND_RATE * deltaTime);
        renderPosition.x += (targetX - renderPosition.x) * blendFactor;
        renderPosition.y += (targetY - renderPosition.y) * blendFactor;
    }

    /**
     * Returns a copy of the smoothed puck position for rendering.
     * Note: Creates a new Vector2 instance each call for immutability.
     *
     * @return a new Vector2 containing the interpolated/predicted position
     */
    public Vector2 getRenderPosition() {
        return new Vector2(renderPosition);
    }

    public float getRenderX() {
        return renderPosition.x;
    }

    public float getRenderY() {
        return renderPosition.y;
    }

    /**
     * Resets the interpolator state.
     * Call this when starting a new game or after a goal reset.
     */
    public void reset() {
        renderPosition.setZero();
        authoritativePosition.setZero();
        authoritativeVelocity.setZero();
        timeSinceUpdate = 0f;
        initialized = false;
    }

    public boolean isInitialized() {
        return initialized;
    }
}
