package no.ntnu.ping404.network;

import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.network.packets.ErrorPacket;
import no.ntnu.ping404.network.packets.GameOver;
import no.ntnu.ping404.network.packets.GameStateSnapshot;
import no.ntnu.ping404.network.packets.GoalScored;
import no.ntnu.ping404.network.packets.PauseEvent;
import no.ntnu.ping404.network.packets.PlayerJoined;
import no.ntnu.ping404.network.packets.PlayerLeft;
import no.ntnu.ping404.network.packets.RematchStart;
import no.ntnu.ping404.network.packets.ResumeEvent;
import no.ntnu.ping404.network.packets.RoomMetricsSnapshot;

/**
 * Factory that creates and wires client-side packet handlers for GameScreen.
 * Centralizes handler registration for all authoritative server packets.
 *
 * <p>Handles deterministic update order:</p>
 * <ol>
 *   <li>Phase transitions (PauseEvent, ResumeEvent)</li>
 *   <li>Score updates (GoalScored)</li>
 *   <li>State snapshots (GameStateSnapshot)</li>
 *   <li>Runtime metrics snapshots (RoomMetricsSnapshot)</li>
 *   <li>Match conclusion (GameOver)</li>
 *   <li>Player events (PlayerJoined, PlayerLeft)</li>
 * </ol>
 *
 * @see ClientPacketDispatcher
 * @see GameScreenState
 */
public class ClientPacketHandlers {

    private ClientPacketHandlers() {}

    /**
     * Creates a dispatcher configured with all standard game packet handlers.
     *
     * @param state the game state to update
     * @param onGameOver callback invoked on the render thread when the game ends;
     *                   may be {@code null}
     * @return configured dispatcher
     */
    public static ClientPacketDispatcher createGameDispatcher(GameScreenState state, Runnable onGameOver) {
        return createGameDispatcher(state, onGameOver, null);
    }

    /**
     * Creates a dispatcher configured with all standard game packet handlers.
     *
     * @param state the game state to update
     * @param onGameOver callback invoked on the render thread when the game ends;
     *                   may be {@code null}
     * @param onOpponentLeft callback invoked on the render thread when the opponent
     *                       leaves mid-game; may be {@code null}
     * @return configured dispatcher
     */
    public static ClientPacketDispatcher createGameDispatcher(GameScreenState state, Runnable onGameOver, Runnable onOpponentLeft) {

        ClientPacketDispatcher dispatcher = new ClientPacketDispatcher();

        // Phase transitions
        dispatcher.register(PauseEvent.class, packet -> handlePauseEvent(state, packet));
        dispatcher.register(ResumeEvent.class, packet -> handleResumeEvent(state, packet));

        // Score updates
        dispatcher.register(GoalScored.class, packet -> handleGoalScored(state, packet));

        // State snapshots
        dispatcher.register(GameStateSnapshot.class, packet -> handleGameStateSnapshot(state, packet));

        // Runtime metrics snapshots
        dispatcher.register(RoomMetricsSnapshot.class, packet -> handleRoomMetricsSnapshot(state, packet));

        // Match conclusion
        dispatcher.register(GameOver.class, packet -> {
            handleGameOver(state, packet);
            if (onGameOver != null) onGameOver.run();
        });

        // Rematch flow
        dispatcher.register(RematchStart.class, packet -> handleRematchStart(state));

        // Player events
        dispatcher.register(PlayerJoined.class, packet -> handlePlayerJoined(state, packet));
        dispatcher.register(PlayerLeft.class, packet -> {
            handlePlayerLeft(state, packet);
            if (onOpponentLeft != null) onOpponentLeft.run();
        });

        // Error responses
        dispatcher.register(ErrorPacket.class, packet -> handleErrorPacket(state, packet));

        return dispatcher;
    }

    /**
     * Handles PauseEvent - sets paused state.
     */
    static void handlePauseEvent(GameScreenState state, PauseEvent packet) {
        state.setPaused(true);
        state.setPausedByPlayerId(packet.requesterId);
        state.setPhase(GameState.Phase.PAUSED);
    }

