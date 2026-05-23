package no.ntnu.ping404.network.packets;

/**
 * Client -&gt; Server: Host requests to start the game.
 * The server validates the request (room must be full, phase must be WAITING)
 * and broadcasts a GameStartEvent to all clients in the room.
 */
public class GameStartRequest {

    /** The connection ID of the requesting player; set by the server. */
    public int requesterId;

    /** Required for Kryo serialization. */
    public GameStartRequest() {}

    public GameStartRequest(int requesterId) {
        this.requesterId = requesterId;
    }

    @Override
    public String toString() {
        return "GameStartRequest{requesterId=" + requesterId + "}";
    }
}
