package no.ntnu.ping404.network.packets;

/**
 * Server -> Client: Game finished, winner announcement.
 */
public class GameOver {

    /** Winning player connection ID (not a slot number) */
    public int winnerId;

    /** Winning player name */
    public String winnerName;

    /** Final score for player 1 */
    public int player1Score;

    /** Final score for player 2 */
    public int player2Score;

    /** Required for Kryo serialization */
    public GameOver() {
    }

    public GameOver(int winnerId, String winnerName, int player1Score, int player2Score) {
        this.winnerId = winnerId;
        this.winnerName = winnerName;
        this.player1Score = player1Score;
        this.player2Score = player2Score;
    }

    @Override
    public String toString() {
        return "GameOver{winner=" + winnerName + " (Player " + winnerId + "), score=" + player1Score + "-" + player2Score + "}";
    }
}
