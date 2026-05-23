package no.ntnu.ping404.screens;

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * Factory for creating platform-specific game renderers with auto-detection.
 *
 * <p>Automatically detects the current platform (Desktop or Android) and
 * returns the appropriate {@link GameRendererView} implementation. This
 * enables true split views with distinct UI behavior per platform.</p>
 *
 * <p><b>Usage Example</b></p>
 * <pre>{@code
 * GameRendererView renderer = GameRendererFactory.create(
 *     shapeRenderer, batch, camera, smallFont, largeFont, buttonFont, glyphLayout
 * );
 * renderer.renderTable();
 * renderer.renderRoleHint(playerSideLabel, worldHeight);
 * }</pre>
 *
 * @see DesktopGameRenderer
 * @see AndroidGameRenderer
 */
public final class GameRendererFactory {

    private GameRendererFactory() {
        // Utility class, no instantiation
    }

    /**
     * Creates a platform-appropriate game renderer using auto-detection.
     *
     * <p>On Desktop platforms, returns a {@link DesktopGameRenderer} with
     * keyboard shortcut hints. On Android, returns an {@link AndroidGameRenderer}
     * with touch-optimized UI.</p>
     *
     * @param shapeRenderer LibGDX shape renderer for geometric primitives
     * @param batch         sprite batch for text and image rendering
     * @param camera        orthographic camera for projection
     * @param smallFont     font for small text
     * @param largeFont     font for large text (scores, countdown)
     * @param buttonFont    font for buttons
     * @param glyphLayout   reusable glyph layout for text measurement
     * @return platform-specific GameRendererView implementation
     */
    public static GameRendererView create(ShapeRenderer shapeRenderer, SpriteBatch batch,
                                          OrthographicCamera camera, BitmapFont smallFont,
                                          BitmapFont largeFont, BitmapFont buttonFont,
                                          GlyphLayout glyphLayout) {
        if (isDesktopPlatform()) {
            return new DesktopGameRenderer(shapeRenderer, batch, camera, smallFont,
                                           largeFont, buttonFont, glyphLayout);
        } else {
            return new AndroidGameRenderer(shapeRenderer, batch, camera, smallFont,
                                           largeFont, buttonFont, glyphLayout);
        }
    }

    /**
     * Returns true if running on a desktop platform (Windows, macOS, Linux).
     */
    public static boolean isDesktopPlatform() {
        return Gdx.app != null && Gdx.app.getType() == ApplicationType.Desktop;
    }

    /**
     * Returns true if running on an Android platform.
     */
    public static boolean isAndroidPlatform() {
        return Gdx.app != null && Gdx.app.getType() == ApplicationType.Android;
    }
}
