package no.ntnu.ping404.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScoreTest {

    private static final int WIN_SCORE_3 = 3;
    private static final int WIN_SCORE_5 = 5;
    private static final int WIN_SCORE_7 = 7;
    private static final int WIN_SCORE_10 = 10;
    private static final int PLAYER_1_SLOT = 1;
    private static final int PLAYER_2_SLOT = 2;
    private static final int NO_WINNER = 0;

    @Test
    @Tag("FR2.7")
    void initialScoresAreZero() {
        Score score = new Score();
        assertEquals(0, score.getPlayer1Score(), "Player 1 score should initially be 0");
        assertEquals(0, score.getPlayer2Score(), "Player 2 score should initially be 0");
    }

    @Test
    @Tag("FR2.9")
    void scoreHasNoWinnerInitially() {
        // FR2.9: Match ends only when a player reaches the winning score threshold.
        // A freshly constructed Score must report hasWinner() == false.
        Score score = new Score();
        assertFalse(score.hasWinner(), "A fresh Score should have no winner");
    }

    @Test
    @Tag("FR2.7")
    void scoringIncrementsPlayer1Score() {
        Score score = new Score();
        score.scorePlayer1();
        assertEquals(1, score.getPlayer1Score(), "Player 1 score should increment by 1");
    }

    @Test
    @Tag("FR2.7")
    void scoreIncrementedWhenPuckCrossesPlayer1GoalLine() {
        // FR2.7: When the puck crosses a goal line, the server increments the scorer's score.
        // scorePlayer2() (puck in player-1's goal) must increase player2Score by 1.
        Score score = new Score();
        int before = score.getPlayer2Score();
        score.scorePlayer2();
        assertEquals(before + 1, score.getPlayer2Score(), "Player 2 score should increment when puck crosses player 1 goal line");
    }

    @Test
    @Tag("FR2.7")
    void scoringIncrementsPlayer2Score() {
        Score score = new Score();
        score.scorePlayer2();
        assertEquals(1, score.getPlayer2Score(), "Player 2 score should increment by 1");
    }

    @Test
    @Tag("FR2.7")
    void scoreIncrementedWhenPuckCrossesPlayer2GoalLine() {
        // FR2.7: Symmetric goal detection - puck in player-2's goal increments player1Score.
        Score score = new Score();
        int before = score.getPlayer1Score();
        score.scorePlayer1();
        assertEquals(before + 1, score.getPlayer1Score(), "Player 1 score should increment when puck crosses player 2 goal line");
    }

    @Test
    @Tag("FR2.9")
    void hasWinnerReturnsTrueWhenPlayer1ReachesWinningScore() {
        Score score = new Score(WIN_SCORE_3);
        score.setPlayer1Score(WIN_SCORE_3);
        assertTrue(score.hasWinner(), "hasWinner should return true when Player 1 reaches winning score");
    }

    @Test
    @Tag("FR2.9")
    void hasWinnerReturnsTrueWhenPlayer2ReachesWinningScore() {
        Score score = new Score(WIN_SCORE_3);
        score.setPlayer2Score(WIN_SCORE_3);
        assertTrue(score.hasWinner(), "hasWinner should return true when Player 2 reaches winning score");
    }

    @Test
    @Tag("FR3.2")
    void getWinnerReturnsCorrectPlayer() {
        Score score = new Score(WIN_SCORE_3);
        score.setPlayer1Score(WIN_SCORE_3);
        assertEquals(PLAYER_1_SLOT, score.getWinner(), "getWinner should return 1 when Player 1 wins");
        score.setPlayer1Score(0);
        score.setPlayer2Score(WIN_SCORE_3);
        assertEquals(PLAYER_2_SLOT, score.getWinner(), "getWinner should return 2 when Player 2 wins");
        }

    @Test
    @Tag("FR3.2")
    void getWinnerReturns1WhenPlayer1HasWon() {
        // FR3.2: GameOver packet must identify the winner - getWinner() returns 1 for player 1.
        Score score = new Score(WIN_SCORE_7);
        score.setPlayer1Score(WIN_SCORE_7);
        assertEquals(PLAYER_1_SLOT, score.getWinner(), "getWinner should return 1 when player 1 has won");
    }

    @Test
    @Tag("FR3.1")
    void getWinnerReturnsZeroWhenNoWinner() {
        Score score = new Score(WIN_SCORE_3);
        score.setPlayer1Score(WIN_SCORE_3 - 1);
        score.setPlayer2Score(WIN_SCORE_3 - 1);
        assertEquals(NO_WINNER, score.getWinner(), "getWinner should return 0 when there is no winner");
    }

    @Test
    @Tag("FR3.2")
    void getWinnerReturns2WhenPlayer2HasWon() {
        // FR3.2: getWinner() must return 2 when player 2 has reached the winning score.
        Score score = new Score(WIN_SCORE_7);
        score.setPlayer2Score(WIN_SCORE_7);
        assertEquals(PLAYER_2_SLOT, score.getWinner(), "getWinner should return 2 when player 2 has won");
    }

    @Test
    @Tag("FR2.10")
    void resetSetsScoresToZero() {
        Score score = new Score();
        score.setPlayer1Score(WIN_SCORE_5);
        score.setPlayer2Score(WIN_SCORE_5);
        score.reset();
        assertEquals(0, score.getPlayer1Score(), "Player 1 score should reset to 0");
        assertEquals(0, score.getPlayer2Score(), "Player 2 score should reset to 0");
    }

    @Test
    @Tag("FR3.1")
    void getWinnerReturns0WhenNoWinnerYet() {
        // FR3.1: GameOver is not sent until someone wins - getWinner() returns 0 mid-game.
        Score score = new Score(WIN_SCORE_5);
        score.setPlayer1Score(WIN_SCORE_3);
        score.setPlayer2Score(WIN_SCORE_5 - 1);
        assertEquals(NO_WINNER, score.getWinner(), "getWinner should return 0 when no winner yet");
    }

    @Test
    @Tag("FR3.2")
    void toStringReturnsCorrectFormat() {
        Score score = new Score();
        score.setPlayer1Score(WIN_SCORE_3);
        score.setPlayer2Score(WIN_SCORE_3 - 1);
        assertEquals(WIN_SCORE_3 + " - " + (WIN_SCORE_3 - 1), score.toString(), "toString should return scores in 'Player1 - Player2' format");
    }

    @Test
    @Tag("FR1.6")
    @Tag("M3")
    void winningScoreIsRuntimeConfigurable() {
        // FR1.6 / QAS-M3: The host sets the winning score at room creation; it must be stored
        // without recompiling. Score(customWinScore).getWinningScore() must equal customWinScore.
        Score score = new Score(WIN_SCORE_10);
        assertEquals(WIN_SCORE_10, score.getWinningScore(),
                "Custom win score should be stored via constructor");
    }

    @Test
    @Tag("M3")
    void winningScoreCanBeChangedViaSetterWithoutRecompile() {
        // QAS-M3: setWinningScore(n) must update the threshold at runtime.
        Score score = new Score();
        assertEquals(WIN_SCORE_5, score.getWinningScore(), "Default win score should be 5");
        
        score.setWinningScore(WIN_SCORE_7);
        assertEquals(WIN_SCORE_7, score.getWinningScore(),
                "Win score should be updatable via setter");
    }


        @Test
        @Tag("FR2.7")
        void incrementScoreByPlayerIdIncrementsPlayer1() {
            Score score = new Score();
            score.incrementScore(PLAYER_1_SLOT);
            assertEquals(1, score.getPlayer1Score());
            assertEquals(0, score.getPlayer2Score());
        }
    
        @Test
        @Tag("FR2.7")
        void incrementScoreByPlayerIdIncrementsPlayer2() {
            Score score = new Score();
            score.incrementScore(PLAYER_2_SLOT);
            assertEquals(0, score.getPlayer1Score());
            assertEquals(1, score.getPlayer2Score());
        }
    
        @Test
        @Tag("FR2.7")
        void incrementScoreWithInvalidPlayerIdDoesNothing() {
            Score score = new Score();
            score.incrementScore(0);
            score.incrementScore(3);
            score.incrementScore(-1);
            assertEquals(0, score.getPlayer1Score());
            assertEquals(0, score.getPlayer2Score());
        }
    
        @Test
        @Tag("FR2.7")
        void multipleIncrementsAccumulateCorrectly() {
            Score score = new Score();
            score.incrementScore(PLAYER_1_SLOT);
            score.incrementScore(PLAYER_1_SLOT);
            score.incrementScore(PLAYER_2_SLOT);
            score.incrementScore(PLAYER_1_SLOT);
            assertEquals(3, score.getPlayer1Score());
            assertEquals(1, score.getPlayer2Score());
        }
    
        // ── Edge cases ───────────────────────────────────────────────────────
    
        @Test
        @Tag("FR2.7")
        void scoresAreNeverNegative() {
            Score score = new Score();
            assertEquals(0, score.getPlayer1Score());
            assertEquals(0, score.getPlayer2Score());
            score.scorePlayer1();
            assertTrue(score.getPlayer1Score() > 0);
        }
    
        @Test
        @Tag("FR2.7")
        void singleGoalIncrementsScoreExactlyOnce() {
            Score score = new Score();
            int before = score.getPlayer1Score();
            score.scorePlayer1();
            assertEquals(before + 1, score.getPlayer1Score());
        }
    
        @Test
        @Tag("FR2.9")
        void hasWinnerReturnsFalseAtOneBelowThreshold() {
            Score score = new Score(WIN_SCORE_5);
            score.setPlayer1Score(WIN_SCORE_5 - 1);
            score.setPlayer2Score(WIN_SCORE_5 - 1);
            assertFalse(score.hasWinner());
            assertEquals(NO_WINNER, score.getWinner());
        }
    
        @Test
        @Tag("FR2.9")
        void hasWinnerReturnsTrueWhenScoreExceedsWinningScore() {
            Score score = new Score(WIN_SCORE_3);
            score.setPlayer1Score(WIN_SCORE_3 + 2);
            assertTrue(score.hasWinner());
            assertEquals(PLAYER_1_SLOT, score.getWinner());
        }
    
        @Test
        @Tag("FR2.10")
        void resetClearsWinnerStatus() {
            Score score = new Score(WIN_SCORE_3);
            score.setPlayer1Score(WIN_SCORE_3);
            assertTrue(score.hasWinner());
            score.reset();
            assertFalse(score.hasWinner());
            assertEquals(NO_WINNER, score.getWinner());
        }
    
        @Test
        @Tag("FR2.10")
        void resetPreservesWinningScoreConfiguration() {
            Score score = new Score(WIN_SCORE_7);
            score.setPlayer1Score(WIN_SCORE_7);
            score.reset();
            assertEquals(WIN_SCORE_7, score.getWinningScore());
        }
    
        @Test
        @Tag("FR2.9")
        void winnerIsPlayer1WhenBothReachWinningScore() {
            Score score = new Score(WIN_SCORE_3);
            score.setPlayer1Score(WIN_SCORE_3);
            score.setPlayer2Score(WIN_SCORE_3);
            assertTrue(score.hasWinner());
            assertEquals(PLAYER_1_SLOT, score.getWinner());
        }
}
