package no.ntnu.ping404.screens;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;

import no.ntnu.ping404.audio.AudioManager;

/**
 * Settings screen for PING-404.
 * Toggle sound/music on or off. Delegates to AudioManager for state and persistence.
 */
public class SettingsScreen extends BaseScreen {

    private boolean soundEnabled;
    private boolean musicEnabled;

    private Rectangle soundButton;
    private Rectangle musicButton;
    private Rectangle backButton;

    public SettingsScreen(Game game) {
        super(game);
    }

    @Override
    public void show() {
        super.show();

        soundEnabled = AudioManager.isSoundEnabled();
        musicEnabled = AudioManager.isMusicEnabled();
        layoutButtons();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        layoutButtons();
    }

    private void layoutButtons() {
        float h = getWorldHeight();
        float centerY = h / 2f;

        soundButton = createButton(centerY + 80);
        musicButton = createButton(centerY - 80);
        backButton = createButton(50);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        handleInput();

        drawGradientBackground();

        // Layout constants
        final float h = getWorldHeight();
        final float TITLE_POS_Y = 80;
        final float LABEL_OFFSET_Y = 40;

        // Draw buttons
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Sound toggle - green when on, gray when off
        if (soundEnabled) {
            drawColoredButton(soundButton, isPressed(soundButton), SUCCESS_COLOR);
        } else {
            drawColoredButton(soundButton, isPressed(soundButton), new Color(0.45f, 0.45f, 0.50f, 1f));
        }

        // Music toggle
        if (musicEnabled) {
            drawColoredButton(musicButton, isPressed(musicButton), SUCCESS_COLOR);
        } else {
            drawColoredButton(musicButton, isPressed(musicButton), new Color(0.45f, 0.45f, 0.50f, 1f));
        }

        drawButton(backButton, isPressed(backButton));

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // Draw text
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        drawCenteredText(titleFont, "Settings", h - TITLE_POS_Y);

        // Labels above buttons
        drawCenteredText(buttonFont, "Sound Effects", soundButton.y + soundButton.height + LABEL_OFFSET_Y, Color.WHITE);
        drawCenteredText(buttonFont, "Music", musicButton.y + musicButton.height + LABEL_OFFSET_Y, Color.WHITE);

        // Button labels showing state
        buttonFont.setColor(Color.WHITE);
        drawButtonLabel(soundEnabled ? "Sound: ON" : "Sound: OFF", soundButton);
        drawButtonLabel(musicEnabled ? "Music: ON" : "Music: OFF", musicButton);
        buttonFont.setColor(BUTTON_TEXT_COLOR);

        drawButtonLabel("Back", backButton);

        batch.end();
    }

    private void handleInput() {
        if (isTouched(soundButton)) {
            soundEnabled = !soundEnabled;
            AudioManager.setSoundEnabled(soundEnabled);
            AudioManager.playMenuSelect();
            return;
        }

        if (isTouched(musicButton)) {
            musicEnabled = !musicEnabled;
            AudioManager.setMusicEnabled(musicEnabled);
            if (musicEnabled) {
                AudioManager.startMenuMusic();
            }
            AudioManager.playMenuSelect();
            return;
        }

        if (isTouched(backButton)) {
            AudioManager.playMenuSelect();
            game.setScreen(new MenuScreen(game));
        }
    }
}
