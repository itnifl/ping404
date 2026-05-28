package no.creekcode.kryonet.dispatch.server;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Encapsulates packet-to-command mappings for server-side dispatch.
 *
 * <p>Applications can create reusable registries outside the framework module,
 * then pass them into {@code NetworkServerBuilder.withCommandRegistry(...)}.
 * This keeps the registration pattern inside the network module while exposing
 * a stable API for external configuration.</p>
 */
public final class PacketCommandRegistry {

    private final Map<Class<?>, PacketHandlerCommand> handlers = new ConcurrentHashMap<>();

    /** Registers or replaces the command for a packet class. */
    public <T> PacketCommandRegistry register(Class<T> packetClass, PacketHandlerCommand command) {
        if (packetClass == null) {
            throw new IllegalArgumentException("packetClass must not be null");
        }
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        handlers.put(packetClass, command);
        return this;
    }

    /** Merges all mappings from another registry into this one. */
    public PacketCommandRegistry mergeFrom(PacketCommandRegistry other) {
        if (other == null) {
            throw new IllegalArgumentException("other must not be null");
        }
        other.handlers.forEach(handlers::put);
        return this;
    }

    /** Applies all registered mappings to a router. */
    public void applyTo(ServerPacketRouter router) {
        if (router == null) {
            throw new IllegalArgumentException("router must not be null");
        }
        handlers.forEach(router::register);
    }

    public boolean hasHandler(Class<?> packetClass) {
        return handlers.containsKey(packetClass);
    }

    public Set<Class<?>> registeredTypes() {
        return Collections.unmodifiableSet(handlers.keySet());
    }
}