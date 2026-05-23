package no.ntnu.ping404.network.packets;

/**
 * Server -> Clients: Broadcast to all players in a room when a resume is confirmed.
 */
public class ResumeEvent {

    /** The connection ID of the player who triggered the resume. */
    public int requesterId;

    /** Required for Kryo serialization. */
    public ResumeEvent() {}

    public ResumeEvent(int requesterId) {
        this.requesterId = requesterId;
    }

    @Override
    public String toString() {
        return "ResumeEvent{requesterId=" + requesterId + "}";
    }
}
