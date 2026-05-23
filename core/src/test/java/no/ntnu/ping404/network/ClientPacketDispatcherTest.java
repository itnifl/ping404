package no.ntnu.ping404.network;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ClientPacketDispatcher - client-side packet routing (issue #48).
 */
class ClientPacketDispatcherTest {

    private ClientPacketDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new ClientPacketDispatcher();
    }

    @Nested
    @DisplayName("Handler registration")
    class HandlerRegistration {

        @Test
        @Tag("FR4.1")
        @DisplayName("Can register handler for packet type")
        void registerHandler_packetTypeRecognized() {
            dispatcher.register(TestPacket.class, packet -> {});
            assertTrue(dispatcher.hasHandler(TestPacket.class));
        }

        @Test
        @DisplayName("Unregistered packet type returns false")
        void unregisteredType_noHandler() {
            assertFalse(dispatcher.hasHandler(TestPacket.class));
        }

        @Test
        @DisplayName("Clear removes all handlers")
        void clear_removesHandlers() {
            dispatcher.register(TestPacket.class, packet -> {});
            dispatcher.clear();
            assertFalse(dispatcher.hasHandler(TestPacket.class));
        }

        @Test
        @DisplayName("Supports method chaining")
        void register_supportsChaining() {
            ClientPacketDispatcher result = dispatcher
                    .register(TestPacket.class, packet -> {})
                    .register(AnotherPacket.class, packet -> {});

            assertSame(dispatcher, result);
            assertTrue(dispatcher.hasHandler(TestPacket.class));
            assertTrue(dispatcher.hasHandler(AnotherPacket.class));
        }
    }

    @Nested
    @DisplayName("Packet dispatching")
    class PacketDispatching {

        @Test
        @Tag("FR4.1")
        @DisplayName("Dispatches packet to registered handler")
        void dispatch_invokesHandler() {
            AtomicBoolean handlerCalled = new AtomicBoolean(false);
            AtomicReference<TestPacket> receivedPacket = new AtomicReference<>();

            dispatcher.register(TestPacket.class, packet -> {
                handlerCalled.set(true);
                receivedPacket.set(packet);
            });

            TestPacket packet = new TestPacket("test-value");
            dispatcher.dispatch(packet);

            assertTrue(handlerCalled.get(), "Handler should be called");
            assertEquals("test-value", receivedPacket.get().value, "Packet should be passed to handler");
        }

        @Test
        @DisplayName("Ignores packets without registered handler")
        void dispatch_unregistered_ignored() {
            AtomicBoolean handlerCalled = new AtomicBoolean(false);
            dispatcher.register(TestPacket.class, packet -> handlerCalled.set(true));

            dispatcher.dispatch(new AnotherPacket());

            assertFalse(handlerCalled.get(), "Handler for different type should not be called");
        }

        @Test
        @DisplayName("Ignores null packets")
        void dispatch_null_ignored() {
            AtomicBoolean handlerCalled = new AtomicBoolean(false);
            dispatcher.register(TestPacket.class, packet -> handlerCalled.set(true));

            assertDoesNotThrow(() -> dispatcher.dispatch(null));

            assertFalse(handlerCalled.get());
        }

        @Test
        @Tag("FR4.2")
        @DisplayName("Each packet type routes to its own handler")
        void dispatch_routesToCorrectHandler() {
            AtomicReference<String> result = new AtomicReference<>("");

            dispatcher.register(TestPacket.class, packet -> result.set(result.get() + "A"));
            dispatcher.register(AnotherPacket.class, packet -> result.set(result.get() + "B"));

            dispatcher.dispatch(new TestPacket("x"));
            dispatcher.dispatch(new AnotherPacket());
            dispatcher.dispatch(new TestPacket("y"));

            assertEquals("ABA", result.get(), "Handlers should be called in dispatch order");
        }
    }

    @Nested
    @DisplayName("Execution behavior")
    class ExecutionBehavior {

        @Test
        @Tag("TC4")
        @DisplayName("Updates are executed in dispatch order")
        void updatesExecutedInOrder() {
            StringBuilder order = new StringBuilder();

            dispatcher.register(TestPacket.class, packet -> order.append(packet.value));

            dispatcher.dispatch(new TestPacket("1"));
            dispatcher.dispatch(new TestPacket("2"));
            dispatcher.dispatch(new TestPacket("3"));

            // When Gdx.app is null, updates execute synchronously
            assertEquals("123", order.toString(), "Updates should be executed in FIFO order");
        }

        @Test
        @DisplayName("Each handler is called exactly once per dispatch")
        void handlerCalledOncePerDispatch() {
            AtomicReference<Integer> count = new AtomicReference<>(0);
            dispatcher.register(TestPacket.class, packet -> count.set(count.get() + 1));

            dispatcher.dispatch(new TestPacket("x"));

            assertEquals(1, count.get(), "Handler should be called exactly once");
        }

        @Test
        @DisplayName("Dispatch is idempotent - does not execute handler more than once")
        void dispatch_idempotent_executesHandlerOnce() {
            AtomicReference<Integer> count = new AtomicReference<>(0);
            dispatcher.register(TestPacket.class, packet -> count.set(count.get() + 1));

            dispatcher.dispatch(new TestPacket("x"));

            assertEquals(1, count.get(), "Handler should only be called once");
        }
    }

    // Test packet classes
    static class TestPacket {
        String value;
        TestPacket(String value) { this.value = value; }
    }

    static class AnotherPacket {}
}
