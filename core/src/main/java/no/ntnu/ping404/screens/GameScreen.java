package no.ntnu.ping404.screens;

import no.ntnu.ping404.model.Puck;
import no.ntnu.ping404.audio.AudioManager;
import no.ntnu.ping404.utils.CollisionDetector;
import no.ntnu.ping404.utils.Constants;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import no.ntnu.ping404.network.ClientConnector;
import no.ntnu.ping404.network.ClientPacketDispatcher;
import no.ntnu.ping404.network.ClientPacketHandlers;
import no.ntnu.ping404.network.GameScreenState;
import no.ntnu.ping404.network.NetworkListener;
import no.ntnu.ping404.network.PuckInterpolator;
import no.ntnu.ping404.network.packets.GameOver;
import no.ntnu.ping404.network.packets.GameReset;
import no.ntnu.ping404.network.packets.GameStateSnapshot;
import no.ntnu.ping404.network.packets.LeaveRoom;
import no.ntnu.ping404.network.packets.PauseRequest;
import no.ntnu.ping404.network.packets.Pong;
import no.ntnu.ping404.network.packets.PlayerPosition;
import no.ntnu.ping404.network.packets.ResumeRequest;
import no.ntnu.ping404.network.packets.RoomMetricsSnapshot;

/**
 * Main gameplay screen for PING-404 air hockey.
 *
 * <p>Acts as the MVC orchestrator for the game scene:</p>
 * <ul>
 *   <li><b>Model</b> - {@link GameScreenState} (server-authoritative state) and local
 *       physics objects ({@link Puck}, mallet positions, scores).</li>
 *   <li><b>View</b> - {@link GameRendererView} handles all pixel drawing with
 *       platform-specific implementations via {@link GameRendererFactory}.</li>
 *   <li><b>Controller</b> - {@link GameInputHandler} captures touch input; this
 *       screen sends {@link PlayerPosition} packets and implements {@link NetworkListener}
 *       to receive server events via {@link ClientPacketDispatcher}.</li>
 * </ul>
 *
 * <p>When a {@link ClientConnector} is provided the server is authoritative: local
 * win-condition checks are disabled and the screen transitions on receipt of a
 * {@link GameOver} packet.</p>
 */
public class GameScreen extends BaseScreen implements NetworkListener, LifecycleAwareScreen {

    private static final float COUNTDOWN_START_SECONDS = 3.0f;
    private static final int COUNTDOWN_START_DISPLAY = 4;
    private static final float HOST_MALLET_X_RATIO = 0.15f;
    private static final float GUEST_MALLET_X_RATIO = 0.85f;
    private static final float GOAL_FLASH_DURATION = 1.5f;
    /** Proximity buffer (pixels) around collision radius used to trigger edge-entry sounds. */
    private static final float COLLISION_SOUND_THRESHOLD = 5f;
    /** Separation epsilon (pixels) applied after wall bounce to prevent tunnelling. */
    private static final float WALL_SEPARATION_EPSILON = 0.01f;
    private static final float SNAPSHOT_STALL_THRESHOLD_SECONDS = 0.75f;
    private static final float PUCK_STILLNESS_THRESHOLD_SECONDS = 0.15f;
    private static final float PUCK_STOP_EPSILON = 0.25f;
    private static final float DISCONNECT_TITLE_OFFSET_Y = 20f;
    private static final float DISCONNECT_SUBTITLE_OFFSET_Y = -15f;
    private static final String LOST_CONNECTION_MESSAGE = "Lost connection to other player!";
    private static final String DISCONNECTED_MESSAGE = "Other player disconnected!";
    private static final String WAITING_MESSAGE = "Waiting..";

    // Player info
    private final String playerName;
    private final String opponentName;

    // Mallet positions
    private float playerMalletX, playerMalletY;
    private float opponentMalletX, opponentMalletY;

    // Puck
    private Puck puck;

    // Scores
    private int playerScore = 0;
    private int opponentScore = 0;

