package no.ntnu.ping404.network.packets;

/**
 * Server -> Client: Response to a login request.
 */
public class LoginResponse {

    /** Whether the login was successful */
    public boolean success;

    /** The assigned player ID (connection ID) */
    public int playerId;

    /** Error message if login failed */
    public String message;

    /** Server version */
    public String serverVersion;

    /** The room's win score (goals needed to win) */
    public int winScore;

    /** Session token for reconnection. Null if login failed. */
    public String sessionToken;

    /** Authoritative room/session code assigned by server. */
    public String roomCode;

    /** Required for Kryo serialization */
    public LoginResponse() {
    }

    /**
     * Create a successful login response.
     */
    public static LoginResponse success(int playerId, int winScore, String sessionToken, String roomCode) {
        LoginResponse response = new LoginResponse();
        response.success = true;
        response.playerId = playerId;
        response.message = "Welcome!";
        response.serverVersion = "1.0.0";
        response.sessionToken = sessionToken;
        response.winScore = winScore;
        response.roomCode = roomCode;
        return response;
    }

    /**
     * Create a failed login response.
     */
    public static LoginResponse failure(String reason) {
        LoginResponse response = new LoginResponse();
        response.success = false;
        response.playerId = -1;
        response.message = reason;
        response.serverVersion = "1.0.0";
        response.winScore = 0;
        response.roomCode = null;
        return response;
    }

    @Override
    public String toString() {
        return "LoginResponse{success=" + success + ", playerId=" + playerId +
            ", winScore=" + winScore + ", roomCode='" + roomCode + "', message='" + message + "'}";
    }
}
