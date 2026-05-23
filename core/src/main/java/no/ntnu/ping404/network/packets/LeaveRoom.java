package no.ntnu.ping404.network.packets;

/**
 * Client -> Server: A player intentionally leaves the room.
 * Unlike a network disconnect, this signals that the player does not intend to reconnect,
 * so the server should clear reconnection data and reset the room to WAITING phase.
 */
public class LeaveRoom {

    /** Required for Kryo serialization. */
    public LeaveRoom() {}

    @Override
    public String toString() {
        return "LeaveRoom{}";
    }
}
