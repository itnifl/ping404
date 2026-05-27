package no.ntnu.kryonet.registry;

import com.esotericsoftware.kryo.Kryo;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the set of user-defined packet classes to register with Kryo.
 *
 * <p>Populate this registry with all game-specific packet types before
 * passing it to a builder. The framework registers its own types first,
 * then applies entries from this registry in the order they were added.</p>
 */
public final class UserPacketRegistry {

    private final List<Class<?>> entries = new ArrayList<>();

    /** Registers one packet class. Returns {@code this} for chaining. */
    public UserPacketRegistry register(Class<?> packetClass) {
        if (packetClass == null) throw new IllegalArgumentException("packetClass must not be null");
        entries.add(packetClass);
        return this;
    }

    /** Registers multiple packet classes in order. Returns {@code this} for chaining. */
    public UserPacketRegistry registerAll(List<Class<?>> classes) {
        if (classes == null) throw new IllegalArgumentException("classes must not be null");
        entries.addAll(classes);
        return this;
    }

    /** Applies all registered classes to the given Kryo instance. */
    public void applyTo(Kryo kryo) {
        for (Class<?> c : entries) {
            kryo.register(c);
        }
    }

    /** Returns a read-only snapshot of the registered classes. */
    public List<Class<?>> getEntries() {
        return List.copyOf(entries);
    }
}
