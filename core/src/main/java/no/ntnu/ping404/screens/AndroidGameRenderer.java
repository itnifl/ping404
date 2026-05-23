package no.ntnu.ping404.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * Android-specific game renderer with touch-optimized UI.
 *
 * <p>Provides rendering optimized for Android devices with larger touch
 * targets, touch gesture hints, and optional touch feedback overlays.</p>
 *
 * <p><b>Layout Adjustments</b></p>
 * <p>Uses larger sizing for finger touch accuracy:</p>
 * <ul>
 *   <li>HUD height: 90px (taller for larger buttons)</li>
 *   <li>Pause button: 56px (finger-friendly)</li>
 *   <li>Touch target: 48px (Android Material guidelines)</li>
 *   <li>Metrics panel: wider for readability</li>
 * </ul>
 *
 * @see DesktopGameRenderer
 * @see GameRendererFactory
 */
class AndroidGameRenderer extends AbstractGameRenderer {

    /** Android-specific HUD height (larger for touch). */
    private static final float ANDROID_HUD_HEIGHT = 90f;
    /** Android-specific pause button size (larger touch target). */
    private static final float ANDROID_PAUSE_BUTTON_SIZE = 56f;
    /** Android minimum touch target per Material Design. */
    private static final float ANDROID_MIN_TOUCH_TARGET = 48f;
    /** Android metrics panel width (wider for readability). */
    private static final float ANDROID_METRICS_PANEL_WIDTH = 460f;
    /** Android metrics panel height (taller for larger text). */
    private static final float ANDROID_METRICS_PANEL_HEIGHT = 110f;

    /** Radius for touch indicator circles (pixels). */
    private static final float TOUCH_INDICATOR_RADIUS = 30f;
    /** Segment count for touch indicator circles. */
    private static final int TOUCH_INDICATOR_SEGMENTS = 20;
    /** Horizontal offset for secondary input hint text (pixels). */
    private static final float INPUT_HINT_X_OFFSET = 20f;
    /** Vertical offset for secondary input hint text (pixels). */
    private static final float INPUT_HINT_Y_OFFSET = 28f;
    /** Debug hint text shown on Android devices. */
    private static final String DEBUG_HINT_TEXT = "3-finger tap for debug";
    /** Input control hint text for Android. */
    private static final String MALLET_CONTROL_HINT = "Drag finger to move mallet";

    /** Horizontal position for role hint text (pixels). */
    private static final float ROLE_HINT_X = 12f;
    /** Vertical offset for player side label from top (pixels). */
    private static final float ROLE_HINT_LABEL_Y_OFFSET = 48f;
    /** Vertical offset for debug hint from top (pixels). */
    private static final float ROLE_HINT_DEBUG_Y_OFFSET = 72f;

    /** Color for touch indicator overlay. */
    private static final Color TOUCH_INDICATOR_COLOR = new Color(1f, 1f, 1f, 0.3f);

    AndroidGameRenderer(ShapeRenderer shapeRenderer, SpriteBatch batch, OrthographicCamera camera,
                        BitmapFont smallFont, BitmapFont largeFont, BitmapFont buttonFont,
                        GlyphLayout glyphLayout) {
        super(shapeRenderer, batch, camera, smallFont, largeFont, buttonFont, glyphLayout);
    }

    @Override
    public void renderRoleHint(String playerSideLabel, float worldHeight) {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        smallFont.setColor(Color.WHITE);
        smallFont.draw(batch, playerSideLabel, ROLE_HINT_X, worldHeight - ROLE_HINT_LABEL_Y_OFFSET);
        smallFont.draw(batch, DEBUG_HINT_TEXT, ROLE_HINT_X, worldHeight - ROLE_HINT_DEBUG_Y_OFFSET);
        batch.end();
    }

    @Override
    public boolean supportsKeyboardDebug() {
        return false;
    }

    @Override
    public float getHudHeight() {
        return ANDROID_HUD_HEIGHT;
    }

    @Override
    public float getPauseButtonSize() {
        return ANDROID_PAUSE_BUTTON_SIZE;
    }

    @Override
    public float getMinTouchTargetSize() {
        return ANDROID_MIN_TOUCH_TARGET;
    }

    @Override
    public float getMetricsPanelWidth() {
        return ANDROID_METRICS_PANEL_WIDTH;
    }

    @Override
    public float getMetricsPanelHeight() {
        return ANDROID_METRICS_PANEL_HEIGHT;
    }

    @Override
    public void renderPlatformOverlay(float worldWidth, float worldHeight) {
        // Draw touch indicators where fingers are touching
        if (!Gdx.input.isTouched()) {
            return;
        }

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(TOUCH_INDICATOR_COLOR);

        for (int i = 0; i < 5; i++) {
            if (Gdx.input.isTouched(i)) {
                float touchX = Gdx.input.getX(i);
                float touchY = worldHeight - Gdx.input.getY(i);
                shapeRenderer.circle(touchX, touchY, TOUCH_INDICATOR_RADIUS, TOUCH_INDICATOR_SEGMENTS);
            }
        }

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    @Override
    public void renderInputPrompt(String message, float worldWidth, float worldHeight) {
        renderInputPromptWithHint(message, MALLET_CONTROL_HINT, INPUT_HINT_X_OFFSET, INPUT_HINT_Y_OFFSET, worldWidth, worldHeight);
    }
}
