package no.ntnu.ping404.network.packets;

/**
 * Client -> Server: A player requests to resume a paused match.
 */
public class ResumeRequest {

    /** The connection ID of the requesting player; set by the server. */
    public int requesterId;

    /** Required for Kryo serialization. */
    public ResumeRequest() {}

    public ResumeRequest(int requesterId) {
        this.requesterId = requesterId;
    }

    @Override
    public String toString() {
        return "ResumeRequest{requesterId=" + requesterId + "}";
    }
}
