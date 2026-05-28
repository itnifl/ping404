package no.ntnu.kryonet.internal;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import no.ntnu.kryonet.core.INetworkClient;
import no.ntnu.kryonet.core.NetworkConfig;
import no.ntnu.kryonet.observer.NetworkListener;
import no.ntnu.kryonet.packets.Ping;
import no.ntnu.kryonet.packets.Pong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * KryoNet-backed implementation of {@link INetworkClient}.
 *
 * <p>Construct via {@link no.ntnu.kryonet.builder.NetworkClientBuilder}.</p>
 */
public class NetworkKryoClient implements INetworkClient {

    private static final Logger logger = LoggerFactory.getLogger(NetworkKryoClient.class);

    private final Client client;
    private final CopyOnWriteArrayList<NetworkListener> listeners = new CopyOnWriteArrayList<>();
    private final BlockingQueue<ClientEvent> eventQueue = new LinkedBlockingQueue<>();
    private volatile boolean running;
    private volatile boolean connected;
    private volatile boolean autoPongEnabled = true;
    private volatile long serverLastHeartbeatTime;
    private Thread consumerThread;
    private Thread staleDetectionThread;
    private String playerName;

    private enum EventType { CONNECTED, DISCONNECTED, RECEIVED }
    private record ClientEvent(EventType type, Object packet) {}

    public NetworkKryoClient(Consumer<Kryo> registrationCallback) {
        this.client = new Client(NetworkConfig.WRITE_BUFFER_SIZE, NetworkConfig.OBJECT_BUFFER_SIZE);
        this.serverLastHeartbeatTime = System.currentTimeMillis();
        registrationCallback.accept(client.getKryo());
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
                eventQueue.offer(new ClientEvent(EventType.RECEIVED, object));
            }
        });
    }

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
        }, "kf-client-event-consumer");
        consumerThread.setDaemon(true);
        consumerThread.start();
    }

    private void startStaleDetectionThread() {
        if (staleDetectionThread != null && staleDetectionThread.isAlive()) {
            return;
        }
        staleDetectionThread = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(NetworkConfig.HEARTBEAT_INTERVAL_MS);
                    if (connected && System.currentTimeMillis() - serverLastHeartbeatTime > NetworkConfig.HEARTBEAT_TIMEOUT_MS) {
                        logger.warn("No heartbeat from server for {} ms, disconnecting",
                                System.currentTimeMillis() - serverLastHeartbeatTime);
                        disconnect();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "kf-client-stale-detection");
        staleDetectionThread.setDaemon(true);
        staleDetectionThread.start();
    }

    private void dispatchEvent(ClientEvent event) {
        List<RuntimeException> errors = new ArrayList<>();
        for (NetworkListener listener : listeners) {
            try {
                switch (event.type()) {
                    case CONNECTED    -> listener.onConnected();
                    case DISCONNECTED -> listener.onDisconnected();
                    case RECEIVED     -> {
                        if (event.packet() instanceof Ping ping) {
                            serverLastHeartbeatTime = System.currentTimeMillis();
                            if (autoPongEnabled) sendUDP(new Pong(ping));
                        }
                        listener.onReceived(event.packet());
                    }
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
    public void connect(String host, int tcpPort, int udpPort) throws IOException {
        if (running) {
            throw new IllegalStateException("Client already running");
        }
        client.start();
        running = true;
        startConsumerThread();
        startStaleDetectionThread();
        try {
            if (udpPort > 0) {
                client.connect(NetworkConfig.CONNECTION_TIMEOUT_MS, host, tcpPort, udpPort);
            } else {
                client.connect(NetworkConfig.CONNECTION_TIMEOUT_MS, host, tcpPort);
            }
        } catch (IOException e) {
            running = false;
            connected = false;
            stopBackgroundThreads();
            client.stop();
            throw e;
        }
    }

    @Override
    public void connect() throws IOException {
        connect(NetworkConfig.DEFAULT_HOST, NetworkConfig.DEFAULT_TCP_PORT, NetworkConfig.DEFAULT_UDP_PORT);
    }

    @Override
    public void connect(String host) throws IOException {
        connect(host, NetworkConfig.DEFAULT_TCP_PORT, NetworkConfig.DEFAULT_UDP_PORT);
    }

    @Override
    public void disconnect() {
        client.close();
        running = false;
        connected = false;
        stopStaleDetectionThread();
        waitForConsumerThreadDrain();
        logger.info("Client disconnected");
    }

    @Override public void sendTCP(Object packet) { if (connected) client.sendTCP(packet); }
    @Override public void sendUDP(Object packet) { if (connected) client.sendUDP(packet); }

    @Override public void addListener(NetworkListener l)    { listeners.add(l); }
    @Override public void removeListener(NetworkListener l) { listeners.remove(l); }

    @Override public boolean isConnected()                  { return connected; }
    @Override public int     getConnectionId()              { return client.getID(); }
    @Override public String  getPlayerName()                { return playerName; }
    @Override public void    setPlayerName(String name)     { this.playerName = name; }

    @Override public void disableAutoPong()                 { this.autoPongEnabled = false; }
    @Override public void enableAutoPong()                  { this.autoPongEnabled = true; }

    @Override
    public void dispose() {
        disconnect();
    }

    private void stopBackgroundThreads() {
        stopStaleDetectionThread();
        if (consumerThread != null) {
            consumerThread.interrupt();
            try {
                consumerThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            consumerThread = null;
        }
    }

    private void stopStaleDetectionThread() {
        if (staleDetectionThread != null) {
            staleDetectionThread.interrupt();
            if (Thread.currentThread() != staleDetectionThread) {
                try {
                    staleDetectionThread.join(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            staleDetectionThread = null;
        }
    }

    private void waitForConsumerThreadDrain() {
        if (consumerThread == null) {
            return;
        }
        try {
            consumerThread.join(1000);
            if (consumerThread.isAlive()) {
                consumerThread.interrupt();
                consumerThread.join(200);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            consumerThread = null;
        }
    }
}
