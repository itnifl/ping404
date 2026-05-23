package no.ntnu.ping404.server.handler;

import no.ntnu.ping404.network.INetworkServer.PlayerConnection;
import no.ntnu.ping404.network.ServerConnector;
import no.ntnu.ping404.network.packets.HostMigration;
import no.ntnu.ping404.network.packets.PlayerLeft;
import no.ntnu.ping404.server.GameRoom;
import no.ntnu.ping404.server.SessionStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Handles intentional room leave requests from clients.
 * Unlike a network disconnect, this clears reconnection data and resets the room to WAITING.
 */
public class LeaveRoomHandlerCommand implements PacketHandlerCommand {

    private final ServerConnector connector;
    private final Map<Integer, GameRoom> playerRooms;
    private final SessionStore sessionStore;
    private static final Logger logger = LoggerFactory.getLogger(LeaveRoomHandlerCommand.class);

    public LeaveRoomHandlerCommand(ServerConnector connector, 
                                   Map<Integer, GameRoom> playerRooms,
                                   SessionStore sessionStore) {
        this.connector = connector;
        this.playerRooms = playerRooms;
        this.sessionStore = sessionStore;
    }

    @Override
    public void handle(PlayerConnection connection, Object packet) {
        GameRoom room = playerRooms.get(connection.getId());
        if (room == null) {
            logger.debug("LeaveRoom from connection {} with no assigned room", connection.getId());
            return;
        }

        String playerName = connection.getPlayerName();
        String roomId = room.getRoomId();
        boolean leavingPlayerIsHost = room.getHostConnectionId() == connection.getId();

        // Invalidate any session tokens for this player
        sessionStore.invalidateByConnectionId(connection.getId());

        // Remove player permanently (clears reconnection data, resets phase to WAITING)
        room.removePlayerPermanently(connection.getId());
        playerRooms.remove(connection.getId());

        // Notify remaining players; promote them if the host just left
        PlayerLeft leftPacket = new PlayerLeft(connection.getId(), playerName, "Left the room");
        for (Integer memberId : room.getConnections().keySet()) {
            connector.send(memberId, leftPacket);
            if (leavingPlayerIsHost) {
                connector.send(memberId, new HostMigration());
            }
        }

        logger.info("Player '{}' left room '{}' ({} / 2).", playerName, roomId, room.getPlayerCount());
    }
}
