package no.ntnu.ping404.model;

import no.ntnu.ping404.model.GameState.Phase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GameState lifecycle / state-machine transitions.
 *
 * Coverage:
 *   FR1.4 – Start match (WAITING → PLAYING, requires ≥2 players)
 *   FR1.5 – Stop match  (PLAYING → FINISHED)
 *   FR4.1 – Pause match (PLAYING → PAUSED)
 *   FR4.2 – Resume match (PAUSED → PLAYING)
 *   FR2.9 – Match completion (win condition triggers FINISHED)
 */
class GameStateLifecycleTest {

    private GameState state;

    @BeforeEach
    void setUp() {
        state = new GameState();
    }

    // ── helpers ──────────────────────────────────────────────

    private void addTwoPlayers() {
        state.addPlayer(new Player(1, "Alice"));
        state.addPlayer(new Player(2, "Bob"));
    }

    /** Puts the state into PLAYING with two players. */
    private void enterPlaying() {
        addTwoPlayers();
        state.startMatch();
    }

    /** Puts the state into PAUSED. */
    private void enterPaused() {
        enterPlaying();
        state.pauseMatch();
    }

    /** Puts the state into FINISHED. */
    private void enterFinished() {
        enterPlaying();
        state.finishMatch();
    }

    // ── FR1.4 – Start match ────────────────────────────────

    @Nested
    @DisplayName("FR1.4 – Start match")
    class StartMatch {

        @Test
        @DisplayName("startMatch succeeds from WAITING with 2 players")
        void startMatch_fromWaiting_withTwoPlayers_transitionsToPlaying() {
            addTwoPlayers();

            assertTrue(state.startMatch());
            assertEquals(Phase.PLAYING, state.getPhase());
        }

        @Test
        @DisplayName("startMatch fails with fewer than 2 players")
        void startMatch_withOnePlayer_returnsFalse() {
            state.addPlayer(new Player(1, "Alice"));

            assertFalse(state.startMatch());
            assertEquals(Phase.WAITING, state.getPhase());
        }

        @Test
        @DisplayName("startMatch fails with zero players")
        void startMatch_withNoPlayers_returnsFalse() {
            assertFalse(state.startMatch());
            assertEquals(Phase.WAITING, state.getPhase());
        }

        @Test
        @DisplayName("startMatch fails when already PLAYING (double-start)")
        void startMatch_alreadyPlaying_returnsFalse() {
            enterPlaying();

            assertFalse(state.startMatch());
            assertEquals(Phase.PLAYING, state.getPhase());
        }

        @Test
        @DisplayName("startMatch fails from PAUSED phase")
        void startMatch_fromPaused_returnsFalse() {
            enterPaused();

            assertFalse(state.startMatch());
            assertEquals(Phase.PAUSED, state.getPhase());
        }

        @Test
        @DisplayName("startMatch fails from FINISHED phase")
        void startMatch_fromFinished_returnsFalse() {
            enterFinished();

            assertFalse(state.startMatch());
            assertEquals(Phase.FINISHED, state.getPhase());
        }
    }

    // ── FR1.5 – Stop / finish match ────────────────────────

    @Nested
    @DisplayName("FR1.5 – Finish match")
    class FinishMatch {

        @Test
        @DisplayName("finishMatch succeeds from PLAYING")
        void finishMatch_fromPlaying_transitionsToFinished() {
            enterPlaying();

            assertTrue(state.finishMatch());
            assertEquals(Phase.FINISHED, state.getPhase());
        }

        @Test
        @DisplayName("finishMatch is idempotent when already FINISHED")
        void finishMatch_alreadyFinished_returnsTrue() {
            enterFinished();

            assertTrue(state.finishMatch());
            assertEquals(Phase.FINISHED, state.getPhase());
        }

        @Test
        @DisplayName("finishMatch fails from WAITING")
        void finishMatch_fromWaiting_returnsFalse() {
            assertFalse(state.finishMatch());
            assertEquals(Phase.WAITING, state.getPhase());
        }

