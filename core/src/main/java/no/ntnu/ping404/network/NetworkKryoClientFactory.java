package no.ntnu.ping404.network;

/**
 * Default factory that creates {@link NetworkKryoClient} instances.
 *
 * <p>This is the production implementation of {@link INetworkClientFactory}.</p>
 */
public class NetworkKryoClientFactory implements INetworkClientFactory {

    @Override
    public INetworkClient create() {
        return new NetworkKryoClient();
    }
}
