package no.ntnu.ping404.screens;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import no.ntnu.ping404.audio.AudioManager;
import no.ntnu.ping404.network.ClientConnector;
import no.ntnu.ping404.network.GameConfig;
import no.ntnu.ping404.network.INetworkClientFactory;
import no.ntnu.ping404.network.NetworkConfig;
import no.ntnu.ping404.network.NetworkKryoClientFactory;
import no.ntnu.ping404.network.NetworkListener;
import no.ntnu.ping404.network.packets.GameStartEvent;
import no.ntnu.ping404.network.packets.LoginRequest;
import no.ntnu.ping404.network.packets.LoginResponse;
import no.ntnu.ping404.network.packets.PlayerLeft;
import no.ntnu.ping404.utils.PreferencesManager;

import java.io.IOException;

/**
 * Join screen for PING-404.
 * Player enters their name and a room code to connect to a host's session.
 */
public class JoinScreen extends BaseScreen implements NetworkListener {

    // Field identifiers for text input
    private static final String FIELD_ID_NAME = "name";
    private static final String FIELD_ID_CODE = "code";

    // UI labels and messages
    private static final String LABEL_ENTER_NAME = "Enter Your Name";
    private static final String LABEL_ENTER_CODE = "Enter Room Code";
    private static final String STATUS_CONNECTING = "Connecting...";
    private static final String THREAD_NAME_CONNECT = "JoinScreen-Connect";

    // Layout constants
    private static final float NAME_BUTTON_OFFSET = 150f;
    private static final float CODE_BUTTON_OFFSET = 0f;
    private static final float CONNECT_BUTTON_OFFSET = -150f;
    private static final float CONNECTING_DOT_INTERVAL = 0.4f;

    private enum State { INPUT, CONNECTING, WAITING }

    private State state = State.INPUT;

    // Player info
    private String playerName = "";
    private String roomCode = "";
    private String serverIp = PreferencesManager.DEFAULT_SERVER_IP;
    private String statusMessage = "";
    private String sessionToken;
    private final ClientConnector connector;

    // Buttons
    private Rectangle nameButton;
    private Rectangle codeButton;
    private Rectangle connectButton;
    private Rectangle backButton;

    // Waiting animation
    private float dotTimer = 0f;
    private int dotCount = 0;
    private volatile boolean connecting = false;

    public JoinScreen(Game game) {
        this(game, "", "", NetworkConfig.DEFAULT_HOST, new NetworkKryoClientFactory());
    }

    public JoinScreen(Game game, String initialPlayerName, String initialRoomCode, String initialServerIp) {
        this(game, initialPlayerName, initialRoomCode, initialServerIp, new NetworkKryoClientFactory());
    }

    public JoinScreen(Game game, String initialPlayerName, String initialRoomCode, String initialServerIp, INetworkClientFactory clientFactory) {
        super(game);
        this.playerName = initialPlayerName != null ? initialPlayerName : "";
        this.roomCode = initialRoomCode != null ? initialRoomCode : "";
        this.serverIp = (initialServerIp == null || initialServerIp.isBlank()) ? PreferencesManager.DEFAULT_SERVER_IP : initialServerIp;
        this.connector = ClientConnector.create(clientFactory);
    }

