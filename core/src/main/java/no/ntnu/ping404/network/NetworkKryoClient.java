package no.ntnu.ping404.network;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import no.ntnu.ping404.network.packets.PacketRegistry;
import no.ntnu.ping404.network.packets.Ping;
import no.ntnu.ping404.network.packets.Pong;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * KryoNet-based implementation of {@link INetworkClient}.
 *
 * <p>This class wraps the KryoNet {@link Client} and provides the network abstraction
 * defined by the interface, supporting M4 (modifiability: swap network library).</p>
 *
 * @see INetworkClient
 */
public class NetworkKryoClient implements INetworkClient {

    private static final Logger logger = LoggerFactory.getLogger(NetworkKryoClient.class);

    private final Client client;
    private final CopyOnWriteArrayList<NetworkListener> listeners;
    private final BlockingQueue<ClientEvent> eventQueue;
    private volatile boolean running;
    private volatile boolean connected;
    private volatile boolean autoPongEnabled = true;
    private Thread consumerThread;
    private String playerName;
    private long serverLastHeartbeatTime;

    /** Event types for the unified event queue. */
    private enum EventType { CONNECTED, DISCONNECTED, RECEIVED }

    /**
     * Holds a client-side network event.
     * For CONNECTED/DISCONNECTED, packet is null. For RECEIVED, packet contains the data.
     */
    private record ClientEvent(EventType type, Object packet) {}

    public NetworkKryoClient() {
        this.client = new Client(NetworkConfig.WRITE_BUFFER_SIZE, NetworkConfig.OBJECT_BUFFER_SIZE);
        this.listeners = new CopyOnWriteArrayList<>();
        this.eventQueue = new LinkedBlockingQueue<>();
        this.running = false;
        this.connected = false;
        this.serverLastHeartbeatTime = System.currentTimeMillis();

        PacketRegistry.register(client.getKryo());
        client.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                NetworkKryoClient.this.connected = true;
                NetworkKryoClient.this.serverLastHeartbeatTime = System.currentTimeMillis();
                eventQueue.offer(new ClientEvent(EventType.CONNECTED, null));
                logger.info("Connected to server");
            }

            @Override
            public void disconnected(Connection connection) {
                NetworkKryoClient.this.connected = false;
                eventQueue.offer(new ClientEvent(EventType.DISCONNECTED, null));
                logger.info("Disconnected from server");
            }

            @Override
            public void received(Connection connection, Object object) {
                // Producer: enqueue the event so the consumer thread handles dispatch,
                // keeping the Kryonet I/O thread free.
                eventQueue.offer(new ClientEvent(EventType.RECEIVED, object));
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
                    ClientEvent event = eventQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (event == null) continue;
                    dispatchEvent(event);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "client-event-consumer");
        consumerThread.setDaemon(true);
        consumerThread.start();
        logger.debug("Client event consumer thread started");
    }

    private void dispatchEvent(ClientEvent event) {
        var runtimeExceptions = new ArrayList<RuntimeException>();
        for (NetworkListener listener : listeners) {
            try {
                switch (event.type()) {
                    case CONNECTED    -> listener.onConnected();              // we just joined the server
                    case DISCONNECTED -> listener.onDisconnected();           // we lost the connection
                    case RECEIVED     -> {
                        if (event.packet() instanceof Ping ping) {
                            this.serverLastHeartbeatTime = System.currentTimeMillis();
                            if (autoPongEnabled) {
                                sendUDP(new Pong(ping));
                            }
                        }
                        listener.onReceived(event.packet());
                    }
                }
            } catch (Exception e) {
                runtimeExceptions.add(new RuntimeException(
                    "Listener threw exception for " + listener.getClass().getSimpleName() + ": " + e.getMessage(), e));
            }
        }
        if (!runtimeExceptions.isEmpty()) {
            logger.error("{} listener(s) threw exceptions:", runtimeExceptions.size());
            runtimeExceptions.forEach(x -> logger.error(x.getMessage(), x));
        }
    }

    public void connect(String host, int tcpPort, int udpPort) throws IOException {
        client.start();
        running = true;
        startConsumerThread();
        startStaleDetectionThread();
        if (udpPort > 0) {
            client.connect(NetworkConfig.CONNECTION_TIMEOUT, host, tcpPort, udpPort);
        } else {
            client.connect(NetworkConfig.CONNECTION_TIMEOUT, host, tcpPort);
        }
    }

    private void startStaleDetectionThread() {
        Thread staleDetectionThread = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(NetworkConfig.HEARTBEAT_INTERVAL_MS);
                    if (connected && System.currentTimeMillis() - serverLastHeartbeatTime > NetworkConfig.HEARTBEAT_TIMEOUT_MS) {
                        logger.warn("No heartbeat received from server for {} ms, disconnecting", 
                                    System.currentTimeMillis() - serverLastHeartbeatTime);
                        disconnect();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "client-stale-detection");
        staleDetectionThread.setDaemon(true);
        staleDetectionThread.start();
        logger.debug("Client stale detection thread started");
    }

    public void connect() throws IOException {
        connect(NetworkConfig.DEFAULT_HOST, NetworkConfig.TCP_PORT, NetworkConfig.UDP_PORT);
    }

    public void connect(String host) throws IOException {
        connect(host, NetworkConfig.TCP_PORT, NetworkConfig.UDP_PORT);
    }

    public void disconnect() {
        running = false;
        if (consumerThread != null) {
            consumerThread.interrupt();
            try {
                consumerThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        client.close();
        connected = false;
        logger.info("Client disconnected");
    }

    @Override
    public void sendTCP(Object packet) {
        if (connected) {
            client.sendTCP(packet);
        }
    }

    @Override
    public void sendUDP(Object packet) {
        if (connected) {
            client.sendUDP(packet);
        }
    }

    @Override
    public void addListener(NetworkListener listener) {
        listeners.add(listener);
    }

    /**
     * Disable automatic Pong responses to Ping packets (for testing stale detection).
     */
    public void disableAutoPong() {
        this.autoPongEnabled = false;
    }

    /**
     * Re-enable automatic Pong responses (for testing).
     */
    public void enableAutoPong() {
        this.autoPongEnabled = true;
    }

    public void removeListener(NetworkListener listener) {
        listeners.remove(listener);
    }

    public boolean isConnected() {
        return connected;
    }

    public int getConnectionId() {
        return client.getID();
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public void dispose() {
        disconnect();
        client.stop();
    }
}
