package no.ntnu.ping404.network.packets;

/**
 * Server -&gt; Clients: Broadcast to all players in a room when the game starts.
 * Clients receiving this transition from waiting/lobby to the GameScreen.
 */
public class GameStartEvent {

    /** The connection ID of this client's player. */
    public int playerId;

    /** The connection ID of the opponent. */
    public int opponentId;

    /** The slot this player occupies on the field (1 = left, 2 = right). */
    public int playerSlot;

    /** Name of the player. */
    public String playerName;

    /** Name of the opponent. */
    public String opponentName;

    /** The win score for this match. */
    public int winScore;

    /** Required for Kryo serialization. */
    public GameStartEvent() {}

    public GameStartEvent(int playerId, int opponentId, int playerSlot,
                          String playerName, String opponentName, int winScore) {
        this.playerId = playerId;
        this.opponentId = opponentId;
        this.playerSlot = playerSlot;
        this.playerName = playerName;
        this.opponentName = opponentName;
        this.winScore = winScore;
    }

    @Override
    public String toString() {
        return "GameStartEvent{playerId=" + playerId
                + ", opponentId=" + opponentId
                + ", playerSlot=" + playerSlot
                + ", playerName='" + playerName + '\''
                + ", opponentName='" + opponentName + '\''
                + ", winScore=" + winScore + '}';
    }
}
