package no.ntnu.ping404.network;

import com.badlogic.gdx.math.Vector2;
import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.network.packets.RoomMetricsSnapshot;

/**
 * Mutable game state container for the client GameScreen.
 * Handlers update this state, and the render loop reads it.
 * 
 * <p>This separates packet parsing from UI mutation (issue #48).</p>
 */
public class GameScreenState {

    /** Current game phase. */
    private GameState.Phase phase = GameState.Phase.WAITING;

    /** Puck position. */
    private final Vector2 puckPosition = new Vector2();

    /** Puck velocity. */
    private final Vector2 puckVelocity = new Vector2();

    /** Player 1 (left) mallet position. */
    private final Vector2 player1Position = new Vector2();

    /** Player 2 (right) mallet position. */
    private final Vector2 player2Position = new Vector2();

    /** Player 1 score. */
    private int player1Score = 0;

    /** Player 2 score. */
    private int player2Score = 0;

    /** Whether the game is paused. */
    private boolean paused = false;

    /** ID of player who paused (for display). */
    private int pausedByPlayerId = -1;

    /** Goal flash message to display. */
    private String goalMessage = "";

    /** Goal flash timer (seconds remaining). */
    private float goalFlashTimer = 0f;

    /** ID of the player who scored the last goal (1 or 2). */
    private int lastScoringPlayerId = 0;

    /** Opponent name (for join/leave messages). */
    private String opponentName = "";

    /** Whether opponent is connected. */
    private boolean opponentConnected = true;

    /** Message to display (e.g., "Opponent disconnected"). */
    private String statusMessage = "";

    /** Last disconnect reason received from PlayerLeft. */
    private String disconnectReason = "";

    /** Name of the winner from the GameOver packet (null until game ends). */
    private String winnerName = null;

    /** Latest server-side room metrics snapshot. */
    private RoomMetricsSnapshot roomMetricsSnapshot;

    /** Latest client-side RTT estimate in milliseconds. */
    private long clientRttMs;

    /** Latest client-side snapshot receive rate estimate in Hz. */
    private float clientSnapshotRateHz;

    /** Latest client-side snapshot receive jitter estimate in milliseconds. */
    private float clientSnapshotJitterMs;

    private long lastSnapshotArrivalNanos;
    private long lastSnapshotGapNanos;

    /** Error message from server (e.g., pause denied). */
    private String errorMessage = "";

    /** Whether the server just reset the puck due to the 7-second stall rule (FR2.6). */
    private boolean puckStallReset = false;

    /** Timer for displaying error flash (seconds remaining). */
    private float errorFlashTimer = 0f;

    // Getters and setters

    public GameState.Phase getPhase() {
        return phase;
    }

    public void setPhase(GameState.Phase phase) {
        this.phase = phase;
    }

    public Vector2 getPuckPosition() {
        return puckPosition;
    }

    public void setPuckPosition(float x, float y) {
        this.puckPosition.set(x, y);
    }

    public Vector2 getPuckVelocity() {
        return puckVelocity;
    }

    public void setPuckVelocity(float x, float y) {
        this.puckVelocity.set(x, y);
    }

    public Vector2 getPlayer1Position() {
        return player1Position;
    }

    public void setPlayer1Position(float x, float y) {
        this.player1Position.set(x, y);
    }

    public Vector2 getPlayer2Position() {
        return player2Position;
    }

    public void setPlayer2Position(float x, float y) {
        this.player2Position.set(x, y);
    }

    public int getPlayer1Score() {
        return player1Score;
    }

    public void setPlayer1Score(int score) {
        this.player1Score = score;
    }

    public int getPlayer2Score() {
        return player2Score;
    }