    // Pause state
    private boolean paused = false;
    private Rectangle resumeButton;
    private Rectangle quitButton;
    private Rectangle pauseButton;
    private float pauseButtonFlashTimer = 0f;
    private String pauseErrorMessage = "";

    // Countdown
    private float countdownTimer = COUNTDOWN_START_SECONDS;
    private boolean countdownDone = false;
    private int lastCountdownSecond = COUNTDOWN_START_DISPLAY;

    // Goal flash
    private float goalFlashTimer = 0f;
    private String goalMessage = "";

    // Stall reset message (7-second rule)
    private static final String STALL_RESET_MESSAGE = "7-second rule: Puck reset!";
    private static final float STALL_MESSAGE_DURATION_SECONDS = 1.2f;
    private float stallMessageTimer = 0f;

    // Collision sound tracking for network mode
    private boolean wasNearPlayerMallet = false;
    private boolean wasNearOpponentMallet = false;
    private boolean wasNearTopWall = false;
    private boolean wasNearBottomWall = false;
    private boolean wasNearLeftWall = false;
    private boolean wasNearRightWall = false;

    // Client connector - null in offline/mock mode
    private final ClientConnector connector;

    // Whether this player is the host (slot 1 = left side)
    private final boolean isHost;

    // Packet dispatcher for server-authoritative updates (issue #48)
    private ClientPacketDispatcher packetDispatcher;
    private GameScreenState networkState;

    // Client-side puck interpolation for smooth rendering (issue #59)
    private PuckInterpolator puckInterpolator;

    // MVC View and Controller components - initialised in show()
    private GameRendererView renderer;
    private GameInputHandler inputHandler;
    private float puckStillnessTimer = 0f;
    private float lastRenderPuckX = 0f;
    private float lastRenderPuckY = 0f;
    private boolean trackedRenderPuckPosition = false;

    public GameScreen(Game game, String playerName, String opponentName, boolean isHost) {
        this(game, playerName, opponentName, isHost, null);
    }

    /**
     * Creates a networked game screen. When {@code connector} is non-null, the
     * server's {@link GameOver} packet drives the screen transition (server-authoritative
     * mode) and local win-condition checks are disabled.
     */
    public GameScreen(Game game, String playerName, String opponentName, boolean isHost, ClientConnector connector) {
        super(game);
        this.playerName = playerName;
        this.opponentName = opponentName;
        this.connector = connector;
        this.isHost = isHost;

        // Initialize packet dispatcher for authoritative server packets (issue #48)
        // Done in constructor so it's available before show() is called
        networkState = new GameScreenState();
        networkState.setOpponentName(opponentName);
        packetDispatcher = ClientPacketHandlers.createGameDispatcher(networkState, this::navigateToGameOver,
            this::handleOpponentDisconnected);

        if (connector != null) {
            puckInterpolator = new PuckInterpolator();
        }
    }


    @Override
    protected float getMinViewportWidth() {
        return Constants.GAME_WIDTH;
    }

    @Override
    protected float getMinViewportHeight() {
        return Constants.GAME_HEIGHT;
    }

