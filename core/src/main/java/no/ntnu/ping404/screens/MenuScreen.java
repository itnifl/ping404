package no.ntnu.ping404.screens;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import no.ntnu.ping404.audio.AudioManager;
import no.ntnu.ping404.network.GameConfig;
import no.ntnu.ping404.utils.PreferencesManager;

/**
 * Main menu screen with host/join/settings navigation.
 *
 * <p>Requirements covered:</p>
 * <ul>
 *   <li>FR1.1: Main menu on app start</li>
 *   <li>FR1.3: Name + server setup before joining</li>
 *   <li>FR1.6: Win-score selector for host flow</li>
 * </ul>
 */
public class MenuScreen extends BaseScreen {

    // Field identifiers for text input
    private static final String FIELD_ID_NAME = "name";
    private static final String FIELD_ID_IP = "ip";

    // Win score bounds
    private static final int MIN_WIN_SCORE = 3;
    private static final int MAX_WIN_SCORE = 15;

    // Layout constants
    private static final float TOP_MARGIN = 70f;
    private static final float BOTTOM_MARGIN = 20f;
    private static final float WIN_BUTTON_SIZE = 50f;
    private static final float WIN_MINUS_OFFSET_X = -120f;
    private static final float WIN_PLUS_OFFSET_X = 70f;
    private static final float WIN_SCORE_BOX_WIDTH = 200f;

    private String playerName = "";
    private String serverIp = PreferencesManager.DEFAULT_SERVER_IP;
    private int winScore = GameConfig.getWinScore();
    private String statusMessage = "";

    private Rectangle nameButton;
    private Rectangle ipButton;
    private Rectangle hostButton;
    private Rectangle joinButton;
    private Rectangle settingsButton;
    private Rectangle winMinusButton;
    private Rectangle winPlusButton;

    private PreferencesManager preferencesManager;

    public MenuScreen(Game game) {
        super(game);
    }

    /**
     * Creates a menu screen with preserved player settings.
     * Used when returning from Host/Join screens to keep the name and IP.
     */
    public MenuScreen(Game game, String playerName, String serverIp, int winScore) {
        super(game);
        this.playerName = playerName != null ? playerName : "";
        this.serverIp = (serverIp == null || serverIp.isBlank()) ? PreferencesManager.DEFAULT_SERVER_IP : serverIp;
        this.winScore = winScore > 0 ? winScore : GameConfig.getWinScore();
    }

    @Override
    protected void onTextInputComplete(String fieldId, String text) {
        switch (fieldId) {
            case FIELD_ID_NAME -> {
                playerName = text;
                if (preferencesManager != null) {
                    preferencesManager.setDisplayName(text);
                }
            }
            case FIELD_ID_IP -> {
                serverIp = text;
                if (preferencesManager != null) {
                    preferencesManager.setLastServer(text);
                }
            }
            default -> {
            }
        }
        statusMessage = "";
    }

    @Override
    public void show() {
        super.show();
        preferencesManager = PreferencesManager.getInstance();
        loadSavedPreferences();
        layoutButtons();
        try {
            AudioManager.startMenuMusic();
        } catch (RuntimeException exception) {
            if (Gdx.app != null) {
                Gdx.app.error("MenuScreen", "Menu music failed to start", exception);
            }
        }
    }

    /**
     * Loads saved preferences only when current values are at their defaults.
     * This preserves values passed via constructor (e.g., returning from HostScreen).
     */
    private void loadSavedPreferences() {
        if (playerName.isEmpty()) {
            playerName = preferencesManager.getDisplayName();
        }
        if (serverIp.equals(PreferencesManager.DEFAULT_SERVER_IP)) {
            serverIp = preferencesManager.getLastServer();
        }
        if (winScore == GameConfig.getWinScore()) {
            winScore = preferencesManager.getWinScore();
        }
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        if (viewport == null) {
            return;
        }
        layoutButtons();
    }

