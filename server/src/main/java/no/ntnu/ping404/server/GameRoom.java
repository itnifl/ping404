package no.ntnu.ping404.server;

import no.ntnu.ping404.model.GameEngine;
import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.model.Player;
import no.ntnu.ping404.network.GameConfigDefaults;
import no.ntnu.ping404.network.INetworkServer.PlayerConnection;
import no.ntnu.ping404.utils.Constants;
import no.ntnu.ping404.network.ServerConnector;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class GameRoom {

    /**
     * Listener interface for room lifecycle events.
     * Allows GameServer to react to room changes without being tightly coupled to GameRoom internals.
     */
    public interface GameRoomListener {
        /** Called when a player joins the room. */
        default void onPlayerJoined(GameRoom room, PlayerConnection connection, Player player) {}
        /** Called when a player leaves the room. */
        default void onPlayerLeft(GameRoom room, int connectionId) {}
        /** Called when the room becomes full (max players reached). */
        default void onRoomFull(GameRoom room) {}
        /** Called when the game starts (phase changes to PLAYING). */
        default void onGameStarted(GameRoom room) {}
        /** Called when the game phase changes. */
        default void onPhaseChanged(GameRoom room, GameState.Phase oldPhase, GameState.Phase newPhase) {}
        /** 
         * Called when the match ends and a winner is determined (Issue #15 Domain Event).
         * @param room the game room
         * @param winnerId the player slot (1 or 2) of the winner
         * @param winnerName the name of the winning player
         */
        default void onMatchEnded(GameRoom room, int winnerId, String winnerName) {}
        /** Returns the connection ID for the given player slot (1 or 2). **/
        default int getConnectionIdForSlot(int playerSlot) { return -1; } 
        /** Get the player slot index for a given player ID. */
        default int getSlotByPlayerId(int playerId) { return -1; } 
    }

    private static final int MAX_PLAYERS = 2;

    /** Immutable (x, y) position of a player on the field. */
    public record Position(float x, float y) {}

    /** Snapshot of a disconnected player needed for end-of-match notifications. */
    public record DisconnectedPlayer(int connectionId, String playerName) {}

    /**
     * A type-safe wrapper around {@link ConcurrentHashMap} keyed by connection ID.
     * Keeps field declarations self-documenting and thread-safe without exposing raw maps.
     */
    public static class ConnectionMap<V> {
        private final ConcurrentHashMap<Integer, V> map = new ConcurrentHashMap<>();

        public V get(int connectionId) { return map.get(connectionId); }
        public V put(int connectionId, V value) { return map.put(connectionId, value); }
        public V putIfAbsent(int connectionId, V value) { return map.putIfAbsent(connectionId, value); }
        public V remove(int connectionId) { return map.remove(connectionId); }
        public boolean containsKey(int connectionId) { return map.containsKey(connectionId); }
        public int size() { return map.size(); }
        public boolean isEmpty() { return map.isEmpty(); }
        public java.util.Collection<V> values() { return map.values(); }
        public java.util.Set<Integer> keySet() { return map.keySet(); }
        public java.util.Set<Map.Entry<Integer, V>> entrySet() { return map.entrySet(); }
    }

    private final String roomId;
    private int hostConnectionId;
    private final int winScore;
    private final GameState gameState;
    private final GameEngine gameEngine;
    private final ConnectionMap<PlayerConnection> connections;
    private final ConnectionMap<Position> lastKnownPositions;
    private final ConnectionMap<Integer> disconnectedSlots;
    private final ConcurrentHashMap<Integer, DisconnectedPlayer> disconnectedPlayersBySlot;
    private final List<GameRoomListener> listeners;

    /** Tracks which connection ID occupies slot 1 (first player added). -1 means empty. */
    private int slot1ConnectionId = -1;
    /** Tracks which connection ID occupies slot 2 (second player added). -1 means empty. */
    private int slot2ConnectionId = -1;

    /** Tracks which players have sent a rematch intent (by connection ID). */
    private final Set<Integer> rematchIntents = ConcurrentHashMap.newKeySet();

    /**
     * The time (via {@link System#currentTimeMillis()}) at which the room became empty.
     * Reset to {@code -1} whenever a player joins. Used to schedule automatic room closure
     * after the room has been empty for a configurable timeout.
     */
    private long emptyRoomSince = -1;

    public GameRoom(String roomId, int hostConnectionId) {
        this(roomId, hostConnectionId, GameConfigDefaults.DefaultWinScore);
    }

    public GameRoom(String roomId, int hostConnectionId, int winScore) {
        this.roomId = roomId;
        this.hostConnectionId = hostConnectionId;
        this.winScore = winScore;
        this.gameState = new GameState(winScore);
        this.gameEngine = new GameEngine(gameState);
        this.connections = new ConnectionMap<>();
        this.lastKnownPositions = new ConnectionMap<>();
        this.disconnectedSlots = new ConnectionMap<>();
        this.disconnectedPlayersBySlot = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
    }

    public int getWinScore() {
        return winScore;
    }

    public void addListener(GameRoomListener listener) {
        listeners.add(listener);
    }

    public void removeListener(GameRoomListener listener) {
        listeners.remove(listener);
    }

    /**
     * Thread-safe add: delegates to {@link #addPlayer(PlayerConnection, Player, Integer)} with no slot preference.
     * Returns false if room is full or player is already present.
     */
    public synchronized boolean addPlayer(PlayerConnection connection, Player player) {
        return addPlayer(connection, player, null);
    }

    /**
     * Adds a player to the room and assigns them to a slot.
     * <p>
     * This is the entry point for the host-migration lifecycle: the first player added
     * becomes the host (set by the caller via {@code hostConnectionId} on construction).
     * On reconnect, passing {@code preferredSlot} restores the player to their original side
     * of the field, preserving game continuity.
     * </p>
     *
     * @param connection    the player's network connection
     * @param player        the player model to register in the game state
     * @param preferredSlot the slot (1 or 2) to restore on reconnect, or {@code null} for first free
     * @return {@code true} if the player was added; {@code false} if the room is full or the
     *         connection is already registered
     */
    public synchronized boolean addPlayer(PlayerConnection connection, Player player, Integer preferredSlot) {
        if (connections.size() >= MAX_PLAYERS) return false;
        if (connections.putIfAbsent(connection.getId(), connection) != null) return false;

        // A player joining cancels the empty-room timeout.
        emptyRoomSince = -1;
        // If the room has no host (all previous players left), the first joiner becomes host.
        if (hostConnectionId == -1) {
            hostConnectionId = connection.getId();
        }

        gameState.addPlayer(player);

        int slot = pickSlot(preferredSlot);
        setSlotConnectionId(slot, connection.getId());
        disconnectedPlayersBySlot.remove(slot);
        initPlayerPosition(slot, player);
        
        // Fire player joined event
        for (GameRoomListener listener : listeners) {
            listener.onPlayerJoined(this, connection, player);
        }
        
        // Fire room full event if we just reached capacity
        if (isFull()) {
            for (GameRoomListener listener : listeners) {
                listener.onRoomFull(this);
            }
        }
        return true;
    }

    /**
     * Removes a player from the room and handles host migration if needed.
     * <p>
     * If the departing player is the current host, {@link #reassignHostIfNeeded(int)} promotes
     * the remaining player. The vacated slot is recorded so that
     * {@link #consumeDisconnectedSlot(int)} can restore it if the player reconnects.
     * </p>
     *
     * @param connectionId the connection ID of the player to remove
     */
    public void removePlayer(int connectionId) {
        PlayerConnection removed = connections.remove(connectionId);
        if (removed != null) {
            int slot = getSlotByPlayerId(connectionId);
            Player removedPlayer = gameState.getPlayer(connectionId);
            if (slot != -1) {
                setSlotConnectionId(slot, -1);
                disconnectedSlots.put(connectionId, slot);
                disconnectedPlayersBySlot.put(slot, new DisconnectedPlayer(
                    connectionId,
                    removedPlayer != null ? removedPlayer.getName() : removed.getPlayerName()
                ));
            }

            reassignHostIfNeeded(connectionId);

            gameState.removePlayer(connectionId);

            if (connections.isEmpty()) {
                emptyRoomSince = System.currentTimeMillis();
            }

            // Fire player left event
            for (GameRoomListener listener : listeners) {
                listener.onPlayerLeft(this, connectionId);
            }
        }
    }

    /**
     * Removes a player permanently from the room (intentional leave, not disconnect).
     * Clears any reconnection data and resets the room phase to WAITING
     * if the room is no longer full.
     *
     * @param connectionId the connection ID of the player to remove
     */
    public void removePlayerPermanently(int connectionId) {
        PlayerConnection removed = connections.remove(connectionId);
        if (removed != null) {
            int slot = getSlotByPlayerId(connectionId);
            if (slot != -1) {
                setSlotConnectionId(slot, -1);
                // Clear reconnection data - player is leaving intentionally
                disconnectedSlots.remove(connectionId);
                disconnectedPlayersBySlot.remove(slot);
            }

            reassignHostIfNeeded(connectionId);

            gameState.removePlayer(connectionId);

            // Reset to WAITING so new players can join
            if (!isFull() && gameState.getPhase() != GameState.Phase.WAITING) {
                gameState.resetForNewMatch();
            }

            if (connections.isEmpty()) {
                emptyRoomSince = System.currentTimeMillis();
            }

            // Fire player left event
            for (GameRoomListener listener : listeners) {
                listener.onPlayerLeft(this, connectionId);
            }
        }
    }

    /**
     * Returns and removes the previous slot index for a disconnected player.
     */
    public Integer consumeDisconnectedSlot(int connectionId) {
        return disconnectedSlots.remove(connectionId);
    }

    public DisconnectedPlayer getDisconnectedPlayer(int slot) {
        return disconnectedPlayersBySlot.get(slot);
    }

    /**
     * Returns and removes the disconnected player snapshot for a slot.
     */
    public DisconnectedPlayer consumeDisconnectedPlayer(int slot) {
        return disconnectedPlayersBySlot.remove(slot);
    }

    public boolean isFull() { return connections.size() >= MAX_PLAYERS; }

    public boolean isEmpty() { return connections.isEmpty(); }

    public boolean canStart() { return connections.size() == MAX_PLAYERS && gameState.getPhase() == GameState.Phase.WAITING; }

    public String getRoomId() { return roomId; }
    public int getHostConnectionId() { return hostConnectionId; }

    /**
     * Returns the time (via {@link System#currentTimeMillis()}) at which the room became empty,
     * or {@code -1} if the room currently has at least one player.
     */
    public long getEmptyRoomSince() { return emptyRoomSince; }
    public GameState getGameState() { return gameState; }
    public GameEngine getGameEngine() { return gameEngine; }
    public ConnectionMap<PlayerConnection> getConnections() { return connections; }
    public int getPlayerCount() { return connections.size(); }
    
    /** Returns the current game phase. */
    public GameState.Phase getPhase() { return gameState.getPhase(); }
    

    public int getSlotByPlayerId(int playerId) { 
        if (playerId == slot1ConnectionId) return 1;
        if (playerId == slot2ConnectionId) return 2;
        return -1;
    }

    private void setSlotConnectionId(int slot, int connectionId) {
        switch (slot) {
            case 1 -> slot1ConnectionId = connectionId;
            case 2 -> slot2ConnectionId = connectionId;
        }
    }

    private int pickSlot(Integer preferredSlot) {
        if (preferredSlot != null && getConnectionIdForSlot(preferredSlot) == -1) return preferredSlot;
        if (slot1ConnectionId == -1) return 1;
        if (slot2ConnectionId == -1) return 2;
        // This should be unreachable: addPlayer guards against MAX_PLAYERS before calling pickSlot.
        throw new IllegalStateException("pickSlot called with no free slot - MAX_PLAYERS guard should have prevented this");
    }

    private void initPlayerPosition(int slot, Player player) {
        if (slot == 1) {
            player.setX(Constants.PADDLE_MARGIN + Constants.PADDLE_WIDTH / 2);
        } else if (slot == 2) {
            player.setX(Constants.DEFAULT_FIELD_WIDTH - Constants.PADDLE_MARGIN - Constants.PADDLE_WIDTH / 2);
        }
        player.setY(Constants.boardCenterY());
    }

    private void reassignHostIfNeeded(int departingConnectionId) {
        if (hostConnectionId != departingConnectionId) return;
        int newHost = slot1ConnectionId != -1 ? slot1ConnectionId : slot2ConnectionId;
        if (newHost != -1) {
            hostConnectionId = newHost;
        } else {
            hostConnectionId = -1;
        }
    }
    
     /**
     * Transitions the game phase using validated state machine methods.
     * Fires phase change events on successful transition.
     * Fires onGameStarted if transitioning to PLAYING phase.
     *
     * @param newPhase the desired phase to transition to
     * @return true if the transition succeeded, false if rejected by state machine
     */
     public boolean setPhase(GameState.Phase newPhase) {
        GameState.Phase oldPhase = gameState.getPhase();
        if (oldPhase == newPhase) return true;

        boolean success = switch (newPhase) {
            case PLAYING   -> oldPhase == GameState.Phase.PAUSED
                ? gameState.resumeMatch()
                : gameState.startMatch();
            case PAUSED    -> gameState.pauseMatch();
            case FINISHED  -> gameState.finishMatch();
            case WAITING   -> { gameState.resetForNewMatch(); rematchIntents.clear(); yield true; }
        };

        if (!success) return false;

        // Fire listeners after successful transition.
        for (GameRoomListener listener : listeners) {
            listener.onPhaseChanged(this, oldPhase, newPhase);
        }

        if (newPhase == GameState.Phase.PLAYING && oldPhase == GameState.Phase.WAITING) {
            for (GameRoomListener listener : listeners) {
                listener.onGameStarted(this);
            }
        }

        return true;
    }

    /**
     * Notifies all listeners that the match has ended (Issue #15 Domain Event).
     * Should be called before broadcasting GameOver packet.
     *
     * @param winnerId the connection ID of the winner
     * @param winnerName the name of the winning player
     */
    public void notifyMatchEnded(int winnerId, String winnerName) {
        for (GameRoomListener listener : listeners) {
            listener.onMatchEnded(this, winnerId, winnerName);
        }
    }

    /**
     * Records a rematch intent from the given connection.
     * Duplicate votes from the same player are ignored.
     *
     * @param connectionId the connection ID of the requesting player
     * @return {@code true} if this vote completes the required count (all players voted)
     */
    public boolean requestRematch(int connectionId) {
        rematchIntents.add(connectionId);
        return rematchIntents.size() >= MAX_PLAYERS;
    }

    /** Clears all rematch votes (called after rematch starts or match resets). */
    public void clearRematchIntents() {
        rematchIntents.clear();
    }

    /** Returns how many players have sent a rematch intent. */
    public int getRematchIntentCount() {
        return rematchIntents.size();
    }

    /** Broadcasts a packet to all players in the room. */
    public void broadcast(Object packet, ServerConnector connector) {
        for (PlayerConnection connection : connections.values()) {
            connector.send(connection.getId(), packet);
        }
    }

    /** Broadcasts a packet to all players except the specified one. */
    public void broadcastExcept(int excludeConnectionId, Object packet, ServerConnector connector) {
        for (PlayerConnection connection : connections.values()) {
            if (connection.getId() != excludeConnectionId) {
                connector.send(connection.getId(), packet);
            }
        }
    }

    /**
     * Returns the connection ID for the given player slot (1 or 2).
     * The ConnectionId is the PlayerId.
     * 
     * @param playerSlot the slot number (1 or 2)
     * @return the connection ID for that slot, or -1 if the slot is empty
     * @throws IllegalArgumentException if playerSlot is not 1 or 2
     */
    public int getConnectionIdForSlot(int playerSlot) {
        return switch (playerSlot) {
            case 1 -> slot1ConnectionId;
            case 2 -> slot2ConnectionId;
            default -> throw new IllegalArgumentException("Invalid player slot: " + playerSlot + ". Must be 1 or 2.");
        };
    }

    public void updatePosition(int connectionId, float x, float y) {
        lastKnownPositions.put(connectionId, new Position(x, y));
    }

    /**
     * Returns the last known position for a disconnected player, or null if not available.
     * Used to restore position on reconnect.
     */
    public Position getLastKnownPosition(int connectionId) {
        return lastKnownPositions.remove(connectionId);
    }

}
