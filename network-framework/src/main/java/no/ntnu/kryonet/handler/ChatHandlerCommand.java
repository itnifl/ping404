package no.ntnu.kryonet.handler;

import no.ntnu.kryonet.core.INetworkServer.PlayerConnection;
import no.ntnu.kryonet.core.ServerConnector;
import no.ntnu.kryonet.dispatch.server.PacketHandlerCommand;
import no.ntnu.kryonet.packets.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Handles incoming {@link ChatMessage} packets by broadcasting them to all room-mates.
 *
 * <p>Room membership is resolved via an injected {@link RoomResolver} so this handler
 * has no dependency on game-specific room types. If the sender is not in any room,
 * a system error message is sent back to the sender only.</p>
 */
public class ChatHandlerCommand implements PacketHandlerCommand {

    private static final Logger logger = LoggerFactory.getLogger(ChatHandlerCommand.class);

    private final ServerConnector connector;
    private final RoomResolver roomResolver;

    public ChatHandlerCommand(ServerConnector connector, RoomResolver roomResolver) {
        this.connector    = connector;
        this.roomResolver = roomResolver;
    }

    @Override
    public void handle(PlayerConnection connection, Object packet) {
        ChatMessage message = (ChatMessage) packet;
        message.senderId   = connection.getId();
        message.senderName = connection.getPlayerName();
        message.timestamp  = System.currentTimeMillis();

        logger.info("Chat: {}", message);

        List<Integer> roomMates = roomResolver.roomMatesOf(connection.getId());
        if (roomMates.isEmpty()) {
            connector.send(connection.getId(),
                    ChatMessage.system("You are not in a room. Join a room to chat with others."));
        } else {
            for (int id : roomMates) {
                connector.send(id, message);
            }
        }
    }
}
