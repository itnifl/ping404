package no.ntnu.ping404.network;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;

import no.ntnu.ping404.network.packets.PacketRegistry;
import no.ntnu.ping404.network.packets.Ping;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * KryoNet-based implementation of {@link INetworkServer}.
 *
 * <p>This class wraps the KryoNet {@link Server} and provides the network abstraction
 * defined by the interface, supporting M4 (modifiability: swap network library).</p>
 *
 * @see INetworkServer
 */
public class NetworkKryoServer implements INetworkServer {

    private static final Logger logger = LoggerFactory.getLogger(NetworkKryoServer.class);

    private final Server server;
    private final Map<Integer, KryoPlayerConnection> connections;
    private final CopyOnWriteArrayList<INetworkServer.ServerListener> listeners;
    private final BlockingQueue<NetworkEvent> eventQueue;
    private volatile boolean running;
    private Thread consumerThread;

    /** Event types for the unified event queue. */
    private enum EventType { CONNECTED, DISCONNECTED, RECEIVED }

    /** 
     * Holds a network event: connection, disconnection, or received packet.
     * For CONNECTED/DISCONNECTED, packet is null. For RECEIVED, packet contains the data.
     */
    private record NetworkEvent(INetworkServer.PlayerConnection connection, EventType type, Object packet) {}
    
    public NetworkKryoServer() {
        this.server = new Server(NetworkConfig.WRITE_BUFFER_SIZE, NetworkConfig.OBJECT_BUFFER_SIZE);
        this.connections = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.eventQueue = new LinkedBlockingQueue<>();
        this.running = false;

        PacketRegistry.register(server.getKryo());
        server.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                KryoPlayerConnection playerConn = new KryoPlayerConnection(connection);
                connections.put(connection.getID(), playerConn);
                eventQueue.offer(new NetworkEvent(playerConn, EventType.CONNECTED, null));
                logger.info("Client connected: {} from {}", connection.getID(), connection.getRemoteAddressTCP());
            }

            @Override
            public void disconnected(Connection connection) {
                KryoPlayerConnection playerConn = connections.remove(connection.getID());
                if (playerConn != null) {
                    eventQueue.offer(new NetworkEvent(playerConn, EventType.DISCONNECTED, null));
                }
                logger.info("Client disconnected: {}", connection.getID());
            }

