package no.ntnu.ping404.server.game;

import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.model.GameState.Phase;
import no.ntnu.ping404.model.Puck;
import no.ntnu.ping404.model.Score;
import no.ntnu.ping404.utils.CollisionDetector;
import no.ntnu.ping404.utils.CollisionDetector.GoalResult;
import no.ntnu.ping404.utils.CollisionDetector.TickResult;
import no.ntnu.ping404.utils.Constants;

/**
 * Server-side game tick orchestrator. Called by {@link GameLoop} each tick.
 *
 * <p>Responsible ONLY for orchestrating game logic in the correct order.
 * Does NOT handle network I/O, broadcasting, or implement physics/collision math.
 * Core logic lives in {@link CollisionDetector} - this class only CALLS it.</p>
 *
 * <p>Tick order:</p>
 * <ol>
 *   <li>{@link #applyRules} - calls {@link CollisionDetector#resolveTick} which
 *       internally moves the puck ({@code position += velocity * dt} per substep)
 *       and resolves wall bounces, paddle bounces, and goal detection</li>
 *   <li>{@link #updateScore} - increment score and reset puck on goal</li>
 *   <li>{@link #checkGameOver} - set phase to FINISHED if max score reached</li>
 * </ol>
 *
 * <p><strong>Note:</strong> Do NOT call {@link #updatePuckPosition} before
 * {@link #applyRules} - {@code resolveTick} already advances the puck
 * internally per substep. Calling both would cause double movement.</p>
 */
public class GameTickProcessor {

    private final GameState gameState;

    public GameTickProcessor(GameState gameState) {
        this.gameState = gameState;
    }

    /**
     * Processes one complete game tick in exact order.
     *
     * @param deltaTime seconds since last tick
     * @return goal result for this tick ({@link GoalResult#NONE} if no goal)
     */
    public GoalResult processTick(float deltaTime) {
        // Steps 1+2: Move puck AND apply collision rules (wall bounce, paddle bounce, goal).
        // CollisionDetector.resolveTick() handles puck.update() internally per substep.
        // Do NOT call updatePuckPosition() separately - that would cause double movement.
        GoalResult goalResult = applyRules(deltaTime);

        // Step 3 - Update score if a goal was detected
        updateScore(goalResult);

        // Step 4 - Check if max score reached --> GAME_OVER
        checkGameOver();

        return goalResult;
    }

    /**
     * Step 1: Advance the puck position using its current velocity.
     * {@code position += velocity * deltaTime}
     *
     * <p>Delegates to existing {@link Puck#update(float)} from Core.</p>
     *
     * @param deltaTime seconds since last tick
     */
    void updatePuckPosition(float deltaTime) {
        gameState.getPuck().update(deltaTime);
    }

    /**
     * Step 2: Delegate collision detection to Core logic.
     * {@link CollisionDetector#resolveTick(GameState, float)} applies in order:
     * <ul>
     *   <li>wallCollisionRule.apply(...)   - top/bottom/side wall bounces</li>
     *   <li>paddleCollisionRule.apply(...) - paddle reflection</li>
     *   <li>goalDetectionRule.apply(...)   - left/right goal-line check</li>
     * </ul>
     * We CALL Core logic here - we do NOT implement any physics.
     *
     * @param deltaTime seconds since last tick
     * @return the goal result from Core's collision resolution
     */
    GoalResult applyRules(float deltaTime) {
        TickResult result = CollisionDetector.resolveTick(gameState, deltaTime);
        return result.getGoalResult();
    }

    /**
     * Step 3: Update the score when a goal is detected.
     * <ul>
     *   <li>{@code PLAYER_1_GOAL} - puck crossed Player 1's goal ? Player 2 scores</li>
     *   <li>{@code PLAYER_2_GOAL} - puck crossed Player 2's goal ? Player 1 scores</li>
     * </ul>
     * After scoring the puck is stopped at center. Relaunch timing
     * is handled by {@link GameLoop}, not here.
     *
     * @param goalResult the result from {@link #applyRules(float)}
     */
    void updateScore(GoalResult goalResult) {
        if (goalResult == GoalResult.NONE) {
            return;
        }

        Score score = gameState.getScore();
        if (goalResult == GoalResult.PLAYER_1_GOAL) {
            score.scorePlayer2();
        } else {
            score.scorePlayer1();
        }

        // Reset puck: stop at center (relaunch timing handled by GameLoop)
        Puck puck = gameState.getPuck();
        puck.setVelocityX(0);
        puck.setVelocityY(0);
        puck.setPosition(Constants.boardCenterX(), Constants.boardCenterY());
    }

    /**
     * Step 4: Check if the maximum score has been reached.
     * If a winner exists, transition the game phase to {@link Phase#FINISHED}.
     */
    void checkGameOver() {
        if (gameState.getScore().hasWinner()) {
            gameState.finishMatch();
        }
    }

    /**
     * Returns the current game state. Broadcasting is handled elsewhere.
     *
     * @return the authoritative game state managed by this processor
     */
    public GameState getGameState() {
        return gameState;
    }
}


