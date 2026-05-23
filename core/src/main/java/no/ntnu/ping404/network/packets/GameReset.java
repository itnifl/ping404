package no.ntnu.ping404.network.packets;

/**
 * Server -> Client: Notification that the game has been reset to WAITING phase.
 * Sent to remaining player when opponent's reconnect window expires.
 */
public class GameReset {

    /** Reason for the reset (e.g., "Opponent timed out") */
    public String reason;

    /** Required for Kryo serialization */
    public GameReset() {}

    public GameReset(String reason) {
        this.reason = reason;
    }
}
