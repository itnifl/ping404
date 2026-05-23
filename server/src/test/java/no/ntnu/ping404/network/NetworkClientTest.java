package no.ntnu.ping404.network;

import no.ntnu.ping404.network.INetworkServer;
import no.ntnu.ping404.network.packets.LoginResponse;
import no.ntnu.ping404.network.packets.Ping;
import no.ntnu.ping404.network.packets.PlayerPosition;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


import static org.junit.jupiter.api.Assertions.*;

class NetworkClientTest {

    private static final int PLAYER_ID = 1;
    private static final float POSITION_X = 10f;
    private static final float POSITION_Y = 20f;
    private static final int PING_SEQUENCE = 1;
    private static final String PLAYER_NAME = "Alice";
    private static final int CONNECT_TIMEOUT_MS = 2_000;
    private static final int LATCH_TIMEOUT_SECONDS = 2;
    private static final String LOCALHOST = "127.0.0.1";

    private static class RecordingNetworkClient extends NetworkKryoClient {
        int sendTcpCalls;
        int sendUdpCalls;

        @Override
        public void sendTCP(Object packet) {
            sendTcpCalls++;
        }

        @Override
        public void sendUDP(Object packet) {
            sendUdpCalls++;
        }
    }

    @Test
    @Tag("TC7")
    @Tag("M4")
    void clientIsNotConnectedBeforeConnect() {
        // Facade / TC7: A freshly constructed NetworkClient must report isConnected() == false
        // until connect() is explicitly called.
        NetworkKryoClient client = new NetworkKryoClient();
        assertFalse(client.isConnected());
    }