    public void setPlayer2Score(int score) {
        this.player2Score = score;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public int getPausedByPlayerId() {
        return pausedByPlayerId;
    }

    public void setPausedByPlayerId(int id) {
        this.pausedByPlayerId = id;
    }

    public String getGoalMessage() {
        return goalMessage;
    }

    public void setGoalMessage(String message) {
        this.goalMessage = message;
    }

    public float getGoalFlashTimer() {
        return goalFlashTimer;
    }

    public void setGoalFlashTimer(float timer) {
        this.goalFlashTimer = timer;
    }

    public int getLastScoringPlayerId() {
        return lastScoringPlayerId;
    }

    public void setLastScoringPlayerId(int playerId) {
        this.lastScoringPlayerId = playerId;
    }

    public String getOpponentName() {
        return opponentName;
    }

    public void setOpponentName(String name) {
        this.opponentName = name;
    }

    public boolean isOpponentConnected() {
        return opponentConnected;
    }

    public void setOpponentConnected(boolean connected) {
        this.opponentConnected = connected;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String message) {
        this.statusMessage = message;
    }

    public String getDisconnectReason() {
        return disconnectReason;
    }

    public void setDisconnectReason(String disconnectReason) {
        this.disconnectReason = disconnectReason != null ? disconnectReason : "";
    }

    public String getWinnerName() {
        return winnerName;
    }

    public void setWinnerName(String name) {
        this.winnerName = name;
    }

    public RoomMetricsSnapshot getRoomMetricsSnapshot() {
        return roomMetricsSnapshot;
    }

    public void setRoomMetricsSnapshot(RoomMetricsSnapshot roomMetricsSnapshot) {
        this.roomMetricsSnapshot = roomMetricsSnapshot;
    }

    public long getClientRttMs() {
        return clientRttMs;
    }

    public float getClientSnapshotRateHz() {
        return clientSnapshotRateHz;
    }

    public float getClientSnapshotJitterMs() {
        return clientSnapshotJitterMs;
    }

    /** Updates local RTT metric from Pong packets. */
    public void updateClientRtt(long rttMs) {
        this.clientRttMs = Math.max(rttMs, 0L);
    }

    /**
     * Updates local snapshot receive rate and jitter metrics.
     * Uses inter-arrival deltas with a lightweight smoothed absolute-difference jitter estimate.
     */
    public void recordSnapshotArrival(long arrivalNanos) {
        if (arrivalNanos <= 0) {
            return;
        }
        if (lastSnapshotArrivalNanos == 0L) {
            lastSnapshotArrivalNanos = arrivalNanos;
            return;
        }

        long gapNanos = arrivalNanos - lastSnapshotArrivalNanos;
        if (gapNanos <= 0L) {
            return;
        }

        clientSnapshotRateHz = 1_000_000_000f / gapNanos;
        if (lastSnapshotGapNanos > 0L) {
            float deltaMs = Math.abs(gapNanos - lastSnapshotGapNanos) / 1_000_000f;
            clientSnapshotJitterMs = clientSnapshotJitterMs == 0f
                ? deltaMs
                : (clientSnapshotJitterMs * 0.8f) + (deltaMs * 0.2f);
        }

        lastSnapshotGapNanos = gapNanos;
        lastSnapshotArrivalNanos = arrivalNanos;
    }

    /** Returns whether at least one authoritative snapshot has been received. */
    public boolean hasReceivedSnapshot() {
        return lastSnapshotArrivalNanos > 0L;
    }

    /** Returns seconds since the most recent authoritative snapshot. */
    public float getSecondsSinceLastSnapshot(long nowNanos) {
        if (lastSnapshotArrivalNanos == 0L || nowNanos <= lastSnapshotArrivalNanos) {
            return 0f;
        }
        return (nowNanos - lastSnapshotArrivalNanos) / 1_000_000_000f;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String message) {
        this.errorMessage = message != null ? message : "";
    }

    public float getErrorFlashTimer() {
        return errorFlashTimer;
    }

    public void setErrorFlashTimer(float timer) {
        this.errorFlashTimer = timer;
    }

    /**
     * Returns and clears the puck stall reset flag. Returns {@code true} once per stall event.
     */
    public boolean consumePuckStallReset() {
        boolean value = puckStallReset;
        puckStallReset = false;
        return value;
    }

    /** Sets the puck stall reset flag. */
    public void setPuckStallReset(boolean reset) {
        this.puckStallReset = reset;
    }
}