    /**
     * Handles ResumeEvent - clears paused state.
     */
    static void handleResumeEvent(GameScreenState state, ResumeEvent packet) {
        state.setPaused(false);
        state.setPausedByPlayerId(-1);
        state.setPhase(GameState.Phase.PLAYING);
    }

    /**
     * Handles GoalScored - updates scores and triggers goal flash.
     */
    static void handleGoalScored(GameScreenState state, GoalScored packet) {
        state.setPlayer1Score(packet.player1Score);
        state.setPlayer2Score(packet.player2Score);

        // Track who scored for sound effects
        state.setLastScoringPlayerId(packet.scoringPlayerId);

        // Determine who scored for the flash message
        String scorer = packet.scoringPlayerId == 1 ? "Player 1" : "Player 2";
        state.setGoalMessage(scorer + " Scores!");
        state.setGoalFlashTimer(1.5f);
    }

    /**
     * Handles GameStateSnapshot - updates all game positions and state.
     */
    static void handleGameStateSnapshot(GameScreenState state, GameStateSnapshot packet) {
        if (packet.puckPosition != null) {
            state.setPuckPosition(packet.puckPosition.x, packet.puckPosition.y);
        }
        if (packet.puckVelocity != null) {
            state.setPuckVelocity(packet.puckVelocity.x, packet.puckVelocity.y);
        }
        if (packet.player1Position != null) {
            state.setPlayer1Position(packet.player1Position.x, packet.player1Position.y);
        }
        if (packet.player2Position != null) {
            state.setPlayer2Position(packet.player2Position.x, packet.player2Position.y);
        }
        state.setPlayer1Score(packet.player1Score);
        state.setPlayer2Score(packet.player2Score);
        if (packet.phase != null) {
            state.setPhase(packet.phase);
            state.setPaused(packet.phase == GameState.Phase.PAUSED);
        }
        if (packet.puckStallReset) {
            state.setPuckStallReset(true);
        }
    }

    /**
     * Handles RoomMetricsSnapshot - updates latest server-side runtime metrics.
     */
    static void handleRoomMetricsSnapshot(GameScreenState state, RoomMetricsSnapshot packet) {
        state.setRoomMetricsSnapshot(packet);
    }

    /**
     * Handles GameOver - updates final scores, phase, and stores the winner name
     * so the screen layer can drive the transition.
     */
    static void handleGameOver(GameScreenState state, GameOver packet) {
        state.setPlayer1Score(packet.player1Score);
        state.setPlayer2Score(packet.player2Score);
        state.setPhase(GameState.Phase.FINISHED);
        state.setWinnerName(packet.winnerName);
    }

    /**
     * Handles PlayerJoined - updates opponent info.
     */
    static void handlePlayerJoined(GameScreenState state, PlayerJoined packet) {
        state.setOpponentName(packet.playerName);
        state.setOpponentConnected(true);
        state.setDisconnectReason("");
        state.setStatusMessage(packet.playerName + " joined");
    }

    /**
     * Handles PlayerLeft - marks opponent as disconnected.
     */
    static void handlePlayerLeft(GameScreenState state, PlayerLeft packet) {
        state.setOpponentConnected(false);
        state.setDisconnectReason(packet.reason);
        state.setStatusMessage("Other player disconnected!");
    }

    /**
     * Handles RematchStart - resets state for a new round.
     */
    static void handleRematchStart(GameScreenState state) {
        state.setPlayer1Score(0);
        state.setPlayer2Score(0);
        state.setPhase(GameState.Phase.WAITING);
        state.setWinnerName(null);
    }

    /**
     * Handles ErrorPacket - triggers error flash and sets error message.
     * Used for denied actions (e.g., non-host trying to pause).
     */
    static void handleErrorPacket(GameScreenState state, ErrorPacket packet) {
        state.setErrorMessage(packet.message != null ? packet.message : "Action denied");
        state.setErrorFlashTimer(1.0f);
    }
}