    @Override
    public void show() {
        super.show();

        puck = new Puck();

        // Initial positions - HORIZONTAL layout
        // Host (slot 1) plays on LEFT, Guest (slot 2) plays on RIGHT
        if (isHost) {
            playerMalletX = Constants.TABLE_X + Constants.TABLE_WIDTH * HOST_MALLET_X_RATIO;
            playerMalletY = Constants.CENTER_Y;
            opponentMalletX = Constants.TABLE_X + Constants.TABLE_WIDTH * GUEST_MALLET_X_RATIO;
            opponentMalletY = Constants.CENTER_Y;
        } else {
            playerMalletX = Constants.TABLE_X + Constants.TABLE_WIDTH * GUEST_MALLET_X_RATIO;
            playerMalletY = Constants.CENTER_Y;
            opponentMalletX = Constants.TABLE_X + Constants.TABLE_WIDTH * HOST_MALLET_X_RATIO;
            opponentMalletY = Constants.CENTER_Y;
        }

        resetPuck();

        renderer = GameRendererFactory.create(shapeRenderer, batch, camera, smallFont, largeFont, buttonFont, glyphLayout);
        inputHandler = new GameInputHandler(viewport, isHost);

        layoutButtons();

        AudioManager.startGameMusic();

        if (connector != null) {
            connector.addListener(this);
        }

        // Desktop uses fixed window sizes; Android keeps device-managed surface sizing.
        if (GameRendererFactory.isDesktopPlatform()) {
            // Resize window to landscape AFTER all state is initialized.
            // setWindowedMode triggers resize callback which may call render.
            Gdx.graphics.setWindowedMode(Constants.GAME_WINDOW_WIDTH, Constants.GAME_WINDOW_HEIGHT);
        }
    }

    @Override
    public void hide() {
        // Restore menu window size on desktop only.
        if (GameRendererFactory.isDesktopPlatform()) {
            Gdx.graphics.setWindowedMode(Constants.MENU_WINDOW_WIDTH, Constants.MENU_WINDOW_HEIGHT);
        }

        if (connector != null) {
            connector.removeListener(this);
        }
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        layoutButtons();
    }

    private void layoutButtons() {
        float w = getMinViewportWidth();
        float h = getWorldHeight();
        resumeButton = createButton(h / 2f + 80);
        quitButton = createButton(h / 2f - 80);
        float pauseSize = renderer != null ? renderer.getPauseButtonSize() : 45f;
        float margin = pauseSize / 3f;
        pauseButton = new Rectangle(w - pauseSize - margin, h - pauseSize - margin, pauseSize, pauseSize);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        drawGradientBackground();

        // Update pause button flash timer
        if (pauseButtonFlashTimer > 0) {
            pauseButtonFlashTimer -= delta;
            if (pauseButtonFlashTimer <= 0) {
                pauseErrorMessage = "";
            }
        }

        // Sync pause/error state from server even when paused (for resume to work)
        if (connector != null) {
            syncPauseStateFromServer();
        }

        if (!paused) {
            if (!countdownDone) {
                updateCountdown(delta);
            } else if (goalFlashTimer > 0) {
                goalFlashTimer -= delta;
                if (goalFlashTimer <= 0) {
                    resetPuck();
                }
            } else if (connector != null && !networkState.isOpponentConnected()) {
                // Freeze active play when the opponent has disconnected.
            } else {
                updateGame(delta);
            }
        }

        if (connector != null) {
            puckInterpolator.update(delta);
        }

        updateDisconnectState(delta);

        float w = getMinViewportWidth();
        float h = getWorldHeight();

        renderer.renderTable();
        renderer.renderPuck(getRenderPuckX(), getRenderPuckY());
        renderer.renderMalletsBySlot(getSlot1MalletX(), getSlot1MalletY(), getSlot2MalletX(), getSlot2MalletY());
        renderer.renderHUD(playerName, opponentName, playerScore, opponentScore, pauseButton, w, h,
                pauseButtonFlashTimer > 0, pauseErrorMessage);
        boolean showMetricsOverlay = shouldRenderMetricsOverlay();
        if (showMetricsOverlay) {
            renderer.renderMetricsOverlay(
                networkState.getClientRttMs(),
                networkState.getClientSnapshotRateHz(),
                networkState.getClientSnapshotJitterMs(),
                buildServerMetricsLine1(),
                buildServerMetricsLine2(),
                h
            );
        } else {
            renderer.renderRoleHint(getPlayerSideLabel(), h);
        }

        if (!countdownDone) {
            renderer.renderCountdown(countdownTimer);
        } else if (goalFlashTimer > 0) {
            renderer.renderGoalFlash("GOAL!", goalMessage, playerScore, opponentScore, w, h);
        } else if (stallMessageTimer > 0) {
            stallMessageTimer -= delta;
            renderer.renderGoalFlash("RESET!", STALL_RESET_MESSAGE, playerScore, opponentScore, w, h);
        }

        if (paused) {
            handlePauseInput();
            boolean canResume = connector == null || isHost;
            renderer.renderPauseOverlay(resumeButton, quitButton,
                    isPressed(resumeButton), isPressed(quitButton),
                    canResume, pauseErrorMessage, w, h);
        }

        renderDisconnectMessage(h);

        // Platform-specific overlay (e.g., touch indicators on Android)
        renderer.renderPlatformOverlay(w, h);
    }

