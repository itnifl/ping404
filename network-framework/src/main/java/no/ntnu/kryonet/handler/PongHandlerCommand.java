package no.ntnu.kryonet.handler;

import no.ntnu.kryonet.core.INetworkServer.PlayerConnection;
import no.ntnu.kryonet.dispatch.server.PacketHandlerCommand;
import no.ntnu.kryonet.packets.Pong;

/**
 * Handles incoming {@link Pong} packets.
 *
 * <p>Always updates the connection heartbeat timestamp. If an {@link RttListener}
 * is provided, it also receives the round-trip time so the application can record
 * latency metrics without importing framework-internal types.</p>
 */
public class PongHandlerCommand implements PacketHandlerCommand {

    private final RttListener rttListener;

    /** No RTT reporting. */
    public PongHandlerCommand() {
        this(null);
    }

    public PongHandlerCommand(RttListener rttListener) {
        this.rttListener = rttListener;
    }

    @Override
    public void handle(PlayerConnection connection, Object packet) {
        connection.updateLastHeartbeat();
        if (rttListener != null && packet instanceof Pong pong) {
            rttListener.onRtt(connection.getId(), pong.getRoundTripTime());
        }
    }
}
