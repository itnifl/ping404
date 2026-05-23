package no.ntnu.ping404.screens;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import no.ntnu.ping404.audio.AudioManager;
import no.ntnu.ping404.network.GameConfig;
import no.ntnu.ping404.network.ClientConnector;
import no.ntnu.ping404.network.INetworkClientFactory;
import no.ntnu.ping404.network.NetworkConfig;
import no.ntnu.ping404.network.NetworkKryoClientFactory;
import no.ntnu.ping404.network.NetworkListener;
import no.ntnu.ping404.network.packets.GameStartEvent;
import no.ntnu.ping404.network.packets.GameStartRequest;
import no.ntnu.ping404.network.packets.LoginRequest;
import no.ntnu.ping404.network.packets.LoginResponse;
import no.ntnu.ping404.network.packets.PlayerJoined;
import no.ntnu.ping404.network.packets.PlayerLeft;
import no.ntnu.ping404.network.packets.PlayerList;

import java.io.IOException;

/**
 * Host screen for PING-404.
 * Phase 1: Enter player name. Create room and generate code.
 * Phase 2: Show room code, wait for opponent. Start button enables when
 * opponent joins.
 */
public class HostScreen extends BaseScreen implements NetworkListener {

    // Field identifiers for text input
    private static final String FIELD_ID_NAME = "name";

    // UI labels and messages
    private static final String LABEL_ENTER_NAME = "Enter Your Name";
    private static final String STATUS_CONNECTING = "Connecting...";
    private static final String THREAD_NAME_CONNECT = "HostScreen-Connect";

    // Layout constants for setup phase
    private static final float SETUP_NAME_BUTTON_OFFSET = 100f;
    private static final float SETUP_CREATE_BUTTON_OFFSET = -50f;
    private static final float START_BUTTON_Y = 180f;
    private static final float TITLE_MARGIN_TOP = 30f;
    private static final float WIN_SCORE_MARGIN_TOP = 90f;
    private static final float LABEL_OFFSET_ABOVE_BUTTON = 40f;
    private static final float STATUS_MESSAGE_OFFSET_BELOW = 30f;

    // Waiting phase panel dimensions
    private static final float TITLE_BOX_WIDTH = 360f;
    private static final float TITLE_BOX_HEIGHT = 120f;
    private static final float TITLE_BOX_TOP_MARGIN = 140f;
    private static final float TITLE_BOX_CORNER_RADIUS = 20f;

    private enum Phase {
        SETUP, WAITING
    }

    private Phase phase = Phase.SETUP;

    // Player info
    private String playerName = "";
    private String roomCode = "";
    private String opponentName = "";
    private int configuredWinScore = GameConfig.getWinScore();
    private String statusMessage = "";
    private String sessionToken;
    private final String serverIp;
    private final ClientConnector connector;

    // Phase 1 buttons
    private Rectangle nameButton;
    private Rectangle createButton;
    private Rectangle backButton;

    // Phase 2 buttons
    private Rectangle startButton;
    private Rectangle cancelButton;

    // Mock opponent join (testing)
    private boolean opponentJoined = false;
    private volatile boolean connecting = false;

    // Waiting animation
    private float dotTimer = 0f;
    private int dotCount = 0;

    public HostScreen(Game game) {
        this(game, "", GameConfig.getWinScore(), NetworkConfig.DEFAULT_HOST, new NetworkKryoClientFactory());
    }

    public HostScreen(Game game, String initialPlayerName, int configuredWinScore) {
        this(game, initialPlayerName, configuredWinScore, NetworkConfig.DEFAULT_HOST, new NetworkKryoClientFactory());
    }

    public HostScreen(Game game, String initialPlayerName, int configuredWinScore, String serverIp) {
        this(game, initialPlayerName, configuredWinScore, serverIp, new NetworkKryoClientFactory());
    }