    private void updateCountdown(float delta) {
        countdownTimer -= delta;
        int currentSecond = (int) Math.ceil(countdownTimer);
        if (currentSecond != lastCountdownSecond && currentSecond > 0) {
            AudioManager.playCountdownTick();
            lastCountdownSecond = currentSecond;
        }
        if (countdownTimer <= 0) {
            countdownDone = true;
            AudioManager.playGameStart();
        }
    }

    private void updateGame(float delta) {
        handlePlayerInput();

        if (connector != null) {
            // Networked mode: sync from server state
            syncFromServerState();
            // Play collision sounds client-side (physics is server-authoritative)
            checkCollisionSounds();
        } else {
            // Offline mode: run local physics
            puck.update(delta);
            checkCollisions();
            checkGoals();
        }
    }

    /**
     * Syncs local game objects from the server-authoritative networkState.
     * Called every frame in networked mode.
     */
    private void syncFromServerState() {
        var puckPos = networkState.getPuckPosition();
        var puckVel = networkState.getPuckVelocity();
        puckInterpolator.onAuthoritativeUpdate(puckPos, puckVel);

        puck.setX(puckPos.x);
        puck.setY(puckPos.y);
        puck.setVelocityX(puckVel.x);
        puck.setVelocityY(puckVel.y);

        // Host is slot 1 (left), Guest is slot 2 (right)
        if (isHost) {
            var opponentPos = networkState.getPlayer2Position();
            opponentMalletX = opponentPos.x;
            opponentMalletY = opponentPos.y;
        } else {
            var opponentPos = networkState.getPlayer1Position();
            opponentMalletX = opponentPos.x;
            opponentMalletY = opponentPos.y;
        }

        // Sync scores from server
        if (isHost) {
            playerScore = networkState.getPlayer1Score();
            opponentScore = networkState.getPlayer2Score();
        } else {
            playerScore = networkState.getPlayer2Score();
            opponentScore = networkState.getPlayer1Score();
        }

        // Sync goal flash from server (set by GoalScored handler)
        if (networkState.getGoalFlashTimer() > 0 && goalFlashTimer <= 0) {
            goalFlashTimer = networkState.getGoalFlashTimer();
            goalMessage = networkState.getGoalMessage();

            // Determine if this player scored based on slot assignment
            // Host = slot 1, Guest = slot 2
            int mySlot = isHost ? 1 : 2;
            int scoringSlot = networkState.getLastScoringPlayerId();

            if (scoringSlot == mySlot) {
                AudioManager.playGoalSound();
            } else {
                AudioManager.playLossSound();
            }
            networkState.setGoalFlashTimer(0f);
        }

        if (networkState.consumePuckStallReset()) {
            stallMessageTimer = STALL_MESSAGE_DURATION_SECONDS;
            AudioManager.playError();
        }

        // Sync pause state from server (set by PauseEvent/ResumeEvent handlers)
        syncPauseStateFromServer();
    }

    /**
     * Syncs pause and error flash state from the server.
     * Called every frame in networked mode, even when paused, so that
     * ResumeEvent packets can unpause the game.
     */
    private void syncPauseStateFromServer() {
        paused = networkState.isPaused();

        // Sync error flash state from server (set by ErrorPacket handler)
        if (networkState.getErrorFlashTimer() > 0 && pauseButtonFlashTimer <= 0) {
            pauseButtonFlashTimer = networkState.getErrorFlashTimer();
            pauseErrorMessage = networkState.getErrorMessage();
            AudioManager.playMenuSelect();
            networkState.setErrorFlashTimer(0f);
        }
    }

