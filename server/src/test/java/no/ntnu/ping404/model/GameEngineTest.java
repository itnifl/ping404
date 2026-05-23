package no.ntnu.ping404.model;

import no.ntnu.ping404.utils.Constants;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameEngineTest {

    private static final float CENTER_X = Constants.boardCenterX();
    private static final float CENTER_Y = Constants.boardCenterY();

    private static GameState createPlayingState(int winScore) {
        GameState state = new GameState(winScore);
        state.addPlayer(new Player(1, "Alice"));
        state.addPlayer(new Player(2, "Bob"));
        assertTrue(state.startMatch(), "Test fixture should enter PLAYING phase");
        return state;
    }

    @Test
    @Tag("FR2.3")
    @Tag("FR2.5")
    void tickDoesNothingWhenMatchIsNotPlaying() {
        GameState state = new GameState();
        GameEngine engine = new GameEngine(state);

        state.getPuck().setPosition(100f, 200f);
        state.getPuck().setVelocityX(250f);
        state.getPuck().setVelocityY(50f);

        GameEngine.TickOutcome outcome = engine.tick(0.25f);

        assertFalse(outcome.hasWallCollision(), "No wall collision should be reported when tick is ignored");
        assertFalse(outcome.hasPaddleCollision(), "No paddle collision should be reported when tick is ignored");
        assertFalse(outcome.hasGoal(), "No goal should be reported when tick is ignored");
        assertEquals(100f, state.getPuck().getX(), "Puck X should stay unchanged outside PLAYING phase");
        assertEquals(200f, state.getPuck().getY(), "Puck Y should stay unchanged outside PLAYING phase");
    }

    @Test
    @Tag("FR2.3")
    @Tag("FR2.9")
    void leftGoalScoresForPlayerTwoAndReportsWinnerAtWinningScore() {
        GameState state = createPlayingState(1);
        GameEngine engine = new GameEngine(state);

        state.getPuck().setPosition(6f, CENTER_Y);
        state.getPuck().setVelocityX(-1000f);
        state.getPuck().setVelocityY(0f);

        GameEngine.TickOutcome outcome = engine.tick(0.08f);

        assertTrue(outcome.hasGoal(), "Crossing the left goal should register as a goal");
        assertEquals(2, outcome.getScoringPlayerSlot(), "Left goal means player 2 scored");
        assertTrue(outcome.isMatchFinished(), "A goal at winning score should finish the match");
        assertEquals(2, outcome.getWinnerSlot(), "Player 2 should be reported as winner");
        assertEquals(0, state.getScore().getPlayer1Score(), "Player 1 score should stay unchanged");
        assertEquals(1, state.getScore().getPlayer2Score(), "Player 2 score should increment");
        assertEquals(CENTER_X, state.getPuck().getX(), "Puck should reset to center X after a goal");
        assertEquals(CENTER_Y, state.getPuck().getY(), "Puck should reset to center Y after a goal");
        assertEquals(0f, state.getPuck().getVelocityX(), "Winning goal should stop puck X velocity");
        assertEquals(0f, state.getPuck().getVelocityY(), "Winning goal should stop puck Y velocity");
        assertEquals(GameState.Phase.PLAYING, state.getPhase(), "Engine should report the win without directly mutating room phase");
    }

    @Test
    @Tag("FR2.3")
    @Tag("FR2.7")
    @Tag("FR2.10")
    void rightGoalScoresForPlayerOne() {
        GameState state = createPlayingState(5);
        GameEngine engine = new GameEngine(state);

        state.getPuck().setPosition(794f, CENTER_Y);
        state.getPuck().setVelocityX(1000f);
        state.getPuck().setVelocityY(0f);

        GameEngine.TickOutcome outcome = engine.tick(0.08f);

        assertTrue(outcome.hasGoal(), "Crossing the right goal should register as a goal");
        assertEquals(1, outcome.getScoringPlayerSlot(), "Right goal means player 1 scored");
        assertFalse(outcome.isMatchFinished(), "Match should continue below winning score");
        assertEquals(1, state.getScore().getPlayer1Score(), "Player 1 score should increment");
        assertEquals(0, state.getScore().getPlayer2Score(), "Player 2 score should stay unchanged");
        assertEquals(Constants.player2HalfCenterX(), state.getPuck().getX(), 0.001f,
            "Non-winning goal should park puck on the conceding player's half");
        assertEquals(CENTER_Y, state.getPuck().getY(), 0.001f,
            "Non-winning goal should park puck at board center height");
        assertEquals(0f, state.getPuck().getVelocityX(), 0.001f,
            "Non-winning goal should stop puck X velocity during reset delay");
        assertEquals(0f, state.getPuck().getVelocityY(), 0.001f,
            "Non-winning goal should stop puck Y velocity during reset delay");
        assertEquals(Constants.player2HalfCenterX(), outcome.getResetPuckX(), 0.001f,
            "Outcome should expose the authoritative reset X used by the server loop");
        assertEquals(CENTER_Y, outcome.getResetPuckY(), 0.001f,
            "Outcome should expose the authoritative reset Y used by the server loop");
    }

    @Test
    @Tag("FR2.9")
    void evaluateMatchStateOnlyReportsWinnerDuringPlayingPhase() {
        GameState state = createPlayingState(3);
        GameEngine engine = new GameEngine(state);

        assertTrue(state.pauseMatch(), "Fixture should pause from PLAYING");
        state.getScore().setPlayer1Score(3);

        GameEngine.TickOutcome pausedOutcome = engine.evaluateMatchState();
        assertFalse(pausedOutcome.isMatchFinished(), "Paused matches should not emit winner flow");

        assertTrue(state.resumeMatch(), "Fixture should resume from PAUSED");
        GameEngine.TickOutcome playingOutcome = engine.evaluateMatchState();
        assertTrue(playingOutcome.isMatchFinished(), "Playing matches should emit winner flow when score has a winner");
        assertEquals(1, playingOutcome.getWinnerSlot(), "Player 1 should be reported as winner");
    }

    @Test
    @Tag("FR2.9")
    void evaluateMatchStateReportsWinnerSlotEvenWithNoPlayersRegistered() {
        // The engine layer reports winner slots based on score alone.
        GameState state = createPlayingState(3);
        GameEngine engine = new GameEngine(state);

        // Simulating stale slot-to-connection mapping.
        state.removePlayer(1);
        state.removePlayer(2);
        state.getScore().setPlayer1Score(3);

        GameEngine.TickOutcome outcome = engine.evaluateMatchState();

        assertTrue(outcome.isMatchFinished(), "Engine should report match finished based on score");
        assertEquals(1, outcome.getWinnerSlot(), "Winner slot should be 1 based on score");
    }
}