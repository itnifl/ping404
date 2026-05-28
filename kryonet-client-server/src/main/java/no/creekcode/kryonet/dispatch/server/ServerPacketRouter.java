package no.creekcode.kryonet.dispatch.server;

import no.creekcode.kryonet.core.INetworkServer.PlayerConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes incoming server-side packets to their registered {@link PacketHandlerCommand}.
 *
 * <p>Usage pattern: create one router per server instance, register one command per
 * packet class, then install the router as a server listener via
 * {@link no.creekcode.kryonet.core.INetworkServer#addListener}.</p>
 *
 * <pre>{@code
 * ServerPacketRouter router = new ServerPacketRouter();
 * router.register(LoginRequest.class, (conn, pkt) -> { ... });
 * router.register(ChatMessage.class,  (conn, pkt) -> { ... });
 * networkServer.addListener(new INetworkServer.ServerListenerAdapter() {
 *     @Override
 *     public void onReceived(PlayerConnection connection, Object packet) {
 *         router.dispatch(connection, packet);
 *     }
 * });
 * }</pre>
 */
public class ServerPacketRouter {

    private static final Logger logger = LoggerFactory.getLogger(ServerPacketRouter.class);

    private final Map<Class<?>, PacketHandlerCommand> handlers = new ConcurrentHashMap<>();

    /** Registers a command for the given packet class. Returns {@code this} for chaining. */
    public <T> ServerPacketRouter register(Class<T> packetClass, PacketHandlerCommand command) {
        handlers.put(packetClass, command);
        return this;
    }

    /**
     * Dispatches {@code packet} to the registered handler for its runtime type.
     * Exceptions thrown by handlers are caught and logged so one bad handler cannot
     * crash the calling thread.
     */
    public void dispatch(PlayerConnection connection, Object packet) {
        PacketHandlerCommand handler = handlers.get(packet.getClass());
        if (handler != null) {
            try {
                handler.handle(connection, packet);
            } catch (Exception e) {
                logger.error("Handler error for {} from connection {}: {}",
                        packet.getClass().getSimpleName(), connection.getId(), e.getMessage(), e);
            }
        } else {
            logger.debug("No handler registered for {}", packet.getClass().getSimpleName());
        }
    }

    public boolean hasHandler(Class<?> packetClass) {
        return handlers.containsKey(packetClass);
    }

    /** Returns an unmodifiable view of all registered packet classes. */
    public java.util.Set<Class<?>> registeredTypes() {
        return java.util.Collections.unmodifiableSet(handlers.keySet());
    }
}
