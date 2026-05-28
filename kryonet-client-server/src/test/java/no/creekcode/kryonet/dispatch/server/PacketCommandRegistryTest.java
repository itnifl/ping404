package no.creekcode.kryonet.dispatch.server;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketCommandRegistryTest {

    @Test
    void register_overridesExistingHandlerForSamePacketClass() {
        PacketCommandRegistry registry = new PacketCommandRegistry();
        PacketHandlerCommand first = (connection, packet) -> { };
        PacketHandlerCommand second = (connection, packet) -> { };

        registry.register(PacketA.class, first);
        registry.register(PacketA.class, second);

        assertTrue(registry.hasHandler(PacketA.class));
        assertEquals(1, registry.registeredTypes().size());
    }

    @Test
    void mergeFrom_overridesExistingHandlerWithOtherRegistryEntry() {
        PacketCommandRegistry left = new PacketCommandRegistry();
        PacketCommandRegistry right = new PacketCommandRegistry();
        AtomicInteger marker = new AtomicInteger(0);

        left.register(PacketA.class, (connection, packet) -> marker.set(1));
        right.register(PacketA.class, (connection, packet) -> marker.set(2));

        left.mergeFrom(right);

        ServerPacketRouter router = new ServerPacketRouter();
        left.applyTo(router);
        router.dispatch(null, new PacketA());

        assertEquals(2, marker.get());
    }

    @Test
    void applyTo_registersHandlersInRouter() {
        PacketCommandRegistry registry = new PacketCommandRegistry();
        registry.register(PacketA.class, (connection, packet) -> { });
        registry.register(PacketB.class, (connection, packet) -> { });

        ServerPacketRouter router = new ServerPacketRouter();
        registry.applyTo(router);

        assertTrue(router.hasHandler(PacketA.class));
        assertTrue(router.hasHandler(PacketB.class));
    }

    @Test
    void hasHandler_nullPacketClass_throwsIllegalArgumentException() {
        PacketCommandRegistry registry = new PacketCommandRegistry();

        assertThrows(IllegalArgumentException.class, () -> registry.hasHandler(null));
    }

    private static final class PacketA { }
    private static final class PacketB { }
}