package no.creekcode.kryonet.dispatch.server;

import no.creekcode.kryonet.core.INetworkServer.PlayerConnection;

/**
 * Server-side command that handles one type of incoming packet.
 *
 * <p>Implementations receive the originating connection and the raw packet object.
 * Each implementation is responsible for casting {@code packet} to its expected type.</p>
 */
@FunctionalInterface
public interface PacketHandlerCommand {

    void handle(PlayerConnection connection, Object packet);
}
