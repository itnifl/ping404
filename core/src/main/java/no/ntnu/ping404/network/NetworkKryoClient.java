package no.ntnu.ping404.network;

import com.esotericsoftware.kryo.Kryo;
import no.ntnu.ping404.network.packets.PacketRegistry;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Legacy ping404 client wrapper backed by the reusable framework client.
 */
public class NetworkKryoClient implements INetworkClient {

    private final no.ntnu.kryonet.core.INetworkClient delegate;
    private final ConcurrentHashMap<NetworkListener, no.ntnu.kryonet.observer.NetworkListener> listenerBridges = new ConcurrentHashMap<>();

    public NetworkKryoClient() {
        this(PacketRegistry::register);
    }

    public NetworkKryoClient(Consumer<Kryo> registrationCallback) {
        this.delegate = new no.ntnu.kryonet.internal.NetworkKryoClient(kryo -> {
            if (registrationCallback != null) {
                registrationCallback.accept(kryo);
            }
        });
    }

    public void connect(String host, int tcpPort, int udpPort) throws IOException {
        delegate.connect(host, tcpPort, udpPort);
    }

    public void connect() throws IOException {
        delegate.connect();
    }

    public void connect(String host) throws IOException {
        delegate.connect(host);
    }

    public void disconnect() {
        delegate.disconnect();
    }

    @Override
    public void sendTCP(Object packet) {
        delegate.sendTCP(PacketTranslator.toFramework(packet));
    }

    @Override
    public void sendUDP(Object packet) {
        delegate.sendUDP(PacketTranslator.toFramework(packet));
    }

    @Override
    public void addListener(NetworkListener listener) {
        no.ntnu.kryonet.observer.NetworkListener bridge = new no.ntnu.kryonet.observer.NetworkListener.Adapter() {
            @Override
            public void onConnected() {
                listener.onConnected();
            }

            @Override
            public void onDisconnected() {
                listener.onDisconnected();
            }

            @Override
            public void onReceived(Object packet) {
                listener.onReceived(PacketTranslator.toLegacy(packet));
            }
        };
        listenerBridges.put(listener, bridge);
        delegate.addListener(bridge);
    }

    public void removeListener(NetworkListener listener) {
        no.ntnu.kryonet.observer.NetworkListener bridge = listenerBridges.remove(listener);
        if (bridge != null) {
            delegate.removeListener(bridge);
        }
    }

    /**
     * Disable automatic Pong responses to Ping packets (for testing stale detection).
     */
    public void disableAutoPong() {
        delegate.disableAutoPong();
    }

    /**
     * Re-enable automatic Pong responses (for testing).
     */
    public void enableAutoPong() {
        delegate.enableAutoPong();
    }

    public boolean isConnected() {
        return delegate.isConnected();
    }

    public int getConnectionId() {
        return delegate.getConnectionId();
    }

    public String getPlayerName() {
        return delegate.getPlayerName();
    }

    public void setPlayerName(String playerName) {
        delegate.setPlayerName(playerName);
    }

    public void dispose() {
        delegate.dispose();
    }
}
