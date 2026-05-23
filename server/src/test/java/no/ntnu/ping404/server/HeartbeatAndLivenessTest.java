package no.ntnu.ping404.server;

import no.ntnu.ping404.network.NetworkKryoClient;
import no.ntnu.ping404.network.NetworkConfig;
import no.ntnu.ping404.network.NetworkListener;
import no.ntnu.ping404.network.packets.Ping;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Issue #26: Server - NetworkKryoServer Liveness - Timeout/heartbeat for inactive clients.
 * 
 * Requirements:
 * - Server sends Ping packet every 5s
 * - Client responds with Pong
 * - If no Pong received within 15s: mark connection stale and disconnect
 * - Cleanup GameRoom on disconnect
 */
class HeartbeatAndLivenessTest {

    private static final int HEARTBEAT_INTERVAL_MS = 200;
    private static final int HEARTBEAT_TIMEOUT_MS = 1_000;
    private static final int SETUP_DELAY_MS = 100;
    private static final int PING_COLLECTION_DELAY_MS = 1_500;
    private static final int PONG_ALIVE_DELAY_MS = 4_000;
    private static final int STALE_DETECTION_DELAY_MS = 3_000;
    private static final int STAGGER_DELAY_MS = 500;
    private static final int MULTI_CLIENT_DELAY_MS = 2_000;
    private static final int MIN_PINGS_SHORT_WINDOW = 5;
    private static final int MIN_PINGS_CLIENT_1 = 7;
    private static final int MIN_PINGS_CLIENT_2 = 4;

    private GameServer gameServer;
    private NetworkKryoClient client1;
    private NetworkKryoClient client2;
    private SessionStore sessionStore; 

