package no.ntnu.ping404.network.packets;

/**
 * Client -> Server: Request to log in to the server.
 */
public class LoginRequest {

    /** Player's display name */
    public String playerName;

    /** Client version for compatibility checking */
    public String clientVersion;

    /** 
     * Desired win score for the room (host only). 
     * Null means use server default (5).
     */
    public Integer winScore;

    /**
     * Room/session code to join (join flow).
     * Null/blank means no explicit room requested.
     */
    public String roomCode;

    /**
     * Host flow flag: true means create a new room explicitly.
     * Defaults to false (regular join flow).
     */
    public boolean createRoom;

    /**
     * Session token for reconnection.
     * Null on first login; set when reconnecting to resume a previous session.
     * This is set by the client side.
     */
    public String sessionToken;

    /** Required for Kryo serialization */
    public LoginRequest() {
    }

    public LoginRequest(String playerName) {
        this.playerName = playerName;
        this.clientVersion = "1.0.0";
    }

    public LoginRequest(String playerName, String clientVersion) {
        this.playerName = playerName;
        this.clientVersion = clientVersion;
    }

    public LoginRequest(String playerName, String clientVersion, int winScore) {
        this.playerName = playerName;
        this.clientVersion = clientVersion;
        this.winScore = winScore;
    }

    public Integer getWinScore() {
        return winScore;
    }

    public void setWinScore(Integer winScore) {
        this.winScore = winScore;
    }

    @Override
    public String toString() {
        return "LoginRequest{playerName='" + playerName + "', version='" + clientVersion + "', winScore=" + winScore + "}";
    }
}