    private void layoutButtons() {
        float h = getWorldHeight();
        float centerX = getWorldWidth() / 2f;

        // Distribute buttons evenly across the screen height
        float usableHeight = h - TOP_MARGIN - BOTTOM_MARGIN;

        // 6 elements: name, ip, winScore, host, join, settings
        float spacing = usableHeight / 6f;

        nameButton = createButton(centerX, h - TOP_MARGIN - spacing * 1);
        ipButton = createButton(centerX, h - TOP_MARGIN - spacing * 2);

        float winY = h - TOP_MARGIN - spacing * 3;
        winMinusButton = new Rectangle(centerX + WIN_MINUS_OFFSET_X, winY, WIN_BUTTON_SIZE, BUTTON_HEIGHT);
        winPlusButton = new Rectangle(centerX + WIN_PLUS_OFFSET_X, winY, WIN_BUTTON_SIZE, BUTTON_HEIGHT);

        hostButton = createButton(centerX, h - TOP_MARGIN - spacing * 4);
        joinButton = createButton(centerX, h - TOP_MARGIN - spacing * 5);
        settingsButton = createButton(centerX, BOTTOM_MARGIN);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        handleInput();
        drawGradientBackground();

        // Layout constants
        final float h = getWorldHeight();
        final float TITLE_POS_Y = 30;
        final float WIN_SCORE_TEXT_OFFSET_Y = 10;
        final float STATUS_MESSAGE_OFFSET_Y = 513;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        drawButton(nameButton, isPressed(nameButton));
        drawButton(ipButton, isPressed(ipButton));

        drawButton(winMinusButton, isPressed(winMinusButton));
        drawButton(winPlusButton, isPressed(winPlusButton));
        drawRoundedRect(getWorldWidth() / 2f - WIN_SCORE_BOX_WIDTH / 2f, winMinusButton.y, WIN_SCORE_BOX_WIDTH, BUTTON_HEIGHT,
                BUTTON_CORNER_RADIUS, PANEL_BACKGROUND_COLOR);

        drawColoredButton(hostButton, isPressed(hostButton), SUCCESS_COLOR);
        drawColoredButton(joinButton, isPressed(joinButton), ACCENT_COLOR);

        drawButton(settingsButton, isPressed(settingsButton));

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        drawCenteredText(titleFont, "PING-404 MENU", h - TITLE_POS_Y);

        drawButtonLabel(playerName.isBlank() ? "Player Name" : "Name: " + playerName, nameButton);
        drawButtonLabel(serverIp.isBlank() ? "Server IP" : "IP: " + serverIp, ipButton);

        drawButtonLabel("-", winMinusButton, largeFont);
        drawButtonLabel("+", winPlusButton, largeFont);
        drawCenteredText(buttonFont, "Win Score: " + winScore, winMinusButton.y + BUTTON_HEIGHT / 2f + WIN_SCORE_TEXT_OFFSET_Y, Color.WHITE);

        drawButtonLabel("Host Game", hostButton);
        drawButtonLabel("Join Game", joinButton);
        drawButtonLabel("Settings", settingsButton);

        if (!statusMessage.isBlank()) {
            drawCenteredText(smallFont, statusMessage, settingsButton.y + BUTTON_HEIGHT + STATUS_MESSAGE_OFFSET_Y, DANGER_COLOR);
        }

        batch.end();

        updateAndRenderTextInput(delta);
    }

    private void handleInput() {
        if (isTouched(nameButton)) {
            AudioManager.playMenuSelect();
            startTextInput(FIELD_ID_NAME, "Enter Player Name", playerName);
            return;
        }
        if (isTouched(ipButton)) {
            AudioManager.playMenuSelect();
            startTextInput(FIELD_ID_IP, "Enter Server IP", serverIp);
            return;
        }

        if (isTouched(winMinusButton)) {
            AudioManager.playMenuSelect();
            adjustWinScore(-1);
            return;
        }
        if (isTouched(winPlusButton)) {
            AudioManager.playMenuSelect();
            adjustWinScore(1);
            return;
        }

        if (isTouched(hostButton)) {
            AudioManager.playMenuSelect();
            navigateToHost();
            return;
        }
        if (isTouched(joinButton)) {
            AudioManager.playMenuSelect();
            navigateToJoin();
            return;
        }

        if (isTouched(settingsButton)) {
            AudioManager.playMenuSelect();
            navigateToSettings();
        }
    }

    void navigateToHost() {
        if (playerName.isBlank()) {
            statusMessage = "Please enter your name first";
            AudioManager.playWarning();
            return;
        }
        game.setScreen(new HostScreen(game, playerName, winScore, serverIp));
    }

    void navigateToJoin() {
        if (playerName.isBlank()) {
            statusMessage = "Please enter your name first";
            AudioManager.playWarning();
            return;
        }
        game.setScreen(new JoinScreen(game, playerName, "", serverIp));
    }

    void navigateToSettings() {
        game.setScreen(new SettingsScreen(game));
    }

    void adjustWinScore(int delta) {
        winScore = Math.max(MIN_WIN_SCORE, Math.min(MAX_WIN_SCORE, winScore + delta));
        if (preferencesManager != null) {
            preferencesManager.setWinScore(winScore);
        }
    }

    int getWinScore() {
        return winScore;
    }

    String getStatusMessage() {
        return statusMessage;
    }
}