    private void navigateToGameOver() {
        String winnerName = networkState.getWinnerName();
        boolean playerWon = winnerName != null && winnerName.equals(playerName);
        game.setScreen(new GameOverScreen(game, playerName, opponentName,
                networkState.getPlayer1Score(), networkState.getPlayer2Score(), playerWon,
                connector, isHost));
    }

    private void handleOpponentDisconnected() {
        paused = false;
        if ("Left the room".equals(networkState.getDisconnectReason())) {
            returnToMenuWithoutLeavePacket();
        }
    }

    private void renderDisconnectMessage(float worldHeight) {
        String title = getDisconnectTitle();
        if (title == null) {
            return;
        }

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawCenteredText(largeFont, title, worldHeight / 2f + DISCONNECT_TITLE_OFFSET_Y, DANGER_COLOR);
        drawCenteredText(smallFont, WAITING_MESSAGE, worldHeight / 2f + DISCONNECT_SUBTITLE_OFFSET_Y, Color.WHITE);
        batch.end();
    }

    private String getDisconnectTitle() {
        if (connector == null || !countdownDone) {
            return null;
        }
        if (!networkState.isOpponentConnected()) {
            return DISCONNECTED_MESSAGE;
        }
        if (shouldShowLostConnectionMessage()) {
            return LOST_CONNECTION_MESSAGE;
        }
        return null;
    }

    private boolean shouldShowLostConnectionMessage() {
        if (!networkState.hasReceivedSnapshot()) {
            return false;
        }
        float secondsSinceSnapshot = networkState.getSecondsSinceLastSnapshot(System.nanoTime());
        return secondsSinceSnapshot >= SNAPSHOT_STALL_THRESHOLD_SECONDS
                && puckStillnessTimer >= PUCK_STILLNESS_THRESHOLD_SECONDS;
    }

    private void updateDisconnectState(float delta) {
        if (connector == null || !countdownDone || paused || goalFlashTimer > 0 || stallMessageTimer > 0) {
            puckStillnessTimer = 0f;
            trackedRenderPuckPosition = false;
            return;
        }

        float currentRenderPuckX = getRenderPuckX();
        float currentRenderPuckY = getRenderPuckY();

        if (!trackedRenderPuckPosition) {
            lastRenderPuckX = currentRenderPuckX;
            lastRenderPuckY = currentRenderPuckY;
            trackedRenderPuckPosition = true;
            puckStillnessTimer = 0f;
            return;
        }

        if (Math.abs(currentRenderPuckX - lastRenderPuckX) <= PUCK_STOP_EPSILON
                && Math.abs(currentRenderPuckY - lastRenderPuckY) <= PUCK_STOP_EPSILON) {
            puckStillnessTimer += delta;
        } else {
            puckStillnessTimer = 0f;
        }

        lastRenderPuckX = currentRenderPuckX;
        lastRenderPuckY = currentRenderPuckY;
    }

    private void handlePlayerInput() {
        if (inputHandler.isPauseTapped(pauseButton)) {
            if (connector != null) {
                connector.send(new PauseRequest());
            } else {
                paused = true;
            }
            return;
        }
        // Don't move mallet if touching pause button
        if (inputHandler.isTouchingButton(pauseButton)) {
            return;
        }
        GameInputHandler.MalletPosition newPos = inputHandler.computeMalletPosition(playerMalletX, playerMalletY);
        if (newPos != null) {
            playerMalletX = newPos.x();
            playerMalletY = newPos.y();
            if (connector != null) {
                connector.send(new PlayerPosition(0, playerMalletX, playerMalletY));
            }
        }
    }

