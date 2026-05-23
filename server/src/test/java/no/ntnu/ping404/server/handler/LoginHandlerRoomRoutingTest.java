package no.ntnu.ping404.server.handler;

import no.ntnu.ping404.network.NetworkKryoServer;
import no.ntnu.ping404.network.INetworkServer;
import no.ntnu.ping404.network.ServerConnector;
import no.ntnu.ping404.network.packets.LoginRequest;
import no.ntnu.ping404.network.packets.LoginResponse;
import no.ntnu.ping404.server.GameRoom;
import no.ntnu.ping404.server.SessionStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class LoginHandlerRoomRoutingTest {

    private RecordingServerConnector connector;
    private Map<String, GameRoom> rooms;
    private Map<Integer, GameRoom> playerRooms;
    private LoginHandlerCommand handler;

    @BeforeEach
    void setUp() {
        connector = new RecordingServerConnector();
        rooms = new ConcurrentHashMap<>();
        playerRooms = new ConcurrentHashMap<>();
        handler = new LoginHandlerCommand(connector, rooms, playerRooms, new SessionStore());
    }

    @Test
    @Tag("FR1.3")
    @Tag("FR4.5")
    @DisplayName("Host create-room request creates a new room and returns room code")
    void hostCreateRoomCreatesRoomAndReturnsRoomCode() {
        INetworkServer.PlayerConnection host = new NetworkKryoServer.KryoPlayerConnection(101);
        LoginRequest request = new LoginRequest("Host", "1.0.0", 9);
        request.createRoom = true;

        handler.handle(host, request);

        assertEquals(1, rooms.size(), "A host create-room request should create a room");
        LoginResponse response = connector.getLastPacket(101, LoginResponse.class);
        assertNotNull(response, "Host should receive LoginResponse");
        assertTrue(response.success, "Host login should succeed");
        assertNotNull(response.roomCode, "Response should include authoritative room code");
        assertEquals(9, response.winScore, "Response should include effective room win score");
    }

    @Test
    @Tag("FR1.3")
    @Tag("FR4.5")
    @DisplayName("Join request with valid room code joins that exact room")
    void joinWithRoomCodeJoinsExactRoom() {
        INetworkServer.PlayerConnection host = new NetworkKryoServer.KryoPlayerConnection(101);
        LoginRequest hostRequest = new LoginRequest("Host", "1.0.0", 7);
        hostRequest.createRoom = true;
        handler.handle(host, hostRequest);

        LoginResponse hostResponse = connector.getLastPacket(101, LoginResponse.class);
        assertNotNull(hostResponse);

        INetworkServer.PlayerConnection guest = new NetworkKryoServer.KryoPlayerConnection(202);
        LoginRequest joinRequest = new LoginRequest("Guest", "1.0.0");
        joinRequest.roomCode = hostResponse.roomCode;
        handler.handle(guest, joinRequest);

        LoginResponse guestResponse = connector.getLastPacket(202, LoginResponse.class);
        assertNotNull(guestResponse, "Guest should receive LoginResponse");
        assertTrue(guestResponse.success, "Join with valid room code should succeed");
        assertEquals(hostResponse.roomCode, guestResponse.roomCode,
            "Guest should be joined into the exact requested room");
        assertSame(playerRooms.get(101), playerRooms.get(202), "Host and guest should be in same room");
    }

    @Test
    @Tag("FR1.3")
    @Tag("FR4.5")
    @DisplayName("Join request with invalid room code fails")
    void joinWithInvalidRoomCodeFails() {
        INetworkServer.PlayerConnection guest = new NetworkKryoServer.KryoPlayerConnection(202);
        LoginRequest joinRequest = new LoginRequest("Guest", "1.0.0");
        joinRequest.roomCode = "room-does-not-exist";

        handler.handle(guest, joinRequest);

        LoginResponse guestResponse = connector.getLastPacket(202, LoginResponse.class);
        assertNotNull(guestResponse, "Guest should receive LoginResponse");
        assertFalse(guestResponse.success, "Join with invalid room code should fail");
        assertTrue(guestResponse.message != null && guestResponse.message.toLowerCase().contains("room"),
            "Failure should explain room lookup failure");
    }

    @Test
    @Tag("FR1.6")
    @Tag("M3")
    @DisplayName("Join request cannot override existing room win score")
    void joinCannotOverrideRoomWinScore() {
        INetworkServer.PlayerConnection host = new NetworkKryoServer.KryoPlayerConnection(101);
        LoginRequest hostRequest = new LoginRequest("Host", "1.0.0", 7);
        hostRequest.createRoom = true;
        handler.handle(host, hostRequest);

        LoginResponse hostResponse = connector.getLastPacket(101, LoginResponse.class);
        assertNotNull(hostResponse);

        INetworkServer.PlayerConnection guest = new NetworkKryoServer.KryoPlayerConnection(202);
        LoginRequest joinRequest = new LoginRequest("Guest", "1.0.0", 15);
        joinRequest.roomCode = hostResponse.roomCode;
        handler.handle(guest, joinRequest);

        LoginResponse guestResponse = connector.getLastPacket(202, LoginResponse.class);
        assertNotNull(guestResponse);
        assertTrue(guestResponse.success, "Guest should still join valid room");
        assertEquals(7, guestResponse.winScore, "Guest cannot override room win score set by host");
    }

    private static class RecordingServerConnector extends ServerConnector {
        private final Map<Integer, List<Object>> packetsByConnection = new ConcurrentHashMap<>();

        RecordingServerConnector() {
            super(new NetworkKryoServer());
        }

        @Override
        public void send(int connectionId, Object packet) {
            packetsByConnection.computeIfAbsent(connectionId, id -> new ArrayList<>()).add(packet);
        }

        <T> T getLastPacket(int connectionId, Class<T> type) {
            List<Object> packets = packetsByConnection.get(connectionId);
            if (packets == null) {
                return null;
            }
            for (int i = packets.size() - 1; i >= 0; i--) {
                Object packet = packets.get(i);
                if (type.isInstance(packet)) {
                    return type.cast(packet);
                }
            }
            return null;
        }
    }
}
