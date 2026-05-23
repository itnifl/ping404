package no.ntnu.ping404.network.packets;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginPacketTest {

    @Test
    @Tag("FR4.5")
    void loginRequestCarriesRoomCodeAndCreateRoomFlagAcrossKryoRoundTrip() {
        Kryo kryo = new Kryo();
        PacketRegistry.register(kryo);

        LoginRequest original = new LoginRequest("Alice", "1.0.0", 9);
        original.roomCode = "room-42";
        original.createRoom = true;
        original.sessionToken = "token-abc";

        byte[] bytes;
        try (Output output = new Output(1024)) {
            kryo.writeObject(output, original);
            bytes = output.toBytes();
        }

        LoginRequest result;
        try (Input input = new Input(bytes)) {
            result = kryo.readObject(input, LoginRequest.class);
        }

        assertEquals("Alice", result.playerName);
        assertEquals("1.0.0", result.clientVersion);
        assertEquals(9, result.winScore);
        assertEquals("room-42", result.roomCode);
        assertEquals(Boolean.TRUE, result.createRoom);
        assertEquals("token-abc", result.sessionToken);
    }

    @Test
    @Tag("FR4.5")
    void loginResponseCarriesAuthoritativeRoomCodeAcrossKryoRoundTrip() {
        Kryo kryo = new Kryo();
        PacketRegistry.register(kryo);

        LoginResponse original = LoginResponse.success(101, 7, "token-xyz", "room-99");

        byte[] bytes;
        try (Output output = new Output(1024)) {
            kryo.writeObject(output, original);
            bytes = output.toBytes();
        }

        LoginResponse result;
        try (Input input = new Input(bytes)) {
            result = kryo.readObject(input, LoginResponse.class);
        }

        assertTrue(result.success);
        assertEquals(101, result.playerId);
        assertEquals(7, result.winScore);
        assertEquals("token-xyz", result.sessionToken);
        assertEquals("room-99", result.roomCode);
    }
}
