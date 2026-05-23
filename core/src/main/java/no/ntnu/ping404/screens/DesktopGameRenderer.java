package no.ntnu.ping404.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * Desktop-specific game renderer with keyboard shortcut hints.
 *
 * <p>Provides rendering optimized for desktop platforms, including
 * keyboard shortcut hints for debug mode (Press SPACE for debug)
 * and mouse-friendly UI sizing.</p>
 *
 * <p><b>Layout Defaults</b></p>
 * <p>Uses compact sizing suitable for mouse precision:</p>
 * <ul>
 *   <li>HUD height: 75px (standard)</li>
 *   <li>Pause button: 45px (mouse-friendly)</li>
 *   <li>Touch target: 44px (matches Apple HIG minimum)</li>
 * </ul>
 *
 * @see AndroidGameRenderer
 * @see GameRendererFactory
 */
class DesktopGameRenderer extends AbstractGameRenderer {

    /** Vertical offset for secondary input hint text (pixels). */
    private static final float INPUT_HINT_Y_OFFSET = 24f;
    /** Input control hint text for desktop. */
    private static final String MALLET_CONTROL_HINT = "Use mouse to control mallet";
    /** Debug hint text shown on desktop. */
    private static final String DEBUG_HINT_TEXT = "Press SPACE for debug";

    /** Horizontal position for role hint text (pixels). */
    private static final float ROLE_HINT_X = 12f;
    /** Vertical offset for player side label from top (pixels). */
    private static final float ROLE_HINT_LABEL_Y_OFFSET = 42f;
    /** Vertical offset for debug hint from top (pixels). */
    private static final float ROLE_HINT_DEBUG_Y_OFFSET = 66f;

    DesktopGameRenderer(ShapeRenderer shapeRenderer, SpriteBatch batch, OrthographicCamera camera,
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
        return true;
    }

    @Override
    public void renderInputPrompt(String message, float worldWidth, float worldHeight) {
        renderInputPromptWithHint(message, MALLET_CONTROL_HINT, 0f, INPUT_HINT_Y_OFFSET, worldWidth, worldHeight);
    }
}
