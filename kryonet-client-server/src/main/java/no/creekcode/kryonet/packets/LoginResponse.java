package no.creekcode.kryonet.packets;

/** Server to client: authentication result. */
public class LoginResponse {

    public boolean success;
    public int playerId;
    public String message;
    public String serverVersion;
    public int winScore;
    public String sessionToken;
    public String roomCode;

    public LoginResponse() {}

    public static LoginResponse success(int playerId, int winScore, String sessionToken, String roomCode) {
        LoginResponse r = new LoginResponse();
        r.success = true;
        r.playerId = playerId;
        r.message = "Welcome!";
        r.serverVersion = "1.0.0";
        r.sessionToken = sessionToken;
        r.winScore = winScore;
        r.roomCode = roomCode;
        return r;
    }

    public static LoginResponse failure(String reason) {
        LoginResponse r = new LoginResponse();
        r.success = false;
        r.playerId = -1;
        r.message = reason;
        r.serverVersion = "1.0.0";
        return r;
    }
}
