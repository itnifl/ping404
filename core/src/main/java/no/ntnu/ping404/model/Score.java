package no.ntnu.ping404.model;

/**
 * Tracks and manages the game score for a match.
 */
public class Score {

    public static final int PLAYER_1 = 1;
    public static final int PLAYER_2 = 2;
    public static final int NO_WINNER = 0;
    public static final int DEFAULT_WINNING_SCORE = 5;

    private int player1Score;
    private int player2Score;
    private int winningScore;

    /** No-arg constructor required for Kryo serialization. */
    public Score() {
        this.winningScore = DEFAULT_WINNING_SCORE;
    }

    /**
     * Creates a new Score instance with the specified winning score.
     *
     * @param winningScore the score needed to win the match
     */
    public Score(int winningScore) {
        this.winningScore = winningScore;
    }

    public void scorePlayer1() { player1Score++; }

    public void scorePlayer2() { player2Score++; }

    public int getPlayer1Score() { return player1Score; }

    public void setPlayer1Score(int score) { this.player1Score = score; }

    public int getPlayer2Score() { return player2Score; }

    public void setPlayer2Score(int score) { this.player2Score = score; }

    public int getWinningScore() { return winningScore; }

    public void setWinningScore(int winningScore) { this.winningScore = winningScore; }

    public boolean hasWinner() {
        return player1Score >= winningScore || player2Score >= winningScore;
    }

    /**
     * Returns PLAYER_1 if player 1 won, PLAYER_2 if player 2 won, NO_WINNER if no winner yet.
     */
    public int getWinner() {
        if (player1Score >= winningScore) return PLAYER_1;
        if (player2Score >= winningScore) return PLAYER_2;
        return NO_WINNER;
    }

    public void reset() {
        player1Score = 0;
        player2Score = 0;
    }

    @Override
    public String toString() {
        return player1Score + " - " + player2Score;
    }

    /**
     * Increments the score for the specified player.
     *
     * @param playerId the player ID (PLAYER_1 or PLAYER_2)
     */
    public void incrementScore(int playerId) {
        if (playerId == PLAYER_1) {
            player1Score++;
        } else if (playerId == PLAYER_2) {
            player2Score++;
        }
    }
}
