package no.ntnu.ping404.server.handler;

import no.ntnu.ping404.network.INetworkServer.PlayerConnection;
import no.ntnu.ping404.network.ServerConnector;
import no.ntnu.ping404.network.packets.ChatMessage;
import no.ntnu.ping404.server.GameRoom;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles chat packets. */
public class ChatHandlerCommand implements PacketHandlerCommand {

    private final ServerConnector connector;
    private final Map<Integer, GameRoom> playerRooms;
    private static final Logger logger = LoggerFactory.getLogger(ChatHandlerCommand.class);

    public ChatHandlerCommand(ServerConnector connector, Map<Integer, GameRoom> playerRooms) {
        this.connector = connector;
        this.playerRooms = playerRooms;
    }

    @Override
    public void handle(PlayerConnection connection, Object packet) {
        ChatMessage message = (ChatMessage) packet;
        message.senderId = connection.getId();
        message.senderName = connection.getPlayerName();
        message.timestamp = System.currentTimeMillis();

        logger.info("Chat: " + message);
        GameRoom room = playerRooms.get(connection.getId());
        if (room != null) {
            room.broadcast(message, connector);
        } else {
            connector.send(connection, ChatMessage.system("You are not in a room. Join a room to chat with others."));
        }
    }
}
