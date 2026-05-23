package no.ntnu.ping404.network.packets;

/**
 * Server -> Client: Generic error/rejection response.
 * Sent when a client request is rejected (e.g., unauthorized pause/resume).
 */
public class ErrorPacket {

    /** Human-readable error message. */
    public String message;

    /** The connection ID of the player who triggered the error. */
    public int requesterId;

    /** Required for Kryo serialization. */
    public ErrorPacket() {}

    public ErrorPacket(int requesterId, String errorMessage) {
        this.requesterId = requesterId;
        this.message = errorMessage;
    }

    public ErrorPacket(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "ErrorPacket{message='" + message + "'}";
    }
}