            @Override
            public void received(Connection connection, Object object) {
                // Producer: enqueue the event so the consumer thread handles dispatch,
                // keeping the Kryonet I/O thread free.
                KryoPlayerConnection playerConn = connections.get(connection.getID());
                if (playerConn != null) {
                    eventQueue.offer(new NetworkEvent(playerConn, EventType.RECEIVED, object));
                }
            }
        });
    }

    /**
     * Consumer: drains the event queue and dispatches each event to all registered listeners.
     * Runs on a dedicated thread so that slow listeners never block network I/O.
     */
    private void startConsumerThread() {
        consumerThread = new Thread(() -> {
            while (running || !eventQueue.isEmpty()) {
                try {
                    NetworkEvent event = eventQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (event == null) continue;
                    dispatchEvent(event);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "event-consumer");
        consumerThread.setDaemon(true);
        consumerThread.start();
        logger.debug("Event consumer thread started");
    }

    private void startPingThread() {
        Thread pingThread = new Thread(() -> {
            int sequence = 0;
            while (running) {
                try {
                    Thread.sleep(NetworkConfig.HEARTBEAT_INTERVAL_MS);
                    long now = System.currentTimeMillis();
                    for (KryoPlayerConnection conn : connections.values()) {
                        try {
                            // Send Ping packet via UDP (latency-sensitive heartbeat)
                            sendToUDP(conn.getId(), new Ping(sequence++));
                            // Check for stale connections based on heartbeat (Pong) responses
                            if (now - conn.getLastHeartbeatTime() > NetworkConfig.HEARTBEAT_TIMEOUT_MS) {
                                logger.warn("Connection {} is stale (last heartbeat {} ms ago), disconnecting",
                                            conn.getId(), now - conn.getLastHeartbeatTime());
                                conn.closeConnection();
                                conn.markStale();
                            }
                        } catch (Exception e) {
                            logger.debug("Ping failed for connection {}: {} - {}",
                                         conn.getId(), e.getClass().getSimpleName(), e.getMessage());
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "ping-thread");
        pingThread.setDaemon(true);
        pingThread.start();
        logger.debug("Ping thread started");
    }

    private void dispatchEvent(NetworkEvent event) {
        var runtimeExceptions = new ArrayList<RuntimeException>();
        for (INetworkServer.ServerListener listener : listeners) {
            try {
                switch (event.type()) {
                    case CONNECTED    -> listener.onClientConnected(event.connection());              // a new client joined
                    case DISCONNECTED -> listener.onClientDisconnected(event.connection());           // a client left or dropped
                    case RECEIVED     -> listener.onReceived(event.connection(), event.packet());     // a client sent us a packet
                }
            } catch (Exception e) {
                runtimeExceptions.add(createListenerException(listener, e));
            }
        }
        if (!runtimeExceptions.isEmpty()) {
            printListenerErrorCount(runtimeExceptions.size());
            runtimeExceptions.forEach(this::printRuntimeException);
        }
    }

    private RuntimeException createListenerException(INetworkServer.ServerListener listener, Exception e) {
        return new RuntimeException("Listener threw exception for " + listener.getClass().getSimpleName() + ": " + e.getMessage(), e);
    }

    private void printListenerErrorCount(int count) {
        logger.error("{} listener(s) threw exceptions:", count);
    }

    private void printRuntimeException(RuntimeException x) {
        logger.error(x.getMessage(), x);
    }

    @Override
    public void start(int tcpPort, int udpPort) throws IOException {
        server.start();
        if (udpPort > 0) {
            server.bind(tcpPort, udpPort);
        } else {
            server.bind(tcpPort);
        }
        running = true;
        startConsumerThread();
        startPingThread();
        logger.info("Server started on TCP:{} UDP:{}", tcpPort, udpPort);
    }

    @Override
    public void start() throws IOException {
        start(NetworkConfig.TCP_PORT, NetworkConfig.UDP_PORT);
    }

    @Override
    public void stop() {
        running = false;
        if (consumerThread != null) {
            consumerThread.interrupt();
            try {
                consumerThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        server.stop();
        connections.clear();
        logger.info("Server stopped");
    }

    @Override
    public void sendToTCP(int connectionId, Object packet) {
        server.sendToTCP(connectionId, packet);
    }

    @Override
    public void sendToUDP(int connectionId, Object packet) {
        server.sendToUDP(connectionId, packet);
    }

    @Override
    public void sendToAllTCP(Object packet) {
        server.sendToAllTCP(packet);
    }

    @Override
    public void sendToAllUDP(Object packet) {
        server.sendToAllUDP(packet);
    }

    @Override
    public void sendToAllExceptTCP(int excludeConnectionId, Object packet) {
        server.sendToAllExceptTCP(excludeConnectionId, packet);
    }

    @Override
    public void sendToAllExceptUDP(int excludeConnectionId, Object packet) {
        server.sendToAllExceptUDP(excludeConnectionId, packet);
    }

    @Override
    public void forEachConnection(BiConsumer<Integer, INetworkServer.PlayerConnection> action) {
        connections.forEach(action);
    }

    @Override
    public INetworkServer.PlayerConnection getConnection(int connectionId) {
        return connections.get(connectionId);
    }

    @Override
    public int getConnectionCount() {
        return connections.size();
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
        return running;
    }

    /**
     * KryoNet-based implementation of PlayerConnection.
     */
    public static class KryoPlayerConnection implements INetworkServer.PlayerConnection {
        private final Connection connection;
        private final int fixedId;
        private String playerName;
        private float x, y;
        private long lastPositionUpdateTime;
        private long lastHeartbeatTime;
        private boolean isStale;
        private String testRemoteAddress;

        public KryoPlayerConnection(Connection connection) {
            this.connection = connection;
            this.fixedId = -1;
            this.lastPositionUpdateTime = System.currentTimeMillis();
            this.lastHeartbeatTime = System.currentTimeMillis();
        }

        /** Test-only constructor: creates a KryoPlayerConnection with a fixed id and no real socket. */
        public KryoPlayerConnection(int id) {
            this.connection = null;
            this.fixedId = id;
            this.lastPositionUpdateTime = System.currentTimeMillis();
            this.lastHeartbeatTime = System.currentTimeMillis();
        }

        /** Test-only constructor: creates a KryoPlayerConnection with a fixed id and test remote address. */
        public KryoPlayerConnection(int id, String testRemoteAddress) {
            this(id);
            this.testRemoteAddress = testRemoteAddress;
        }

        @Override
        public int getId() {
            return fixedId >= 0 ? fixedId : connection.getID();
        }

        /** Returns the underlying KryoNet Connection (for internal use). */
        public Connection getKryoConnection() {
            return connection;
        }

        /** Closes the underlying connection. */
        public void closeConnection() {
            if (connection != null) {
                connection.close();
            }
        }

        @Override
        public String getRemoteAddress() {
            if (testRemoteAddress != null) {
                return testRemoteAddress;
            }
            if (connection != null && connection.getRemoteAddressTCP() != null
                && connection.getRemoteAddressTCP().getAddress() != null) {
                return connection.getRemoteAddressTCP().getAddress().getHostAddress();
            }
            return null;
        }

        @Override
        public String getPlayerName() {
            return playerName;
        }

        @Override
        public void setPlayerName(String playerName) {
            this.playerName = playerName;
        }

        @Override
        public float getX() {
            return x;
        }

        @Override
        public void setX(float x) {
            this.x = x;
        }

        @Override
        public float getY() {
            return y;
        }

        @Override
        public void setY(float y) {
            this.y = y;
        }

        @Override
        public void setPosition(float x, float y) {
            this.x = x;
            this.y = y;
            this.lastPositionUpdateTime = System.currentTimeMillis();
        }

        @Override
        public long getLastPositionUpdateTime() {
            return lastPositionUpdateTime;
        }

        @Override
        public long getLastHeartbeatTime() {
            return lastHeartbeatTime;
        }

        @Override
        public void sendToTCP(Object packet) {            
            if (connection != null && connection.isConnected()) {
                connection.sendTCP(packet);
            }
        }

        @Override
        public void sendToUDP(Object packet) {
            if (connection != null && connection.isConnected()) {
                connection.sendUDP(packet);
            }
        }

        @Override
        public void updateLastHeartbeat() {
            this.lastHeartbeatTime = System.currentTimeMillis();
        }

        @Override
        public boolean isStale() {
            return isStale;
        }

        @Override
        public void markStale() {
            this.isStale = true;
        }
    }

    // Type aliases for backward compatibility
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
