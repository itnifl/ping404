package no.ntnu.ping404.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import no.ntnu.ping404.utils.Constants;

/**
 * Abstract base class containing shared rendering logic for all platforms.
 *
 * <p>Provides common game element rendering (table, puck, mallets, HUD, etc.)
 * while allowing platform-specific subclasses to customize layout constants
 * and override platform-specific interface methods.</p>
 *
 * <p><b>Layout Constants</b></p>
 * <p>Override {@link #getHudHeight()}, {@link #getPauseButtonSize()}, etc. to
 * customize dimensions per platform without duplicating rendering logic.</p>
 *
 * <p><b>Platform Methods</b></p>
 * <p>Override {@link #renderPlatformOverlay} and {@link #renderInputPrompt}
 * for platform-specific UI elements.</p>
 *
 * @see DesktopGameRenderer
 * @see AndroidGameRenderer
 */
abstract class AbstractGameRenderer implements GameRendererView {

    /** Default HUD height for desktop (pixels). */
    protected static final float DEFAULT_HUD_HEIGHT = 75f;
    /** Default pause button size for desktop (pixels). */
    protected static final float DEFAULT_PAUSE_BUTTON_SIZE = 45f;
    /** Default minimum touch target for desktop (pixels). */
    protected static final float DEFAULT_MIN_TOUCH_TARGET = 44f;
    /** Default metrics panel width (pixels). */
    protected static final float DEFAULT_METRICS_PANEL_WIDTH = 430f;
    /** Default metrics panel height (pixels). */
    protected static final float DEFAULT_METRICS_PANEL_HEIGHT = 96f;

    private static final float HUD_BACKGROUND_ALPHA = 0.5f;
    private static final float BUTTON_PRESSED_DARKEN_FACTOR = 0.8f;

    protected static final Color TABLE_COLOR = new Color(0.08f, 0.12f, 0.22f, 1f);
    protected static final Color TABLE_BORDER_COLOR = new Color(0.25f, 0.35f, 0.55f, 1f);
    protected static final Color LINE_COLOR = new Color(0.20f, 0.30f, 0.50f, 0.6f);
    protected static final Color PLAYER_MALLET_COLOR = new Color(0.30f, 0.70f, 1.0f, 1f);
    protected static final Color OPPONENT_MALLET_COLOR = new Color(1.0f, 0.40f, 0.35f, 1f);
    protected static final Color PUCK_COLOR = Color.WHITE;
    protected static final Color GOAL_COLOR = new Color(0.20f, 0.85f, 0.45f, 0.7f);
    protected static final Color PUCK_GLOW_COLOR = new Color(1f, 1f, 1f, 0.15f);
    protected static final Color PUCK_INNER_COLOR = new Color(0.85f, 0.85f, 0.85f, 1f);
    protected static final Color PLAYER_MALLET_GLOW_COLOR = new Color(0.30f, 0.70f, 1.0f, 0.3f);
    protected static final Color PLAYER_MALLET_INNER_COLOR = new Color(0.5f, 0.85f, 1.0f, 1f);
    protected static final Color OPPONENT_MALLET_GLOW_COLOR = new Color(1.0f, 0.40f, 0.35f, 0.3f);
    protected static final Color OPPONENT_MALLET_INNER_COLOR = new Color(1.0f, 0.7f, 0.6f, 1f);
    protected static final Color HUD_BACKGROUND_COLOR = new Color(0f, 0f, 0f, HUD_BACKGROUND_ALPHA);
    protected static final Color PAUSE_BUTTON_BACKGROUND_COLOR = new Color(1f, 1f, 1f, 0.2f);
    protected static final Color GOAL_FLASH_OVERLAY_COLOR = new Color(0f, 0f, 0f, 0.4f);
    protected static final Color PAUSE_OVERLAY_COLOR = new Color(0f, 0f, 0f, 0.7f);
    protected static final Color PAUSE_PANEL_COLOR = new Color(0.12f, 0.12f, 0.25f, 0.95f);
    protected static final Color METRICS_PANEL_COLOR = new Color(0f, 0f, 0f, 0.55f);
    protected static final Color DISABLED_BUTTON_COLOR = new Color(0.4f, 0.4f, 0.4f, 0.6f);