    // Collision logic moved to CollisionDetector.java in utils
    private void checkCollisions() {
        // Use table bounds for wall collisions (offset by TABLE_X, TABLE_Y)
        handleTableWallCollisions();

        if (CollisionDetector.handleMalletCollision(puck, playerMalletX, playerMalletY)) {
            AudioManager.playPaddleHit();
        }
        if (CollisionDetector.handleMalletCollision(puck, opponentMalletX, opponentMalletY)) {
            AudioManager.playPaddleHit();
        }
    }

    private void handleTableWallCollisions() {
        float x = puck.getX();
        float y = puck.getY();
        float r = Constants.PUCK_RADIUS;

        // Top/bottom table walls
        if (y - r <= Constants.TABLE_Y) {
            puck.setY(Constants.TABLE_Y + r + WALL_SEPARATION_EPSILON);
            puck.setVelocityY(Math.abs(puck.getVelocityY()));
            AudioManager.playPaddleHit();
        } else if (y + r >= Constants.TABLE_Y + Constants.TABLE_HEIGHT) {
            puck.setY(Constants.TABLE_Y + Constants.TABLE_HEIGHT - r - WALL_SEPARATION_EPSILON);
            puck.setVelocityY(-Math.abs(puck.getVelocityY()));
            AudioManager.playPaddleHit();
        }

        // Left/right table walls (outside goal area)
        if (!isInGoalLane(y)) {
            if (x - r <= Constants.TABLE_X) {
                puck.setX(Constants.TABLE_X + r + WALL_SEPARATION_EPSILON);
                puck.setVelocityX(Math.abs(puck.getVelocityX()));
                AudioManager.playPaddleHit();
            } else if (x + r >= Constants.TABLE_X + Constants.TABLE_WIDTH) {
                puck.setX(Constants.TABLE_X + Constants.TABLE_WIDTH - r - WALL_SEPARATION_EPSILON);
                puck.setVelocityX(-Math.abs(puck.getVelocityX()));
                AudioManager.playPaddleHit();
            }
        }
    }

    /**
     * Detects collisions for sound effects in network mode.
     * Physics is server-authoritative, but sounds are played client-side.
     */
    private void checkCollisionSounds() {
        float x = puck.getX();
        float y = puck.getY();
        float r = Constants.PUCK_RADIUS;
        float malletCollisionDist = Constants.MALLET_RADIUS + r + COLLISION_SOUND_THRESHOLD;

        // Mallet collision detection
        float playerDist = Vector2.dst(x, y, playerMalletX, playerMalletY);
        float opponentDist = Vector2.dst(x, y, opponentMalletX, opponentMalletY);

        boolean nearPlayerMallet = playerDist < malletCollisionDist;
        boolean nearOpponentMallet = opponentDist < malletCollisionDist;

        if (nearPlayerMallet && !wasNearPlayerMallet) {
            AudioManager.playPaddleHit();
        }
        if (nearOpponentMallet && !wasNearOpponentMallet) {
            AudioManager.playPaddleHit();
        }
        wasNearPlayerMallet = nearPlayerMallet;
        wasNearOpponentMallet = nearOpponentMallet;

        // Wall collision detection
        boolean nearTopWall = y + r >= Constants.TABLE_Y + Constants.TABLE_HEIGHT - COLLISION_SOUND_THRESHOLD;
        boolean nearBottomWall = y - r <= Constants.TABLE_Y + COLLISION_SOUND_THRESHOLD;
        boolean nearLeftWall = x - r <= Constants.TABLE_X + COLLISION_SOUND_THRESHOLD;
        boolean nearRightWall = x + r >= Constants.TABLE_X + Constants.TABLE_WIDTH - COLLISION_SOUND_THRESHOLD;

        // Only play sound when entering collision zone (edge detection)
        if (nearTopWall && !wasNearTopWall) {
            AudioManager.playPaddleHit();
        }
        if (nearBottomWall && !wasNearBottomWall) {
            AudioManager.playPaddleHit();
        }

        // Left/right walls only outside goal area
        if (!isInGoalLane(y)) {
            if (nearLeftWall && !wasNearLeftWall) {
                AudioManager.playPaddleHit();
            }
            if (nearRightWall && !wasNearRightWall) {
                AudioManager.playPaddleHit();
            }
        }

        wasNearTopWall = nearTopWall;
        wasNearBottomWall = nearBottomWall;
        wasNearLeftWall = nearLeftWall;
        wasNearRightWall = nearRightWall;
    }