    public HostScreen(Game game, String initialPlayerName, int configuredWinScore, String serverIp, INetworkClientFactory clientFactory) {
        super(game);
        this.playerName = initialPlayerName != null ? initialPlayerName : "";
        this.configuredWinScore = configuredWinScore;
        this.serverIp = (serverIp == null || serverIp.isBlank()) ? NetworkConfig.DEFAULT_HOST : serverIp;
        this.connector = ClientConnector.create(clientFactory);
    }

    @Override
    protected void onTextInputComplete(String fieldId, String text) {
        if (FIELD_ID_NAME.equals(fieldId)) {
            playerName = text;
        }
    }

    @Override
    public void show() {
        super.show();
        connector.addListener(this);
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

        // Phase 1 buttons - spread out with more spacing for 100px buttons
        nameButton = createButton(centerY + SETUP_NAME_BUTTON_OFFSET);
        createButton = createButton(centerY + SETUP_CREATE_BUTTON_OFFSET);
        backButton = createButton(BOTTOM_BUTTON_Y);

        // Phase 2 buttons - positioned with proper spacing
        startButton = createButton(START_BUTTON_Y);
        cancelButton = createButton(BOTTOM_BUTTON_Y);
    }

    @Override
    public void hide() {
        connector.removeListener(this);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        drawGradientBackground();

        if (phase == Phase.SETUP) {
            renderSetupPhase(delta);
        } else {
            renderWaitingPhase(delta);
        }

        updateAndRenderTextInput(delta);
    }

    //Phase 1: Setup
    private void renderSetupPhase(float delta) {
        handleSetupInput();

        // Draw buttons
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        drawButton(nameButton, isPressed(nameButton));

        // Create button only enabled if name is entered
        if (!playerName.isEmpty()) {
            drawColoredButton(createButton, isPressed(createButton), ACCENT_COLOR);
        } else {
            drawRoundedRect(createButton.x, createButton.y, createButton.width, createButton.height,
                    BUTTON_CORNER_RADIUS, DISABLED_BUTTON_COLOR);
        }

        drawButton(backButton, isPressed(backButton));

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // Draw text
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        float h = getWorldHeight();
        drawCenteredText(titleFont, "Host Game", h - TITLE_MARGIN_TOP);
        drawCenteredText(buttonFont, "Win score: " + configuredWinScore, h - WIN_SCORE_MARGIN_TOP,
            TEXT_DIM_COLOR);

        // Show current name
        if (playerName.isEmpty()) {
            drawCenteredText(buttonFont, "No name set", nameButton.y + nameButton.height + LABEL_OFFSET_ABOVE_BUTTON, TEXT_SUBTLE_COLOR);
        } else {
            drawCenteredText(buttonFont, "Name: " + playerName, nameButton.y + nameButton.height + LABEL_OFFSET_ABOVE_BUTTON, Color.WHITE);
        }

        drawButtonLabel(LABEL_ENTER_NAME, nameButton);
        drawButtonLabel("Create Room", createButton);
        drawButtonLabel("Back", backButton);

        if (!statusMessage.isBlank()) {
            drawCenteredText(buttonFont, statusMessage, backButton.y - STATUS_MESSAGE_OFFSET_BELOW, DANGER_COLOR);
        }

        batch.end();
    }

    private void handleSetupInput() {
        if (isTouched(nameButton)) {
            startTextInput(FIELD_ID_NAME, LABEL_ENTER_NAME, playerName);
            return;
        }

        if (!playerName.isEmpty() && isTouched(createButton)) {
            statusMessage = "";
            sendHostLoginRequest();
            return;
        }

        if (playerName.isEmpty() && isTouched(createButton)) {
            statusMessage = "Please enter your name first";
            AudioManager.playWarning();
            return;
        }

        if (isTouched(backButton)) {
            navigateBackToMenu();
        }
    }