    protected final ShapeRenderer shapeRenderer;
    protected final SpriteBatch batch;
    protected final OrthographicCamera camera;
    protected final BitmapFont smallFont;
    protected final BitmapFont largeFont;
    protected final BitmapFont buttonFont;
    protected final GlyphLayout glyphLayout;
    protected final Color reusableColor = new Color();

    AbstractGameRenderer(ShapeRenderer shapeRenderer, SpriteBatch batch, OrthographicCamera camera,
                         BitmapFont smallFont, BitmapFont largeFont, BitmapFont buttonFont,
                         GlyphLayout glyphLayout) {
        this.shapeRenderer = shapeRenderer;
        this.batch = batch;
        this.camera = camera;
        this.smallFont = smallFont;
        this.largeFont = largeFont;
        this.buttonFont = buttonFont;
        this.glyphLayout = glyphLayout;
    }

    @Override
    public void renderTable() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        drawRoundedRect(Constants.TABLE_X - 4, Constants.TABLE_Y - 4,
                Constants.TABLE_WIDTH + 8, Constants.TABLE_HEIGHT + 8, 12, TABLE_BORDER_COLOR);
        drawRoundedRect(Constants.TABLE_X, Constants.TABLE_Y,
                Constants.TABLE_WIDTH, Constants.TABLE_HEIGHT, 10, TABLE_COLOR);

        shapeRenderer.setColor(LINE_COLOR);
        shapeRenderer.rectLine(Constants.CENTER_X, Constants.TABLE_Y + 10,
                Constants.CENTER_X, Constants.TABLE_Y + Constants.TABLE_HEIGHT - 10, 2);

        shapeRenderer.setColor(LINE_COLOR);
        int circleSegments = 40;
        float centerCircleRadius = 50f;
        for (int i = 0; i < circleSegments; i++) {
            float a1 = (float) i / circleSegments * MathUtils.PI2;
            float a2 = (float) (i + 1) / circleSegments * MathUtils.PI2;
            shapeRenderer.rectLine(
                    Constants.CENTER_X + MathUtils.cos(a1) * centerCircleRadius,
                    Constants.CENTER_Y + MathUtils.sin(a1) * centerCircleRadius,
                    Constants.CENTER_X + MathUtils.cos(a2) * centerCircleRadius,
                    Constants.CENTER_Y + MathUtils.sin(a2) * centerCircleRadius,
                    2
            );
        }

        float goalBottom = Constants.CENTER_Y - Constants.GOAL_WIDTH / 2f;
        shapeRenderer.setColor(GOAL_COLOR);
        shapeRenderer.rect(Constants.TABLE_X - 3, goalBottom, 6, Constants.GOAL_WIDTH);
        shapeRenderer.rect(Constants.TABLE_X + Constants.TABLE_WIDTH - 3, goalBottom, 6, Constants.GOAL_WIDTH);

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    @Override
    public void renderPuck(float x, float y) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        shapeRenderer.setColor(PUCK_GLOW_COLOR);
        shapeRenderer.circle(x, y, Constants.PUCK_RADIUS + 8, 25);

        shapeRenderer.setColor(PUCK_COLOR);
        shapeRenderer.circle(x, y, Constants.PUCK_RADIUS, 25);

