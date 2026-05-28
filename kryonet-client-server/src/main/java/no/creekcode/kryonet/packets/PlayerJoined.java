package no.creekcode.kryonet.packets;

/** Server to client: a player joined the session. */
public class PlayerJoined {

    public int playerId;
    public String playerName;
    public float x;
    public float y;

    public PlayerJoined() {}

    public PlayerJoined(int playerId, String playerName, float x, float y) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.x = x;
        this.y = y;
    }

    @Override
    public String toString() {
        return "PlayerJoined{playerId=" + playerId + ", name='" + playerName + "'}";
    }
}
