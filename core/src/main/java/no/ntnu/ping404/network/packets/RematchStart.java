package no.ntnu.ping404.network.packets;

/**
 * Server -> Client: Both players agreed to rematch; the new round is starting.
 */
public class RematchStart {

    /** Required for Kryo serialization. */
    public RematchStart() {}

    @Override
    public String toString() {
        return "RematchStart{}";
    }
}
