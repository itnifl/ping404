package no.creekcode.kryonet.internal;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import no.creekcode.kryonet.core.INetworkServer;
import no.creekcode.kryonet.core.NetworkConfig;
import no.creekcode.kryonet.packets.Ping;
import no.creekcode.kryonet.packets.Pong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * KryoNet-backed implementation of {@link INetworkServer}.
 *
 * <p>Construct via {@link no.creekcode.kryonet.builder.NetworkServerBuilder}; the builder
 * injects the Kryo registration callback so both framework and user packets are
 * registered before any connections arrive.</p>
 */
public class NetworkKryoServer implements INetworkServer {

    private static final Logger logger = LoggerFactory.getLogger(NetworkKryoServer.class);

    private final Server server;
    private final Map<Integer, KryoPlayerConnection> connections = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<ServerListener> listeners = new CopyOnWriteArrayList<>();
    private final BlockingQueue<NetworkEvent> eventQueue = new LinkedBlockingQueue<>();
    private volatile boolean running;
    private Thread consumerThread;
    private Thread pingThread;

    private enum EventType { CONNECTED, DISCONNECTED, RECEIVED }

    private record NetworkEvent(PlayerConnection connection, EventType type, Object packet) {}

    /**
     * @param registrationCallback applied to the server's Kryo instance during construction;
     *                              use this to register all packet classes before connections arrive.
     */
    public NetworkKryoServer(Consumer<Kryo> registrationCallback) {
        this.server = new Server(NetworkConfig.WRITE_BUFFER_SIZE, NetworkConfig.OBJECT_BUFFER_SIZE);
        registrationCallback.accept(server.getKryo());
        server.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                KryoPlayerConnection conn = new KryoPlayerConnection(connection);
                connections.put(connection.getID(), conn);
                eventQueue.offer(new NetworkEvent(conn, EventType.CONNECTED, null));
                logger.info("Client connected: {} from {}", connection.getID(), connection.getRemoteAddressTCP());
            }

            @Override
            public void disconnected(Connection connection) {
                KryoPlayerConnection conn = connections.remove(connection.getID());
                if (conn != null) eventQueue.offer(new NetworkEvent(conn, EventType.DISCONNECTED, null));
                logger.info("Client disconnected: {}", connection.getID());
            }

            @Override
            public void received(Connection connection, Object object) {
                KryoPlayerConnection conn = connections.get(connection.getID());
                if (conn != null) {
                    if (object instanceof Pong) {
                        conn.updateLastHeartbeat();
                    }
                    eventQueue.offer(new NetworkEvent(conn, EventType.RECEIVED, object));
                }
            }
        });
    }

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
        }, "kf-event-consumer");
        consumerThread.setDaemon(true);
        consumerThread.start();
    }

    private void startPingThread() {
        if (pingThread != null && pingThread.isAlive()) {
            return;
        }
        pingThread = new Thread(() -> {
            int sequence = 0;
            while (running) {
                try {
                    Thread.sleep(NetworkConfig.HEARTBEAT_INTERVAL_MS);
                    long now = System.currentTimeMillis();
                    for (KryoPlayerConnection conn : connections.values()) {
                        try {
                            sendToUDP(conn.getId(), new Ping(sequence++));
                            if (now - conn.getLastHeartbeatTime() > NetworkConfig.HEARTBEAT_TIMEOUT_MS) {
                                logger.warn("Connection {} stale ({} ms), disconnecting",
                                        conn.getId(), now - conn.getLastHeartbeatTime());
                                conn.closeConnection();
                                conn.markStale();
                            }
                        } catch (Exception e) {
                            logger.debug("Ping failed for {}: {}", conn.getId(), e.getMessage());
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "kf-ping-thread");
        pingThread.setDaemon(true);
        pingThread.start();
    }

    private void dispatchEvent(NetworkEvent event) {
        List<RuntimeException> errors = new ArrayList<>();
        for (ServerListener listener : listeners) {
            try {
                switch (event.type()) {
                    case CONNECTED    -> listener.onClientConnected(event.connection());
                    case DISCONNECTED -> listener.onClientDisconnected(event.connection());
                    case RECEIVED     -> listener.onReceived(event.connection(), event.packet());
                }
            } catch (Exception e) {
                errors.add(new RuntimeException(
                        "Listener " + listener.getClass().getSimpleName() + ": " + e.getMessage(), e));
            }
        }
        if (!errors.isEmpty()) {
            logger.error("{} listener(s) threw exceptions:", errors.size());
            errors.forEach(x -> logger.error(x.getMessage(), x));
        }
    }

    @Override
    public void start(int tcpPort, int udpPort) throws IOException {
        if (running) {
            throw new IllegalStateException("Server already running");
        }
        server.start();
        try {
            if (udpPort > 0) {
                server.bind(tcpPort, udpPort);
            } else {
                server.bind(tcpPort);
            }
        } catch (IOException e) {
            server.stop();
            running = false;
            throw e;
        }
        running = true;
        startConsumerThread();
        startPingThread();
        logger.info("Server started on TCP:{} UDP:{}", tcpPort, udpPort);
    }

    @Override
    public void start() throws IOException {
        start(NetworkConfig.DEFAULT_TCP_PORT, NetworkConfig.DEFAULT_UDP_PORT);
    }

    @Override
    public void stop() {
        running = false;
        if (pingThread != null) {
            pingThread.interrupt();
            try { pingThread.join(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            pingThread = null;
        }
        if (consumerThread != null) {
            consumerThread.interrupt();
            try { consumerThread.join(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            consumerThread = null;
        }
        server.stop();
        connections.clear();
        logger.info("Server stopped");
    }

    @Override public void sendToTCP(int id, Object p)             { server.sendToTCP(id, p); }
    @Override public void sendToUDP(int id, Object p)             { server.sendToUDP(id, p); }
    @Override public void sendToAllTCP(Object p)                  { server.sendToAllTCP(p); }
    @Override public void sendToAllUDP(Object p)                  { server.sendToAllUDP(p); }
    @Override public void sendToAllExceptTCP(int excl, Object p)  { server.sendToAllExceptTCP(excl, p); }
    @Override public void sendToAllExceptUDP(int excl, Object p)  { server.sendToAllExceptUDP(excl, p); }

    @Override public void forEachConnection(BiConsumer<Integer, PlayerConnection> action) { connections.forEach(action); }
    @Override public PlayerConnection getConnection(int id)       { return connections.get(id); }
    @Override public int getConnectionCount()                     { return connections.size(); }

    @Override public void addListener(ServerListener l)           { listeners.add(l); }
    @Override public void removeListener(ServerListener l)        { listeners.remove(l); }
    @Override public boolean isRunning()                          { return running; }
}
