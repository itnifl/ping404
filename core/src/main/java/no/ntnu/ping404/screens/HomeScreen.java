package no.ntnu.ping404.screens;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Rectangle;

/**
 * Home screen for PING-404 (FR1.1: Main Menu).
 * Displays the title, mascot picture, and navigation buttons.
 *
 * <p>Navigation:</p>
 * <ul>
 *   <li>Host Game \u2192 {@link HostScreen}</li>
 *   <li>Join Game \u2192 {@link JoinScreen}</li>
 *   <li>Settings \u2192 {@link SettingsScreen}</li>
 * </ul>
 */
public class HomeScreen extends BaseScreen {

    private Texture mascotTexture;

    private Rectangle hostButton;
    private Rectangle joinButton;
    private Rectangle settingsButton;

    public HomeScreen(Game game) {
        super(game);
    }

    @Override
    public void show() {
        super.show();

        // Load mascot picture
        mascotTexture = new Texture(Gdx.files.internal("mascot.jpeg"));
        mascotTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

        layoutButtons();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        layoutButtons();
    }

    private void layoutButtons() {
        // Position buttons in the lower portion with proper spacing
        float startY = 50;
        settingsButton = createButton(startY);
        joinButton = createButton(startY + BUTTON_HEIGHT + BUTTON_SPACING);
        hostButton = createButton(startY + 2 * (BUTTON_HEIGHT + BUTTON_SPACING));
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Handle input
        handleInput();

        // Draw gradient background
        drawGradientBackground();

        // Draw title and mascot
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        drawCenteredText(titleFont, "PING-404", getWorldHeight() - 60);

        // Draw mascot centered
        float mascotSize = 250;
        float mascotX = (getWorldWidth() - mascotSize) / 2f;
        float mascotY = getWorldHeight() - 110 - mascotSize;
        batch.draw(mascotTexture, mascotX, mascotY, mascotSize, mascotSize);

        batch.end();

        // Draw buttons
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled);

        drawButton(hostButton, isPressed(hostButton));
        drawButton(joinButton, isPressed(joinButton));
        drawButton(settingsButton, isPressed(settingsButton));

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // Draw button labels
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawButtonLabel("Host Game", hostButton);
        drawButtonLabel("Join Game", joinButton);
        drawButtonLabel("Settings", settingsButton);
        batch.end();
    }

    private void handleInput() {
        if (isTouched(hostButton)) {
            game.setScreen(new HostScreen(game));
            return;
        }
        if (isTouched(joinButton)) {
            game.setScreen(new JoinScreen(game));
            return;
        }
        if (isTouched(settingsButton)) {
            game.setScreen(new SettingsScreen(game));
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        if (mascotTexture != null) mascotTexture.dispose();
    }
}
