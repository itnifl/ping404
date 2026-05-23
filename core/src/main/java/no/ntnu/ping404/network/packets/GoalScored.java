package no.ntnu.ping404.network.packets;

/**
 * Server -> Client: Signals that a goal has been scored and includes the updated score.
 * Sent over TCP to guarantee delivery.
 */
public class GoalScored {

    /** Player ID who scored (1 or 2). */
    public int scoringPlayerId;

    /** Updated score for player 1. */
    public int player1Score;

    /** Updated score for player 2. */
    public int player2Score;

    /** Required for Kryo serialization. */
    public GoalScored() {
    }

    public GoalScored(int scoringPlayerId, int player1Score, int player2Score) {
        this.scoringPlayerId = scoringPlayerId;
        this.player1Score = player1Score;
        this.player2Score = player2Score;
    }

    @Override
    public String toString() {
        return "GoalScored{scoringPlayerId=" + scoringPlayerId
            + ", P1score=" + player1Score + ", P2score=" + player2Score + "}";
    }
}