        shapeRenderer.setColor(PUCK_INNER_COLOR);
        shapeRenderer.circle(x, y, Constants.PUCK_RADIUS * 0.5f, 20);

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    @Override
    public void renderMalletsBySlot(float slot1X, float slot1Y, float slot2X, float slot2Y) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        shapeRenderer.setColor(PLAYER_MALLET_GLOW_COLOR);
        shapeRenderer.circle(slot1X, slot1Y, Constants.MALLET_RADIUS + 6, 30);
        shapeRenderer.setColor(PLAYER_MALLET_COLOR);
        shapeRenderer.circle(slot1X, slot1Y, Constants.MALLET_RADIUS, 30);
        shapeRenderer.setColor(PLAYER_MALLET_INNER_COLOR);
        shapeRenderer.circle(slot1X, slot1Y, Constants.MALLET_RADIUS * 0.4f, 20);

        shapeRenderer.setColor(OPPONENT_MALLET_GLOW_COLOR);
        shapeRenderer.circle(slot2X, slot2Y, Constants.MALLET_RADIUS + 6, 30);
        shapeRenderer.setColor(OPPONENT_MALLET_COLOR);
        shapeRenderer.circle(slot2X, slot2Y, Constants.MALLET_RADIUS, 30);
        shapeRenderer.setColor(OPPONENT_MALLET_INNER_COLOR);
        shapeRenderer.circle(slot2X, slot2Y, Constants.MALLET_RADIUS * 0.4f, 20);

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    @Override
    public void renderHUD(String playerName, String opponentName, int playerScore, int opponentScore,
                          Rectangle pauseButton, float worldWidth, float worldHeight,
                          boolean pauseFlashing, String pauseErrorMessage) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        shapeRenderer.setColor(HUD_BACKGROUND_COLOR);
        shapeRenderer.rect(0, worldHeight - getHudHeight(), worldWidth, getHudHeight());

        if (pauseFlashing) {
            shapeRenderer.setColor(BaseScreen.DANGER_COLOR);
        } else {
            shapeRenderer.setColor(PAUSE_BUTTON_BACKGROUND_COLOR);
        }
        float pauseRadius = getPauseButtonSize() / 2f;
        shapeRenderer.circle(pauseButton.x + pauseButton.width / 2f,
                pauseButton.y + pauseButton.height / 2f, pauseRadius, 20);

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        smallFont.setColor(PLAYER_MALLET_COLOR);
        smallFont.draw(batch, playerName, 15, worldHeight - 20);
        largeFont.setColor(Color.WHITE);
        glyphLayout.setText(largeFont, String.valueOf(playerScore));
        largeFont.draw(batch, String.valueOf(playerScore), worldWidth / 2f - glyphLayout.width - 20, worldHeight - 20);

        glyphLayout.setText(largeFont, "-");
        largeFont.draw(batch, "-", worldWidth / 2f - glyphLayout.width / 2f, worldHeight - 20);

        glyphLayout.setText(largeFont, String.valueOf(opponentScore));
        largeFont.draw(batch, String.valueOf(opponentScore), worldWidth / 2f + 20, worldHeight - 20);
        smallFont.setColor(OPPONENT_MALLET_COLOR);
        glyphLayout.setText(smallFont, opponentName);
        float pauseButtonSpace = getPauseButtonSize() + getPauseButtonSize() / 3f + 10;
        smallFont.draw(batch, opponentName, worldWidth - glyphLayout.width - pauseButtonSpace, worldHeight - 20);

        smallFont.setColor(Color.WHITE);
        smallFont.draw(batch, "II", pauseButton.x + 14, pauseButton.y + 32);

        if (pauseFlashing && pauseErrorMessage != null && !pauseErrorMessage.isEmpty()) {
            smallFont.setColor(BaseScreen.DANGER_COLOR);
            glyphLayout.setText(smallFont, "Host only");
            smallFont.draw(batch, "Host only", worldWidth - glyphLayout.width - 15, worldHeight - getHudHeight() - 8);
            smallFont.setColor(Color.WHITE);
        }

