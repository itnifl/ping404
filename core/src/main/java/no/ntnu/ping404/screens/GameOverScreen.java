package no.ntnu.ping404.screens;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import no.ntnu.ping404.network.ClientConnector;
import no.ntnu.ping404.network.ClientPacketDispatcher;
import no.ntnu.ping404.network.NetworkListener;
import no.ntnu.ping404.network.packets.GameStartEvent;
import no.ntnu.ping404.network.packets.RematchRequest;
import no.ntnu.ping404.network.packets.RematchStart;
import no.ntnu.ping404.audio.AudioManager;

/**
 * Game Over screen for PING-404.
 * Shows win/lose result, final score, buttons to rematch or return to the menu.
 *
 * <p>Requirements:</p>
 * <ul>
 *   <li>FR1.5: Rematch flow - both players tap Rematch to restart</li>
 *   <li>FR3.3: Return to {@link MenuScreen} via "Back to Menu" button</li>
 * </ul>
 */
public class GameOverScreen extends BaseScreen implements NetworkListener {

    // Layout constants
    private static final float MENU_BUTTON_Y = 80f;
    private static final float REMATCH_BUTTON_Y = 200f;

    // Colors for player/opponent names
    private static final Color PLAYER_NAME_COLOR = new Color(0.30f, 0.70f, 1.0f, 1f);
    private static final Color OPPONENT_NAME_COLOR = new Color(1.0f, 0.40f, 0.35f, 1f);
    private static final Color DARK_PANEL_COLOR = new Color(0.10f, 0.10f, 0.22f, 0.85f);
    private static final Color SCORE_BOX_COLOR = new Color(0.15f, 0.15f, 0.30f, 0.9f);

    // Animation constants
    private static final float PULSE_INTENSITY = 0.05f;
    private static final float PULSE_SPEED = 3.0f;

    private final String playerName;
    private final String opponentName;
    private final int playerScore;
    private final int opponentScore;
    private final boolean playerWon;

    private final ClientConnector connector;
    private final boolean isHost;
    private ClientPacketDispatcher packetDispatcher;

    private Rectangle menuButton;
    private Rectangle rematchButton;
    private boolean rematchPending = false;

    private float animTimer = 0f;

    public GameOverScreen(Game game, String playerName, String opponentName,
                          int playerScore, int opponentScore, boolean playerWon) {
        this(game, playerName, opponentName, playerScore, opponentScore, playerWon, null, false);
    }

    /**
     * Creates a networked game-over screen.
     * When {@code connector} is non-null, the Rematch button is shown and
     * a {@link RematchRequest} is sent when tapped.
     * A {@link RematchStart} packet from the server triggers a return to GameScreen.
     */
    public GameOverScreen(Game game, String playerName, String opponentName,
                          int playerScore, int opponentScore, boolean playerWon,
                          ClientConnector connector, boolean isHost) {
        super(game);
        this.playerName = playerName;
        this.opponentName = opponentName;
        this.playerScore = playerScore;
        this.opponentScore = opponentScore;
        this.playerWon = playerWon;
        this.connector = connector;
        this.isHost = isHost;

        if (connector != null) {
            packetDispatcher = new ClientPacketDispatcher();
            packetDispatcher.register(RematchStart.class, packet -> {
                postOnRenderThread(() -> rematchPending = false);
            });
            packetDispatcher.register(GameStartEvent.class, event -> {
                boolean host = event.playerSlot == 1;
                postOnRenderThread(() -> game.setScreen(
                        new GameScreen(game, event.playerName, event.opponentName, host, connector)));
            });
        }
    }

    @Override
    public void show() {
        super.show();
        layoutButtons();
        AudioManager.stopMusic();
        AudioManager.playGameOver();
        if (connector != null) {
            connector.addListener(this);
        }
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        layoutButtons();
    }

    @Override
    public void hide() {
        if (connector != null) {
            connector.removeListener(this);
        }
    }

    @Override
    public void onConnected() {}

    @Override
    public void onDisconnected() {}

    @Override
    public void onReceived(Object packet) {
        if (packetDispatcher != null) {
            packetDispatcher.dispatch(packet);
        }
    }

