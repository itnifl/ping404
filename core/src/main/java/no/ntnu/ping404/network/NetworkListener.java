package no.ntnu.ping404.network;

/**
 * Listener interface for client-side network events dispatched by {@link NetworkClient}.
 *
 * <p>Implement this interface to react to connection lifecycle changes and incoming packets.
 * All methods are invoked on the client event-consumer thread, not the Kryonet I/O thread.</p>
 *
 * <p>Use {@link Adapter} as a base class when only a subset of events is needed.</p>
 *
 * @see NetworkClient#addListener(NetworkListener)
 * @see ClientConnector#addListener(NetworkListener)
 */
public interface NetworkListener {

    /**
     * Called when the client has successfully connected to the server.
     */
    void onConnected();

    /**
     * Called when the connection to the server has been lost or closed.
     */
    void onDisconnected();

    /**
     * Called when a packet has been received from the server.
     *
     * @param packet the deserialized packet object; cast to the expected type before use
     */
    void onReceived(Object packet);

    /**
     * No-op base implementation of {@link NetworkListener}.
     * Extend this class and override only the methods you need.
     */
    class Adapter implements NetworkListener {
        @Override
        public void onConnected() {}

        @Override
        public void onDisconnected() {}

        @Override
        public void onReceived(Object packet) {}
    }
}