        batch.end();
    }

    @Override
    public void renderMetricsOverlay(long clientRttMs,
                                     float clientSnapshotRateHz,
                                     float clientSnapshotJitterMs,
                                     String serverLine1,
                                     String serverLine2,
                                     float worldHeight) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(METRICS_PANEL_COLOR);
        shapeRenderer.rect(0, worldHeight - getMetricsPanelHeight(), getMetricsPanelWidth(), getMetricsPanelHeight());
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        smallFont.setColor(Color.WHITE);
        smallFont.draw(batch,
            String.format("Client RTT: %dms  Rx: %.1fHz  Jit: %.1fms",
                clientRttMs,
                clientSnapshotRateHz,
                clientSnapshotJitterMs),
            12,
            worldHeight - 18);
        smallFont.draw(batch, serverLine1, 12, worldHeight - 44);
        smallFont.draw(batch, serverLine2, 12, worldHeight - 70);
        batch.end();
    }

    @Override
    public void renderCountdown(float timer) {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        int count = (int) Math.ceil(timer);
        if (count <= 0) count = 1;

        largeFont.setColor(Color.WHITE);
        largeFont.getData().setScale(5f);
        drawCenteredText(largeFont, String.valueOf(count), Constants.CENTER_Y + 30);
        largeFont.getData().setScale(3f);

        batch.end();
    }

    @Override
    public void renderGoalFlash(String heading, String message, int playerScore, int opponentScore,
                                float worldWidth, float worldHeight) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(GOAL_FLASH_OVERLAY_COLOR);
        shapeRenderer.rect(0, 0, worldWidth, worldHeight);
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawCenteredText(largeFont, heading, Constants.CENTER_Y + 40, BaseScreen.SUCCESS_COLOR);
        drawCenteredText(smallFont, message, Constants.CENTER_Y - 10, Color.WHITE);
        drawCenteredText(smallFont, playerScore + " - " + opponentScore, Constants.CENTER_Y - 50, Color.WHITE);
        batch.end();
    }

    @Override
    public void renderPauseOverlay(Rectangle resumeButton, Rectangle quitButton,
                                   boolean resumePressed, boolean quitPressed,
                                   boolean canResume, String errorMessage,
                                   float worldWidth, float worldHeight) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        shapeRenderer.setColor(PAUSE_OVERLAY_COLOR);
        shapeRenderer.rect(0, 0, worldWidth, worldHeight);

        drawRoundedRect(worldWidth / 2f - 160, worldHeight / 2f - 120, 320, 280, 20,
            PAUSE_PANEL_COLOR);

        if (canResume) {
            drawColoredButton(resumeButton, resumePressed, BaseScreen.ACCENT_COLOR);
        } else {
            drawColoredButton(resumeButton, false, DISABLED_BUTTON_COLOR);
        }
        drawColoredButton(quitButton, quitPressed, BaseScreen.DANGER_COLOR);

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        drawCenteredText(largeFont, "     PAUSED!", worldHeight / 2f + 70, Color.WHITE);

        buttonFont.setColor(canResume ? Color.WHITE : Color.GRAY);
        drawButtonLabel("Resume", resumeButton);
        buttonFont.setColor(Color.WHITE);
        drawButtonLabel("Quit", quitButton);
        buttonFont.setColor(BaseScreen.BUTTON_TEXT_COLOR);

        if (!canResume) {
            smallFont.setColor(Color.GRAY);
            drawCenteredText(smallFont, "Host only", resumeButton.y - 15);
            smallFont.setColor(Color.WHITE);
        }

        if (errorMessage != null && !errorMessage.isEmpty()) {
            drawCenteredText(smallFont, errorMessage, worldHeight / 2f - 90, BaseScreen.DANGER_COLOR);
        }

        batch.end();
    }

    protected void drawRoundedRect(float x, float y, float w, float h, float r, Color color) {
        if (r > h / 2f) r = h / 2f;
        shapeRenderer.setColor(color);
        shapeRenderer.rect(x + r, y, w - 2 * r, h);
        shapeRenderer.rect(x, y + r, r, h - 2 * r);
        shapeRenderer.rect(x + w - r, y + r, r, h - 2 * r);
        int arcSegments = 20;
        shapeRenderer.arc(x + r, y + r, r, 180, 90, arcSegments);
        shapeRenderer.arc(x + w - r, y + r, r, 270, 90, arcSegments);
        shapeRenderer.arc(x + w - r, y + h - r, r, 0, 90, arcSegments);
        shapeRenderer.arc(x + r, y + h - r, r, 90, 90, arcSegments);
    }

    protected void drawColoredButton(Rectangle rect, boolean pressed, Color color) {
        Color fillColor = color;
        if (pressed) {
            reusableColor.set(
                    color.r * BUTTON_PRESSED_DARKEN_FACTOR,
                    color.g * BUTTON_PRESSED_DARKEN_FACTOR,
                    color.b * BUTTON_PRESSED_DARKEN_FACTOR,
                    color.a
            );
            fillColor = reusableColor;
        }
        drawRoundedRect(rect.x, rect.y, rect.width, rect.height, 35, fillColor);
    }

    protected void drawCenteredText(BitmapFont font, String text, float y) {
        glyphLayout.setText(font, text);
        float x = (Constants.GAME_WIDTH - glyphLayout.width) / 2f;
        font.draw(batch, text, x, y);
    }

    protected void drawCenteredText(BitmapFont font, String text, float y, Color color) {
        Color prev = font.getColor();
        float prevR = prev.r;
        float prevG = prev.g;
        float prevB = prev.b;
        float prevA = prev.a;
        font.setColor(color);
        drawCenteredText(font, text, y);
        font.setColor(prevR, prevG, prevB, prevA);
    }

    protected void drawButtonLabel(String text, Rectangle button) {
        glyphLayout.setText(buttonFont, text);
        float textX = button.x + (button.width - glyphLayout.width) / 2f;
        float textY = button.y + (button.height + glyphLayout.height) / 2f;
        buttonFont.draw(batch, text, textX, textY);
    }

    @Override
    public float getHudHeight() {
        return DEFAULT_HUD_HEIGHT;
    }

    @Override
    public float getPauseButtonSize() {
        return DEFAULT_PAUSE_BUTTON_SIZE;
    }

    @Override
    public float getMinTouchTargetSize() {
        return DEFAULT_MIN_TOUCH_TARGET;
    }

    @Override
    public float getMetricsPanelWidth() {
        return DEFAULT_METRICS_PANEL_WIDTH;
    }

    @Override
    public float getMetricsPanelHeight() {
        return DEFAULT_METRICS_PANEL_HEIGHT;
    }

    @Override
    public void renderPlatformOverlay(float worldWidth, float worldHeight) {
        // Default: no platform overlay. Subclasses can override.
    }

    @Override
    public void renderInputPrompt(String message, float worldWidth, float worldHeight) {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        smallFont.setColor(Color.WHITE);
        glyphLayout.setText(smallFont, message);
        float x = (worldWidth - glyphLayout.width) / 2f;
        float y = worldHeight / 4f;
        smallFont.draw(batch, message, x, y);
        batch.end();
    }

    /**
     * Renders an input prompt with a platform-specific hint below it.
     * Shared implementation used by subclasses to reduce code duplication.
     *
     * @param message      main prompt message
     * @param hintText     platform-specific hint (e.g., "Use mouse to control mallet")
     * @param hintXOffset  horizontal offset for hint text from centered position
     * @param hintYOffset  vertical offset below the main message
     * @param worldWidth   viewport width
     * @param worldHeight  viewport height
     */
    protected void renderInputPromptWithHint(String message, String hintText, float hintXOffset,
                                             float hintYOffset, float worldWidth, float worldHeight) {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        smallFont.setColor(Color.WHITE);
        glyphLayout.setText(smallFont, message);
        float x = (worldWidth - glyphLayout.width) / 2f;
        float y = worldHeight / 4f;
        smallFont.draw(batch, message, x, y);
        smallFont.draw(batch, hintText, x - hintXOffset, y - hintYOffset);
        batch.end();
    }
}
