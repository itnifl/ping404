package no.ntnu.kryonet.core;

import no.ntnu.kryonet.core.INetworkServer.PlayerConnection;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Routes outgoing server packets to the correct transport channel (TCP for reliable,
 * UDP for unreliable). Uses {@link INetworkServer} via constructor injection.
 *
 * <p>The set of unreliable packet types is configurable: pass an initial set to the
 * constructor, or call {@link #markUnreliable} at any time. By default only
 * {@code Ping} and {@code Pong} from the framework are unreliable; the builder
 * adds {@code PlayerPosition} automatically when framework packets are enabled.</p>
 */
public class ServerConnector {

    private final INetworkServer networkServer;
    private final Set<Class<?>> unreliableTypes;

    public ServerConnector(INetworkServer networkServer) {
        this(networkServer, new HashSet<>());
    }

    public ServerConnector(INetworkServer networkServer, Set<Class<?>> unreliableTypes) {
        this.networkServer = networkServer;
        this.unreliableTypes = new HashSet<>(unreliableTypes);
    }

    /** Marks a packet class as unreliable (sent via UDP). */
    public void markUnreliable(Class<?> packetClass) {
        unreliableTypes.add(packetClass);
    }

    public void send(int connectionId, Object packet) {
        if (isUnreliable(packet)) networkServer.sendToUDP(connectionId, packet);
        else                       networkServer.sendToTCP(connectionId, packet);
    }

    public void send(PlayerConnection connection, Object packet) {
        if (isUnreliable(packet)) connection.sendToUDP(packet);
        else                       connection.sendToTCP(packet);
    }

    public void broadcast(Object packet) {
        if (isUnreliable(packet)) networkServer.sendToAllUDP(packet);
        else                       networkServer.sendToAllTCP(packet);
    }

    public void broadcastExcept(int excludeId, Object packet) {
        if (isUnreliable(packet)) networkServer.sendToAllExceptUDP(excludeId, packet);
        else                       networkServer.sendToAllExceptTCP(excludeId, packet);
    }

    public int getConnectionCount() {
        return networkServer.getConnectionCount();
    }

    public void forEachConnection(BiConsumer<Integer, PlayerConnection> action) {
        networkServer.forEachConnection(action);
    }

    private boolean isUnreliable(Object packet) {
        return unreliableTypes.contains(packet.getClass());
    }
}