    private void navigateBackToMenu() {
        sendLeaveRoomIfConnected();
        disposeNetworkAndNavigate(new MenuScreen(game, playerName, serverIp, configuredWinScore));
    }

    private void sendLeaveRoomIfConnected() {
        if (phase == Phase.WAITING && connector.isConnected()) {
            connector.send(new no.ntnu.ping404.network.packets.LeaveRoom());
        }
    }

    private void disposeNetworkAndNavigate(com.badlogic.gdx.Screen target) {
        connector.dispose();
        game.setScreen(target);
    }

    //Phase 2: Waiting

    private void renderWaitingPhase(float delta) {
        // Animate waiting dots
        dotTimer += delta;
        if (dotTimer >= DOT_ANIMATION_INTERVAL) {
            dotTimer = 0f;
            dotCount = (dotCount + 1) % 4;
        }

        handleWaitingInput();

        // Layout constants for text positioning
        final float h = getWorldHeight();
        final float TITLE_POS_Y = 45;
        final float ROOM_CODE_LABEL_POS_Y = 105;
        final float ROOM_CODE_POS_Y = 150;
        final float WIN_SCORE_POS_Y = 215;
        final float SHARE_MESSAGE_POS_Y = 275;
        final float SERVER_INFO_POS_Y = 310;
        final float PLAYER_NAME_POS_Y = 400;
        final float OPPONENT_INFO_POS_Y = 435;
        final float READY_MESSAGE_POS_Y = 470;

        // Draw buttons
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Title box - contains only "Host Game"
        drawRoundedRect(getWorldWidth() / 2f - TITLE_BOX_WIDTH / 2f, h - TITLE_BOX_TOP_MARGIN,
                TITLE_BOX_WIDTH, TITLE_BOX_HEIGHT, TITLE_BOX_CORNER_RADIUS,
                PANEL_BACKGROUND_COLOR);

        // Start button - green when opponent joined, gray when disabled
        if (opponentJoined) {
            drawColoredButton(startButton, isPressed(startButton), SUCCESS_COLOR);
        } else {
            drawRoundedRect(startButton.x, startButton.y, startButton.width, startButton.height,
                    BUTTON_CORNER_RADIUS, DISABLED_BUTTON_COLOR);
        }

        drawButton(cancelButton, isPressed(cancelButton));

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // Draw text
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        // Title inside box
        drawCenteredText(titleFont, "Host Game", h - TITLE_POS_Y);

        // Content below box
        drawCenteredText(buttonFont, "Your room code:", h - ROOM_CODE_LABEL_POS_Y, ACCENT_COLOR);

        // Room code (large text)
        drawCenteredText(largeFont, roomCode, h - ROOM_CODE_POS_Y);

        drawCenteredText(buttonFont, "Win score: " + configuredWinScore, h - WIN_SCORE_POS_Y,
            TEXT_DIM_COLOR);
        drawCenteredText(buttonFont, "Share this code with your friend!", h - SHARE_MESSAGE_POS_Y, 
            TEXT_MUTED_COLOR);
        drawCenteredText(buttonFont, "Server: " + serverIp, h - SERVER_INFO_POS_Y, 
            TEXT_SUBTLE_COLOR);

        // Player info
        drawCenteredText(buttonFont, "You: " + playerName, h - PLAYER_NAME_POS_Y, Color.WHITE);

        if (opponentJoined) {
            drawCenteredText(buttonFont, "Opponent: " + opponentName, h - OPPONENT_INFO_POS_Y, SUCCESS_COLOR);
            drawCenteredText(buttonFont, "Ready to play!", h - READY_MESSAGE_POS_Y, SUCCESS_COLOR);
        } else {
            String dots = ".".repeat(dotCount);
            drawCenteredText(buttonFont, "Waiting for opponent" + dots, h - OPPONENT_INFO_POS_Y,
                    TEXT_MUTED_COLOR);
        }

        // Button labels
        Color startTextColor = opponentJoined ? BUTTON_TEXT_COLOR : TEXT_FAINT_COLOR;
        buttonFont.setColor(startTextColor);
        drawButtonLabel("Start Game", startButton);
        buttonFont.setColor(BUTTON_TEXT_COLOR);

        drawButtonLabel("Cancel", cancelButton);

        batch.end();
    }