        @Test
        @DisplayName("finishMatch fails from PAUSED")
        void finishMatch_fromPaused_returnsFalse() {
            enterPaused();

            assertFalse(state.finishMatch());
            assertEquals(Phase.PAUSED, state.getPhase());
        }
    }

    // ── FR4.1 – Pause match ────────────────────────────────

    @Nested
    @DisplayName("FR4.1 – Pause match")
    class PauseMatch {

        @Test
        @DisplayName("pauseMatch succeeds from PLAYING")
        void pauseMatch_fromPlaying_transitionsToPaused() {
            enterPlaying();

            assertTrue(state.pauseMatch());
            assertEquals(Phase.PAUSED, state.getPhase());
        }

        @Test
        @DisplayName("pauseMatch fails from WAITING (pause before start)")
        void pauseMatch_fromWaiting_returnsFalse() {
            assertFalse(state.pauseMatch());
            assertEquals(Phase.WAITING, state.getPhase());
        }

        @Test
        @DisplayName("pauseMatch fails when already PAUSED (double-pause)")
        void pauseMatch_alreadyPaused_returnsFalse() {
            enterPaused();

            assertFalse(state.pauseMatch());
            assertEquals(Phase.PAUSED, state.getPhase());
        }

        @Test
        @DisplayName("pauseMatch fails from FINISHED")
        void pauseMatch_fromFinished_returnsFalse() {
            enterFinished();

            assertFalse(state.pauseMatch());
            assertEquals(Phase.FINISHED, state.getPhase());
        }
    }

    // ── FR4.2 – Resume match ───────────────────────────────

    @Nested
    @DisplayName("FR4.2 – Resume match")
    class ResumeMatch {

        @Test
        @DisplayName("resumeMatch succeeds from PAUSED")
        void resumeMatch_fromPaused_transitionsToPlaying() {
            enterPaused();

            assertTrue(state.resumeMatch());
            assertEquals(Phase.PLAYING, state.getPhase());
        }

        @Test
        @DisplayName("resumeMatch fails from WAITING (resume before start)")
        void resumeMatch_fromWaiting_returnsFalse() {
            assertFalse(state.resumeMatch());
            assertEquals(Phase.WAITING, state.getPhase());
        }

        @Test
        @DisplayName("resumeMatch fails from PLAYING")
        void resumeMatch_fromPlaying_returnsFalse() {
            enterPlaying();

            assertFalse(state.resumeMatch());
            assertEquals(Phase.PLAYING, state.getPhase());
        }

        @Test
        @DisplayName("resumeMatch fails from FINISHED")
        void resumeMatch_fromFinished_returnsFalse() {
            enterFinished();

            assertFalse(state.resumeMatch());
            assertEquals(Phase.FINISHED, state.getPhase());
        }
    }

    // ── FR2.9 – Match completion (win condition) ───────────

    @Nested
    @DisplayName("FR2.9 – Match completion via win condition")
    class MatchCompletion {

        @Test
        @DisplayName("Score reaching winningScore triggers hasWinner")
        void score_reachingWinScore_hasWinner() {
            Score score = new Score(3);
            score.setPlayer1Score(3);

            assertTrue(score.hasWinner());
            assertEquals(1, score.getWinner());
        }

        @Test
        @DisplayName("Player 2 wins when their score reaches winningScore")
        void score_player2Wins() {
            Score score = new Score(3);
            score.setPlayer2Score(3);

            assertTrue(score.hasWinner());
            assertEquals(2, score.getWinner());
        }

        @Test
        @DisplayName("No winner when scores are below winningScore")
        void score_belowWinScore_noWinner() {
            Score score = new Score(5);
            score.setPlayer1Score(4);
            score.setPlayer2Score(3);

            assertFalse(score.hasWinner());
            assertEquals(0, score.getWinner());
        }

