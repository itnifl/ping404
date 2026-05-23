package no.ntnu.ping404.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.Viewport;
import no.ntnu.ping404.utils.Constants;

/**
 * Controller component responsible for capturing player touch input and
 * computing the next mallet position.
 *
 * <p>Reads raw {@link Gdx#input} events, constrains the touch position to the
 * player's half of the table and applies a smooth-follow interpolation. This fulfills
 * the Controller role in the client MVC architecture alongside
 * {@link no.ntnu.ping404.network.NetworkListener}.</p>
 *
 * @see GameScreen
 * @see GameRenderer
 */
class GameInputHandler {

    static final float FOLLOW_SMOOTHING_FACTOR = 0.5f;

    record MalletPosition(float x, float y) {}

    private final Viewport viewport;
    private final boolean isHost;
    private final Vector3 touchPos = new Vector3();

    GameInputHandler(Viewport viewport, boolean isHost) {
        this.viewport = viewport;
        this.isHost = isHost;
    }

    /**
     * Returns {@code true} if the player just tapped the pause button.
     */
    boolean isPauseTapped(Rectangle pauseButton) {
        if (!Gdx.input.justTouched()) return false;
        Vector3 pos = unprojectTouch();
        return pauseButton.contains(pos.x, pos.y);
    }

    /**
     * Returns {@code true} if the player is currently touching the given button.
     * Unlike tap detection, this returns true for the entire duration of the touch.
     */
    boolean isTouchingButton(Rectangle button) {
        if (!Gdx.input.isTouched()) return false;
        Vector3 pos = unprojectTouch();
        return button.contains(pos.x, pos.y);
    }

    /**
    * Reads the current touch position and returns an updated mallet position
    * after clamping to the player's half and applying smooth follow.
     *
     * @param currentX current mallet x in world coordinates
     * @param currentY current mallet y in world coordinates
     * @return new position, or {@code null} when no touch is active
     */
    MalletPosition computeMalletPosition(float currentX, float currentY) {
        if (!Gdx.input.isTouched()) return null;

        Vector3 pos = unprojectTouch();

        float targetX;
        if (isHost) {
            targetX = MathUtils.clamp(pos.x,
                    Constants.TABLE_X + Constants.MALLET_RADIUS,
                    Constants.CENTER_X - Constants.MALLET_RADIUS);
        } else {
            targetX = MathUtils.clamp(pos.x,
                    Constants.CENTER_X + Constants.MALLET_RADIUS,
                    Constants.TABLE_X + Constants.TABLE_WIDTH - Constants.MALLET_RADIUS);
        }
        float targetY = MathUtils.clamp(pos.y,
                Constants.TABLE_Y + Constants.MALLET_RADIUS,
                Constants.TABLE_Y + Constants.TABLE_HEIGHT - Constants.MALLET_RADIUS);

        float newX = currentX + (targetX - currentX) * FOLLOW_SMOOTHING_FACTOR;
        float newY = currentY + (targetY - currentY) * FOLLOW_SMOOTHING_FACTOR;

        return new MalletPosition(newX, newY);
    }

    /**
     * Returns {@code true} if the player just tapped the resume button.
     */
    boolean isResumeTapped(Rectangle resumeButton) {
        if (!Gdx.input.justTouched()) return false;
        Vector3 pos = unprojectTouch();
        return resumeButton.contains(pos.x, pos.y);
    }

    /**
     * Returns {@code true} if the player just tapped the quit button.
     */
    boolean isQuitTapped(Rectangle quitButton) {
        if (!Gdx.input.justTouched()) return false;
        Vector3 pos = unprojectTouch();
        return quitButton.contains(pos.x, pos.y);
    }

    private Vector3 unprojectTouch() {
        touchPos.set(Gdx.input.getX(), Gdx.input.getY(), 0);
        viewport.unproject(touchPos);
        return touchPos;
    }
}
