package no.ntnu.ping404.network.packets;

/**
 * Client -> Server: A player requests a rematch after a game ends.
 */
public class RematchRequest {

    /** Required for Kryo serialization. */
    public RematchRequest() {}

    @Override
    public String toString() {
        return "RematchRequest{}";
    }
}
