package no.ntnu.ping404.server.handler;

import no.ntnu.ping404.network.INetworkServer.PlayerConnection;

public interface PacketHandlerCommand {

    void handle(PlayerConnection connection, Object packet);
}
