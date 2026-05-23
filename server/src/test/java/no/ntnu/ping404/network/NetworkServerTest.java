package no.ntnu.ping404.network;

import no.ntnu.ping404.network.INetworkServer;
import com.esotericsoftware.kryonet.Client;
import no.ntnu.ping404.network.packets.PacketRegistry;
import no.ntnu.ping404.network.packets.Ping;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NetworkServerTest {

    private static final int CONNECT_TIMEOUT_MS = 2_000;
    private static final int LATCH_TIMEOUT_SECONDS = 2;
    private static final int PING_SEQUENCE_A = 7;
    private static final int PING_SEQUENCE_B = 11;
    private static final String LOCALHOST = "127.0.0.1";

    @Test
    @Tag("TC7")
    @Tag("M4")
    void serverIsNotRunningBeforeStart() {
        // Facade / TC7: A freshly constructed NetworkKryoServer must report isRunning() == false
        // until start() is explicitly called.
        NetworkKryoServer server = new NetworkKryoServer();
        assertFalse(server.isRunning());
    }

    @Test
    @Tag("TC7")
    @Tag("M4")
    void serverIsRunningAfterStart() {
        // Facade / TC7: After calling start(tcpPort, udpPort), isRunning() must return true,
        // confirming the server socket is bound and ready to accept connections.
        NetworkKryoServer server = new NetworkKryoServer();
        int tcpPort = findFreeTcpPort();

        try {
            server.start(tcpPort, 0);
            assertTrue(server.isRunning());
        } catch (IOException e) {
            fail("Server should start on a free TCP port", e);
        } finally {
            server.stop();
        }
    }

    @Test
    @Tag("A2")
    @Tag("M4")
    void serverIsNotRunningAfterStop() {
        // Facade / A2: After calling stop(), isRunning() must return false and the server
        // must release its ports so they can be rebound.
        NetworkKryoServer server = new NetworkKryoServer();
        int tcpPort = findFreeTcpPort();

        try {
            server.start(tcpPort, 0);
            assertTrue(server.isRunning(), "Server should be running before stop() is called");

            server.stop();

            assertFalse(server.isRunning(), "Server should not be running after stop()");
        } catch (IOException e) {
            fail("Server should start on a free TCP port", e);
        } finally {
            server.stop();
        }
    }

    @Test
    @Tag("A1")
    @Tag("TC5")
    void stopClearsAllConnections() {
        // A1 / TC5: stop() must remove every entry from the connections map so no orphaned
        // PlayerConnection state remains after shutdown.
        int tcpPort = findFreeTcpPort();
        NetworkKryoServer server = new NetworkKryoServer();
        Client client = newTestClient();
        CountDownLatch connected = new CountDownLatch(1);

        server.addListener(new INetworkServer.ServerListenerAdapter() {
            @Override
            public void onClientConnected(INetworkServer.PlayerConnection connection) {
                connected.countDown();
            }
        });

        try {
            server.start(tcpPort, 0);
            client.start();
            client.connect(CONNECT_TIMEOUT_MS, LOCALHOST, tcpPort);

            assertTrue(connected.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS), "Client should connect before shutdown");
            assertEquals(1, server.getConnectionCount(), "Server should track the connected client before stop()");

            server.stop();

            assertEquals(0, server.getConnectionCount(), "stop() should clear all tracked connections");
        } catch (IOException | InterruptedException e) {
            fail("Test failed while starting server/client or waiting for connection", e);
        } finally {
            client.stop();
            server.stop();
        }
    }

    @Test
    @Tag("FR1.2")
    void connectionCountIsZeroBeforeAnyClientConnects() {
        // FR1.2: Before any client connects, getConnectionCount() must return 0.
        NetworkKryoServer server = new NetworkKryoServer();
        assertEquals(0, server.getConnectionCount());
    }

    @Test
    @Tag("Observer")
    void listenerReceivesOnClientConnectedCallback() {
        // Observer: When a client connects, every registered ServerListener must receive
        // an onClientConnected callback with the corresponding PlayerConnection.
        int tcpPort = findFreeTcpPort();
        NetworkKryoServer server = new NetworkKryoServer();
        Client client = newTestClient();
        CountDownLatch connected = new CountDownLatch(1);
        AtomicInteger connectionId = new AtomicInteger(-1);

        server.addListener(new INetworkServer.ServerListenerAdapter() {
            @Override
            public void onClientConnected(INetworkServer.PlayerConnection connection) {
                connectionId.set(connection.getId());
                connected.countDown();
            }
        });

        try {
            server.start(tcpPort, 0);
            client.start();
            client.connect(CONNECT_TIMEOUT_MS, LOCALHOST, tcpPort);

            assertTrue(connected.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS), "Listener should receive onClientConnected");
            assertTrue(connectionId.get() > 0, "Callback should expose the server-side connection id");
        } catch (IOException | InterruptedException e) {
            fail("Test failed while starting server/client or waiting for callback", e);
        } finally {
            client.stop();
            server.stop();
        }
    }

    @Test
    @Tag("Observer")
    @Tag("A1")
    void listenerReceivesOnClientDisconnectedCallback() {
        // Observer / A1: When a client disconnects, every registered ServerListener must
        // receive an onClientDisconnected callback so the GameServer can clean up the room
        // and notify the opponent.
        int tcpPort = findFreeTcpPort();
        NetworkKryoServer server = new NetworkKryoServer();
        Client client = newTestClient();
        CountDownLatch connected = new CountDownLatch(1);
        CountDownLatch disconnected = new CountDownLatch(1);
        AtomicInteger disconnectedId = new AtomicInteger(-1);

        server.addListener(new INetworkServer.ServerListenerAdapter() {
            @Override
            public void onClientConnected(INetworkServer.PlayerConnection connection) {
                connected.countDown();
            }

            @Override
            public void onClientDisconnected(INetworkServer.PlayerConnection connection) {
                disconnectedId.set(connection.getId());
                disconnected.countDown();
            }
        });

        try {
            server.start(tcpPort, 0);
            client.start();
            client.connect(CONNECT_TIMEOUT_MS, LOCALHOST, tcpPort);

            assertTrue(connected.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS), "Client should connect before disconnecting");

            client.stop();

            assertTrue(disconnected.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS), "Listener should receive onClientDisconnected");
            assertTrue(disconnectedId.get() > 0, "Disconnect callback should expose the server-side connection id");
        } catch (IOException | InterruptedException e) {
            fail("Test failed while starting server/client or waiting for disconnect callback", e);
        } finally {
            client.stop();
            server.stop();
        }
    }

    @Test
    @Tag("Observer")
    @Tag("Command")
    void listenerReceivesOnReceivedCallback() {
        // Observer / Command: When a packet arrives from a client, every registered
        // ServerListener must receive an onReceived callback with the PlayerConnection
        // and the deserialized packet object.
        int tcpPort = findFreeTcpPort();
        NetworkKryoServer server = new NetworkKryoServer();
        Client client = newTestClient();
        CountDownLatch connected = new CountDownLatch(1);
        CountDownLatch received = new CountDownLatch(1);
        AtomicInteger senderId = new AtomicInteger(-1);
        AtomicInteger sequence = new AtomicInteger(-1);

        server.addListener(new INetworkServer.ServerListenerAdapter() {
            @Override
            public void onClientConnected(INetworkServer.PlayerConnection connection) {
                connected.countDown();
            }

            @Override
            public void onReceived(INetworkServer.PlayerConnection connection, Object packet) {
                if (packet instanceof Ping ping) {
                    senderId.set(connection.getId());
                    sequence.set(ping.sequence);
                    received.countDown();
                }
            }
        });

        try {
            server.start(tcpPort, 0);
            client.start();
            client.connect(CONNECT_TIMEOUT_MS, LOCALHOST, tcpPort);

            assertTrue(connected.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS), "Client should connect before sending a packet");

            client.sendTCP(new Ping(PING_SEQUENCE_A));

            assertTrue(received.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS), "Listener should receive the queued packet callback");
            assertTrue(senderId.get() > 0, "Received callback should expose the sender connection id");
            assertEquals(PING_SEQUENCE_A, sequence.get(), "Received callback should expose the deserialized packet");
        } catch (IOException | InterruptedException e) {
            fail("Test failed while starting server/client or waiting for packet callback", e);
        } finally {
            client.stop();
            server.stop();
        }
    }

    @Test
    @Tag("Observer")
    void removedListenerDoesNotReceiveCallbacks() {
        // Observer: After removeListener() is called, the removed ServerListener must no
        // longer receive any callbacks (onClientConnected, onReceived, etc.).
        int tcpPort = findFreeTcpPort();
        NetworkKryoServer server = new NetworkKryoServer();
        Client client = newTestClient();

        AtomicInteger removedConnectCalls = new AtomicInteger(0);
        AtomicInteger removedReceiveCalls = new AtomicInteger(0);
        AtomicInteger activeConnectCalls = new AtomicInteger(0);
        AtomicInteger activeReceiveCalls = new AtomicInteger(0);
        CountDownLatch activeConnected = new CountDownLatch(1);
        CountDownLatch activeReceived = new CountDownLatch(1);

        INetworkServer.ServerListener removedListener = new INetworkServer.ServerListenerAdapter() {
            @Override
            public void onClientConnected(INetworkServer.PlayerConnection connection) {
                removedConnectCalls.incrementAndGet();
            }

            @Override
            public void onReceived(INetworkServer.PlayerConnection connection, Object packet) {
                removedReceiveCalls.incrementAndGet();
            }
        };

        INetworkServer.ServerListener activeListener = new INetworkServer.ServerListenerAdapter() {
            @Override
            public void onClientConnected(INetworkServer.PlayerConnection connection) {
                activeConnectCalls.incrementAndGet();
                activeConnected.countDown();
            }

            @Override
            public void onReceived(INetworkServer.PlayerConnection connection, Object packet) {
                if (packet instanceof Ping) {
                    activeReceiveCalls.incrementAndGet();
                    activeReceived.countDown();
                }
            }
        };

        server.addListener(removedListener);
        server.addListener(activeListener);
        server.removeListener(removedListener);

        try {
            server.start(tcpPort, 0);
            client.start();
            client.connect(CONNECT_TIMEOUT_MS, LOCALHOST, tcpPort);

            assertTrue(activeConnected.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS), "Active listener should be notified on client connection");

            client.sendTCP(new Ping(PING_SEQUENCE_B));

            assertTrue(activeReceived.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS), "Active listener should receive packet callbacks");
            assertEquals(1, activeConnectCalls.get(), "Active listener should receive exactly one connect callback");
            assertEquals(1, activeReceiveCalls.get(), "Active listener should receive exactly one receive callback");
            assertEquals(0, removedConnectCalls.get(), "Removed listener must not receive connect callbacks");
            assertEquals(0, removedReceiveCalls.get(), "Removed listener must not receive receive callbacks");
        } catch (IOException | InterruptedException e) {
            fail("Test failed while starting server/client or waiting for callback", e);
        } finally {
            client.stop();
            server.stop();
        }
    }

    @Test
    @Tag("TC7")
    @Tag("A1")
    void startThrowsIOExceptionWhenPortIsAlreadyBound() {
        // TC7 / Availability: If the requested TCP/UDP port is already in use, start()
        // must throw an IOException rather than silently failing or corrupting state.
        int occupiedPort = findFreeTcpPort();

        try (ServerSocket occupiedSocket = new ServerSocket(occupiedPort)) {
            NetworkKryoServer server = new NetworkKryoServer();

            assertThrows(IOException.class, () -> server.start(occupiedPort, 0));
            assertFalse(server.isRunning());
        } catch (IOException e) {
            fail("Test setup failed while occupying TCP port", e);
        }
    }

    private int findFreeTcpPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Unable to acquire free TCP port for test", e);
        }
    }

    private Client newTestClient() {
        Client client = new Client();
        PacketRegistry.register(client.getKryo());
        return client;
    }
}
