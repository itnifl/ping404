package no.creekcode.kryonet.dispatch.client;

/**
 * Typed handler for a single incoming packet type on the client side.
 *
 * @param <T> the packet type this handler processes
 */
@FunctionalInterface
public interface ClientPacketHandler<T> {

    void handle(T packet);
}
