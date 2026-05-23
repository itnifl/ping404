package no.ntnu.ping404.server.handler;

import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.model.Player;
import no.ntnu.ping404.network.GameConfig;
import no.ntnu.ping404.network.GameConfigDefaults;
import no.ntnu.ping404.network.NetworkConfig;
import no.ntnu.ping404.network.INetworkServer.PlayerConnection;
import no.ntnu.ping404.network.ServerConnector;
import no.ntnu.ping404.network.packets.*;
import no.ntnu.ping404.server.GameRoom;
import no.ntnu.ping404.server.SessionStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles login requests from players and assigns them to game rooms.
 * Creates new rooms when needed and broadcasts room assignments to all players in the assigned room.
 * Uses {@link #ROOM_COUNTER} to generate unique room IDs across all concurrent login handlers.
 */
public class LoginHandlerCommand implements PacketHandlerCommand {

    // Thread-safe room ID generator: shared across all login handlers to produce unique room IDs (room-1, room-2, ...).
    private static final AtomicInteger ROOM_COUNTER = new AtomicInteger(0);

    /** Server-side global state: connector for sending packets to clients. */
    private final ServerConnector connector;
    
    /** Server-side global state: maps room ID strings to GameRoom objects; shared across all login handlers to track all active rooms on the server. */
    private final Map<String, GameRoom> rooms;
    
    /** Server-side global state: maps player connection ID to their assigned GameRoom; shared across all handlers to locate a player's current room. */
    private final Map<Integer, GameRoom> playerRooms;

    private final SessionStore sessionStore;

    private static final Logger logger = LoggerFactory.getLogger(LoginHandlerCommand.class);

    public LoginHandlerCommand(ServerConnector connector,
                        Map<String, GameRoom> rooms, Map<Integer, GameRoom> playerRooms, SessionStore sessionStore) {
        this.connector = connector;
        this.rooms = rooms;
        this.playerRooms = playerRooms;
        this.sessionStore = sessionStore;
    }

    @Override
    public void handle(PlayerConnection connection, Object packet) {
        LoginRequest request = (LoginRequest) packet;
        logger.info("Login request from: " + request.playerName);

        // sweep expired sessions on every new login
        sessionStore.invalidateExpired();

        // --- Reconnect branch: if the client presents a valid session token, reattach ---
        if (request.sessionToken != null && !request.sessionToken.isEmpty()) {
            handleReconnect(connection, request);
            return;
        }

        // --- Normal login branch ---
        if (connector.getConnectionCount() > NetworkConfig.MAX_PLAYERS) {
            connector.send(connection, LoginResponse.failure("Server is full"));
            return;
        }


        if (invalidPlayerName(request.playerName)) {
            connector.send(connection, LoginResponse.failure("Invalid player name"));
            return;
        }

        for (PlayerConnection existing : allConnections()) {
            if (request.playerName.equalsIgnoreCase(existing.getPlayerName())) {
                connector.send(connection, LoginResponse.failure("Name already taken"));
                return;
            }
        }

    
        int validatedWinScore = validateWinScore(request.getWinScore());
        GameRoom room = resolveRoomForLogin(connection.getId(), request, validatedWinScore);
        if (room == null) {
            return;
        }
        
        var playerName = spawnPlayer(room, connection, request, null, null);

        logger.info("Player '{}' joined room '{}' ({} / 2).", playerName, room.getRoomId(), room.getPlayerCount());
    }

    private String spawnPlayer(GameRoom room, PlayerConnection connection, LoginRequest request, GameRoom.Position lastPos, Integer preferredSlot) {
        var playerName = request.playerName.trim();
        connection.setPlayerName(playerName);
        if (lastPos != null) {
            connection.setPosition(lastPos.x(), lastPos.y());
        } else {
            connection.setPosition(
                GameConfigDefaults.SPAWN_X_OFFSET + (float) (Math.random() * GameConfigDefaults.SPAWN_X_RANGE),
                GameConfigDefaults.SPAWN_Y_OFFSET + (float) (Math.random() * GameConfigDefaults.SPAWN_Y_RANGE));
        }

        Player player = new Player(connection.getId(), playerName);
        room.addPlayer(connection, player, preferredSlot);
        playerRooms.put(connection.getId(), room);

        // Issue a fresh token for the new connection
        String newToken = sessionStore.createSession(connection.getId(), room.getRoomId());
        connector.send(connection.getId(), LoginResponse.success(connection.getId(), room.getWinScore(), newToken,
            room.getRoomId()));

        sendRoomStateToPlayer(connection, room);
        notifyRoomOfJoin(connection, room);

        return playerName;
    }

    private boolean invalidPlayerName(String playerName) {
        return playerName == null || playerName.trim().isEmpty();
    }

    /**
     * Handles a reconnect attempt: validates the session token, finds the original room,
     * reattaches the player to it, and issues a fresh token.
     */
    private void handleReconnect(PlayerConnection connection, LoginRequest request) {
        String oldToken = request.sessionToken;

        if (!sessionStore.isValid(oldToken)) {
            connector.send(connection, LoginResponse.failure("Session expired or invalid"));
            return;
        }

        String roomId = sessionStore.getRoomId(oldToken);
        GameRoom room = rooms.get(roomId);

        if (room == null) {
            sessionStore.invalidate(oldToken);
            connector.send(connection, LoginResponse.failure("Room no longer exists"));
            return;
        }

        if (room.isFull()) {
            sessionStore.invalidate(oldToken);
            connector.send(connection, LoginResponse.failure("Room is full"));
            return;
        }

        if (room.getPhase() == GameState.Phase.FINISHED) {
            sessionStore.invalidate(oldToken);
            connector.send(connection, LoginResponse.failure("Match has ended"));
            return;
        }

        if (invalidPlayerName(request.playerName)) {
            connector.send(connection, LoginResponse.failure("Invalid player name"));
            return;
        }

        // Consume old token and reattach
        int originalConnectionId = sessionStore.getConnectionId(oldToken);
        sessionStore.invalidate(oldToken);

        Integer preferredSlot = room.consumeDisconnectedSlot(originalConnectionId);
        var playerName = spawnPlayer(room, connection, request, room.getLastKnownPosition(originalConnectionId), preferredSlot);

        logger.info("Player '{}' reconnected to room '{}' ({} / 2).", playerName, room.getRoomId(), room.getPlayerCount());
    }

    /** Sends the list of other players already in the room to the newly joined player. */
    private void sendRoomStateToPlayer(PlayerConnection connection, GameRoom room) {
        PlayerList playerList = new PlayerList();
        for (Map.Entry<Integer, PlayerConnection> entry : room.getConnections().entrySet()) {
            PlayerConnection existing = entry.getValue();
            if (existing.getId() != connection.getId() && existing.getPlayerName() != null) {
                playerList.addPlayer(existing.getId(), existing.getPlayerName(), existing.getX(), existing.getY());
            }
        }
        connector.send(connection.getId(), playerList);
    }

    /** Notifies other room members that a player has joined. */
    private void notifyRoomOfJoin(PlayerConnection connection, GameRoom room) {
        PlayerJoined joinedPacket = new PlayerJoined(
                connection.getId(),
                connection.getPlayerName(),
                connection.getX(),
                connection.getY()
        );
        System.out.println("[LoginHandler] notifyRoomOfJoin: " + connection.getPlayerName() + 
                           " joined, room has " + room.getConnections().size() + " connections");
        for (Integer roomMemberId : room.getConnections().keySet()) {
            if (roomMemberId != connection.getId()) {
                System.out.println("[LoginHandler] Sending PlayerJoined to connection " + roomMemberId);
                connector.send(roomMemberId, joinedPacket);
                connector.send(roomMemberId, ChatMessage.system(connection.getPlayerName() + " has joined the game!"));
            }
        }
    }

    private GameRoom findOrCreateRoom(int hostConnectionId, int winScore) {
        for (GameRoom room : rooms.values()) {
            if (!room.isFull() && room.getPhase() == GameState.Phase.WAITING) {
                return room;
            }
        }
        return createRoom(hostConnectionId, winScore);
    }

    private GameRoom createRoom(int hostConnectionId, int winScore) {
        // Create new room with the host's configured winScore
        String roomId = "room-" + ROOM_COUNTER.incrementAndGet();
        GameRoom newRoom = new GameRoom(roomId, hostConnectionId, winScore);
        rooms.put(roomId, newRoom);
        return newRoom;
    }

    private GameRoom resolveRoomForLogin(int connectionId, LoginRequest request, int validatedWinScore) {
        if (Boolean.TRUE.equals(request.createRoom)) {
            return createRoom(connectionId, validatedWinScore);
        }

        String requestedRoomCode = request.roomCode == null ? null : request.roomCode.trim();
        if (requestedRoomCode != null && !requestedRoomCode.isEmpty()) {
            GameRoom requestedRoom = rooms.get(requestedRoomCode);
            if (requestedRoom == null) {
                connector.send(connectionId, LoginResponse.failure("Room not found"));
                return null;
            }
            if (requestedRoom.isFull()) {
                connector.send(connectionId, LoginResponse.failure("Room is full"));
                return null;
            }
            if (requestedRoom.getPhase() != GameState.Phase.WAITING) {
                connector.send(connectionId, LoginResponse.failure("Room is not joinable"));
                return null;
            }
            return requestedRoom;
        }

        return findOrCreateRoom(connectionId, validatedWinScore);
    }

    /**
     * Validates and normalizes the win score from request.
     * @param requestedWinScore the client-requested win score (may be null)
     * @return requested score if valid (> 0), otherwise configured default
     */
    private int validateWinScore(Integer requestedWinScore) {
        if (requestedWinScore != null && requestedWinScore > 0) {
            return requestedWinScore;
        }
        return GameConfig.getWinScore();
    }

    private List<PlayerConnection> allConnections() {
        List<PlayerConnection> list = new ArrayList<>();
        connector.forEachConnection((id, conn) -> list.add(conn));
        return list;
    }
}
