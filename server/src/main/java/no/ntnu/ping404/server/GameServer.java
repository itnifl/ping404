package no.ntnu.ping404.server;

import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.model.GameState.Phase;
import no.ntnu.ping404.network.GameConfig;
import no.ntnu.ping404.network.NetworkConfig;
import no.ntnu.ping404.network.NetworkKryoServer;
import no.ntnu.ping404.network.INetworkServer.PlayerConnection;
import no.ntnu.ping404.network.INetworkServer.ServerListenerAdapter;
import no.ntnu.ping404.network.ServerConnector;
import no.ntnu.ping404.network.packets.*;
import no.ntnu.ping404.server.game.GameLoop;
import no.ntnu.ping404.server.game.InputEventDispatcher;
import no.ntnu.ping404.server.game.InputQueue;
import no.ntnu.ping404.server.handler.*;
import no.ntnu.ping404.server.metrics.MetricsCollector;
import no.ntnu.ping404.server.metrics.NoOpGeoLocationService;
import no.ntnu.ping404.server.metrics.RoomMetricsListener;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GameServer extends ServerListenerAdapter {

    /** Manages all client network connections (TCP and UDP). Owned by GameServer and shared with handlers. */
    private final NetworkKryoServer networkServer;

    /** Sends packets to clients via the network server. Owned by GameServer and shared with all handlers. */
    private final ServerConnector connector;

    /** Maps room ID strings to GameRoom objects, tracks all active game rooms on the server. */
    private final Map<String, GameRoom> rooms;

    /** Maps player connection IDs to their assigned GameRoom. */
    private final Map<Integer, GameRoom> playerRooms;

    /**
     * Command Pattern: Maps packet types to their corresponding handler commands.
     */
    private final Map<Class<?>, PacketHandlerCommand> handlers;
    private final PlayerDisconnectHandler disconnectHandler;
    private final SessionStore sessionStore;
    private static final Logger logger = LoggerFactory.getLogger(GameServer.class);
    private static final long DEFAULT_SESSION_CLEANUP_INTERVAL_MS = 5_000;
    private static final long DEFAULT_EMPTY_ROOM_TIMEOUT_MS = 30_000;
    private final long sessionCleanupIntervalMs;
    private final long emptyRoomTimeoutMs;

    private volatile boolean running;
    private ScheduledExecutorService sessionCleanupScheduler;
    private final MetricsCollector metricsCollector;
    private final Set<String> instrumentedRooms;

    /** Maps room ID --> active GameLoop. One loop per room while game is PLAYING. */
    private final Map<String, GameLoop> gameLoops = new ConcurrentHashMap<>();

    /**
     * Delegate that handlers use to enqueue input events into the correct GameLoop.
     * Replaces direct GameServer injection - avoids circular dependency.
     * MUST be declared BEFORE the constructor so it is initialized when handlers are created.
     */
    private final InputEventDispatcher dispatcher = (room, event) -> {
        GameLoop loop = gameLoops.get(room.getRoomId());
        if (loop != null) {
            loop.getInputQueue().enqueue(event);
        }
    };

    public GameServer(SessionStore sessionStore) {
        this(sessionStore, DEFAULT_SESSION_CLEANUP_INTERVAL_MS, DEFAULT_EMPTY_ROOM_TIMEOUT_MS);
    }

    GameServer(SessionStore sessionStore, long sessionCleanupIntervalMs, long emptyRoomTimeoutMs) {
        this.networkServer = new NetworkKryoServer();
        this.networkServer.addListener(this);
        this.connector = new ServerConnector(networkServer);
        this.rooms = new ConcurrentHashMap<>();
        this.playerRooms = new ConcurrentHashMap<>();
        this.sessionStore = sessionStore;
        this.sessionCleanupIntervalMs = sessionCleanupIntervalMs > 0 ? sessionCleanupIntervalMs : DEFAULT_SESSION_CLEANUP_INTERVAL_MS;
        this.emptyRoomTimeoutMs = emptyRoomTimeoutMs > 0 ? emptyRoomTimeoutMs : DEFAULT_EMPTY_ROOM_TIMEOUT_MS;
        this.handlers = new HashMap<>();
        this.disconnectHandler = new PlayerDisconnectHandler(connector, sessionStore);
        this.metricsCollector = GameConfig.isMetricsEnabled()
                ? new MetricsCollector(new NoOpGeoLocationService())
                : null;
        this.instrumentedRooms = ConcurrentHashMap.newKeySet();

        // Register handlers - dispatcher is already initialized at this point
        handlers.put(LoginRequest.class,    new LoginHandlerCommand(connector, rooms, playerRooms, sessionStore));
        handlers.put(PlayerPosition.class,  new PositionHandlerCommand(connector, playerRooms, dispatcher, metricsCollector));
        handlers.put(ChatMessage.class,     new ChatHandlerCommand(connector, playerRooms));
        handlers.put(Ping.class,            new PingHandlerCommand(connector));
        handlers.put(Pong.class,            new PongHandlerCommand(metricsCollector, playerRooms));
        handlers.put(PauseRequest.class,    new PauseHandlerCommand(connector, playerRooms, dispatcher));
        handlers.put(ResumeRequest.class,   new ResumeHandlerCommand(connector, playerRooms, dispatcher));
        handlers.put(GameStartRequest.class, new GameStartHandlerCommand(connector, playerRooms, this::startGameLoop));
        handlers.put(LeaveRoom.class,       new LeaveRoomHandlerCommand(connector, playerRooms, sessionStore));
        handlers.put(RematchRequest.class,  new RematchHandlerCommand(connector, playerRooms, this::startGameLoop));
    }

    public void start() throws java.io.IOException {
        networkServer.start();
        running = true;
        if (metricsCollector != null) metricsCollector.start();
        ensureSessionCleanupSchedulerStarted();
        logger.info("Game server started successfully!");
        logger.info("TCP Port: " + NetworkConfig.TCP_PORT);
        logger.info("UDP Port: " + NetworkConfig.UDP_PORT);
        logger.info("Max players: " + NetworkConfig.MAX_PLAYERS);
    }

    public void stop() {
        running = false;
        if (sessionCleanupScheduler != null) {
            sessionCleanupScheduler.shutdownNow();
        }
        // Stop all active game loops
        for (GameLoop loop : gameLoops.values()) {
            loop.stop();
        }
        gameLoops.clear();
        if (metricsCollector != null) metricsCollector.stop();
        networkServer.stop();
        logger.info("Game server stopped.");
    }

    @Override
    public void onClientConnected(PlayerConnection connection) {
        logger.info("New connection from client #" + connection.getId());
    }

    @Override
    public void onClientDisconnected(PlayerConnection connection) {
        try {
            ensureSessionCleanupSchedulerStarted();
            GameRoom room = playerRooms.get(connection.getId());
            if (room != null) {
                String playerName = connection.getPlayerName();

                // Notify remaining room members as soon as the disconnect is confirmed.
                disconnectHandler.handlePlayerDisconnect(connection.getId(), room);

                // Refresh the session token expiry so the 30s reconnect window starts now
                sessionStore.refreshByConnectionId(connection.getId());

                saveDisconnectedPlayerPosition(room, connection);

                // Clean up room and player tracking
                room.removePlayer(connection.getId());
                playerRooms.remove(connection.getId());

                // Stop game loop if room is no longer viable for play
                if (room.isEmpty() || (!room.isFull() && isActiveGame(room.getPhase()))) {
                    stopGameLoop(room.getRoomId());
                }

                // Clean up disconnected slots for expired sessions and reset game if needed
                cleanupExpiredSessionsAndResetRoomsIfNeeded();

                if (playerName != null) {
                    logger.info("Player '" + playerName + "' disconnected from room '" + room.getRoomId() + "'.");
                }
            }
        } catch (Exception e) {
            logger.error("Error handling disconnect for connection " + connection.getId() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void onReceived(PlayerConnection connection, Object packet) {
        if (!(packet instanceof no.ntnu.ping404.network.packets.PlayerPosition)
                && !(packet instanceof no.ntnu.ping404.network.packets.Pong)) {
            System.out.println("[GameServer] Received " + packet.getClass().getSimpleName() + " from connection " + connection.getId());
        }
        sessionStore.refreshByConnectionId(connection.getId());
        PacketHandlerCommand handler = handlers.get(packet.getClass());
        if (handler != null) {
            try {
                handler.handle(connection, packet);
            } catch (Exception e) {
                // Isolate per-handler failures so one bad room cannot crash the server.
                logger.error("Handler error for " + packet.getClass().getSimpleName()
                    + " from connection " + connection.getId() + ": " + e.getMessage(), e);
            }
        } else {
            System.out.println("[GameServer] No handler registered for " + packet.getClass().getSimpleName());
        }

        if (packet instanceof LoginRequest) {
            attachMetricsListenersToNewRooms();
        }

        // Resume game loop when a player reconnects into an active game.
        // Note: Game loop is NOT auto-started here; host must click "Start Game"
        // which triggers GameStartHandlerCommand to call startGameLoop.
        GameRoom room = playerRooms.get(connection.getId());
        if (room != null && !gameLoops.containsKey(room.getRoomId())) {
            if (room.isFull() && isActiveGame(room.getPhase())) {
                resumeGameLoop(room);          // PLAYING or PAUSED --> resume
            }
        }
    }

    /**
     * Creates and starts a GameLoop for the given room.
     * Called when a room transitions to PLAYING phase.
     */
    public void startGameLoop(GameRoom room) {
        InputQueue inputQueue = new InputQueue();
        GameLoop loop = new GameLoop(room, inputQueue, connector, metricsCollector);
        gameLoops.put(room.getRoomId(), loop);
        // Phase already set by GameStartHandlerCommand
        loop.start();
        logger.info("Game loop started for room '{}'", room.getRoomId());
    }

    /**
     * Resumes a GameLoop for a room already in an active phase (PLAYING or PAUSED).
     * Used when a player reconnects into an active game - preserves existing phase
     * and does not reset the puck. See issue #14 code review Defect #1.
     */
    public void resumeGameLoop(GameRoom room) {
        InputQueue inputQueue = new InputQueue();
        GameLoop loop = new GameLoop(room, inputQueue, connector, metricsCollector);
        gameLoops.put(room.getRoomId(), loop);
        loop.startWithoutReset();  // preserves phase and score
        logger.info("Game loop resumed for room '{}' (phase: {})", room.getRoomId(), room.getPhase());
    }

    /**
     * Stops and removes the GameLoop for the given room.
     */
    public void stopGameLoop(String roomId) {
        GameLoop loop = gameLoops.remove(roomId);
        if (loop != null) {
            loop.stop();
            logger.info("Game loop stopped for room '{}'", roomId);
        }
    }

    /**
     * Returns the GameLoop for a room, or null if none is running.
     * Used by network handlers to enqueue input events (Producer side).
     */
    public GameLoop getGameLoop(String roomId) {
        return gameLoops.get(roomId);
    }

    /** Returns the game loops map (for testing). */
    public Map<String, GameLoop> getGameLoops() {
        return gameLoops;
    }

    /** Attaches a RoomMetricsListener to any room that was created since the last check. */
    private void attachMetricsListenersToNewRooms() {
        if (metricsCollector == null) return;
        rooms.forEach((id, room) -> {
            if (instrumentedRooms.add(id)) {
                room.addListener(new RoomMetricsListener(metricsCollector));
            }
        });
    }

    public void broadcast(Object packet) {
        connector.broadcast(packet);
    }

    public NetworkKryoServer getNetworkServer() {
        return networkServer;
    }

    public Map<String, GameRoom> getRooms() {
        return rooms;
    }

    public boolean isRunning() {
        return running;
    }

    private void startSessionCleanupScheduler() {
        sessionCleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "session-cleanup");
            t.setDaemon(true);
            return t;
        });
        sessionCleanupScheduler.scheduleAtFixedRate(
            this::cleanupExpiredSessionsAndResetRoomsIfNeeded,
            sessionCleanupIntervalMs, sessionCleanupIntervalMs, TimeUnit.MILLISECONDS
        );
        logger.debug("Session cleanup scheduler started (interval: {}ms)", sessionCleanupIntervalMs);
    }

    private synchronized void ensureSessionCleanupSchedulerStarted() {
        if (sessionCleanupScheduler == null || sessionCleanupScheduler.isShutdown()) {
            startSessionCleanupScheduler();
        }
    }

    /** Returns true if the phase represents an active game (PLAYING or PAUSED). */
    private boolean isActiveGame(Phase phase) {
        return phase == Phase.PLAYING || phase == Phase.PAUSED;
    }

    /**
     * Cleans up expired reconnect sessions and resets affected rooms to WAITING phase.
     * Also removes rooms that have been empty for longer than {@link #EMPTY_ROOM_TIMEOUT_MS}.
     */
    private void cleanupExpiredSessionsAndResetRoomsIfNeeded() {
        java.util.Map<Integer, String> expiredSessions = sessionStore.invalidateExpiredAndGetExpiredSessions();
        for (var expiredSession : expiredSessions.entrySet()) {
            int expiredId = expiredSession.getKey();
            String roomId = expiredSession.getValue();
            GameRoom roomWithExpiredSession = rooms.get(roomId);
            if (roomWithExpiredSession != null) {
                Integer disconnectedSlot = roomWithExpiredSession.consumeDisconnectedSlot(expiredId);
                if (disconnectedSlot != null) {
                    roomWithExpiredSession.consumeDisconnectedPlayer(disconnectedSlot);
                }
                if (shouldResetRoomToWaiting(roomWithExpiredSession)) {
                    stopGameLoop(roomId);
                    roomWithExpiredSession.setPhase(Phase.WAITING);
                    roomWithExpiredSession.broadcast(new GameReset("Opponent timed out"), connector);
                    logger.info("Room '{}' reset to WAITING after player {} timed out.", roomId, expiredId);
                }
            }
        }

        rooms.entrySet().removeIf(entry -> {
            GameRoom room = entry.getValue();
            long emptyAt = room.getEmptyRoomSince();
            if (emptyAt != -1 && System.currentTimeMillis() - emptyAt >= emptyRoomTimeoutMs) {
                stopGameLoop(entry.getKey());
                logger.info("Room '{}' removed after being empty for {}+ seconds.",
                        entry.getKey(), emptyRoomTimeoutMs / 1_000);
                return true;
            }
            return false;
        });
    }

    /** Returns true if room has one player, was active, and should reset for new teammate. */
    private boolean shouldResetRoomToWaiting(GameRoom room) {
        return !room.isEmpty() && !room.isFull() && isActiveGame(room.getPhase());
    }

    /** Saves the player's last known position for potential reconnect restoration. */
    private void saveDisconnectedPlayerPosition(GameRoom room, PlayerConnection connection) {
        PlayerConnection pc = room.getConnections().get(connection.getId());
        if (pc != null) {
            room.updatePosition(connection.getId(), pc.getX(), pc.getY());
        }
    }
}
