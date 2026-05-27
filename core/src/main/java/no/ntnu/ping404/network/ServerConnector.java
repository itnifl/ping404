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
    private final no.ntnu.kryonet.core.INetworkServer frameworkNetworkServer;

    public ServerConnector(INetworkServer networkServer) {
        this.networkServer = networkServer;
        this.frameworkNetworkServer = null;
    }

    public ServerConnector(no.ntnu.kryonet.core.INetworkServer frameworkNetworkServer) {
        this.networkServer = null;
        this.frameworkNetworkServer = frameworkNetworkServer;
    }

    public void send(int connectionId, Object packet) {
        if (isUnreliable(packet)) {
            if (networkServer != null) {
                networkServer.sendToUDP(connectionId, packet);
            } else {
                frameworkNetworkServer.sendToUDP(connectionId, packet);
            }
        } else {
            if (networkServer != null) {
                networkServer.sendToTCP(connectionId, packet);
            } else {
                frameworkNetworkServer.sendToTCP(connectionId, packet);
            }
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
            if (networkServer != null) {
                networkServer.sendToAllUDP(packet);
            } else {
                frameworkNetworkServer.sendToAllUDP(packet);
            }
        } else {
            if (networkServer != null) {
                networkServer.sendToAllTCP(packet);
            } else {
                frameworkNetworkServer.sendToAllTCP(packet);
            }
        }
    }

    public void broadcastExcept(int excludeId, Object packet) {
        if (isUnreliable(packet)) {
            if (networkServer != null) {
                networkServer.sendToAllExceptUDP(excludeId, packet);
            } else {
                frameworkNetworkServer.sendToAllExceptUDP(excludeId, packet);
            }
        } else {
            if (networkServer != null) {
                networkServer.sendToAllExceptTCP(excludeId, packet);
            } else {
                frameworkNetworkServer.sendToAllExceptTCP(excludeId, packet);
            }
        }
    }

    public int getConnectionCount() {
        return networkServer != null ? networkServer.getConnectionCount() : frameworkNetworkServer.getConnectionCount();
    }

    public void forEachConnection(BiConsumer<Integer, PlayerConnection> action) {
        if (networkServer != null) {
            networkServer.forEachConnection(action);
        } else {
            frameworkNetworkServer.forEachConnection((id, connection) -> action.accept(id, adaptConnection(connection)));
        }
    }

    private static PlayerConnection adaptConnection(no.ntnu.kryonet.core.INetworkServer.PlayerConnection connection) {
        return new PlayerConnection() {
            @Override
            public int getId() {
                return connection.getId();
            }

            @Override
            public String getRemoteAddress() {
                return connection.getRemoteAddress();
            }

            @Override
            public String getPlayerName() {
                return connection.getPlayerName();
            }

            @Override
            public void setPlayerName(String playerName) {
                connection.setPlayerName(playerName);
            }

            @Override
            public float getX() {
                return connection.getX();
            }

            @Override
            public void setX(float x) {
                connection.setX(x);
            }

            @Override
            public float getY() {
                return connection.getY();
            }

            @Override
            public void setY(float y) {
                connection.setY(y);
            }

            @Override
            public void setPosition(float x, float y) {
                connection.setPosition(x, y);
            }

            @Override
            public long getLastPositionUpdateTime() {
                return connection.getLastPositionUpdateTime();
            }

            @Override
            public long getLastHeartbeatTime() {
                return connection.getLastHeartbeatTime();
            }

            @Override
            public void updateLastHeartbeat() {
                connection.updateLastHeartbeat();
            }

            @Override
            public boolean isStale() {
                return connection.isStale();
            }

            @Override
            public void markStale() {
                connection.markStale();
            }

            @Override
            public void sendToTCP(Object packet) {
                connection.sendToTCP(packet);
            }

            @Override
            public void sendToUDP(Object packet) {
                connection.sendToUDP(packet);
            }
        };
    }

    private static boolean isUnreliable(Object packet) {
        return packet instanceof PlayerPosition
            || packet instanceof GameStateSnapshot
            || packet instanceof RoomMetricsSnapshot
            || packet instanceof Ping
            || packet instanceof Pong;
    }
}