    private void checkGoals() {
        // Goals are on LEFT and RIGHT edges (horizontal layout)
        if (!isInGoalLane(puck.getY())) {
            return;
        }
        // Left goal - opponent scores (puck crossed left edge)
        if (puck.getX() - Constants.PUCK_RADIUS <= Constants.TABLE_X) {
            opponentScore++;
            goalFlashTimer = GOAL_FLASH_DURATION;
            goalMessage = opponentName + " scores!";
            AudioManager.playLossSound();
            checkWinCondition();
            return;
        }
        // Right goal - player scores (puck crossed right edge)
        if (puck.getX() + Constants.PUCK_RADIUS >= Constants.TABLE_X + Constants.TABLE_WIDTH) {
            playerScore++;
            goalFlashTimer = GOAL_FLASH_DURATION;
            goalMessage = playerName + " scores!";
            AudioManager.playGoalSound();
            checkWinCondition();
        }
    }

    private boolean isInGoalLane(float y) {
        float goalTop = Constants.CENTER_Y + Constants.GOAL_WIDTH / 2f;
        float goalBottom = Constants.CENTER_Y - Constants.GOAL_WIDTH / 2f;
        return y >= goalBottom && y <= goalTop;
    }

    private void checkWinCondition() {
        if (connector != null) {
            // Server is authoritative; transition happens via onReceived(GameOver)
            return;
        }
        if (playerScore >= Constants.WINNING_SCORE) {
            game.setScreen(new GameOverScreen(game, playerName, opponentName, playerScore, opponentScore, true));
        } else if (opponentScore >= Constants.WINNING_SCORE) {
            game.setScreen(new GameOverScreen(game, playerName, opponentName, playerScore, opponentScore, false));
        }
    }

    private void resetPuck() {
        puck.reset(Constants.TABLE_X + Constants.TABLE_WIDTH / 2f, Constants.CENTER_Y);
        // Reset interpolator to avoid smoothing from old position to center (issue #59)
        if (puckInterpolator != null) {
            puckInterpolator.reset();
        }
    }

    private float getSlot1MalletX() {
        if (connector != null) {
            return networkState.getPlayer1Position().x;
        }
        return isHost ? playerMalletX : opponentMalletX;
    }

    private float getSlot1MalletY() {
        if (connector != null) {
            return networkState.getPlayer1Position().y;
        }
        return isHost ? playerMalletY : opponentMalletY;
    }

    private float getSlot2MalletX() {
        if (connector != null) {
            return networkState.getPlayer2Position().x;
        }
        return isHost ? opponentMalletX : playerMalletX;
    }

    private float getSlot2MalletY() {
        if (connector != null) {
            return networkState.getPlayer2Position().y;
        }
        return isHost ? opponentMalletY : playerMalletY;
    }

    private float getRenderPuckX() {
        if (shouldInterpolatePuck()) {
            return puckInterpolator.getRenderX();
        }
        return puck.getX();
    }

    private float getRenderPuckY() {
        if (shouldInterpolatePuck()) {
            return puckInterpolator.getRenderY();
        }
        return puck.getY();
    }

    private boolean shouldInterpolatePuck() {
        return connector != null && puckInterpolator.isInitialized();
    }

