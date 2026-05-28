package no.ntnu.ping404.network;

import no.ntnu.ping404.network.packets.PauseRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class PacketTranslatorTest {

    @Test
    @DisplayName("Legacy Ping timestamp converts from ms to ns")
    void toFramework_pingTimestampConvertedToNanos() {
        no.ntnu.ping404.network.packets.Ping legacy = new no.ntnu.ping404.network.packets.Ping();
        legacy.timestamp = 1234L;
        legacy.sequence = 42;

        Object translated = PacketTranslator.toFramework(legacy);

        no.ntnu.kryonet.packets.Ping framework =
                assertInstanceOf(no.ntnu.kryonet.packets.Ping.class, translated);
        assertEquals(TimeUnit.MILLISECONDS.toNanos(1234L), framework.timestamp);
        assertEquals(42, framework.sequence);
    }

    @Test
    @DisplayName("Framework Ping timestamp converts from ns to ms")
    void toLegacy_pingTimestampConvertedToMillis() {
        no.ntnu.kryonet.packets.Ping framework = new no.ntnu.kryonet.packets.Ping();
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

        no.ntnu.kryonet.packets.Pong framework =
                assertInstanceOf(no.ntnu.kryonet.packets.Pong.class, translated);
        assertEquals(TimeUnit.MILLISECONDS.toNanos(111L), framework.originalTimestamp);
        assertEquals(TimeUnit.MILLISECONDS.toNanos(222L), framework.serverTimestamp);
        assertEquals(3, framework.sequence);
    }

    @Test
    @DisplayName("Framework Pong timestamps convert from ns to ms")
    void toLegacy_pongTimestampsConvertedToMillis() {
        no.ntnu.kryonet.packets.Pong framework = new no.ntnu.kryonet.packets.Pong();
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
}