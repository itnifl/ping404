package no.ntnu.ping404.server.game;

import com.badlogic.gdx.math.Vector2;
import no.ntnu.ping404.model.GameEngine;
import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.model.GameState.Phase;
import no.ntnu.ping404.model.Player;
import no.ntnu.ping404.model.Puck;
import no.ntnu.ping404.model.Score;
import no.ntnu.ping404.network.GameConfig;
import no.ntnu.ping404.network.ServerConnector;
import no.ntnu.ping404.network.packets.GameOver;
import no.ntnu.ping404.network.packets.GameStateSnapshot;
import no.ntnu.ping404.network.packets.GoalScored;
import no.ntnu.ping404.network.packets.ScoreUpdate; 
import no.ntnu.ping404.server.GameRoom;
import no.ntnu.ping404.server.metrics.MetricsCollector;
import no.ntnu.ping404.utils.Constants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-authoritative game loop running on a dedicated thread at a fixed tick rate.
 * Implements the Consumer side of the Producer-Consumer pattern (issue #14).
 *
 * <p>Each tick performs the following steps in order:</p>
 * <ol>
 *   <li>Drain and process the {@link InputQueue} (paddle moves, pause/resume)</li>
 *   <li>Advance the authoritative match via {@link GameEngine#tick(float)}</li>
 *   <li>Update {@link Score} on goal detection and broadcast {@link GoalScored}</li>
 *   <li>Broadcast {@link GameStateSnapshot} to all clients in room</li>
 *   <li>Check win condition and trigger {@link GameOver} if reached</li>
 * </ol>
 *
 * <p>Affected requirements: FR2.x (physics), FR3.x (scoring), P2 (latency)</p>
 */
public class GameLoop implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(GameLoop.class);

    /** Target tick rate in Hz. */
    public static final int TICK_RATE = 60;

    /** Duration of one tick in nanoseconds. */
    static final long TICK_DURATION_NS = 1_000_000_000L / TICK_RATE;

    /** Duration of one tick in seconds (passed to physics). */
    static final float TICK_DELTA = 1.0f / TICK_RATE;

    /** Delay in ms after a goal before the puck is relaunched. */
    static final long GOAL_RESET_DELAY_MS = 1000;

    private static final int THREAD_STOP_TIMEOUT_MS = 1000;
    private static final int CATCHUP_SKIP_THRESHOLD_TICKS = 10;
    private static final double DEFAULT_MAX_LOOP_JITTER_MS = 10.0;
    private static final String THREAD_NAME_PREFIX = "game-loop-";
    private static final String FALLBACK_PLAYER_NAME_PREFIX = "Player ";
    private static final String DECIMAL_FORMAT = "%.2f";
    private final GameRoom room;
    private final InputQueue inputQueue;
    private final ServerConnector connector;
    private final int simulationTickHz;
    private final long simulationTickDurationNs;
    private final float simulationTickDeltaSeconds;
    private final int maxStateBroadcastHz;
    private final float stateBroadcastIntervalSeconds;
    private final long maxLoopJitterNs;

    private volatile boolean running;
    private Thread thread;

    /**
     * Timestamp (System.currentTimeMillis) when the puck should relaunch after a goal. 0 = no pending reset.
     * System.currentTimeMillis() is used here because it represents a wall-clock delay
     * (human-readable duration), unlike tick timing which uses System.nanoTime().
     */
    private long goalResetTime;

    /**
     * X-coordinate the puck will relaunch from after the goal-reset delay.
     * Set to the conceding player's half centre on each goal (FR2.10).
     */
    private float pendingResetX = Constants.boardCenterX();
    private float secondsSinceLastStateBroadcast;
    private float latestLoopJitterMs;

    private final BroadcastRateController backpressure;
    private final MetricsBroadcaster metricsBroadcaster;
    private final LoopJitterMonitor jitterMonitor;
    private final GameInputProcessor inputProcessor;
    private GameStateSnapshot pendingStateSnapshot;

    public GameLoop(GameRoom room, InputQueue inputQueue, ServerConnector connector) {
        this(room, inputQueue, connector, null);
    }

    public GameLoop(GameRoom room, InputQueue inputQueue, ServerConnector connector, MetricsCollector metricsCollector) {
        this.room = room;
        this.inputQueue = inputQueue;
        this.connector = connector;
        int configuredSimulationTickHz = GameConfig.getSimulationTickHz();
        int configuredBroadcastHz = GameConfig.getMaxStateBroadcastHz();
        double configuredJitterMs = GameConfig.getMaxLoopJitterMs();

        this.simulationTickHz = configuredSimulationTickHz > 0 ? configuredSimulationTickHz : TICK_RATE;
        this.simulationTickDurationNs = 1_000_000_000L / this.simulationTickHz;
        this.simulationTickDeltaSeconds = 1.0f / this.simulationTickHz;
        this.maxStateBroadcastHz = configuredBroadcastHz > 0 ? configuredBroadcastHz : this.simulationTickHz;
        this.stateBroadcastIntervalSeconds = 1.0f / this.maxStateBroadcastHz;
        this.maxLoopJitterNs = (long) ((configuredJitterMs > 0 ? configuredJitterMs : DEFAULT_MAX_LOOP_JITTER_MS) * 1_000_000L);
        this.secondsSinceLastStateBroadcast = this.stateBroadcastIntervalSeconds;
        this.backpressure = new BroadcastRateController(
            metricsCollector, maxLoopJitterNs, stateBroadcastIntervalSeconds, maxStateBroadcastHz, room.getRoomId());
        this.metricsBroadcaster = new MetricsBroadcaster(
            metricsCollector, room, connector, simulationTickHz, maxStateBroadcastHz, maxLoopJitterNs);
        this.jitterMonitor = new LoopJitterMonitor(simulationTickDurationNs, maxLoopJitterNs, room.getRoomId());
        this.inputProcessor = new GameInputProcessor(inputQueue, room);
    }

    /**
     * Starts the game loop on a new daemon thread.
     * Safe to call multiple times; subsequent calls are ignored if already running.
     */
    public void start() {
        if (running) return;
        running = true;
        pendingStateSnapshot = null;

        // Reset puck to center with a random launch direction
        Puck puck = room.getGameState().getPuck();
        puck.reset(Constants.boardCenterX(), Constants.boardCenterY());

        launchThread("GameLoop started for room '{}' (simulationTick={}Hz, maxBroadcast={}Hz, maxJitter={}ms)");
    }

    /**
     * Starts the game loop on a new daemon thread WITHOUT resetting the puck.
     * Used for reconnect scenarios where the game state should be preserved.
     * See issue #14 code review Defect #1.
     */
    public void startWithoutReset() {
        if (running) return;
        running = true;
        pendingStateSnapshot = null;

        launchThread("GameLoop started (without reset) for room '{}' (simulationTick={}Hz, maxBroadcast={}Hz, maxJitter={}ms)");
    }

    private void launchThread(String logMessage) {
        thread = new Thread(this, THREAD_NAME_PREFIX + room.getRoomId());
        thread.setDaemon(true);
        thread.start();
        logger.info(logMessage, room.getRoomId(), simulationTickHz, maxStateBroadcastHz, String.format(DECIMAL_FORMAT, maxLoopJitterNs / 1_000_000.0));
    }

    /**
     * Stops the game loop. Blocks until the thread terminates (up to 1 second).
     */
    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(THREAD_STOP_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
        logger.info("GameLoop stopped for room '{}'", room.getRoomId());
    }

    public boolean isRunning() {
        return running;
    }

    /** Exposes the input queue so network handlers (producers) can enqueue events. */
    public InputQueue getInputQueue() {
        return inputQueue;
    }

    @Override
    public void run() {
        long nextTick = System.nanoTime();

        while (running) {
            long now = System.nanoTime();
            if (now < nextTick) {
                long sleepMs = (nextTick - now) / 1_000_000;
                if (sleepMs > 0) {
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                continue;
            }

            latestLoopJitterMs = jitterMonitor.measure(now);

            try {
                tick(simulationTickDeltaSeconds);
            } catch (Exception e) {
                logger.error("Error in game loop tick for room '{}': {}", room.getRoomId(), e.getMessage(), e);
            }

            nextTick += simulationTickDurationNs;

            // If we're falling behind, skip ahead instead of trying to catch up endlessly
            if (System.nanoTime() - nextTick > simulationTickDurationNs * CATCHUP_SKIP_THRESHOLD_TICKS) {
                nextTick = System.nanoTime();
            }
        }
    }

    /**
     * Executes a single game tick. Public for cross-package testability.
     *
     * @param deltaTime time step in seconds
     */
    public void tick(float deltaTime) {
        // 1. Process input queue (Consumer side of Producer-Consumer)
        inputProcessor.process();

        Phase phase = room.getPhase();

        // Only run physics while actively playing
        if (phase != Phase.PLAYING) {
            pendingStateSnapshot = null;
            return;
        }

        // If waiting for goal reset delay, check if it's time to relaunch
        if (goalResetTime > 0) {
            if (System.currentTimeMillis() >= goalResetTime) {
                resetPuckToCenter();
                goalResetTime = 0;
                room.getGameEngine().resetStallTimer();
            } else {
                // Still waiting; throttle network snapshots independently from simulation ticks.
                maybeBroadcastState(deltaTime);
                metricsBroadcaster.maybeBroadcast(deltaTime, latestLoopJitterMs);
                return;
            }
        }

        // 2-4. Advance pure game rules in Core and consume the outcome on the server.
        GameEngine.TickOutcome outcome = room.getGameEngine().tick(deltaTime);

        if (outcome.hasGoal()) {
            handleGoal(outcome);
            return; // State already broadcast inside handleGoal
        }

        if (outcome.hasPuckStallReset()) {
            logger.info("Puck reset to center (7-second rule, FR2.6) in room '{}'", room.getRoomId());
            GameStateSnapshot stallSnapshot = createStateSnapshot();
            stallSnapshot.puckStallReset = true;
            broadcastStateSnapshot(stallSnapshot);
            return;
        }

        // 5. Broadcast state snapshots on capped network frequency.
        maybeBroadcastState(deltaTime);
        metricsBroadcaster.maybeBroadcast(deltaTime, latestLoopJitterMs);

        // 6. Check win condition (defensive, also checked in handleGoal)
        checkWinCondition();
    }

    private void handleGoal(GameEngine.TickOutcome outcome) {
        int scoringSlot = outcome.getScoringPlayerSlot();
        int scoringConnectionId = room.getConnectionIdForSlot(scoringSlot);

        // Broadcast GoalScored event
        GoalScored goalPacket = new GoalScored(
            scoringConnectionId,
            outcome.getPlayer1Score(),
            outcome.getPlayer2Score()
        );
        room.broadcast(goalPacket, connector);

        // Broadcast dedicated ScoreUpdate event
        ScoreUpdate scorePacket = new ScoreUpdate(
            outcome.getPlayer1Score(),
            outcome.getPlayer2Score()
        );
        room.broadcast(scorePacket, connector);

        logger.info("Goal in room '{}': Player {} scored. Score: {}-{}",
            room.getRoomId(), scoringSlot, outcome.getPlayer1Score(), outcome.getPlayer2Score());

        // Check win condition immediately after goal
        if (checkWinCondition(outcome)) {
            return; // Game is over, don't schedule reset
        }

        // Core already parks the puck on the conceding player's half for the reset delay.
        pendingResetX = outcome.getResetPuckX();
        goalResetTime = System.currentTimeMillis() + GOAL_RESET_DELAY_MS;

        broadcastStateImmediately();
    }

    private void resetPuckToCenter() {
        Puck puck = room.getGameState().getPuck();
        puck.reset(pendingResetX, Constants.boardCenterY());
        logger.debug("Puck relaunched from x={} in room '{}'", pendingResetX, room.getRoomId());
    }

    /**
     * Checks if a player has won and, if so, transitions to FINISHED and broadcasts GameOver.
     *
     * @return true if the game is over
     */
    boolean checkWinCondition() {
        GameState gameState = room.getGameState();
        if (gameState.getPhase() == Phase.FINISHED && gameState.getScore().hasWinner()) {
            return true;
        }
        return checkWinCondition(room.getGameEngine().evaluateMatchState());
    }

    private boolean checkWinCondition(GameEngine.TickOutcome outcome) {
        GameState gameState = room.getGameState();

        if (!outcome.isMatchFinished()) return false;
        if (gameState.getPhase() == Phase.FINISHED) return true; // already handled

        // Transition to FINISHED
        room.setPhase(Phase.FINISHED);

        int winnerSlot = outcome.getWinnerSlot();
        int winnerConnectionId = room.getConnectionIdForSlot(winnerSlot);
        Player winnerPlayer = winnerConnectionId != -1 ? gameState.getPlayer(winnerConnectionId) : null;
        String winnerName;

        if (winnerPlayer != null) {
            winnerName = winnerPlayer.getName();
        } else {
            GameRoom.DisconnectedPlayer disconnectedWinner = room.getDisconnectedPlayer(winnerSlot);
            if (disconnectedWinner != null) {
                winnerConnectionId = disconnectedWinner.connectionId();
                winnerName = disconnectedWinner.playerName();
            } else {
                if (winnerConnectionId == -1) {
                    winnerConnectionId = winnerSlot;
                }
                winnerName = FALLBACK_PLAYER_NAME_PREFIX + winnerSlot;
            }
        }

        GameOver gameOverPacket = new GameOver(
            winnerConnectionId,
            winnerName,
            outcome.getPlayer1Score(),
            outcome.getPlayer2Score()
        );
        room.broadcast(gameOverPacket, connector);

        logger.info("Game over in room '{}': {} wins! Score: {}-{}",
            room.getRoomId(), winnerName, outcome.getPlayer1Score(), outcome.getPlayer2Score());

        // Stop the loop once the game is done.
        running = false;
        return true;
    }

    private GameStateSnapshot createStateSnapshot() {
        GameState gameState = room.getGameState();
        Puck puck = gameState.getPuck();
        Score score = gameState.getScore();

        Vector2 p1Pos = getPlayerPosition(1);
        Vector2 p2Pos = getPlayerPosition(2);

        return new GameStateSnapshot(
            new Vector2(puck.getX(), puck.getY()),
            new Vector2(puck.getVelocityX(), puck.getVelocityY()),
            p1Pos,
            p2Pos,
            score.getPlayer1Score(),
            score.getPlayer2Score(),
            gameState.getPhase()
        );
    }

    private void broadcastStateSnapshot(GameStateSnapshot snapshot) {
        room.broadcast(snapshot, connector);
        metricsBroadcaster.recordStateBroadcast();
        secondsSinceLastStateBroadcast = 0f;
    }

    private void broadcastStateImmediately() {
        pendingStateSnapshot = null;
        broadcastStateSnapshot(createStateSnapshot());
    }

    private void maybeBroadcastState(float deltaTime) {
        if (deltaTime > 0f) {
            secondsSinceLastStateBroadcast += deltaTime;
        }
        // Always refresh the buffer so stale intermediate snapshots are replaced by the current state.
        pendingStateSnapshot = createStateSnapshot();

        backpressure.update(latestLoopJitterMs);
        float effectiveIntervalSeconds = backpressure.effectiveIntervalSeconds();
        if (secondsSinceLastStateBroadcast >= effectiveIntervalSeconds) {
            GameStateSnapshot toSend = pendingStateSnapshot;
            pendingStateSnapshot = null;
            broadcastStateSnapshot(toSend);
        }
    }

    private Vector2 getPlayerPosition(int slot) {
        int connectionId = room.getConnectionIdForSlot(slot);
        if (connectionId == -1) return new Vector2(0, 0);

        Player player = room.getGameState().getPlayer(connectionId);
        if (player == null) return new Vector2(0, 0);

        return new Vector2(player.getX(), player.getY());
    }
}