    private String buildServerMetricsLine1() {
        RoomMetricsSnapshot metrics = networkState.getRoomMetricsSnapshot();
        if (metrics == null) {
            return "Server: waiting for metrics...";
        }
        return String.format("Srv Tick: %dHz  Bcast: %.1f/%dHz  LoopJit: %.1f/%.1fms",
            metrics.simulationTickHz,
            metrics.effectiveStateBroadcastHz,
            metrics.maxStateBroadcastHz,
            metrics.loopJitterMs,
            metrics.maxLoopJitterMs);
    }

    private String buildServerMetricsLine2() {
        RoomMetricsSnapshot metrics = networkState.getRoomMetricsSnapshot();
        if (metrics == null) {
            return "InDrop: --  OutDrop: --  Queue: --  Bw: --";
        }
        return String.format("InDrop: %.1f%%  OutDrop: %.1f%%  Queue: %d  Bw: %.0f B/s",
            metrics.incomingDropRate * 100f,
            metrics.outgoingDropRate * 100f,
            metrics.incomingQueueDepth,
            metrics.outgoingBandwidthBps);
    }

    private String getPlayerSideLabel() {
        return isHost ? "You: LEFT player (Slot 1)" : "You: RIGHT player (Slot 2)";
    }

    private boolean shouldRenderMetricsOverlay() {
        if (renderer.supportsKeyboardDebug()) {
            return Gdx.input != null && Gdx.input.isKeyPressed(Input.Keys.SPACE);
        } else {
            // Android: trigger debug with 3-finger touch
            return Gdx.input != null && Gdx.input.isTouched(2);
        }
    }

    private void handlePauseInput() {
        boolean canResume = connector == null || isHost;
        if (inputHandler.isResumeTapped(resumeButton) && canResume) {
            if (connector != null) {
                connector.send(new ResumeRequest());
            } else {
                paused = false;
            }
            return;
        }
        if (inputHandler.isQuitTapped(quitButton)) {
            navigateToMenu();
        }
    }

    void navigateToMenu() {
        if (connector != null) {
            connector.send(new LeaveRoom());
        }
        returnToMenuWithoutLeavePacket();
    }

    private void returnToMenuWithoutLeavePacket() {
        game.setScreen(new MenuScreen(game));
    }

    /**
     * Called when the Android app is backgrounded (FR4.1, A1).
     * Sends a pause request to the server if this player is the host.
     * Non-host players cannot pause; their mallet simply stops moving.
     */
    @Override
    public void onAppPause() {
        if (connector != null && !paused && isHost) {
            connector.send(new PauseRequest());
        }
    }

    /**
     * Called when the Android app returns to foreground (FR4.2, A1).
     * The game will resume automatically when the server broadcasts ResumeEvent.
     */
    @Override
    public void onAppResume() {
        // No action needed; host must explicitly resume via ResumeEvent
    }

    @Override
    public void onConnected() {}

    @Override
    public void onDisconnected() {}

    /**
     * Handles packets received from the server.
     * Routes packets to the {@link ClientPacketDispatcher} for type-safe handling
     * on the render thread (issue #48).
     *
    * <p>Supported packets: GameStateSnapshot, GoalScored, GameOver, PauseEvent,
    * ResumeEvent, PlayerJoined, PlayerLeft, RoomMetricsSnapshot.
    * Local client metrics are also updated from Pong and GameStateSnapshot arrivals.</p>
     */
    @Override
    public void onReceived(Object packet) {
        if (packet instanceof GameReset) {
            returnToMenuWithoutLeavePacket();
            return;
        }
        if (packet instanceof Pong pong) {
            networkState.updateClientRtt(pong.getRoundTripTime());
        } else if (packet instanceof GameStateSnapshot) {
            networkState.recordSnapshotArrival(System.nanoTime());
        }
        if (packetDispatcher != null) {
            packetDispatcher.dispatch(packet);
        }
    }

    /** Returns the packet dispatcher for testing. */
    ClientPacketDispatcher getPacketDispatcher() {
        return packetDispatcher;
    }

    /** Returns the network state for testing. */
    GameScreenState getNetworkState() {
        return networkState;
    }
}
