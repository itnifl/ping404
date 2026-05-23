package no.ntnu.ping404.network;

import java.io.IOException;
import java.util.function.BiConsumer;

/**
 * Interface for server-side network communication (M4: network library abstraction).
 *
 * <p>The {@link ServerConnector} uses this interface via constructor injection,
 * enabling seamless swapping of the underlying network implementation.</p>
 *
 * @see NetworkKryoServer
 * @see ServerConnector
 */
public interface INetworkServer {

    /**
     * Starts the server on the specified ports.
     *
     * @param tcpPort the TCP port for reliable communication
     * @param udpPort the UDP port for unreliable communication (0 to disable)
     * @throws IOException if the server fails to bind to the ports
     */
    void start(int tcpPort, int udpPort) throws IOException;

    /**
     * Starts the server on the default ports from {@link NetworkConfig}.
     *
     * @throws IOException if the server fails to bind
     */
    void start() throws IOException;

    /**
     * Stops the server and disconnects all clients.
     */
    void stop();

    /**
     * Sends a packet reliably (TCP) to a specific client.
     *
     * @param connectionId the client connection ID
     * @param packet the packet to send
     */
    void sendToTCP(int connectionId, Object packet);

    /**
     * Sends a packet unreliably (UDP) to a specific client.
     *
     * @param connectionId the client connection ID
     * @param packet the packet to send
     */
    void sendToUDP(int connectionId, Object packet);

    /**
     * Broadcasts a packet reliably (TCP) to all connected clients.
     *
     * @param packet the packet to send
     */
    void sendToAllTCP(Object packet);

    /**
     * Broadcasts a packet unreliably (UDP) to all connected clients.
     *
     * @param packet the packet to send
     */
    void sendToAllUDP(Object packet);

    /**
     * Broadcasts a packet reliably (TCP) to all clients except one.
     *
     * @param excludeConnectionId the connection ID to exclude
     * @param packet the packet to send
     */
    void sendToAllExceptTCP(int excludeConnectionId, Object packet);

    /**
     * Broadcasts a packet unreliably (UDP) to all clients except one.
     *
     * @param excludeConnectionId the connection ID to exclude
     * @param packet the packet to send
     */
    void sendToAllExceptUDP(int excludeConnectionId, Object packet);

    /**
     * Iterates over all active connections.
     *
     * @param action the action to perform for each connection (id, connection)
     */
    void forEachConnection(BiConsumer<Integer, PlayerConnection> action);

    /**
     * Retrieves a specific player connection by ID.
     *
     * @param connectionId the connection ID
     * @return the PlayerConnection, or null if not found
     */
    PlayerConnection getConnection(int connectionId);

    /**
     * Returns the current number of connected clients.
     *
     * @return the connection count
     */
    int getConnectionCount();

    /**
     * Registers a listener for server events.
     *
     * @param listener the listener to add
     */
    void addListener(ServerListener listener);

    /**
     * Removes a previously registered listener.
     *
     * @param listener the listener to remove
     */
    void removeListener(ServerListener listener);

    /**
     * Returns true if the server is currently running.
     *
     * @return true if running
     */
    boolean isRunning();

    /**
     * Represents a connected player with position and metadata.
     * Nested in INetworkServer to maintain backward compatibility with existing code.
     */
    interface PlayerConnection {
        int getId();
        String getRemoteAddress();
        String getPlayerName();
        void setPlayerName(String playerName);
        float getX();
        void setX(float x);
        float getY();
        void setY(float y);
        void setPosition(float x, float y);
        long getLastPositionUpdateTime();
        long getLastHeartbeatTime();
        void updateLastHeartbeat();
        boolean isStale();
        void markStale();
        void sendToTCP(Object packet);
        void sendToUDP(Object packet);
    }

    /**
     * Listener for server-side network events.
     */
    interface ServerListener {
        void onClientConnected(PlayerConnection connection);
        void onClientDisconnected(PlayerConnection connection);
        void onReceived(PlayerConnection connection, Object packet);
    }

    /**
     * Adapter with no-op implementations for convenience.
     */
    abstract class ServerListenerAdapter implements ServerListener {
        @Override
        public void onClientConnected(PlayerConnection connection) {}

        @Override
        public void onClientDisconnected(PlayerConnection connection) {}

        @Override
        public void onReceived(PlayerConnection connection, Object packet) {}
    }
}
