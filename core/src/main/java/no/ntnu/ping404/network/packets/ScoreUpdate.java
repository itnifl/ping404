package no.ntnu.ping404.network.packets;

/**
 * Server -> Client: Synchronizes the current score.
 */
public class ScoreUpdate {

    public int player1Score;
    public int player2Score;

    /** Required for Kryo serialization */
    public ScoreUpdate() {
    }

    public ScoreUpdate(int player1Score, int player2Score) {
        this.player1Score = player1Score;
        this.player2Score = player2Score;
    }

    @Override
    public String toString() {
        return "ScoreUpdate{score=" + player1Score + "-" + player2Score + "}";
    }
}