package no.ntnu.kryonet.packets;

/** Client to server: request to authenticate and join. */
public class LoginRequest {

    public String playerName;
    public String clientVersion;
    public Integer winScore;
    public String roomCode;
    public boolean createRoom;
    public String sessionToken;

    public LoginRequest() {}

    public LoginRequest(String playerName) {
        this.playerName = playerName;
        this.clientVersion = "1.0.0";
    }

    public LoginRequest(String playerName, String clientVersion) {
        this.playerName = playerName;
        this.clientVersion = clientVersion;
    }
}
