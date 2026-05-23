package no.ntnu.ping404.screens;

import no.ntnu.ping404.utils.Constants;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

public abstract class BaseScreen extends ScreenAdapter {

    // Setting size of the screen according to Constants file
    // Menus use portrait orientation
    protected static final float WORLD_WIDTH = Constants.MENU_WIDTH;
    protected static final float WORLD_HEIGHT = Constants.MENU_HEIGHT;

    // Button defaults - fill most of screen width with 50px margin on each side
    protected static final float BUTTON_WIDTH = WORLD_WIDTH - 100;
    protected static final float BUTTON_HEIGHT = 100;
    protected static final float BUTTON_CORNER_RADIUS = 35;
    protected static final float BUTTON_SPACING = 40;

    // Colors based on Figma prototype
    protected static final Color TOP_COLOR = new Color(0.24f, 0.28f, 0.95f, 1f);
    protected static final Color BOTTOM_COLOR = new Color(0.05f, 0.05f, 0.15f, 1f);
    protected static final Color BUTTON_COLOR = new Color(0.65f, 0.65f, 0.72f, 1f);
    protected static final Color BUTTON_PRESSED_COLOR = new Color(0.55f, 0.55f, 0.62f, 1f);
    protected static final Color BUTTON_TEXT_COLOR = new Color(0.15f, 0.15f, 0.20f, 1f);
    protected static final Color ACCENT_COLOR = new Color(0.40f, 0.75f, 1.0f, 1f);
    protected static final Color SUCCESS_COLOR = new Color(0.30f, 0.85f, 0.45f, 1f);
    protected static final Color DANGER_COLOR = new Color(0.95f, 0.30f, 0.30f, 1f);

    // Shared UI colors for disabled/semi-transparent elements
    protected static final Color DISABLED_BUTTON_COLOR = new Color(0.35f, 0.35f, 0.40f, 0.6f);
    protected static final Color PANEL_BACKGROUND_COLOR = new Color(0.15f, 0.15f, 0.30f, 0.8f);
    protected static final Color TEXT_DIM_COLOR = new Color(1f, 1f, 1f, 0.7f);
    protected static final Color TEXT_MUTED_COLOR = new Color(1f, 1f, 1f, 0.6f);
    protected static final Color TEXT_SUBTLE_COLOR = new Color(1f, 1f, 1f, 0.5f);
    protected static final Color TEXT_FAINT_COLOR = new Color(1f, 1f, 1f, 0.4f);

    // Common layout constants
    protected static final float BOTTOM_BUTTON_Y = 50f;
    protected static final float DOT_ANIMATION_INTERVAL = 0.5f;

    protected final Game game;

    protected OrthographicCamera camera;
    protected Viewport viewport;
    protected SpriteBatch batch;
    protected ShapeRenderer shapeRenderer;
    protected BitmapFont titleFont;
    protected BitmapFont buttonFont;
    protected BitmapFont smallFont;
    protected BitmapFont largeFont;
    protected GlyphLayout glyphLayout;

    // Touch state
    private final Vector3 touchPos = new Vector3();

    // Text input state
    private boolean textInputActive = false;
    private StringBuilder textInputBuffer = new StringBuilder();
    private String textInputFieldId = "";
    private String textInputTitle = "";
    private float cursorBlinkTimer = 0f;
    private boolean cursorVisible = true;
    private static final int MAX_INPUT_LENGTH = 20;

    public BaseScreen(Game game) {
        this.game = game;
    }

    /**
     * Posts {@code update} to the LibGDX render thread when running in the engine,
     * or executes it synchronously when {@code Gdx.app} is null (unit tests).
     */
    protected static void postOnRenderThread(Runnable update) {
        if (Gdx.app != null) {
            Gdx.app.postRunnable(update);
        } else {
            update.run();
        }
    }

    /**
     * Returns the minimum viewport width for this screen.
     * Override to use a different viewport size (e.g., landscape for game).
     */
    protected float getMinViewportWidth() {
        return WORLD_WIDTH;
    }

