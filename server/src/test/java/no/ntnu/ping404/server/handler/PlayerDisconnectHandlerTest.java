package no.ntnu.ping404.server.handler;

import com.esotericsoftware.kryonet.Connection;
import no.ntnu.ping404.model.Player;
import no.ntnu.ping404.network.NetworkKryoServer;
import no.ntnu.ping404.network.INetworkServer;
import no.ntnu.ping404.network.ServerConnector;
import no.ntnu.ping404.network.packets.HostMigration;
import no.ntnu.ping404.network.packets.PlayerLeft;
import no.ntnu.ping404.server.GameRoom;
import no.ntnu.ping404.server.SessionStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerDisconnectHandlerTest {

    private static final int HOST_ID = 1;
    private static final int GUEST_ID = 2;

    private static class TestConnection extends Connection {
        private final int id;

        TestConnection(int id) {
            this.id = id;
        }

        @Override
        public int getID() {
            return id;
        }
    }

    private static class RecordingServerConnector extends ServerConnector {
        private final List<SentPacket> sentPackets = new ArrayList<>();

        RecordingServerConnector() {
            super(new NetworkKryoServer());
        }

        @Override
        public void send(int connectionId, Object packet) {
            sentPackets.add(new SentPacket(connectionId, packet));
        }

        List<Object> getPacketsSentTo(int connectionId) {
            return sentPackets.stream()
                    .filter(p -> p.connectionId == connectionId)
                    .map(SentPacket::packet)
                    .toList();
        }

        private record SentPacket(int connectionId, Object packet) {}
    }

    private static INetworkServer.PlayerConnection conn(int id, String name) {
        INetworkServer.PlayerConnection connection = new NetworkKryoServer.KryoPlayerConnection(new TestConnection(id));
        connection.setPlayerName(name);
        return connection;
    }

    private RecordingServerConnector connector;
    private PlayerDisconnectHandler handler;
    private GameRoom room;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        connector = new RecordingServerConnector();
        handler = new PlayerDisconnectHandler(connector, new SessionStore());
        room = new GameRoom("room-1", HOST_ID);
        room.addPlayer(conn(HOST_ID, "Alice"), new Player(HOST_ID, "Alice"));
        room.addPlayer(conn(GUEST_ID, "Bob"), new Player(GUEST_ID, "Bob"));
    }

    @Test
    @Tag("A1")
    @Tag("FR4.1")
    @DisplayName("Host disconnect sends HostMigration to remaining player")
    void hostDisconnectSendsHostMigration() {
        handler.handlePlayerDisconnect(HOST_ID, room);

        List<Object> packetsToGuest = connector.getPacketsSentTo(GUEST_ID);
        assertTrue(packetsToGuest.stream().anyMatch(PlayerLeft.class::isInstance),
                "Remaining player should receive PlayerLeft");
        assertTrue(packetsToGuest.stream().anyMatch(HostMigration.class::isInstance),
                "Remaining player should receive HostMigration");
    }

    @Test
    @Tag("A1")
    @Tag("FR4.2")
    @DisplayName("Non-host disconnect does not send HostMigration")
    void nonHostDisconnectDoesNotSendHostMigration() {
        handler.handlePlayerDisconnect(GUEST_ID, room);

        List<Object> packetsToHost = connector.getPacketsSentTo(HOST_ID);
        assertEquals(1, packetsToHost.size(), "Host should only receive PlayerLeft");
        assertTrue(packetsToHost.get(0) instanceof PlayerLeft);
    }
}
