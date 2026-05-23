package no.ntnu.ping404.network;

/**
 * Factory interface for creating {@link INetworkClient} instances.
 *
 * <p>This abstraction allows decoupling of client code from the concrete
 * network client implementation (M4: modifiability).</p>
 */
@FunctionalInterface
public interface INetworkClientFactory {

    /**
     * Creates a new network client instance.
     *
     * @return a new INetworkClient
     */
    INetworkClient create();
}
