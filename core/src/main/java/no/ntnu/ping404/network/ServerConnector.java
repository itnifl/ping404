package no.ntnu.ping404.network;

import no.ntnu.ping404.network.INetworkServer.PlayerConnection;
import no.ntnu.ping404.network.packets.GameStateSnapshot;
import no.ntnu.ping404.network.packets.Ping;
import no.ntnu.ping404.network.packets.PlayerPosition;
import no.ntnu.ping404.network.packets.Pong;
import no.ntnu.ping404.network.packets.RoomMetricsSnapshot;

import java.util.function.BiConsumer;

/**
 * Pattern: Strategy (policy-based transport selection).
 *
 * Routes outgoing server packets to the correct transport channel. Uses
 * {@link INetworkServer} interface via constructor injection, enabling
 * seamless swapping of the underlying network implementation (M4 compliance).
 */
public class ServerConnector {

    private final INetworkServer networkServer;

    public ServerConnector(INetworkServer networkServer) {
        this.networkServer = networkServer;
    }

    public void send(int connectionId, Object packet) {
        if (isUnreliable(packet)) {
            networkServer.sendToUDP(connectionId, packet);
        } else {
            networkServer.sendToTCP(connectionId, packet);
        }
    }

    public void send(PlayerConnection connection, Object packet) {
        if (isUnreliable(packet)) {
            connection.sendToUDP(packet);
        } else {
            connection.sendToTCP(packet);
        }
    }

    public void broadcast(Object packet) {
        if (isUnreliable(packet)) {
            networkServer.sendToAllUDP(packet);
        } else {
            networkServer.sendToAllTCP(packet);
        }
    }

    public void broadcastExcept(int excludeId, Object packet) {
        if (isUnreliable(packet)) {
            networkServer.sendToAllExceptUDP(excludeId, packet);
        } else {
            networkServer.sendToAllExceptTCP(excludeId, packet);
        }
    }

    public int getConnectionCount() {
        return networkServer.getConnectionCount();
    }

    public void forEachConnection(BiConsumer<Integer, PlayerConnection> action) {
        networkServer.forEachConnection(action);
    }

    private static boolean isUnreliable(Object packet) {
        return packet instanceof PlayerPosition
            || packet instanceof GameStateSnapshot
            || packet instanceof RoomMetricsSnapshot
            || packet instanceof Ping
            || packet instanceof Pong;
    }
}
