package no.ntnu.kryonet.packets;

/** Server to client: a player left the session. */
public class PlayerLeft {

    public int playerId;
    public String playerName;
    public String reason;

    public PlayerLeft() {}

    public PlayerLeft(int playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
    }

    public PlayerLeft(int playerId, String playerName, String reason) {
        this(playerId, playerName);
        this.reason = reason;
    }
}
