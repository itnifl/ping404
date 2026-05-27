package no.ntnu.kryonet.packets;

/** Server to client: generic error or request rejection. */
public class ErrorPacket {

    public String message;
    public int requesterId;

    public ErrorPacket() {}

    public ErrorPacket(int requesterId, String message) {
        this.requesterId = requesterId;
        this.message = message;
    }

    public ErrorPacket(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "ErrorPacket{message='" + message + "'}";
    }
}