    @Override
    protected void onTextInputComplete(String fieldId, String text) {
        if (FIELD_ID_NAME.equals(fieldId)) {
            playerName = text;
        } else if (FIELD_ID_CODE.equals(fieldId)) {
            roomCode = text;
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

        nameButton = createButton(centerY + NAME_BUTTON_OFFSET);
        codeButton = createButton(centerY + CODE_BUTTON_OFFSET);
        connectButton = createButton(centerY + CONNECT_BUTTON_OFFSET);
        backButton = createButton(BOTTOM_BUTTON_Y);
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

        switch (state) {
            case INPUT:
                renderInputState(delta);
                break;
            case CONNECTING:
                renderConnectingState(delta);
                break;
            case WAITING:
                renderWaitingState(delta);
                break;
        }

        updateAndRenderTextInput(delta);
    }

    // Input State
    private void renderInputState(float delta) {
        handleInputState();

        boolean canConnect = !playerName.isEmpty() && !roomCode.isEmpty();

        // Layout constants
        final float h = getWorldHeight();
        final float TITLE_POS_Y = 20;
        final float LABEL_OFFSET_Y = 40;
        final float ERROR_MESSAGE_OFFSET_Y = 40;

        // Draw buttons
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        drawButton(nameButton, isPressed(nameButton));
        drawButton(codeButton, isPressed(codeButton));

        if (canConnect) {
            drawColoredButton(connectButton, isPressed(connectButton), ACCENT_COLOR);
        } else {
            drawRoundedRect(connectButton.x, connectButton.y, connectButton.width, connectButton.height,
                    BUTTON_CORNER_RADIUS, DISABLED_BUTTON_COLOR);
        }

        drawButton(backButton, isPressed(backButton));

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // Draw text
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        drawCenteredText(titleFont, "Join Game", h - TITLE_POS_Y);

        // Show current values
        if (!playerName.isEmpty()) {
            drawCenteredText(buttonFont, "Name: " + playerName, nameButton.y + nameButton.height + LABEL_OFFSET_Y, Color.WHITE);
        }
        if (!roomCode.isEmpty()) {
            drawCenteredText(buttonFont, "Code: " + roomCode, codeButton.y + codeButton.height + LABEL_OFFSET_Y, ACCENT_COLOR);
        }

        drawButtonLabel(LABEL_ENTER_NAME, nameButton);
        drawButtonLabel(LABEL_ENTER_CODE, codeButton);
        drawButtonLabel("Connect", connectButton);
        drawButtonLabel("Back", backButton);

        // Error message
        if (!statusMessage.isEmpty()) {
            drawCenteredText(buttonFont, statusMessage, connectButton.y - ERROR_MESSAGE_OFFSET_Y, DANGER_COLOR);
        }

        batch.end();
    }

    private void handleInputState() {
        if (isTouched(nameButton)) {
            startTextInput(FIELD_ID_NAME, LABEL_ENTER_NAME, playerName);
            return;
        }

        if (isTouched(codeButton)) {
            startTextInput(FIELD_ID_CODE, LABEL_ENTER_CODE, roomCode);
            return;
        }

        if (!playerName.isEmpty() && !roomCode.isEmpty() && isTouched(connectButton)) {
            state = State.CONNECTING;
            statusMessage = "";
            sendJoinLoginRequest();
            return;
        }

        if ((playerName.isEmpty() || roomCode.isEmpty()) && isTouched(connectButton)) {
            statusMessage = playerName.isEmpty() ? "Please enter your name first" : "Please enter a room code";
            AudioManager.playWarning();
            return;
        }

        if (isTouched(backButton)) {
            navigateBackToMenu();
        }
    }

    private void navigateBackToMenu() {
        sendLeaveRoomIfConnected();
        disposeNetworkAndNavigate(new MenuScreen(game, playerName, serverIp, GameConfig.getWinScore()));
    }

    private void sendLeaveRoomIfConnected() {
        if (state == State.WAITING && connector.isConnected()) {
            connector.send(new no.ntnu.ping404.network.packets.LeaveRoom());
        }
    }

    private void disposeNetworkAndNavigate(com.badlogic.gdx.Screen target) {
        connector.dispose();
        game.setScreen(target);
    }

    // Connecting State
    private void renderConnectingState(float delta) {
        // Animate dots
        dotTimer += delta;
        if (dotTimer >= CONNECTING_DOT_INTERVAL) {
            dotTimer = 0f;
            dotCount = (dotCount + 1) % 4;
        }

        // Layout constants
        final float h = getWorldHeight();
        final float TITLE_POS_Y = 80;
        final float CONNECTING_OFFSET_Y = 40;
        final float ROOM_INFO_OFFSET_Y = 30;
        final float SERVER_INFO_OFFSET_Y = 70;

        // Draw
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        drawCenteredText(titleFont, "Join Game", h - TITLE_POS_Y);

        String dots = ".".repeat(dotCount);
        drawCenteredText(largeFont, "Connecting" + dots, h / 2f + CONNECTING_OFFSET_Y, ACCENT_COLOR);
        drawCenteredText(buttonFont, "Room: " + roomCode, h / 2f - ROOM_INFO_OFFSET_Y, TEXT_MUTED_COLOR);
        drawCenteredText(buttonFont, "Server: " + serverIp, h / 2f - SERVER_INFO_OFFSET_Y, TEXT_MUTED_COLOR);

        batch.end();
    }

    // Waiting State
    private void renderWaitingState(float delta) {
        // Animate dots
        dotTimer += delta;
        if (dotTimer >= DOT_ANIMATION_INTERVAL) {
            dotTimer = 0f;
            dotCount = (dotCount + 1) % 4;
        }

        // Layout constants
        final float h = getWorldHeight();
        final float TITLE_POS_Y = 35;
        final float CONNECTED_OFFSET_Y = 100;
        final float SERVER_INFO_OFFSET_Y = 60;
        final float PLAYER_NAME_OFFSET_Y = 20;
        final float WAITING_MESSAGE_OFFSET_Y = 40;

        // Draw back button
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        drawButton(backButton, isPressed(backButton));
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // Draw text
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        drawCenteredText(titleFont, "Join Game", h - TITLE_POS_Y);
        drawCenteredText(buttonFont, "Connected to room " + roomCode, h / 2f + CONNECTED_OFFSET_Y, SUCCESS_COLOR);
        drawCenteredText(buttonFont, "Server: " + serverIp, h / 2f + SERVER_INFO_OFFSET_Y, TEXT_MUTED_COLOR);
        drawCenteredText(buttonFont, "You: " + playerName, h / 2f + PLAYER_NAME_OFFSET_Y, Color.WHITE);

        String dots = ".".repeat(dotCount);
        drawCenteredText(buttonFont, "Waiting for host to start" + dots, h / 2f - WAITING_MESSAGE_OFFSET_Y,
                TEXT_MUTED_COLOR);

        drawButtonLabel("Leave", backButton);

        batch.end();

        // Handle back
        if (isTouched(backButton)) {
            navigateBackToMenu();
        }
    }

    private void sendJoinLoginRequest() {
        if (connecting) return;
        connecting = true;
        statusMessage = STATUS_CONNECTING;

        new Thread(() -> {
            try {
                if (!connector.isConnected()) {
                    connector.connect(serverIp, NetworkConfig.TCP_PORT, NetworkConfig.UDP_PORT);
                }
                postOnRenderThread(() -> {
                    connecting = false;
                    LoginRequest request = new LoginRequest(playerName, NetworkConfig.CLIENT_VERSION);
                    request.roomCode = roomCode;
                    request.sessionToken = sessionToken;
                    request.createRoom = false;
                    connector.send(request);
                    statusMessage = "";
                });
            } catch (IOException e) {
                postOnRenderThread(() -> {
                    connecting = false;
                    statusMessage = "Unable to connect: " + e.getMessage();
                    state = State.INPUT;
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
            state = State.INPUT;
        });
    }

    @Override
    public void onReceived(Object packet) {
        if (packet instanceof LoginResponse response) {
            postOnRenderThread(() -> {
                if (!response.success) {
                    statusMessage = response.message != null ? response.message : "Login failed";
                    state = State.INPUT;
                    return;
                }
                roomCode = response.roomCode != null ? response.roomCode : roomCode;
                sessionToken = response.sessionToken;
                statusMessage = "";
                state = State.WAITING;
            });
            return;
        }

        if (packet instanceof GameStartEvent event) {
            postOnRenderThread(() -> {
                boolean isHost = event.playerSlot == 1;
                game.setScreen(new GameScreen(game, event.playerName, event.opponentName, isHost, connector));
            });
            return;
        }

        if (packet instanceof PlayerLeft left) {
            postOnRenderThread(() -> {
                statusMessage = left.playerName != null
                        ? left.playerName + " left the room"
                        : "Host left the room";
                navigateBackToMenu();
            });
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        connector.dispose();
    }
}