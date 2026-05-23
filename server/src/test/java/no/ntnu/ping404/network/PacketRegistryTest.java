package no.ntnu.ping404.network;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Registration;
import no.ntnu.ping404.network.packets.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PacketRegistryTest {

    @Test
    @Tag("TC4")
    void packetRegistryRegistersWithoutThrowingExceptions() {
        Kryo kryo = new Kryo();
        assertDoesNotThrow(() -> PacketRegistry.register(kryo));
    }

    @Test
    @Tag("TC4")
    void loginRequestIsRegisteredBeforeLoginResponse() {
        RecordingKryo kryo = new RecordingKryo();
        PacketRegistry.register(kryo);
        List<Class<?>> order = kryo.getRegistrationOrder();
        int loginRequestIdx = order.indexOf(LoginRequest.class);
        int loginResponseIdx = order.indexOf(LoginResponse.class);
        assertTrue(loginRequestIdx >= 0, "LoginRequest must be registered");
        assertTrue(loginResponseIdx >= 0, "LoginResponse must be registered");
        assertTrue(loginRequestIdx < loginResponseIdx, "LoginRequest must be registered before LoginResponse");
    }

    @Test
    @Tag("TC4")
    void playerPositionIsRegistered() {
        RecordingKryo kryo = new RecordingKryo();
        PacketRegistry.register(kryo);
        assertTrue(kryo.getRegistrationOrder().contains(PlayerPosition.class),
                "PlayerPosition must be registered in PacketRegistry");
    }

    @Test
    @Tag("TC4")
    void chatMessageAndMessageTypeAreRegisteredConsecutively() {
        RecordingKryo kryo = new RecordingKryo();
        PacketRegistry.register(kryo);
        List<Class<?>> order = kryo.getRegistrationOrder();
        int chatIdx = order.indexOf(ChatMessage.class);
        int typeIdx = order.indexOf(ChatMessage.MessageType.class);
        assertTrue(chatIdx >= 0, "ChatMessage must be registered");
        assertEquals(chatIdx + 1, typeIdx, "ChatMessage.MessageType must be registered immediately after ChatMessage");
    }

    @Test
    @Tag("TC4")
    void allExpectedPacketClassesAreRegistered() {
        RecordingKryo kryo = new RecordingKryo();
        PacketRegistry.register(kryo);
        List<Class<?>> order = kryo.getRegistrationOrder();
        assertAll(
            () -> assertTrue(order.contains(LoginRequest.class),       "LoginRequest must be registered"),
            () -> assertTrue(order.contains(LoginResponse.class),      "LoginResponse must be registered"),
            () -> assertTrue(order.contains(PlayerPosition.class),     "PlayerPosition must be registered"),
            () -> assertTrue(order.contains(ChatMessage.class),        "ChatMessage must be registered"),
            () -> assertTrue(order.contains(PlayerJoined.class),       "PlayerJoined must be registered"),
            () -> assertTrue(order.contains(PlayerLeft.class),         "PlayerLeft must be registered"),
            () -> assertTrue(order.contains(PlayerList.class),         "PlayerList must be registered"),
            () -> assertTrue(order.contains(Ping.class),               "Ping must be registered"),
            () -> assertTrue(order.contains(Pong.class),               "Pong must be registered"),
            () -> assertTrue(order.contains(GameOver.class),           "GameOver must be registered"),
            () -> assertTrue(order.contains(GameStateSnapshot.class),  "GameStateSnapshot must be registered")
        );
    }

    @Test
    @Tag("TC4")
    void registrationOrderIsIdenticalOnClientAndServer() {
        RecordingKryo kryo1 = new RecordingKryo();
        RecordingKryo kryo2 = new RecordingKryo();
        PacketRegistry.register(kryo1);
        PacketRegistry.register(kryo2);
        assertEquals(kryo1.getRegistrationOrder(), kryo2.getRegistrationOrder(),
                "Registration order must be identical when called twice - client and server must agree");
    }

    private static class RecordingKryo extends Kryo {
        private final List<Class<?>> registrationOrder = new ArrayList<>();

        @Override
        public Registration register(Class type) {
            registrationOrder.add(type);
            return super.register(type);
        }

        public List<Class<?>> getRegistrationOrder() {
            return registrationOrder;
        }
    }
}
