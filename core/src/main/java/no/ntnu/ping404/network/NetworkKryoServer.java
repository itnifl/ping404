package no.ntnu.ping404.network;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Connection;
import no.ntnu.ping404.network.packets.PacketRegistry;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Legacy ping404 server wrapper backed by the reusable framework server.
 */
public class NetworkKryoServer implements INetworkServer {

    private final no.creekcode.kryonet.core.INetworkServer delegate;
    private final CopyOnWriteArrayList<INetworkServer.ServerListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<Integer, KryoPlayerConnection> connectionWrappers = new ConcurrentHashMap<>();

    public NetworkKryoServer() {
        this(PacketRegistry::register);
    }

    public NetworkKryoServer(Consumer<Kryo> registrationCallback) {
        this.delegate = new no.creekcode.kryonet.internal.NetworkKryoServer(kryo -> {
            if (registrationCallback != null) {
                registrationCallback.accept(kryo);
            }
        });
        this.delegate.addListener(new no.creekcode.kryonet.core.INetworkServer.ServerListenerAdapter() {
            @Override
            public void onClientConnected(no.creekcode.kryonet.core.INetworkServer.PlayerConnection connection) {
                KryoPlayerConnection wrappedConnection = adaptConnection(connection);
                for (INetworkServer.ServerListener listener : listeners) {
                    listener.onClientConnected(wrappedConnection);
                }
            }

            @Override
            public void onClientDisconnected(no.creekcode.kryonet.core.INetworkServer.PlayerConnection connection) {
                KryoPlayerConnection wrappedConnection = connectionWrappers.remove(connection.getId());
                if (wrappedConnection == null) {
                    wrappedConnection = adaptConnection(connection);
                }
                for (INetworkServer.ServerListener listener : listeners) {
                    listener.onClientDisconnected(wrappedConnection);
                }
            }

            @Override
            public void onReceived(no.creekcode.kryonet.core.INetworkServer.PlayerConnection connection, Object packet) {
                KryoPlayerConnection wrappedConnection = adaptConnection(connection);
                for (INetworkServer.ServerListener listener : listeners) {
                    listener.onReceived(wrappedConnection, PacketTranslator.toLegacy(packet));
                }
            }
        });
    }

    @Override
    public void start(int tcpPort, int udpPort) throws IOException {
        delegate.start(tcpPort, udpPort);
    }

    @Override
    public void start() throws IOException {
        delegate.start();
    }

    @Override
    public void stop() {
        delegate.stop();
        connectionWrappers.clear();
    }

    @Override
    public void sendToTCP(int connectionId, Object packet) {
        delegate.sendToTCP(connectionId, PacketTranslator.toFramework(packet));
    }

    @Override
    public void sendToUDP(int connectionId, Object packet) {
        delegate.sendToUDP(connectionId, PacketTranslator.toFramework(packet));
    }

    @Override
    public void sendToAllTCP(Object packet) {
        delegate.sendToAllTCP(PacketTranslator.toFramework(packet));
    }

    @Override
    public void sendToAllUDP(Object packet) {
        delegate.sendToAllUDP(PacketTranslator.toFramework(packet));
    }

    @Override
    public void sendToAllExceptTCP(int excludeConnectionId, Object packet) {
        delegate.sendToAllExceptTCP(excludeConnectionId, PacketTranslator.toFramework(packet));
    }

    @Override
    public void sendToAllExceptUDP(int excludeConnectionId, Object packet) {
        delegate.sendToAllExceptUDP(excludeConnectionId, PacketTranslator.toFramework(packet));
    }

    @Override
    public void forEachConnection(BiConsumer<Integer, INetworkServer.PlayerConnection> action) {
        delegate.forEachConnection((id, connection) -> action.accept(id, adaptConnection(connection)));
    }

    @Override
    public INetworkServer.PlayerConnection getConnection(int connectionId) {
        no.creekcode.kryonet.core.INetworkServer.PlayerConnection connection = delegate.getConnection(connectionId);
        return connection == null ? null : adaptConnection(connection);
    }

    @Override
    public int getConnectionCount() {
        return delegate.getConnectionCount();
    }