    private void handleWaitingInput() {
        if (opponentJoined && isTouched(startButton)) {
            sendGameStartRequest();
            return;
        }

        if (isTouched(cancelButton)) {
            navigateBackToMenu();
        }
    }

    private void sendGameStartRequest() {
        GameStartRequest request = new GameStartRequest(0); // Server sets requesterId
        connector.send(request);
    }

    private void sendHostLoginRequest() {
        if (connecting) return;
        connecting = true;
        phase = Phase.WAITING;
        opponentJoined = false;
        statusMessage = STATUS_CONNECTING;

        new Thread(() -> {
            try {
                if (!connector.isConnected()) {
                    connector.connect(serverIp, NetworkConfig.TCP_PORT, NetworkConfig.UDP_PORT);
                }
                postOnRenderThread(() -> {
                    connecting = false;
                    LoginRequest request = new LoginRequest(playerName, NetworkConfig.CLIENT_VERSION, configuredWinScore);
                    request.createRoom = true;
                    request.sessionToken = sessionToken;
                    connector.send(request);
                    statusMessage = "";
                });
            } catch (IOException e) {
                postOnRenderThread(() -> {
                    connecting = false;
                    statusMessage = "Unable to connect: " + e.getMessage();
                    phase = Phase.SETUP;
                });
            }
        }, THREAD_NAME_CONNECT).start();
    }

    @Override
    public void onConnected() {
    }

    @Override
    public void onDisconnected() {
        postOnRenderThread(() -> {
            connecting = false;
            statusMessage = "Disconnected from server";
            phase = Phase.SETUP;
        });
    }

    @Override
    public void onReceived(Object packet) {
        if (packet instanceof LoginResponse response) {
            postOnRenderThread(() -> {
                if (!response.success) {
                    statusMessage = response.message != null ? response.message : "Login failed";
                    phase = Phase.SETUP;
                    return;
                }
                roomCode = response.roomCode != null ? response.roomCode : roomCode;
                configuredWinScore = response.winScore > 0 ? response.winScore : configuredWinScore;
                sessionToken = response.sessionToken;
                statusMessage = "";
            });
            return;
        }

        if (packet instanceof PlayerJoined joined) {
            postOnRenderThread(() -> {
                if (joined.playerName != null && !joined.playerName.equals(playerName)) {
                    opponentName = joined.playerName;
                    opponentJoined = true;
                }
            });
            return;
        }

        if (packet instanceof PlayerLeft left) {
            postOnRenderThread(() -> {
                if (left.playerName != null && left.playerName.equals(opponentName)) {
                    opponentName = "";
                    opponentJoined = false;
                    statusMessage = left.playerName + " left the room";
                    navigateBackToMenu();
                }
            });
            return;
        }

        if (packet instanceof PlayerList list && !list.players.isEmpty()) {
            postOnRenderThread(() -> {
                var first = list.players.get(0);
                if (first.playerName != null && !first.playerName.equals(playerName)) {
                    opponentName = first.playerName;
                    opponentJoined = true;
                }
            });
            return;
        }

        if (packet instanceof GameStartEvent event) {
            postOnRenderThread(() -> {
                // Transition to game with authoritative info from server
                boolean isHost = event.playerSlot == 1;
                game.setScreen(new GameScreen(game, event.playerName, event.opponentName, isHost, connector));
            });
            return;
        }

        if (packet instanceof no.ntnu.ping404.network.packets.ErrorPacket error) {
            postOnRenderThread(() -> {
                statusMessage = error.message;
            });
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        connector.dispose();
    }
}
