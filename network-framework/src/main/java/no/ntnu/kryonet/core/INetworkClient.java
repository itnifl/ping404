package no.ntnu.kryonet.core;

import no.ntnu.kryonet.observer.NetworkListener;

import java.io.IOException;

/**
 * Abstraction over a KryoNet client. Implementations handle all transport details;
 * callers interact only through this interface.
 */
public interface INetworkClient {

    void connect(String host, int tcpPort, int udpPort) throws IOException;
    void connect() throws IOException;
    void connect(String host) throws IOException;

    void disconnect();
    void dispose();

    void sendTCP(Object packet);
    void sendUDP(Object packet);

    void addListener(NetworkListener listener);
    void removeListener(NetworkListener listener);

    boolean isConnected();
    int getConnectionId();

    String getPlayerName();
    void setPlayerName(String playerName);

    void disableAutoPong();
    void enableAutoPong();
}