    @Test
    @Tag("TC7")
    @Tag("M4")
    void clientIsConnectedAfterConnect() throws IOException, InterruptedException {
        // Facade / TC7: After calling connect(host, tcpPort, udpPort) against a live server,
        // isConnected() must return true.
        // KryoNet fires the connected() callback on its network thread after connect()
        // returns, so we use a latch to wait for the async flag to be set.
        int port = findFreeTcpPort();
        NetworkKryoServer server = new NetworkKryoServer();
        NetworkKryoClient client = new NetworkKryoClient();
        CountDownLatch connectedLatch = new CountDownLatch(1);
        client.addListener(new NetworkListener.Adapter() {
            @Override
            public void onConnected() { connectedLatch.countDown(); }
        });
        try {
            server.start(port, 0);
            client.connect(LOCALHOST, port, 0);
            assertTrue(connectedLatch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS), "onConnected callback not fired within 2s");
            assertTrue(client.isConnected());
        } finally {
            client.disconnect();
            server.stop();
        }
    }

    @Test
    @Tag("A2")
    @Tag("M4")
    void clientIsNotConnectedAfterDisconnect() {
        NetworkKryoClient client = new NetworkKryoClient();
        client.disconnect();
        assertFalse(client.isConnected(), "isConnected() must return false after disconnect()");
    }

    @Test
    @Tag("TC7")
    @Tag("A1")
    void connectThrowsIOExceptionWhenServerIsUnreachable() {
        // TC7 / Availability: If no server is listening on the given host/port,
        // connect() must throw an IOException rather than hanging or silently failing.
        int emptyPort = findFreeTcpPort(); // port found but never bound - nothing listening
        NetworkKryoClient client = new NetworkKryoClient();
        assertThrows(IOException.class, () -> client.connect(LOCALHOST, emptyPort, 0));
    }

    @Test
    @Tag("Observer")
    void listenerReceivesOnConnectedCallback() throws IOException, InterruptedException {
        int port = findFreeTcpPort();
        NetworkKryoServer server = new NetworkKryoServer();
        NetworkKryoClient client = new NetworkKryoClient();
        AtomicInteger connectedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        client.addListener(new NetworkListener.Adapter() {
            @Override
            public void onConnected() {
                connectedCount.incrementAndGet();
                latch.countDown();
            }
        });
        try {
            server.start(port, 0);
            client.connect(LOCALHOST, port, 0);
            assertTrue(latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS), "onConnected callback not fired within 2s");
            assertEquals(1, connectedCount.get(), "onConnected must fire exactly once");
        } finally {
            client.disconnect();
            server.stop();
        }
    }

    @Test
    @Tag("Observer")
    @Tag("A1")
    void listenerReceivesOnDisconnectedCallback() throws IOException, InterruptedException {
        int port = findFreeTcpPort();
        NetworkKryoServer server = new NetworkKryoServer();
        NetworkKryoClient client = new NetworkKryoClient();
        CountDownLatch connectedLatch = new CountDownLatch(1);
        CountDownLatch disconnectedLatch = new CountDownLatch(1);
        AtomicInteger disconnectedCount = new AtomicInteger(0);
        client.addListener(new NetworkListener.Adapter() {
            @Override
            public void onConnected() { connectedLatch.countDown(); }
            @Override
            public void onDisconnected() {
                disconnectedCount.incrementAndGet();
                disconnectedLatch.countDown();
            }
        });
        try {
            server.start(port, 0);
            client.connect(LOCALHOST, port, 0);
            assertTrue(connectedLatch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS), "onConnected not fired");
            // Trigger a remote disconnect so the client listener receives DISCONNECTED from KryoNet.
            server.stop();
            assertTrue(disconnectedLatch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS), "onDisconnected callback not fired within 2s");
            assertEquals(1, disconnectedCount.get(), "onDisconnected must fire exactly once");
        } finally {
            client.disconnect();
        }
    }

    @Test
    @Tag("Observer")
    @Tag("Command")
    void listenerReceivesOnReceivedCallback() throws IOException, InterruptedException {
        int port = findFreeTcpPort();
        NetworkKryoServer server = new NetworkKryoServer();
        NetworkKryoClient client = new NetworkKryoClient();
        CountDownLatch receivedLatch = new CountDownLatch(1);
        AtomicReference<Object> receivedPacket = new AtomicReference<>();
        client.addListener(new NetworkListener.Adapter() {
            @Override
            public void onReceived(Object packet) {
                if (packet instanceof LoginResponse) {
                    receivedPacket.set(packet);
                    receivedLatch.countDown();
                }
            }
        });
        server.addListener(new INetworkServer.ServerListenerAdapter() {
            @Override
            public void onClientConnected(INetworkServer.PlayerConnection connection) {
                server.sendToTCP(connection.getId(), new LoginResponse());
            }
        });
        try {
            server.start(port, 0);
            client.connect(LOCALHOST, port, 0);
            assertTrue(receivedLatch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS), "onReceived callback not fired within 2s");
            assertInstanceOf(LoginResponse.class, receivedPacket.get());
        } finally {
            client.disconnect();
            server.stop();
        }
    }

    @Test
    @Tag("Observer")
    void removedListenerDoesNotReceiveCallbacks() throws IOException, InterruptedException {
        int port = findFreeTcpPort();
        NetworkKryoServer server = new NetworkKryoServer();
        NetworkKryoClient client = new NetworkKryoClient();
        AtomicInteger connectedCount = new AtomicInteger(0);
        NetworkListener removedListener = new NetworkListener.Adapter() {
            @Override
            public void onConnected() { connectedCount.incrementAndGet(); }
        };
        CountDownLatch sentinelLatch = new CountDownLatch(1);
        client.addListener(new NetworkListener.Adapter() {
            @Override
            public void onConnected() { sentinelLatch.countDown(); }
        });
        client.addListener(removedListener);
        client.removeListener(removedListener);
        try {
            server.start(port, 0);
            client.connect(LOCALHOST, port, 0);
            assertTrue(sentinelLatch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS), "sentinel onConnected not fired");
            assertEquals(0, connectedCount.get(), "Removed listener must not receive onConnected");
        } finally {
            client.disconnect();
            server.stop();
        }
    }

    @Test
    @Tag("M4")
    void sendTCPDropsPacketSilentlyWhenNotConnected() {
        // Facade: Calling sendTCP() before connect() must not throw - it should silently
        // drop the packet, since the client has no active connection to send on.
        NetworkKryoClient client = new NetworkKryoClient();
        assertDoesNotThrow(() -> client.sendTCP(new Ping(PING_SEQUENCE)));
    }

    @Test
    @Tag("M4")
    void sendUDPDropsPacketSilentlyWhenNotConnected() {
        // Facade: Calling sendUDP() before connect() must not throw - it should silently
        // drop the packet, since the client has no active connection to send on.
        NetworkKryoClient client = new NetworkKryoClient();
        assertDoesNotThrow(() -> client.sendUDP(new PlayerPosition(PLAYER_ID, POSITION_X, POSITION_Y)));
    }

    @Test
    @Tag("FR4.5")
    void playerNameRoundTrip() {
        // FR4.5: setPlayerName() and getPlayerName() must store and return the same value
        // so the game can associate a human-readable name with a connection.
        NetworkKryoClient client = new NetworkKryoClient();
        client.setPlayerName(PLAYER_NAME);
        assertEquals(PLAYER_NAME, client.getPlayerName());
    }

    private int findFreeTcpPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Unable to acquire free TCP port for test", e);
        }
    }
}
