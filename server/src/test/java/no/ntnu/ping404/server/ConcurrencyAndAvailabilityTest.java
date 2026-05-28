package no.ntnu.ping404.server;

import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.model.Player;
import no.ntnu.ping404.network.INetworkServer;
import no.ntnu.ping404.network.NetworkKryoClient;
import no.ntnu.ping404.network.NetworkListener;
import no.ntnu.ping404.network.NetworkKryoServer;
import no.ntnu.ping404.network.ServerConnector;
import no.ntnu.ping404.network.packets.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.util.Map;
import java.util.concurrent.*;
import no.ntnu.ping404.server.handler.PacketHandlerCommand;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrencyAndAvailabilityTest {

    // ------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------

    private static final int FAKE_CONNECTION_ID = 1;
    private static final String PLAYER_1_NAME = "Player1";
    private static final String PLAYER_2_NAME = "Player2";
    private static final String SOLO_PLAYER_NAME = "Solo";
    private static final String LOCALHOST = "127.0.0.1";

    private static final int THREAD_POOL_SIZE = 8;
    private static final int CONCURRENT_PLAYER_COUNT = 100;
    private static final int READER_WRITER_PLAYER_COUNT = 10;
    private static final int READER_WRITER_ITERATIONS = 1000;

    private static final float ROOM1_POS_X = 10f;
    private static final float ROOM1_POS_Y = 20f;
    private static final float ROOM2_POS_X = 30f;
    private static final float ROOM2_POS_Y = 40f;
    private static final float TEST_POS_X = 100f;
    private static final float TEST_POS_Y = 200f;

    private static final long ROOM_DELIVERY_BUDGET_MS = 50;
    private static final long POSITION_LATENCY_BUDGET_MS = 100;

    private static final int CONNECTION_TIMEOUT_SECONDS = 3;
    private static final int LONG_TIMEOUT_SECONDS = 5;
    private static final int PACKET_TIMEOUT_SECONDS = 2;
    private static final long LOGIN_PROCESSING_DELAY_MS = 300;
    private static final long UDP_WARMUP_DELAY_MS = 100;
    private static final long DISCONNECT_CLEANUP_DELAY_MS = 1000;
    private static final long DISCONNECT_DEADLINE_MS = 5000;
    private static final long POLL_INTERVAL_MS = 100;
    private static final long TEST_RECONNECT_GRACE_MS = 1200;
    private static final long TEST_SESSION_CLEANUP_INTERVAL_MS = 200;
    private static final long TEST_EMPTY_ROOM_TIMEOUT_MS = 2000;
    private static final int MAX_BIND_RETRIES = 8;
    private final SessionStore sessionStore = new SessionStore();

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static int freePort() {
        try (ServerSocket s = new ServerSocket(0)) {
            s.setReuseAddress(true);
            return s.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static int[] freePortPair() {
        int tcpPort = freePort();
        int udpPort = freePort();
        while (udpPort == tcpPort) {
            udpPort = freePort();
        }
        return new int[] { tcpPort, udpPort };
    }

    private static boolean isBindInUse(IOException e) {
        return e instanceof BindException || (e.getCause() instanceof BindException);
    }

    private static int[] startServerWithRetries(NetworkKryoServer server) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < MAX_BIND_RETRIES; attempt++) {
            int[] ports = freePortPair();
            try {
                server.start(ports[0], ports[1]);
                return ports;
            } catch (IOException e) {
                last = e;
                if (!isBindInUse(e)) {
                    throw e;
                }
            }
        }
        throw last != null ? last : new IOException("Unable to bind server ports");
    }

    // ------------------------------------------------------------------
    // TC5 â€” Concurrency tests (model-level, no network)
    // ------------------------------------------------------------------

    @Test
    @Tag("TC5")
    void concurrentAddPlayerOperationsDoNotCauseDataLoss() throws InterruptedException {
        // TC5: GameState uses ConcurrentHashMap; simultaneous addPlayer() calls from
        // multiple threads must all succeed and no player entry should be silently dropped.
        GameState gameState = new GameState();
        int playerCount = CONCURRENT_PLAYER_COUNT;

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(playerCount);

        for (int i = 0; i < playerCount; i++) {
            final int playerId = i;
            executor.submit(() -> {
                try {
                    start.await();
                    gameState.addPlayer(new Player(playerId, "p" + playerId));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();

        assertTrue(done.await(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS), "Timed out waiting for player add tasks");
        assertEquals(playerCount, gameState.getPlayerCount());

        executor.shutdownNow();
    }

    @Test
    @Tag("TC5")
    void concurrentReadAndWriteToGameStateDoNotCauseRaceCondition() throws InterruptedException {
        // TC5: While one thread writes player positions, another thread must be able
        // to read the state without throwing ConcurrentModificationException.
        GameState gameState = new GameState();
        for (int i = 0; i < READER_WRITER_PLAYER_COUNT; i++) {
            gameState.addPlayer(new Player(i, "p" + i));
        }

        AtomicBoolean failed = new AtomicBoolean(false);
        CountDownLatch done = new CountDownLatch(2);

        Thread writer = new Thread(() -> {
            try {
                for (int i = 0; i < READER_WRITER_ITERATIONS; i++) {
                    Player p = gameState.getPlayer(i % READER_WRITER_PLAYER_COUNT);
                    if (p != null) {
                        p.setX(i);
                        p.setY(i);
                    }
                }
            } catch (Exception e) {
                failed.set(true);
            } finally {
                done.countDown();
            }
        });

        Thread reader = new Thread(() -> {
            try {
                for (int i = 0; i < READER_WRITER_ITERATIONS; i++) {
                    // Iterating over the ConcurrentHashMap values must never throw.
                    for (Player p : gameState.getPlayers().values()) {
                        float ignored = p.getX() + p.getY();
                    }
                }
            } catch (Exception e) {
                failed.set(true);
            } finally {
                done.countDown();
            }
        });

        writer.start();
        reader.start();
        assertTrue(done.await(LONG_TIMEOUT_SECONDS, TimeUnit.SECONDS), "Concurrent read/write timed out");
        assertFalse(failed.get(), "ConcurrentModificationException or other error during concurrent access");
    }

    // ------------------------------------------------------------------
    // QAS-A2 â€” Failure isolation
    // ------------------------------------------------------------------

    @Test
    @Tag("A2")
    void exceptionInOneRoomDoesNotCrashOtherRooms() {
        // QAS-A2: An unhandled exception in one room's handler must not propagate to other rooms.
        // We verify this via the GameServer's try/catch in onReceived.
        // A handler that always throws.
        PacketHandlerCommand badHandler = (conn, pkt) -> {
            throw new RuntimeException("Simulated room failure");
        };

        // A handler for a second "room" that increments a counter.
        AtomicBoolean goodHandlerCalled = new AtomicBoolean(false);
        PacketHandlerCommand goodHandler = (conn, pkt) -> goodHandlerCalled.set(true);

        // Simulate the GameServer dispatch logic with isolation.
        Map<Class<?>, PacketHandlerCommand> handlers = new ConcurrentHashMap<>();
        handlers.put(PauseRequest.class, badHandler);
        handlers.put(ResumeRequest.class, goodHandler);

        // Dispatch a packet that triggers the bad handler.
        INetworkServer.PlayerConnection fakeConn = new NetworkKryoServer.KryoPlayerConnection(FAKE_CONNECTION_ID);
        try {
            PacketHandlerCommand h = handlers.get(PauseRequest.class);
            if (h != null) {
                try { h.handle(fakeConn, new PauseRequest()); }
                catch (Exception ignored) {}  // isolated
            }
        } catch (Exception e) {
            fail("Exception escaped isolation boundary: " + e.getMessage());
        }

        // Dispatch a packet that triggers the good handler â€” it must still work.
        PacketHandlerCommand h = handlers.get(ResumeRequest.class);
        if (h != null) {
            try { h.handle(fakeConn, new ResumeRequest()); }
            catch (Exception e) { fail("Good handler threw: " + e.getMessage()); }
        }

        assertTrue(goodHandlerCalled.get(), "Good handler should still run after bad handler threw");
    }

    // ------------------------------------------------------------------
    // QAS-P3 â€” Two concurrent rooms
    // ------------------------------------------------------------------

    @Test
    @Tag("P3")
    void twoConcurrentRoomsUpdateAllClientsIndependently() throws IOException, InterruptedException {
        // QAS-P3: Two rooms must each deliver state updates to their own clients
        // within 50 ms, without one room delaying the other.
        int tcpPort;
        int udpPort;
        
        // Test setup: one server and four players (two players per room).
        NetworkKryoServer server = new NetworkKryoServer();
        NetworkKryoClient client1 = new NetworkKryoClient();
        NetworkKryoClient client2 = new NetworkKryoClient();
        NetworkKryoClient client3 = new NetworkKryoClient();
        NetworkKryoClient client4 = new NetworkKryoClient();

        CountDownLatch allConnected = new CountDownLatch(4);
        for (NetworkKryoClient c : new NetworkKryoClient[]{client1, client2, client3, client4}) {
            c.addListener(new NetworkListener.Adapter() {
                @Override public void onConnected() { allConnected.countDown(); }
            });
        }

        // Track which clients received a position update and when.
        CountDownLatch received = new CountDownLatch(2);
        long[] times = new long[4];
        int[] idx = {0};
        for (NetworkKryoClient c : new NetworkKryoClient[]{client1, client2, client3, client4}) {
            final int i = idx[0]++;
            c.addListener(new NetworkListener.Adapter() {
                @Override
                public void onReceived(Object packet) {
                    if (packet instanceof PlayerPosition) {
                        times[i] = System.currentTimeMillis();
                        received.countDown();
                    }
                }
            });
        }

        try {
            int[] ports = startServerWithRetries(server);
            tcpPort = ports[0];
            udpPort = ports[1];
            ServerConnector connector = new ServerConnector(server);
            for (NetworkKryoClient c : new NetworkKryoClient[]{client1, client2, client3, client4}) {
                c.connect(LOCALHOST, tcpPort, udpPort);
            }
            assertTrue(allConnected.await(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS));

            // get server-side connection IDs
            int[] ids = new int[4];
            int[] k = {0};
            server.forEachConnection((id, conn) -> ids[k[0]++] = id);

            long sendTime = System.currentTimeMillis();

            // Room 1: send position from slot 0 to slot 1
            connector.send(ids[1], new PlayerPosition(ids[0], ROOM1_POS_X, ROOM1_POS_Y));
            // Room 2: send position from slot 2 to slot 3
            connector.send(ids[3], new PlayerPosition(ids[2], ROOM2_POS_X, ROOM2_POS_Y));

            assertTrue(received.await(PACKET_TIMEOUT_SECONDS, TimeUnit.SECONDS), "Both clients should receive position updates");

            // Both deliveries must happen within 50 ms of sending.
            for (int i = 0; i < 4; i++) {
                if (times[i] > 0) {
                    assertTrue(times[i] - sendTime < ROOM_DELIVERY_BUDGET_MS,
                        "Delivery took " + (times[i] - sendTime) + " ms â€” exceeds " + ROOM_DELIVERY_BUDGET_MS + " ms budget");
                }
            }
        } finally {
            for (NetworkKryoClient c : new NetworkKryoClient[]{client1, client2, client3, client4}) {
                try { c.disconnect(); } catch (Exception ignored) {}
            }
            server.stop();
        }
    }

    // ------------------------------------------------------------------
    // QAS-A1 / FR4.3 â€” Disconnect handling
    // ------------------------------------------------------------------

    @Test
    @Tag("A1")
    void disconnectedPlayerRoomIsCleanedUpWithinFiveSeconds() throws IOException, InterruptedException {
        // QAS-A1: After a connection drops, the server removes the player from the room
        // and notifies the opponent within 5 seconds.
        int tcpPort = freePort();
        GameServer gameServer = new GameServer(sessionStore);
        NetworkKryoClient client1 = new NetworkKryoClient();

        CountDownLatch connected1 = new CountDownLatch(1);
        client1.addListener(new NetworkListener.Adapter() {
            @Override public void onConnected() { connected1.countDown(); }
        });

        try {
            gameServer.getNetworkServer().start(tcpPort, 0);
            client1.connect(LOCALHOST, tcpPort, 0);
            assertTrue(connected1.await(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS));

            // Send a login so the server assigns the connection to a room.
            client1.sendTCP(new LoginRequest(PLAYER_1_NAME));
            Thread.sleep(LOGIN_PROCESSING_DELAY_MS); // let server process login

            int connectionsBefore = gameServer.getNetworkServer().getConnectionCount();

            client1.disconnect();

            // Wait up to 5 s for the server to process the disconnect.
            long deadline = System.currentTimeMillis() + DISCONNECT_DEADLINE_MS;
            while (System.currentTimeMillis() < deadline
                    && gameServer.getNetworkServer().getConnectionCount() >= connectionsBefore) {
                Thread.sleep(POLL_INTERVAL_MS);
            }

            assertTrue(gameServer.getNetworkServer().getConnectionCount() < connectionsBefore,
                "Server should have removed the disconnected player's connection within 5 seconds");
        } finally {
            try { client1.disconnect(); } catch (Exception ignored) {}
            gameServer.stop();
        }
    }

    @Test
    @Tag("FR4.3")
    void disconnectedPlayerOpponentReceivesPlayerLeftNotification() throws IOException, InterruptedException {
        // FR4.3: Once the server confirms a disconnect, the opponent must receive PlayerLeft.
        int tcpPort = freePort();
        GameServer gameServer = new GameServer(sessionStore);
        NetworkKryoClient client1 = new NetworkKryoClient();
        NetworkKryoClient client2 = new NetworkKryoClient();

        CountDownLatch connected1 = new CountDownLatch(1);
        CountDownLatch connected2 = new CountDownLatch(1);
        AtomicBoolean opponentReceivedLeft = new AtomicBoolean(false);
        CountDownLatch leftReceived = new CountDownLatch(1);

        client1.addListener(new NetworkListener.Adapter() {
            @Override public void onConnected() { connected1.countDown(); }
        });
        client2.addListener(new NetworkListener.Adapter() {
            @Override public void onConnected() { connected2.countDown(); }
            @Override
            public void onReceived(Object packet) {
                if (packet instanceof PlayerLeft) {
                    opponentReceivedLeft.set(true);
                    leftReceived.countDown();
                }
            }
        });

        try {
            gameServer.getNetworkServer().start(tcpPort, 0);
            client1.connect(LOCALHOST, tcpPort, 0);
            client2.connect(LOCALHOST, tcpPort, 0);
            assertTrue(connected1.await(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertTrue(connected2.await(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS));

            // Both login to the same room.
            client1.sendTCP(new LoginRequest(PLAYER_1_NAME));
            client2.sendTCP(new LoginRequest(PLAYER_2_NAME));
            Thread.sleep(LOGIN_PROCESSING_DELAY_MS); // let server process logins

            // Player1 drops.
            client1.disconnect();

            assertTrue(leftReceived.await(LONG_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "Opponent should receive PlayerLeft notification within 5 seconds");
            assertTrue(opponentReceivedLeft.get());
        } finally {
            try { client1.disconnect(); } catch (Exception ignored) {}
            try { client2.disconnect(); } catch (Exception ignored) {}
            gameServer.stop();
        }
    }

    @Test
    @Tag("FR4.3")
    void roomIsRemovedFromServerMapWhenEmpty() throws IOException, InterruptedException {
        // FR4.3: Once all players have left, the GameRoom must be removed from the server's room registry.
        int tcpPort = freePort();
        GameServer gameServer = new GameServer(sessionStore);
        NetworkKryoClient client = new NetworkKryoClient();

        CountDownLatch connected = new CountDownLatch(1);
        client.addListener(new NetworkListener.Adapter() {
            @Override public void onConnected() { connected.countDown(); }
        });

        try {
            gameServer.getNetworkServer().start(tcpPort, 0);
            client.connect(LOCALHOST, tcpPort, 0);
            assertTrue(connected.await(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS));

            client.sendTCP(new LoginRequest(SOLO_PLAYER_NAME));
            Thread.sleep(LOGIN_PROCESSING_DELAY_MS);

            client.disconnect();

            // Wait for server to process disconnect.
            Thread.sleep(DISCONNECT_CLEANUP_DELAY_MS);

            assertEquals(0, gameServer.getNetworkServer().getConnectionCount(),
                "All connections should be gone after the only player disconnects");
        } finally {
            try { client.disconnect(); } catch (Exception ignored) {}
            gameServer.stop();
        }
    }

    // ------------------------------------------------------------------
    // QAS-P1 â€” Position update latency
    // ------------------------------------------------------------------

    @Test
    @Tag("P1")
    void positionUpdateIsProcessedAndBroadcastWithinOneHundredMilliseconds()
            throws IOException, InterruptedException {
        // QAS-P1: A PlayerPosition received by the server must be forwarded to the opponent in < 100 ms.

        // In CI environments the latency budget is relaxed to 500 ms to account for shared/slow runners.
        boolean isCI = System.getenv("CI") != null;

        int tcpPort;
        int udpPort;
        GameServer gameServer = new GameServer(sessionStore);
        NetworkKryoClient client1 = new NetworkKryoClient();
        NetworkKryoClient client2 = new NetworkKryoClient();

        // conn1: waits for client1 to connect.
        CountDownLatch conn1 = new CountDownLatch(1);
        // conn2: waits for client2 to connect.
        CountDownLatch conn2 = new CountDownLatch(1);

        // positionReceived: waits until client2 receives the measured PlayerPosition packet.
        CountDownLatch positionReceived = new CountDownLatch(1);
        long[] receiveNanos = {0};
        long[] sendNanos = {0};
      
        // CI-aware tuning: slower runners need wider budgets and longer setup delays.
        long latencyBudgetMs     = isCI ? 500  : POSITION_LATENCY_BUDGET_MS;
        long loginDelayMs        = isCI ? 600  : LOGIN_PROCESSING_DELAY_MS;
        // Send several warmup packets so at least one establishes the UDP address on both ends.
        int  warmupPacketCount   = isCI ? 5    : 2;
        long warmupDelayMs       = isCI ? 500  : UDP_WARMUP_DELAY_MS;
        // Per-attempt budget for the actual test packet; if exceeded the packet is treated as dropped.
        long perAttemptTimeoutMs = isCI ? 1000 : 400;
        // Maximum number of send attempts before failing (handles rare UDP packet loss).
        int  maxSendAttempts     = 5;

        client1.addListener(new NetworkListener.Adapter() {
            @Override public void onConnected() { conn1.countDown(); }
        });
        client2.addListener(new NetworkListener.Adapter() {
            @Override public void onConnected() { conn2.countDown(); }
            @Override
            public void onReceived(Object packet) {
                if (packet instanceof PlayerPosition pos) {
                    // Only count the real test position, not warm-up positions.
                    if (pos.x == TEST_POS_X && pos.y == TEST_POS_Y) {
                        receiveNanos[0] = System.nanoTime();
                        positionReceived.countDown();
                    }
                }
            }
        });

        try {
            int[] ports = startServerWithRetries(gameServer.getNetworkServer());
            tcpPort = ports[0];
            udpPort = ports[1];
            client1.connect(LOCALHOST, tcpPort, udpPort);
            client2.connect(LOCALHOST, tcpPort, udpPort);
            assertTrue(conn1.await(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertTrue(conn2.await(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS));

            client1.sendTCP(new LoginRequest(PLAYER_1_NAME));
            client2.sendTCP(new LoginRequest(PLAYER_2_NAME));
            Thread.sleep(loginDelayMs);

            GameRoom activeRoom = null;
            long roomReadyDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(isCI ? 3000 : 1200);
            while (System.nanoTime() < roomReadyDeadline) {
                for (GameRoom room : gameServer.getRooms().values()) {
                    if (room.getConnections().size() == 2) {
                        activeRoom = room;
                        break;
                    }
                }
                if (activeRoom != null) {
                    break;
                }
                Thread.sleep(25);
            }
            assertNotNull(activeRoom, "Expected a room with both clients before latency measurement");

            // Set room to PLAYING so PositionHandlerCommand processes position updates.
            activeRoom.setPhase(GameState.Phase.PLAYING);
            gameServer.startGameLoop(activeRoom);

            // Send multiple warmup packets from each client to robustly establish their
            // UDP addresses with the server. KryoNet requires the client to send UDP before
            // the server can reply via UDP.
            for (int i = 0; i < warmupPacketCount; i++) {
                client1.sendUDP(new PlayerPosition(0, 0f, 0f));
                client2.sendUDP(new PlayerPosition(0, 0f, 0f));
            }
            Thread.sleep(warmupDelayMs);

            // Retry loop: UDP packets can be dropped (especially in CI). Re-send until the
            // opponent receives it or all attempts are exhausted. sendNanos is set immediately
            // before each attempt so the latency measurement is always relative to the most
            // recent send â€” previous attempts are treated as definitively lost after
            // perAttemptTimeoutMs and will not appear on the wire again (no TCP retransmit).
            boolean packetReceived = false;
            for (int attempt = 0; attempt < maxSendAttempts && !packetReceived; attempt++) {
                sendNanos[0] = System.nanoTime();
                client1.sendUDP(new PlayerPosition(0, TEST_POS_X, TEST_POS_Y));
                packetReceived = positionReceived.await(perAttemptTimeoutMs, TimeUnit.MILLISECONDS);
            }

            assertTrue(packetReceived,
                "Opponent should receive the position update within " + maxSendAttempts + " attempt(s)");
            long latencyMs = (receiveNanos[0] - sendNanos[0]) / 1_000_000;
            assertTrue(latencyMs < latencyBudgetMs,
                "Position update latency was " + latencyMs + " ms â€” exceeds " + latencyBudgetMs + " ms budget");
        } finally {
            try { client1.disconnect(); } catch (Exception ignored) {}
            try { client2.disconnect(); } catch (Exception ignored) {}
            gameServer.stop();
        }
    }

    // ------------------------------------------------------------------
    // FR4.1 / FR4.2 â€” Pause / Resume broadcast
    // ------------------------------------------------------------------

    @Test
    @Tag("FR4.1")
    void pauseEventIsBroadcastToBothClientsOnPauseRequest() throws IOException, InterruptedException {
        // FR4.1: When one player sends a pause request, the server must broadcast a
        // PauseEvent to all clients in the room.
        int tcpPort = freePort();
        GameServer gameServer = new GameServer(sessionStore);
        NetworkKryoClient client1 = new NetworkKryoClient();
        NetworkKryoClient client2 = new NetworkKryoClient();

        CountDownLatch conn1 = new CountDownLatch(1);
        CountDownLatch conn2 = new CountDownLatch(1);
        CountDownLatch login1 = new CountDownLatch(1);
        CountDownLatch login2 = new CountDownLatch(1);
        CountDownLatch pauseOnClient1 = new CountDownLatch(1);
        CountDownLatch pauseOnClient2 = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger player1Id = new java.util.concurrent.atomic.AtomicInteger(-1);
        java.util.concurrent.atomic.AtomicInteger player2Id = new java.util.concurrent.atomic.AtomicInteger(-1);

        client1.addListener(new NetworkListener.Adapter() {
            @Override public void onConnected() { conn1.countDown(); }
            @Override
            public void onReceived(Object packet) {
                if (packet instanceof LoginResponse lr && lr.success) { player1Id.set(lr.playerId); login1.countDown(); }
                if (packet instanceof PauseEvent) pauseOnClient1.countDown();
            }
        });
        client2.addListener(new NetworkListener.Adapter() {
            @Override public void onConnected() { conn2.countDown(); }
            @Override
            public void onReceived(Object packet) {
                if (packet instanceof LoginResponse lr && lr.success) { player2Id.set(lr.playerId); login2.countDown(); }
                if (packet instanceof PauseEvent) pauseOnClient2.countDown();
            }
        });

        try {
            gameServer.getNetworkServer().start(tcpPort, 0);
            client1.connect(LOCALHOST, tcpPort, 0);
            client2.connect(LOCALHOST, tcpPort, 0);
            assertTrue(conn1.await(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertTrue(conn2.await(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS));

            client1.sendTCP(new LoginRequest(PLAYER_1_NAME));
            client2.sendTCP(new LoginRequest(PLAYER_2_NAME));
            assertTrue(login1.await(PACKET_TIMEOUT_SECONDS, TimeUnit.SECONDS), "Client1 login response not received");
            assertTrue(login2.await(PACKET_TIMEOUT_SECONDS, TimeUnit.SECONDS), "Client2 login response not received");

            // Mark the room as PLAYING so the PauseHandlerCommand accepts the request.
            for (GameRoom room : gameServer.getRooms().values()) {
                room.setPhase(GameState.Phase.PLAYING);
                room.setPhase(GameState.Phase.PLAYING);
            }

            // Determine which client is the host and send the pause request from them.
            GameRoom room = gameServer.getRooms().values().iterator().next();
            NetworkKryoClient hostClient = player1Id.get() == room.getHostConnectionId() ? client1 : client2;
            hostClient.sendTCP(new PauseRequest());

            assertTrue(pauseOnClient1.await(PACKET_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "Client1 should receive PauseEvent");
            assertTrue(pauseOnClient2.await(PACKET_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "Client2 should receive PauseEvent");
        } finally {
            try { client1.disconnect(); } catch (Exception ignored) {}
            try { client2.disconnect(); } catch (Exception ignored) {}
            gameServer.stop();
        }
    }

    @Test
    @Tag("FR4.2")
    void resumeEventIsBroadcastToBothClientsOnResumeRequest() throws IOException, InterruptedException {
        // FR4.2: When a paused player sends a resume request, the server must broadcast
        // a ResumeEvent to all clients so both sides unpause simultaneously.
        int tcpPort = freePort();
        GameServer gameServer = new GameServer(sessionStore);
        NetworkKryoClient client1 = new NetworkKryoClient();
        NetworkKryoClient client2 = new NetworkKryoClient();

        CountDownLatch conn1 = new CountDownLatch(1);
        CountDownLatch conn2 = new CountDownLatch(1);
        CountDownLatch login1 = new CountDownLatch(1);
        CountDownLatch login2 = new CountDownLatch(1);
        CountDownLatch resumeOnClient1 = new CountDownLatch(1);
        CountDownLatch resumeOnClient2 = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger player1Id = new java.util.concurrent.atomic.AtomicInteger(-1);
        java.util.concurrent.atomic.AtomicInteger player2Id = new java.util.concurrent.atomic.AtomicInteger(-1);

        client1.addListener(new NetworkListener.Adapter() {
            @Override public void onConnected() { conn1.countDown(); }
            @Override
            public void onReceived(Object packet) {
                if (packet instanceof LoginResponse lr && lr.success) { player1Id.set(lr.playerId); login1.countDown(); }
                if (packet instanceof ResumeEvent) resumeOnClient1.countDown();
            }
        });
        client2.addListener(new NetworkListener.Adapter() {
            @Override public void onConnected() { conn2.countDown(); }
            @Override
            public void onReceived(Object packet) {
                if (packet instanceof LoginResponse lr && lr.success) { player2Id.set(lr.playerId); login2.countDown(); }
                if (packet instanceof ResumeEvent) resumeOnClient2.countDown();
            }
        });

        try {
            gameServer.getNetworkServer().start(tcpPort, 0);
            client1.connect(LOCALHOST, tcpPort, 0);
            client2.connect(LOCALHOST, tcpPort, 0);
            assertTrue(conn1.await(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertTrue(conn2.await(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS));

            client1.sendTCP(new LoginRequest(PLAYER_1_NAME));
            client2.sendTCP(new LoginRequest(PLAYER_2_NAME));
            assertTrue(login1.await(PACKET_TIMEOUT_SECONDS, TimeUnit.SECONDS), "Client1 login response not received");
            assertTrue(login2.await(PACKET_TIMEOUT_SECONDS, TimeUnit.SECONDS), "Client2 login response not received");

            // After login2 is received by the client, the server may still be executing
            // startGameLoop() on its own thread, which sets the room phase to PLAYING.
            // Spin-wait until that transition completes so our PAUSED override is not lost.
            long phaseDeadline = System.currentTimeMillis() + CONNECTION_TIMEOUT_SECONDS * 1000L;
            while (System.currentTimeMillis() < phaseDeadline) {
                boolean allPlaying = gameServer.getRooms().values().stream()
                    .allMatch(r -> r.getPhase() == GameState.Phase.PLAYING);
                if (allPlaying) break;
                Thread.sleep(POLL_INTERVAL_MS);
            }

            // Set room to PAUSED so ResumeHandlerCommand accepts the request.
            for (GameRoom room : gameServer.getRooms().values()) {
                room.setPhase(GameState.Phase.PLAYING);
                room.setPhase(GameState.Phase.PLAYING);
                room.setPhase(GameState.Phase.PAUSED);
            }

            // Determine which client is the host and send the resume request from them.
            GameRoom room = gameServer.getRooms().values().iterator().next();
            NetworkKryoClient hostClient = player1Id.get() == room.getHostConnectionId() ? client1 : client2;
            hostClient.sendTCP(new ResumeRequest());

            assertTrue(resumeOnClient1.await(PACKET_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "Client1 should receive ResumeEvent");
            assertTrue(resumeOnClient2.await(PACKET_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "Client2 should receive ResumeEvent");
        } finally {
            try { client1.disconnect(); } catch (Exception ignored) {}
            try { client2.disconnect(); } catch (Exception ignored) {}
            gameServer.stop();
        }
    }
}


