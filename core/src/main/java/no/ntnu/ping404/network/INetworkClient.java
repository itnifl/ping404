package no.ntnu.ping404.network;

import java.io.IOException;

/**
 * Interface for client-side network communication (M4: network library abstraction).
 *
 * <p>The {@link ClientConnector} uses this interface via constructor injection,
 * enabling seamless swapping of the underlying network implementation.</p>
 *
 * @see NetworkKryoClient
 * @see ClientConnector
 */
public interface INetworkClient {

    /**
     * Connects to a server at the specified host and ports.
     *
     * @param host the server hostname or IP address
     * @param tcpPort the TCP port for reliable communication
     * @param udpPort the UDP port for unreliable communication (0 to disable)
     * @throws IOException if the connection fails
     */
    void connect(String host, int tcpPort, int udpPort) throws IOException;

    /**
     * Connects to a server using default ports from {@link NetworkConfig}.
     *
     * @throws IOException if the connection fails
     */
    void connect() throws IOException;

    /**
     * Connects to a server at the specified host using default ports.
     *
     * @param host the server hostname or IP address
     * @throws IOException if the connection fails
     */
    void connect(String host) throws IOException;

    /**
     * Disconnects from the server.
     */
    void disconnect();

    /**
     * Sends a packet reliably (TCP) to the server.
     *
     * @param packet the packet to send
     */
    void sendTCP(Object packet);

    /**
     * Sends a packet unreliably (UDP) to the server.
     *
     * @param packet the packet to send
     */
    void sendUDP(Object packet);

    /**
     * Registers a listener for client events.
     *
     * @param listener the listener to add
     */
    void addListener(NetworkListener listener);

    /**
     * Removes a previously registered listener.
     *
     * @param listener the listener to remove
     */
    void removeListener(NetworkListener listener);

    /**
     * Returns true if the client is currently connected to the server.
     *
     * @return true if connected
     */
    boolean isConnected();

    /**
     * Returns the unique connection ID assigned by the server.
     *
     * @return the connection ID
     */
    int getConnectionId();

    /**
     * Gets the player's display name.
     *
     * @return the player name
     */
    String getPlayerName();

    /**
     * Sets the player's display name.
     *
     * @param playerName the player name
     */
    void setPlayerName(String playerName);

    /**
     * Releases resources and closes the connection.
     */
    void dispose();

    /**
     * Disables automatic Pong responses to Ping packets (for testing).
     */
    void disableAutoPong();

    /**
     * Re-enables automatic Pong responses (for testing).
     */
    void enableAutoPong();
}
