package no.ntnu.ping404.server.handler;

import no.ntnu.ping404.network.INetworkServer.PlayerConnection;
import no.ntnu.ping404.network.ServerConnector;
import no.ntnu.ping404.network.packets.Ping;
import no.ntnu.ping404.network.packets.Pong;

/**
 * Handles incoming ping packets by responding immediately with a pong packet.
 * Used for connection health checks between client and server.
 */
public class PingHandlerCommand implements PacketHandlerCommand {

    private final ServerConnector connector;

    public PingHandlerCommand(ServerConnector connector) {
        this.connector = connector;
    }

    @Override
    public void handle(PlayerConnection connection, Object packet) {
        connector.send(connection.getId(), new Pong((Ping) packet));
    }
}
