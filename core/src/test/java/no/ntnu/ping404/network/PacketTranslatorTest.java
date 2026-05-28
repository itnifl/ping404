package no.ntnu.ping404.network;

import no.ntnu.ping404.network.packets.PauseRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketTranslatorTest {

    @Test
    @DisplayName("Legacy Ping timestamp converts from ms to ns")
    void toFramework_pingTimestampConvertedToNanos() {
        no.ntnu.ping404.network.packets.Ping legacy = new no.ntnu.ping404.network.packets.Ping();
        legacy.timestamp = 1234L;
        legacy.sequence = 42;

        Object translated = PacketTranslator.toFramework(legacy);

        no.creekcode.kryonet.packets.Ping framework =
                assertInstanceOf(no.creekcode.kryonet.packets.Ping.class, translated);
        assertEquals(TimeUnit.MILLISECONDS.toNanos(1234L), framework.timestamp);
        assertEquals(42, framework.sequence);
    }

    @Test
    @DisplayName("Framework Ping timestamp converts from ns to ms")
    void toLegacy_pingTimestampConvertedToMillis() {
        no.creekcode.kryonet.packets.Ping framework = new no.creekcode.kryonet.packets.Ping();
        framework.timestamp = TimeUnit.MILLISECONDS.toNanos(9876L);
        framework.sequence = 7;

        Object translated = PacketTranslator.toLegacy(framework);

        no.ntnu.ping404.network.packets.Ping legacy =
                assertInstanceOf(no.ntnu.ping404.network.packets.Ping.class, translated);
        assertEquals(9876L, legacy.timestamp);
        assertEquals(7, legacy.sequence);
    }

    @Test
    @DisplayName("Legacy Pong timestamps convert from ms to ns")
    void toFramework_pongTimestampsConvertedToNanos() {
        no.ntnu.ping404.network.packets.Pong legacy = new no.ntnu.ping404.network.packets.Pong();
        legacy.originalTimestamp = 111L;
        legacy.serverTimestamp = 222L;
        legacy.sequence = 3;

        Object translated = PacketTranslator.toFramework(legacy);

        no.creekcode.kryonet.packets.Pong framework =
                assertInstanceOf(no.creekcode.kryonet.packets.Pong.class, translated);
        assertEquals(TimeUnit.MILLISECONDS.toNanos(111L), framework.originalTimestamp);
        assertEquals(TimeUnit.MILLISECONDS.toNanos(222L), framework.serverTimestamp);
        assertEquals(3, framework.sequence);
    }

    @Test
    @DisplayName("Framework Pong timestamps convert from ns to ms")
    void toLegacy_pongTimestampsConvertedToMillis() {
        no.creekcode.kryonet.packets.Pong framework = new no.creekcode.kryonet.packets.Pong();
        framework.originalTimestamp = TimeUnit.MILLISECONDS.toNanos(333L);
        framework.serverTimestamp = TimeUnit.MILLISECONDS.toNanos(444L);
        framework.sequence = 9;

        Object translated = PacketTranslator.toLegacy(framework);

        no.ntnu.ping404.network.packets.Pong legacy =
                assertInstanceOf(no.ntnu.ping404.network.packets.Pong.class, translated);
        assertEquals(333L, legacy.originalTimestamp);
        assertEquals(444L, legacy.serverTimestamp);
        assertEquals(9, legacy.sequence);
    }

    @Test
    @DisplayName("Packet type without target class is passed through unchanged")
    void toFramework_missingTargetClassReturnsOriginal() {
        PauseRequest pauseRequest = new PauseRequest(12);

        Object translated = PacketTranslator.toFramework(pauseRequest);

        assertSame(pauseRequest, translated);
    }

    @Test
    @DisplayName("Legacy packet inside java.util.List is translated")
    void toFramework_translatesPacketInsideCollection() {
        no.ntnu.ping404.network.packets.Ping legacy = new no.ntnu.ping404.network.packets.Ping();
        legacy.timestamp = 55L;
        legacy.sequence = 2;
        List<Object> payload = new ArrayList<>();
        payload.add(legacy);

        Object translated = PacketTranslator.toFramework(payload);

        List<?> translatedList = assertInstanceOf(List.class, translated);
        assertTrue(translatedList.get(0) instanceof no.creekcode.kryonet.packets.Ping);
    }

    @Test
    @DisplayName("Legacy packet inside Object array is translated")
    void toFramework_translatesPacketInsideArray() {
        no.ntnu.ping404.network.packets.Pong legacy = new no.ntnu.ping404.network.packets.Pong();
        legacy.originalTimestamp = 11L;
        legacy.serverTimestamp = 22L;
        legacy.sequence = 1;
        Object[] payload = new Object[] { legacy };

        Object translated = PacketTranslator.toFramework(payload);

        Object[] translatedArray = assertInstanceOf(Object[].class, translated);
        assertTrue(translatedArray[0] instanceof no.creekcode.kryonet.packets.Pong);
    }
}