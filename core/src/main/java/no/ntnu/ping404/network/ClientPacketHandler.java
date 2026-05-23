package no.ntnu.ping404.network;

/**
 * Functional interface for client-side packet handlers.
 * Each handler processes a specific packet type and updates the game state.
 *
 * @param <T> the packet type this handler processes
 */
@FunctionalInterface
public interface ClientPacketHandler<T> {

    /**
     * Handles a packet received from the server.
     * Called on the render thread via {@code Gdx.app.postRunnable()}.
     *
     * @param packet the received packet
     */
    void handle(T packet);
}