        @Test
        @DisplayName("Full flow: score to win → finishMatch → FINISHED")
        void fullFlow_scoreToWin_finishMatch() {
            state = new GameState(3); // winScore = 3
            state.addPlayer(new Player(1, "Alice"));
            state.addPlayer(new Player(2, "Bob"));
            state.startMatch();

            Score score = state.getScore();
            score.incrementScore(1);
            score.incrementScore(1);
            score.incrementScore(1);

            assertTrue(score.hasWinner());
            assertEquals(1, score.getWinner());

            assertTrue(state.finishMatch());
            assertEquals(Phase.FINISHED, state.getPhase());
        }

        @Test
        @DisplayName("After finish, resetForNewMatch returns to WAITING with zeroed scores")
        void resetForNewMatch_afterFinish_returnsToWaiting() {
            state = new GameState(3);
            state.addPlayer(new Player(1, "Alice"));
            state.addPlayer(new Player(2, "Bob"));
            state.startMatch();
            state.getScore().setPlayer1Score(3);
            state.finishMatch();

            state.resetForNewMatch();

            assertEquals(Phase.WAITING, state.getPhase());
            assertEquals(0, state.getScore().getPlayer1Score());
            assertEquals(0, state.getScore().getPlayer2Score());
        }
    }

    // ── Edge cases / full cycle ────────────────────────────

    @Nested
    @DisplayName("Edge cases and full lifecycle")
    class EdgeCases {

        @Test
        @DisplayName("Full cycle: WAITING → PLAYING → PAUSED → PLAYING → FINISHED → WAITING")
        void fullLifecycle() {
            addTwoPlayers();

            assertEquals(Phase.WAITING, state.getPhase());
            assertTrue(state.startMatch());
            assertEquals(Phase.PLAYING, state.getPhase());
            assertTrue(state.pauseMatch());
            assertEquals(Phase.PAUSED, state.getPhase());
            assertTrue(state.resumeMatch());
            assertEquals(Phase.PLAYING, state.getPhase());
            assertTrue(state.finishMatch());
            assertEquals(Phase.FINISHED, state.getPhase());

            state.resetForNewMatch();
            assertEquals(Phase.WAITING, state.getPhase());
        }

        @Test
        @DisplayName("Multiple pause/resume cycles are allowed")
        void multiplePauseResumeCycles() {
            enterPlaying();

            for (int i = 0; i < 5; i++) {
                assertTrue(state.pauseMatch(), "pause #" + i);
                assertEquals(Phase.PAUSED, state.getPhase());
                assertTrue(state.resumeMatch(), "resume #" + i);
                assertEquals(Phase.PLAYING, state.getPhase());
            }
        }

        @Test
        @DisplayName("beginPlaying is idempotent when already PLAYING")
        void beginPlaying_alreadyPlaying_returnsTrue() {
            enterPlaying();

            assertTrue(state.beginPlaying());
            assertEquals(Phase.PLAYING, state.getPhase());
        }

        @Test
        @DisplayName("beginPlaying fails from PAUSED")
        void beginPlaying_fromPaused_returnsFalse() {
            enterPaused();

            assertFalse(state.beginPlaying());
            assertEquals(Phase.PAUSED, state.getPhase());
        }

        @Test
        @DisplayName("Default phase is WAITING on construction")
        void defaultPhase_isWaiting() {
            assertEquals(Phase.WAITING, new GameState().getPhase());
        }

        @Test
        @DisplayName("Constructor with winScore sets phase to WAITING")
        void constructorWithWinScore_phaseIsWaiting() {
            assertEquals(Phase.WAITING, new GameState(10).getPhase());
        }

        @Test
        @DisplayName("Removing a player below 2 does not auto-change phase")
        void removePlayer_doesNotAutoChangePhase() {
            enterPlaying();
            state.removePlayer(1);

            // Phase stays PLAYING — it's up to the server layer to handle this
            assertEquals(Phase.PLAYING, state.getPhase());
            assertEquals(1, state.getPlayerCount());
        }
    }
}
