package no.ntnu.ping404.server.handler;

import no.ntnu.ping404.network.ServerConnector;
import no.ntnu.ping404.network.packets.HostMigration;
import no.ntnu.ping404.network.packets.PlayerLeft;
import no.ntnu.ping404.network.INetworkServer.PlayerConnection;
import no.ntnu.ping404.server.GameRoom;
import no.ntnu.ping404.server.SessionStore;

/**
 * Handles player disconnect events.
 * Not a PacketHandlerCommand since disconnects are connection events, not packets.
 */
public class PlayerDisconnectHandler {

    private final ServerConnector connector;
    private final SessionStore sessionStore;

    public PlayerDisconnectHandler(ServerConnector connector, SessionStore sessionStore) {
        this.connector = connector;
        this.sessionStore = sessionStore;
    }

    public void handlePlayerDisconnect(int connectionId, GameRoom room) {
        // Get player name before removing from room
        PlayerConnection disconnectedPlayer = room.getConnections().get(connectionId);
        String playerName = disconnectedPlayer != null ? disconnectedPlayer.getPlayerName() : "Unknown";

        // Create PlayerLeft notification
        PlayerLeft playerLeft = new PlayerLeft();
        playerLeft.playerId = connectionId;
        playerLeft.playerName = playerName;
        playerLeft.reason = "disconnected";

        // sweep expired sessions whenever a player disconnects
        sessionStore.invalidateExpired();

        // Broadcast to remaining room members (room-scoped, excluding disconnected player)
        room.broadcastExcept(connectionId, playerLeft, connector);

        // If the host is leaving, tell the remaining player they are promoted.
        // removePlayer (which reassigns hostConnectionId) runs AFTER this handler.
        if (room.getHostConnectionId() == connectionId) {
            room.broadcastExcept(connectionId, new HostMigration(), connector);
        }
    }

    /**
     * Broadcasts a room-scoped timeout notification when a disconnected player did not reconnect
     * within the grace window.
     */
    public void handleTimedOutDisconnect(int connectionId, String playerName, GameRoom room) {
        PlayerLeft playerLeft = new PlayerLeft();
        playerLeft.playerId = connectionId;
        playerLeft.playerName = playerName != null ? playerName : "Unknown";
        playerLeft.reason = "connection timed out";

        sessionStore.invalidateExpired();
        room.broadcastExcept(connectionId, playerLeft, connector);
    }
}
