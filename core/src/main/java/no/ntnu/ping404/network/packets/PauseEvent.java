package no.ntnu.ping404.network.packets;

/**
 * Server -> Clients: Broadcast to all players in a room when a pause is confirmed.
 */
public class PauseEvent {

    /** The connection ID of the player who triggered the pause. */
    public int requesterId;

    /** Required for Kryo serialization. */
    public PauseEvent() {}

    public PauseEvent(int requesterId) {
        this.requesterId = requesterId;
    }

    @Override
    public String toString() {
        return "PauseEvent{requesterId=" + requesterId + "}";
    }
}
