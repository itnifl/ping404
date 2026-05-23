package no.ntnu.ping404.network.packets;

/**
 * Client -> Server: A player requests to pause the match.
 * The server must broadcast a PauseEvent to all clients in the room.
 */
public class PauseRequest {

    /** The connection ID of the requesting player; set by the server. */
    public int requesterId;

    /** Required for Kryo serialization. */
    public PauseRequest() {}

    public PauseRequest(int requesterId) {
        this.requesterId = requesterId;
    }

    @Override
    public String toString() {
        return "PauseRequest{requesterId=" + requesterId + "}";
    }
}
