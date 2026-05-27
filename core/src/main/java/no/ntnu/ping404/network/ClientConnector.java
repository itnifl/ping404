package no.ntnu.ping404.network;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import no.ntnu.ping404.network.packets.Ping;
import no.ntnu.ping404.network.packets.PlayerPosition;
import no.ntnu.ping404.network.packets.Pong;

/**
 * Facade for client-side network operations.
 *
 * <p>This is the sole entry point for client network communication. It wraps an
 * {@link INetworkClient} and routes packets to the appropriate transport channel
 * (TCP/UDP). External code should never directly use NetworkKryoClient.</p>
 *
 * <p>Use {@link #create()} factory method for production code. Constructor injection
 * is available for testing with mock implementations.</p>
 *
 * <p>Supports M4 (modifiability: swap network library) by encapsulating all
 * KryoNet-specific details behind this facade.</p>
 */
public class ClientConnector {

    private final INetworkClient networkClient;
    private final no.ntnu.kryonet.core.INetworkClient frameworkNetworkClient;
    private final Map<NetworkListener, no.ntnu.kryonet.observer.NetworkListener> frameworkListenerBridges =
            new ConcurrentHashMap<>();

    /**
     * Creates a ClientConnector wrapping the provided network client.
     *
     * @param networkClient the network client implementation (injected)
     */
    public ClientConnector(INetworkClient networkClient) {
        this.networkClient = networkClient;
        this.frameworkNetworkClient = null;
    }

    public ClientConnector(no.ntnu.kryonet.core.INetworkClient frameworkNetworkClient) {
        this.networkClient = null;
        this.frameworkNetworkClient = frameworkNetworkClient;
    }

    /**
     * Factory method to create a ClientConnector using the provided factory.
     *
     * @param factory the factory to create the underlying network client
     * @return a new ClientConnector ready for use
     */
    public static ClientConnector create(INetworkClientFactory factory) {
        return new ClientConnector(factory.create());
    }

    /**
     * Connects to a server at the specified host and ports.
     *
     * @param host the server hostname or IP address
     * @param tcpPort the TCP port for reliable communication
     * @param udpPort the UDP port for unreliable communication
     * @throws IOException if the connection fails
     */
    public void connect(String host, int tcpPort, int udpPort) throws IOException {
        if (networkClient != null) {
            networkClient.connect(host, tcpPort, udpPort);
        } else {
            frameworkNetworkClient.connect(host, tcpPort, udpPort);
        }
    }

    /**
     * Connects to the default server using default ports.
     *
     * @throws IOException if the connection fails
     */
    public void connect() throws IOException {
        if (networkClient != null) {
            networkClient.connect();
        } else {
            frameworkNetworkClient.connect();
        }
    }

    /**
     * Connects to a server at the specified host using default ports.
     *
     * @param host the server hostname or IP address
     * @throws IOException if the connection fails
     */
    public void connect(String host) throws IOException {
        if (networkClient != null) {
            networkClient.connect(host);
        } else {
            frameworkNetworkClient.connect(host);
        }
    }

    /**
     * Disconnects from the server.
     */
    public void disconnect() {
        if (networkClient != null) {
            networkClient.disconnect();
        } else {
            frameworkNetworkClient.disconnect();
        }
    }

    /**
     * Returns true if currently connected to the server.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return networkClient != null ? networkClient.isConnected() : frameworkNetworkClient.isConnected();
    }

    /**
     * Returns the connection ID assigned by the server.
     *
     * @return the connection ID
     */
    public int getConnectionId() {
        return networkClient != null ? networkClient.getConnectionId() : frameworkNetworkClient.getConnectionId();
    }

    /**
     * Gets the player's display name.
     *
     * @return the player name
     */
    public String getPlayerName() {
        return networkClient != null ? networkClient.getPlayerName() : frameworkNetworkClient.getPlayerName();
    }

    /**
     * Sets the player's display name.
     *
     * @param playerName the player name
     */
    public void setPlayerName(String playerName) {
        if (networkClient != null) {
            networkClient.setPlayerName(playerName);
        } else {
            frameworkNetworkClient.setPlayerName(playerName);
        }
    }

    /**
     * Releases resources and closes the connection.
     */
    public void dispose() {
        if (networkClient != null) {
            networkClient.dispose();
        } else {
            frameworkNetworkClient.dispose();
        }
    }

    /**
     * Sends a packet using the appropriate transport channel.
     *
     * @param packet the packet to send
     */
    public void send(Object packet) {
        if (isUnreliable(packet)) {
            if (networkClient != null) {
                networkClient.sendUDP(packet);
            } else {
                frameworkNetworkClient.sendUDP(PacketTranslator.toFramework(packet));
            }
        } else {
            if (networkClient != null) {
                networkClient.sendTCP(packet);
            } else {
                frameworkNetworkClient.sendTCP(PacketTranslator.toFramework(packet));
            }
        }
    }

    /**
     * Registers a listener for network events.
     *
     * @param listener the listener to add
     */
    public void addListener(NetworkListener listener) {
        if (networkClient != null) {
            networkClient.addListener(listener);
        } else {
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
            frameworkListenerBridges.put(listener, bridge);
            frameworkNetworkClient.addListener(bridge);
        }
    }

    /**
     * Removes a previously registered listener.
     *
     * @param listener the listener to remove
     */
    public void removeListener(NetworkListener listener) {
        if (networkClient != null) {
            networkClient.removeListener(listener);
        } else {
            no.ntnu.kryonet.observer.NetworkListener bridge = frameworkListenerBridges.remove(listener);
            if (bridge != null) {
                frameworkNetworkClient.removeListener(bridge);
            }
        }
    }

    private static boolean isUnreliable(Object packet) {
        return packet instanceof PlayerPosition
            || packet instanceof Ping
            || packet instanceof Pong;
    }
}