    @BeforeEach
    void setUp() throws IOException {
        // Use faster timeouts for testing (200ms interval, 1000ms timeout)
        // This allows tests to run quickly while still being practical
        NetworkConfig.setHeartbeatTimeouts(HEARTBEAT_INTERVAL_MS, HEARTBEAT_TIMEOUT_MS);
        
        sessionStore = new SessionStore();
        gameServer = new GameServer(sessionStore);
        gameServer.start();
        
        // Give server time to bind
        try {
            Thread.sleep(SETUP_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        client1 = new NetworkKryoClient();
        client2 = new NetworkKryoClient();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        // Reset timeouts to defaults
        NetworkConfig.resetHeartbeatTimeouts();
        
        try {
            if (client1 != null) client1.disconnect();
        } catch (Exception ignored) {}
        try {
            if (client2 != null) client2.disconnect();
        } catch (Exception ignored) {}
        gameServer.stop();
        Thread.sleep(SETUP_DELAY_MS);
    }

    @Test
    @Tag("A1")
    void serverSendsPingToConnectedClientsEveryFiveSeconds() throws IOException, InterruptedException {
        // With fast timeouts: 200ms interval means ~5 pings per second
        // Arrange
        AtomicInteger pingsReceived = new AtomicInteger(0);
        client1.addListener(new NetworkListener.Adapter() {
            @Override
            public void onReceived(Object packet) {
                if (packet instanceof Ping) {
                    pingsReceived.incrementAndGet();
                }
            }
        });
        
        client1.connect("localhost");
        
        // Act: Wait for heartbeat pings to arrive (200ms interval)
        Thread.sleep(PING_COLLECTION_DELAY_MS);  // Should receive ~7-8 pings
        
        // Assert
        assertTrue(pingsReceived.get() >= MIN_PINGS_SHORT_WINDOW, 
            "Server should send Ping packets every 200ms; expected >= 5 pings, got " + pingsReceived.get());
    }

    @Test
    @Tag("A1")
    void pongResponseUpdatesLastActivityTimestamp() throws IOException, InterruptedException {
        // Arrange
        final AtomicBoolean receivedPing = new AtomicBoolean(false);
        client1.addListener(new NetworkListener.Adapter() {
            @Override
            public void onReceived(Object packet) {
                if (packet instanceof Ping) {
                    receivedPing.set(true);
                }
            }
        });
        client1.connect("localhost");
        Thread.sleep(SETUP_DELAY_MS);
        
        // Act: Client automatically responds to Pong, verify connection stays alive
        // With UDP (unreliable), we need enough time for multiple Ping/Pong exchanges
        // At 200ms interval over 4 seconds, we should see multiple heartbeat cycles
        Thread.sleep(PONG_ALIVE_DELAY_MS);  // Wait beyond 1000ms timeout
        
        // Assert: Connection should stay alive - we should receive Pings without disconnection
        assertTrue(receivedPing.get(), "Should receive at least one Ping");
        assertTrue(client1.isConnected(), 
            "Connection should remain active if responding to heartbeats");
    }

    @Test
    @Tag("A1")
    void connectionWithoutPongForFifteenSecondsIsMarkedStale() throws IOException, InterruptedException {
        // Arrange: Disable auto-Pong so connection becomes stale
        client1.disableAutoPong();
        client1.connect("localhost");
        
        // Act: Wait for heartbeat to detect stale connection (1000ms timeout)
        // Give extra time for stale detection to trigger reliably
        Thread.sleep(STALE_DETECTION_DELAY_MS);  // Wait well beyond 1000ms timeout
        
        // Assert: Connection should be marked as stale or disconnected
        assertFalse(client1.isConnected(), 
            "Connection should be disconnected after 1000ms+ without Pong response");
    }

    @Test
    @Tag("A1")
    void staleConnectionIsDisconnectedAutomatically() throws IOException, InterruptedException {
        // Arrange: Disable auto-Pong so connection becomes stale
        client1.disableAutoPong();
        client1.connect("localhost");
        Thread.sleep(HEARTBEAT_INTERVAL_MS);
        
        // Find the room for this client
        GameRoom gameRoom = null;
        for (GameRoom room : gameServer.getRooms().values()) {
            if (room.getConnections().containsKey(client1.getConnectionId())) {
                gameRoom = room;
                break;
            }
        }
        
        // Act: Let server timeout the stale connection
        Thread.sleep(STALE_DETECTION_DELAY_MS);  // Wait for 1000ms timeout to trigger reliably
        
        // Assert: GameRoom should be cleaned up after disconnect
        if (gameRoom != null) {
            assertTrue(gameRoom.isEmpty() || !gameRoom.getConnections().containsKey(client1.getConnectionId()),
                "Stale connection should be removed from GameRoom");
        }
        assertFalse(client1.isConnected(), 
            "Stale connection should be automatically disconnected by server");
    }

    @Test
    @Tag("A1")
    @Tag("TC5")
    void multipleClientsReceiveIndependentHeartbeats() throws IOException, InterruptedException {
        // Arrange: Track pings received by each client
        AtomicInteger pingsClient1 = new AtomicInteger(0);
        AtomicInteger pingsClient2 = new AtomicInteger(0);
        
        client1.addListener(new NetworkListener.Adapter() {
            @Override
            public void onReceived(Object packet) {
                if (packet instanceof Ping) {
                    pingsClient1.incrementAndGet();
                }
            }
        });
        
        client2.addListener(new NetworkListener.Adapter() {
            @Override
            public void onReceived(Object packet) {
                if (packet instanceof Ping) {
                    pingsClient2.incrementAndGet();
                }
            }
        });
        
        // Act
        client1.connect("localhost");
        Thread.sleep(STAGGER_DELAY_MS);  // Stagger connections
        client2.connect("localhost");
        Thread.sleep(MULTI_CLIENT_DELAY_MS);  // Wait for several heartbeat cycles
        
        // Assert: Both clients should receive pings independently
        assertTrue(pingsClient1.get() >= MIN_PINGS_CLIENT_1, 
            "Client 1 should receive >= 7 pings over 2500ms at 200ms interval; got " + pingsClient1.get());
        assertTrue(pingsClient2.get() >= MIN_PINGS_CLIENT_2, 
            "Client 2 should receive >= 4 pings over 2000ms at 200ms interval; got " + pingsClient2.get());
    }
}
