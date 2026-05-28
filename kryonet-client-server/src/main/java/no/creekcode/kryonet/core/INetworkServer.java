package no.creekcode.kryonet.core;

import java.io.IOException;
import java.util.function.BiConsumer;

/**
 * Abstraction over a KryoNet server. Implementations handle all transport details;
 * callers interact only through this interface.
 */
public interface INetworkServer {

    void start(int tcpPort, int udpPort) throws IOException;
    void start() throws IOException;
    void stop();

    void sendToTCP(int connectionId, Object packet);
    void sendToUDP(int connectionId, Object packet);
    void sendToAllTCP(Object packet);
    void sendToAllUDP(Object packet);
    void sendToAllExceptTCP(int excludeConnectionId, Object packet);
    void sendToAllExceptUDP(int excludeConnectionId, Object packet);

    void forEachConnection(BiConsumer<Integer, PlayerConnection> action);
    PlayerConnection getConnection(int connectionId);
    int getConnectionCount();

    void addListener(ServerListener listener);
    void removeListener(ServerListener listener);

    boolean isRunning();

    // ---- Nested types -------------------------------------------------------

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

    interface ServerListener {
        void onClientConnected(PlayerConnection connection);
        void onClientDisconnected(PlayerConnection connection);
        void onReceived(PlayerConnection connection, Object packet);
    }

    class ServerListenerAdapter implements ServerListener {
        @Override public void onClientConnected(PlayerConnection connection) {}
        @Override public void onClientDisconnected(PlayerConnection connection) {}
        @Override public void onReceived(PlayerConnection connection, Object packet) {}
    }
}
