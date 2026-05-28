package no.creekcode.kryonet.handler;

import no.creekcode.kryonet.core.INetworkServer.PlayerConnection;
import no.creekcode.kryonet.core.ServerConnector;
import no.creekcode.kryonet.dispatch.server.PacketHandlerCommand;
import no.creekcode.kryonet.packets.Ping;
import no.creekcode.kryonet.packets.Pong;

/**
 * Handles incoming {@link Ping} packets by replying immediately with a {@link Pong}.
 * Ships with the framework; register via {@code builder.withFrameworkHandler(Ping.class)}.
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