    private void layoutButtons() {
        menuButton = createButton(MENU_BUTTON_Y);
        rematchButton = (connector != null) ? createButton(REMATCH_BUTTON_Y) : null;
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        animTimer += delta;

        handleInput();

        drawGradientBackground();

        final float h = getWorldHeight();
        final float PANEL_MARGIN = 30;
        final float PANEL_BOTTOM = 140;
        final float PANEL_CORNER = 20;
        final float SCORE_BOX_WIDTH = 160;
        final float SCORE_BOX_HEIGHT = 80;
        final float SCORE_BOX_OFFSET_Y = 60;
        final float SCORE_BOX_CORNER = 15;
        final float TITLE_POS_Y = 60;
        final float PLAYER_NAME_POS_Y = 130;
        final float VS_POS_Y = 170;
        final float OPPONENT_NAME_POS_Y = 210;
        final float SCORE_POS_Y = 10;
        final float SUBTEXT_POS_Y = 460;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        drawRoundedRect(PANEL_MARGIN, PANEL_BOTTOM, getWorldWidth() - PANEL_MARGIN * 2,
                h - PANEL_BOTTOM - PANEL_MARGIN - 10, PANEL_CORNER,
                DARK_PANEL_COLOR);

        drawRoundedRect(getWorldWidth() / 2f - SCORE_BOX_WIDTH / 2f, h / 2f - SCORE_BOX_OFFSET_Y,
                SCORE_BOX_WIDTH, SCORE_BOX_HEIGHT, SCORE_BOX_CORNER,
                SCORE_BOX_COLOR);

        drawColoredButton(menuButton, isPressed(menuButton), ACCENT_COLOR);
        if (rematchButton != null) {
            Color rematchColor = rematchPending
                    ? BUTTON_PRESSED_COLOR
                    : SUCCESS_COLOR;
            drawColoredButton(rematchButton, isPressed(rematchButton), rematchColor);
        }

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        float pulse = 1.0f + PULSE_INTENSITY * (float) Math.sin(animTimer * PULSE_SPEED);
        Color resultColor = playerWon ? SUCCESS_COLOR : DANGER_COLOR;
        String resultText = playerWon ? "YOU WIN!" : "YOU LOSE!";

        titleFont.getData().setScale(4f * pulse);
        drawCenteredText(titleFont, resultText, h - TITLE_POS_Y, resultColor);
        titleFont.getData().setScale(4f);

        drawCenteredText(buttonFont, playerName, h - PLAYER_NAME_POS_Y, PLAYER_NAME_COLOR);
        drawCenteredText(buttonFont, "vs", h - VS_POS_Y, TEXT_SUBTLE_COLOR);
        drawCenteredText(buttonFont, opponentName, h - OPPONENT_NAME_POS_Y, OPPONENT_NAME_COLOR);

        largeFont.setColor(Color.WHITE);
        drawCenteredText(largeFont, playerScore + " - " + opponentScore, h / 2f - SCORE_POS_Y);

        String subText = playerWon ? "Well played!" : "Better luck next time!";
        drawCenteredText(buttonFont, subText, SUBTEXT_POS_Y, TEXT_MUTED_COLOR);

        buttonFont.setColor(Color.WHITE);
        drawButtonLabel("Back to Menu", menuButton);
        if (rematchButton != null) {
            String rematchLabel = rematchPending ? "Cancel" : "Rematch";
            drawButtonLabel(rematchLabel, rematchButton);
        }
        buttonFont.setColor(BUTTON_TEXT_COLOR);

        batch.end();
    }

    private void handleInput() {
        if (isTouched(menuButton)) {
            navigateToMenu();
        }
        if (rematchButton != null && isTouched(rematchButton)) {
            if (rematchPending) {
                cancelRematchRequest();
            } else {
                rematchPending = true;
                sendRematchRequest();
            }
        }
    }

    private void sendRematchRequest() {
        if (connector != null) {
            connector.send(new RematchRequest());
        }
    }

    private void cancelRematchRequest() {
        rematchPending = false;
    }

    void navigateToMenu() {
        game.setScreen(new MenuScreen(game));
    }

    void navigateToGame() {
        game.setScreen(new GameScreen(game, playerName, opponentName, isHost, connector));
    }

}
