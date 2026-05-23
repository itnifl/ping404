package no.ntnu.ping404.model;

import no.ntnu.ping404.utils.CollisionDetector;
import no.ntnu.ping404.utils.Constants;
import no.ntnu.ping404.model.StuckPuckDetector;

/**
 * Orchestrates pure match logic on top of the GameState.
 * Keeps puck physics, scoring and win checks in core so the same logic can be
 * reused by server or client without networking concerns.
 */
public class GameEngine {

    private static final int NO_PLAYER_SLOT = 0;

    private final GameState state;
    private final StuckPuckDetector stuckPuckDetector;

    /** Creates a GameEngine with default field dimensions and win score. */
    public GameEngine() {
        this(new GameState());
    }

    /**
     * Creates a GameEngine with the specified winning score.
     *
     * @param winScore the score required to win the match
     */
    public GameEngine(int winScore) {
        this(new GameState(winScore));
    }

    /**
     * Creates a GameEngine with custom field dimensions.
     *
     * @param fieldWidth the width of the playing field
     * @param fieldHeight the height of the playing field
     */
    public GameEngine(float fieldWidth, float fieldHeight) {
        this(new GameState(fieldWidth, fieldHeight));
    }

    /**
     * Creates a GameEngine wrapping the provided game state.
     *
     * @param state the game state to use; must not be null
     */
    public GameEngine(GameState state) {
        if (state == null) {
            throw new IllegalArgumentException("state cannot be null");
        }
        this.state = state;
        this.stuckPuckDetector = new StuckPuckDetector(Constants.boardCenterX());
    }

    public GameState getState() {
        return state;
    }

    /**
     * Advances one game tick while the match is playing.
     */
    public TickOutcome tick(float deltaTime) {
        if (state.getPhase() != GameState.Phase.PLAYING || deltaTime <= 0f) {
            return buildOutcome(false, false, NO_PLAYER_SLOT, false);
        }

        CollisionDetector.TickResult collisionResult = CollisionDetector.resolveTick(state, deltaTime);
        int scoringPlayerSlot = registerGoal(collisionResult.getGoalResult());

        if (scoringPlayerSlot != NO_PLAYER_SLOT) {
            return buildOutcome(collisionResult.hasWallCollision(), collisionResult.hasPaddleCollision(), scoringPlayerSlot, false);
        }

        stuckPuckDetector.update(state.getPuck().getX(), deltaTime);
        if (stuckPuckDetector.isStuck()) {
            state.getPuck().reset(Constants.boardCenterX(), Constants.boardCenterY());
            stuckPuckDetector.reset();
            return buildOutcome(collisionResult.hasWallCollision(), collisionResult.hasPaddleCollision(), NO_PLAYER_SLOT, true);
        }

        return buildOutcome(collisionResult.hasWallCollision(), collisionResult.hasPaddleCollision(), NO_PLAYER_SLOT, false);
    }

    /**
     * Resets the stall timer. Call when the puck is relaunched after a goal-reset delay.
     */
    public void resetStallTimer() {
        stuckPuckDetector.reset();
    }

    /**
     * Evaluates the current match state without advancing puck simulation.
     */
    public TickOutcome evaluateMatchState() {
        return buildOutcome(false, false, NO_PLAYER_SLOT, false);
    }

    private TickOutcome buildOutcome(boolean wallCollision, boolean paddleCollision, int scoringPlayerSlot, boolean puckStallReset) {
        int winnerSlot = resolveWinnerSlot();
        return new TickOutcome(
            wallCollision,
            paddleCollision,
            scoringPlayerSlot,
            winnerSlot,
            state.getScore().getPlayer1Score(),
            state.getScore().getPlayer2Score(),
            state.getPuck().getX(),
            state.getPuck().getY(),
            puckStallReset
        );
    }

    private int resolveWinnerSlot() {
        if (state.getPhase() != GameState.Phase.PLAYING || !state.getScore().hasWinner()) {
            return NO_PLAYER_SLOT;
        }
        return state.getScore().getWinner();
    }

    private int registerGoal(CollisionDetector.GoalResult goalResult) {
        int scoringPlayerSlot = switch (goalResult) {
            case PLAYER_1_GOAL -> 2;
            case PLAYER_2_GOAL -> 1;
            case NONE -> NO_PLAYER_SLOT;
        };

        if (scoringPlayerSlot == NO_PLAYER_SLOT) {
            return NO_PLAYER_SLOT;
        }

        state.getScore().incrementScore(scoringPlayerSlot);
        stuckPuckDetector.reset();
        if (state.getScore().hasWinner()) {
            stopPuckAt(Constants.boardCenterX(), Constants.boardCenterY());
        } else {
            parkPuckForNextRound(scoringPlayerSlot);
        }
        return scoringPlayerSlot;
    }

    private void parkPuckForNextRound(int scoringPlayerSlot) {
        float resetX = scoringPlayerSlot == 1
            ? Constants.player2HalfCenterX()
            : Constants.player1HalfCenterX();
        stopPuckAt(resetX, Constants.boardCenterY());
    }

    private void stopPuckAt(float x, float y) {
        state.getPuck().setPosition(x, y);
        state.getPuck().setVelocityX(0f);
        state.getPuck().setVelocityY(0f);
    }

    public static final class TickOutcome {
        private final boolean wallCollision;
        private final boolean paddleCollision;
        private final int scoringPlayerSlot;
        private final int winnerSlot;
        private final int player1Score;
        private final int player2Score;
        private final float resetPuckX;
        private final float resetPuckY;
        private final boolean puckStallReset;

        private TickOutcome(
            boolean wallCollision,
            boolean paddleCollision,
            int scoringPlayerSlot,
            int winnerSlot,
            int player1Score,
            int player2Score,
            float resetPuckX,
            float resetPuckY,
            boolean puckStallReset
        ) {
            this.wallCollision = wallCollision;
            this.paddleCollision = paddleCollision;
            this.scoringPlayerSlot = scoringPlayerSlot;
            this.winnerSlot = winnerSlot;
            this.player1Score = player1Score;
            this.player2Score = player2Score;
            this.resetPuckX = resetPuckX;
            this.resetPuckY = resetPuckY;
            this.puckStallReset = puckStallReset;
        }

        public boolean hasWallCollision() {
            return wallCollision;
        }

        public boolean hasPaddleCollision() {
            return paddleCollision;
        }

        public boolean hasGoal() {
            return scoringPlayerSlot != NO_PLAYER_SLOT;
        }

        public int getScoringPlayerSlot() {
            return scoringPlayerSlot;
        }

        public boolean isMatchFinished() {
            return winnerSlot != NO_PLAYER_SLOT;
        }

        public int getWinnerSlot() {
            return winnerSlot;
        }

        public int getPlayer1Score() {
            return player1Score;
        }

        public int getPlayer2Score() {
            return player2Score;
        }

        public float getResetPuckX() {
            return resetPuckX;
        }

        public float getResetPuckY() {
            return resetPuckY;
        }

        public boolean hasPuckStallReset() {
            return puckStallReset;
        }
    }
}
