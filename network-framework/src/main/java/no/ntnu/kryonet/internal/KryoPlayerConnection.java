package no.ntnu.kryonet.internal;

import com.esotericsoftware.kryonet.Connection;
import no.ntnu.kryonet.core.INetworkServer;

/**
 * KryoNet-backed implementation of {@link INetworkServer.PlayerConnection}.
 * Package-private to the framework; callers interact through the interface only.
 */
public class KryoPlayerConnection implements INetworkServer.PlayerConnection {

    private final Connection connection;
    private final int fixedId;
    private String playerName;
    private float x, y;
    private long lastPositionUpdateTime;
    private volatile long lastHeartbeatTime;
    private volatile boolean stale;
    private String testRemoteAddress;

    public KryoPlayerConnection(Connection connection) {
        this.connection = connection;
        this.fixedId = -1;
        this.lastPositionUpdateTime = System.currentTimeMillis();
        this.lastHeartbeatTime      = System.currentTimeMillis();
    }

    /** Test-only: fixed ID, no real socket. */
    public KryoPlayerConnection(int id) {
        this.connection = null;
        this.fixedId    = id;
        this.lastPositionUpdateTime = System.currentTimeMillis();
        this.lastHeartbeatTime      = System.currentTimeMillis();
    }

    /** Test-only: fixed ID + remote address string. */
    public KryoPlayerConnection(int id, String testRemoteAddress) {
        this(id);
        this.testRemoteAddress = testRemoteAddress;
    }

    @Override public int getId() { return fixedId >= 0 ? fixedId : connection.getID(); }

    public Connection getKryoConnection() { return connection; }

    public void closeConnection() { if (connection != null) connection.close(); }

    @Override
    public String getRemoteAddress() {
        if (testRemoteAddress != null) return testRemoteAddress;
        if (connection != null && connection.getRemoteAddressTCP() != null
                && connection.getRemoteAddressTCP().getAddress() != null) {
            return connection.getRemoteAddressTCP().getAddress().getHostAddress();
        }
        return null;
    }

    @Override public String  getPlayerName()            { return playerName; }
    @Override public void    setPlayerName(String name) { this.playerName = name; }

    @Override public float   getX()                     { return x; }
    @Override public void    setX(float x)              { this.x = x; }
    @Override public float   getY()                     { return y; }
    @Override public void    setY(float y)              { this.y = y; }

    @Override
    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
        this.lastPositionUpdateTime = System.currentTimeMillis();
    }

    @Override public long    getLastPositionUpdateTime() { return lastPositionUpdateTime; }
    @Override public long    getLastHeartbeatTime()      { return lastHeartbeatTime; }
    @Override public void    updateLastHeartbeat()       { this.lastHeartbeatTime = System.currentTimeMillis(); }
    @Override public boolean isStale()                   { return stale; }
    @Override public void    markStale()                 { this.stale = true; }

    @Override
    public void sendToTCP(Object packet) {
        if (connection != null && connection.isConnected()) connection.sendTCP(packet);
    }

    @Override
    public void sendToUDP(Object packet) {
        if (connection != null && connection.isConnected()) connection.sendUDP(packet);
    }
}
