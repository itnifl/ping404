package no.creekcode.kryonet.observer;

/**
 * Listener for client-side network lifecycle events.
 *
 * <p>All methods are invoked on the client event-consumer thread.
 * Use {@link Adapter} as a base when only a subset of events is needed.</p>
 */
public interface NetworkListener {

    void onConnected();

    void onDisconnected();

    /** Called for every packet received from the server. Cast {@code packet} to the expected type. */
    void onReceived(Object packet);

    /** No-op base class; override only the events you care about. */
    class Adapter implements NetworkListener {
        @Override public void onConnected() {}
        @Override public void onDisconnected() {}
        @Override public void onReceived(Object packet) {}
    }
}