    @Override
    public void addListener(INetworkServer.ServerListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(INetworkServer.ServerListener listener) {
        listeners.remove(listener);
    }

    @Override
    public boolean isRunning() {
        return delegate.isRunning();
    }

    public no.creekcode.kryonet.core.INetworkServer getFrameworkServer() {
        return delegate;
    }

    private KryoPlayerConnection adaptConnection(no.creekcode.kryonet.core.INetworkServer.PlayerConnection connection) {
        return connectionWrappers.computeIfAbsent(connection.getId(), id -> new KryoPlayerConnection(connection));
    }

    public static class KryoPlayerConnection implements INetworkServer.PlayerConnection {
        private final no.creekcode.kryonet.core.INetworkServer.PlayerConnection frameworkConnection;
        private final Connection connection;
        private final int fixedId;
        private String playerName;
        private float x, y;
        private long lastPositionUpdateTime = System.currentTimeMillis();
        private long lastHeartbeatTime = System.currentTimeMillis();
        private boolean stale;
        private String testRemoteAddress;

        public KryoPlayerConnection(Connection connection) {
            this.connection = connection;
            this.frameworkConnection = null;
            this.fixedId = -1;
        }

        public KryoPlayerConnection(no.creekcode.kryonet.core.INetworkServer.PlayerConnection frameworkConnection) {
            this.connection = null;
            this.frameworkConnection = frameworkConnection;
            this.fixedId = -1;
        }

        public KryoPlayerConnection(int id) {
            this.connection = null;
            this.frameworkConnection = null;
            this.fixedId = id;
        }

        public KryoPlayerConnection(int id, String testRemoteAddress) {
            this(id);
            this.testRemoteAddress = testRemoteAddress;
        }

        @Override
        public int getId() {
            if (frameworkConnection != null) {
                return frameworkConnection.getId();
            }
            return fixedId >= 0 ? fixedId : connection.getID();
        }

        public Connection getKryoConnection() {
            if (connection != null) {
                return connection;
            }
            if (frameworkConnection instanceof no.creekcode.kryonet.internal.KryoPlayerConnection kryoConnection) {
                return kryoConnection.getKryoConnection();
            }
            return null;
        }

        public void closeConnection() {
            if (frameworkConnection instanceof no.creekcode.kryonet.internal.KryoPlayerConnection kryoConnection) {
                kryoConnection.closeConnection();
            } else if (connection != null) {
                connection.close();
            }
        }

        @Override
        public String getRemoteAddress() {
            if (frameworkConnection != null) {
                return frameworkConnection.getRemoteAddress();
            }
            if (testRemoteAddress != null) {
                return testRemoteAddress;
            }
            if (connection != null && connection.getRemoteAddressTCP() != null && connection.getRemoteAddressTCP().getAddress() != null) {
                return connection.getRemoteAddressTCP().getAddress().getHostAddress();
            }
            return null;
        }

        @Override
        public String getPlayerName() {
            return frameworkConnection != null ? frameworkConnection.getPlayerName() : playerName;
        }

        @Override
        public void setPlayerName(String playerName) {
            if (frameworkConnection != null) {
                frameworkConnection.setPlayerName(playerName);
            } else {
                this.playerName = playerName;
            }
        }

        @Override
        public float getX() {
            return frameworkConnection != null ? frameworkConnection.getX() : x;
        }

        @Override
        public void setX(float x) {
            if (frameworkConnection != null) {
                frameworkConnection.setX(x);
            } else {
                this.x = x;
            }
        }

        @Override
        public float getY() {
            return frameworkConnection != null ? frameworkConnection.getY() : y;
        }

        @Override
        public void setY(float y) {
            if (frameworkConnection != null) {
                frameworkConnection.setY(y);
            } else {
                this.y = y;
            }
        }

        @Override
        public void setPosition(float x, float y) {
            if (frameworkConnection != null) {
                frameworkConnection.setPosition(x, y);
            } else {
                this.x = x;
                this.y = y;
                this.lastPositionUpdateTime = System.currentTimeMillis();
            }
        }

        @Override
        public long getLastPositionUpdateTime() {
            return frameworkConnection != null ? frameworkConnection.getLastPositionUpdateTime() : lastPositionUpdateTime;
        }

        @Override
        public long getLastHeartbeatTime() {
            return frameworkConnection != null ? frameworkConnection.getLastHeartbeatTime() : lastHeartbeatTime;
        }

        @Override
        public void sendToTCP(Object packet) {
            if (frameworkConnection != null) {
                frameworkConnection.sendToTCP(PacketTranslator.toFramework(packet));
            } else if (connection != null && connection.isConnected()) {
                connection.sendTCP(PacketTranslator.toFramework(packet));
            }
        }

        @Override
        public void sendToUDP(Object packet) {
            if (frameworkConnection != null) {
                frameworkConnection.sendToUDP(PacketTranslator.toFramework(packet));
            } else if (connection != null && connection.isConnected()) {
                connection.sendUDP(PacketTranslator.toFramework(packet));
            }
        }

        @Override
        public void updateLastHeartbeat() {
            if (frameworkConnection != null) {
                frameworkConnection.updateLastHeartbeat();
            } else {
                this.lastHeartbeatTime = System.currentTimeMillis();
            }
        }

        @Override
        public boolean isStale() {
            return frameworkConnection != null ? frameworkConnection.isStale() : stale;
        }

        @Override
        public void markStale() {
            if (frameworkConnection != null) {
                frameworkConnection.markStale();
            } else {
                stale = true;
            }
        }
    }

    /** @deprecated Use {@link INetworkServer.PlayerConnection} instead. */
    @Deprecated
    public interface PlayerConnection extends INetworkServer.PlayerConnection {}

    /** @deprecated Use {@link INetworkServer.ServerListener} instead. */
    @Deprecated
    public interface ServerListener extends INetworkServer.ServerListener {}

    /** @deprecated Use {@link INetworkServer.ServerListenerAdapter} instead. */
    @Deprecated
    public static class ServerListenerAdapter extends INetworkServer.ServerListenerAdapter {}
}