    /**
     * Returns the minimum viewport height for this screen.
     * Override to use a different viewport size (e.g., landscape for game).
     */
    protected float getMinViewportHeight() {
        return WORLD_HEIGHT;
    }

    @Override
    public void show() {
        float vpWidth = getMinViewportWidth();
        float vpHeight = getMinViewportHeight();
        
        camera = new OrthographicCamera();
        viewport = new ExtendViewport(vpWidth, vpHeight, camera);
        if (Gdx.graphics != null && Gdx.graphics.getWidth() > 0 && Gdx.graphics.getHeight() > 0) {
            viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
        }
        camera.position.set(viewport.getWorldWidth() / 2f, viewport.getWorldHeight() / 2f, 0);
        camera.update();

        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        glyphLayout = new GlyphLayout();

        titleFont = new BitmapFont();
        titleFont.setColor(Color.WHITE);
        titleFont.getData().setScale(4f);

        largeFont = new BitmapFont();
        largeFont.setColor(Color.WHITE);
        largeFont.getData().setScale(3f);

        buttonFont = new BitmapFont();
        buttonFont.setColor(BUTTON_TEXT_COLOR);
        buttonFont.getData().setScale(1.8f);

        smallFont = new BitmapFont();
        smallFont.setColor(Color.WHITE);
        smallFont.getData().setScale(1.4f);

        // Set up keyboard input processor for inline text input
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyTyped(char character) {
                if (!textInputActive)
                    return false;
                if (character == '\r' || character == '\n') {
                    String result = textInputBuffer.toString().trim();
                    endTextInput();
                    if (!result.isEmpty()) {
                        onTextInputComplete(textInputFieldId, result);
                    }
                    return true;
                }
                if (character == '\b') {
                    if (textInputBuffer.length() > 0) {
                        textInputBuffer.deleteCharAt(textInputBuffer.length() - 1);
                    }
                    return true;
                }
                if (character >= 32 && textInputBuffer.length() < MAX_INPUT_LENGTH) {
                    textInputBuffer.append(character);
                    return true;
                }
                return false;
            }

            @Override
            public boolean keyDown(int keycode) {
                if (!textInputActive)
                    return false;
                if (keycode == Input.Keys.ESCAPE) {
                    endTextInput();
                    return true;
                }
                return false;
            }
        });
    }

    // Gradient Background
    protected void drawGradientBackground() {
        if (viewport != null) {
            viewport.apply();
        }

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        float worldW = getWorldWidth();
        float worldH = getWorldHeight();
        int segments = 40;
        float segmentHeight = worldH / segments;

        for (int i = 0; i < segments; i++) {
            float t = (float) i / (segments - 1);
            float t2 = (float) Math.min((i + 1), segments - 1) / (segments - 1);

            Color segColor = lerpColor(BOTTOM_COLOR, TOP_COLOR, t);
            Color nextColor = lerpColor(BOTTOM_COLOR, TOP_COLOR, t2);

            shapeRenderer.rect(0, i * segmentHeight, worldW, segmentHeight,
                    segColor, segColor, nextColor, nextColor);
        }

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private Color lerpColor(Color a, Color b, float t) {
        return new Color(
                a.r + t * (b.r - a.r),
                a.g + t * (b.g - a.g),
                a.b + t * (b.b - a.b),
                1f);
    }

    // Rounded Rectangle
    protected void drawRoundedRect(float x, float y, float w, float h, float r, Color color) {
        if (r > h / 2f)
            r = h / 2f;

        shapeRenderer.setColor(color);

        // Center rectangle
        shapeRenderer.rect(x + r, y, w - 2 * r, h);
        // Left rectangle
        shapeRenderer.rect(x, y + r, r, h - 2 * r);
        // Right rectangle
        shapeRenderer.rect(x + w - r, y + r, r, h - 2 * r);

        // Corner arcs
        int arcSegments = 20;
        shapeRenderer.arc(x + r, y + r, r, 180, 90, arcSegments);
        shapeRenderer.arc(x + w - r, y + r, r, 270, 90, arcSegments);
        shapeRenderer.arc(x + w - r, y + h - r, r, 0, 90, arcSegments);
        shapeRenderer.arc(x + r, y + h - r, r, 90, 90, arcSegments);
    }

    // Button Drawing Helpers

    /**
     * Draws a rounded button with centered text label.
     * Must be called between shapeRenderer.begin() and shapeRenderer.end() for the
     * shape,
     * then separately for the text in a batch block.
     */
    protected void drawButton(Rectangle rect, boolean pressed) {
        Color fillColor = pressed ? BUTTON_PRESSED_COLOR : BUTTON_COLOR;
        drawRoundedRect(rect.x, rect.y, rect.width, rect.height, BUTTON_CORNER_RADIUS, fillColor);
    }

    protected void drawButtonLabel(String text, Rectangle button, BitmapFont font) {
        glyphLayout.setText(font, text);
        float textX = button.x + (button.width - glyphLayout.width) / 2f;
        float textY = button.y + (button.height + glyphLayout.height) / 2f;
        font.draw(batch, text, textX, textY);
    }

    protected void drawButtonLabel(String text, Rectangle button) {
        drawButtonLabel(text, button, buttonFont);
    }

    /**
     * Draws a colored button (non-default color).
     */
    protected void drawColoredButton(Rectangle rect, boolean pressed, Color color) {
        Color fillColor = pressed
                ? new Color(color.r * 0.8f, color.g * 0.8f, color.b * 0.8f, color.a)
                : color;
        drawRoundedRect(rect.x, rect.y, rect.width, rect.height, BUTTON_CORNER_RADIUS, fillColor);
    }

    // Text Drawing Helpers
    protected void drawCenteredText(BitmapFont font, String text, float y) {
        glyphLayout.setText(font, text);
        float x = (getWorldWidth() - glyphLayout.width) / 2f;
        font.draw(batch, text, x, y);
    }

    protected void drawCenteredText(BitmapFont font, String text, float y, Color color) {
        Color prev = font.getColor().cpy();
        font.setColor(color);
        drawCenteredText(font, text, y);
        font.setColor(prev);
    }

    // Touch Helpers
    /**
     * Get touch position in world coordinates.
     */
    protected Vector3 getTouchWorldPos() {
        touchPos.set(Gdx.input.getX(), Gdx.input.getY(), 0);
        viewport.unproject(touchPos);
        return touchPos;
    }

    /**
     * Check if a rectangle was just tapped.
     * Call this once per frame in your render method.
     */
    protected boolean isTouched(Rectangle rect) {
        if (textInputActive)
            return false;
        if (Gdx.input.justTouched()) {
            Vector3 pos = getTouchWorldPos();
            return rect.contains(pos.x, pos.y);
        }
        return false;
    }

    /**
     * Check if a rectangle is currently being pressed.
     */
    protected boolean isPressed(Rectangle rect) {
        if (textInputActive)
            return false;
        if (Gdx.input.isTouched()) {
            Vector3 pos = getTouchWorldPos();
            return rect.contains(pos.x, pos.y);
        }
        return false;
    }

    /**
     * Returns the current world height from the viewport.
     * With ExtendViewport, this may be larger than WORLD_HEIGHT when the window is taller.
     */
    protected float getWorldHeight() {
        if (viewport == null) {
            return getMinViewportHeight();
        }
        return viewport.getWorldHeight();
    }

    /**
     * Returns the current world width from the viewport.
     * With ExtendViewport, this may be larger than the minimum width on wide screens.
     */
    protected float getWorldWidth() {
        if (viewport == null) {
            return getMinViewportWidth();
        }
        return viewport.getWorldWidth();
    }

    // Button Creation
    protected Rectangle createButton(float centerX, float y) {
        return new Rectangle(centerX - BUTTON_WIDTH / 2f, y, BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    protected Rectangle createButton(float y) {
        return createButton(getWorldWidth() / 2f, y);
    }

    // Inline Text Input

    /**
     * Starts an inline text input overlay.
     * 
     * @param fieldId      identifier for the field being edited
     * @param title        prompt title shown to the user
     * @param currentValue the current value to pre-fill
     */
    protected void startTextInput(String fieldId, String title, String currentValue) {
        textInputActive = true;
        textInputFieldId = fieldId;
        textInputTitle = title;
        textInputBuffer = new StringBuilder(currentValue != null ? currentValue : "");
        cursorBlinkTimer = 0f;
        cursorVisible = true;
        setOnscreenKeyboardVisible(true);
    }

    /**
     * Returns whether an inline text input overlay is currently active.
     */
    protected boolean isTextInputActive() {
        return textInputActive;
    }

    /**
     * Called when the user confirms an inline text input.
     * Override in subclasses to handle the result.
     * 
     * @param fieldId the field identifier passed to {@link #startTextInput}
     * @param text    the confirmed, trimmed text
     */
    protected void onTextInputComplete(String fieldId, String text) {
        // Override in subclasses
    }

    /**
     * Updates the cursor blink and renders the text input overlay.
     * Call this at the end of your render method.
     */
    protected void updateAndRenderTextInput(float delta) {
        if (!textInputActive)
            return;

        cursorBlinkTimer += delta;
        if (cursorBlinkTimer >= 0.5f) {
            cursorBlinkTimer = 0f;
            cursorVisible = !cursorVisible;
        }

        // Dark overlay
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        float worldW = getWorldWidth();
        float worldH = getWorldHeight();
        shapeRenderer.setColor(new Color(0, 0, 0, 0.7f));
        shapeRenderer.rect(0, 0, worldW, worldH);

        // Panel
        float panelW = 380, panelH = 180;
        float panelX = (worldW - panelW) / 2f;
        float panelY = (worldH - panelH) / 2f;
        drawRoundedRect(panelX, panelY, panelW, panelH, 20,
                new Color(0.12f, 0.12f, 0.25f, 0.95f));

        // Text field background
        float fieldW = panelW - 40, fieldH = 45;
        float fieldX = panelX + 20;
        float fieldY = panelY + 60;
        drawRoundedRect(fieldX, fieldY, fieldW, fieldH, 10,
                new Color(0.08f, 0.08f, 0.18f, 1f));

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // Text
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        drawCenteredText(smallFont, textInputTitle, panelY + panelH - 20, ACCENT_COLOR);

        String displayText = textInputBuffer.toString() + (cursorVisible ? "|" : "");
        smallFont.setColor(Color.WHITE);
        smallFont.draw(batch, displayText, fieldX + 10, fieldY + 32);

        drawCenteredText(smallFont, "Enter to confirm  |  Esc to cancel",
                panelY + 30, new Color(1, 1, 1, 0.4f));

        batch.end();
    }

    private void endTextInput() {
        textInputActive = false;
        setOnscreenKeyboardVisible(false);
    }

    private void setOnscreenKeyboardVisible(boolean visible) {
        if (Gdx.input != null) {
            Gdx.input.setOnscreenKeyboardVisible(visible);
        }
    }

    // Lifecycle

    @Override
    public void hide() {
        endTextInput();
    }

    @Override
    public void resize(int width, int height) {
        if (viewport == null || camera == null) {
            return;
        }
        if (width <= 0 || height <= 0) {
            return;
        }
        viewport.update(width, height, true);
        camera.position.set(viewport.getWorldWidth() / 2f, viewport.getWorldHeight() / 2f, 0);
        camera.update();
    }

    @Override
    public void dispose() {
        if (batch != null)
            batch.dispose();
        if (shapeRenderer != null)
            shapeRenderer.dispose();
        if (titleFont != null)
            titleFont.dispose();
        if (buttonFont != null)
            buttonFont.dispose();
        if (smallFont != null)
            smallFont.dispose();
        if (largeFont != null)
            largeFont.dispose();
    }
}
