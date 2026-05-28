package no.creekcode.kryonet.builder;

import no.creekcode.kryonet.core.INetworkServer;
import no.creekcode.kryonet.core.ServerConnector;
import no.creekcode.kryonet.dispatch.server.PacketCommandRegistry;
import no.creekcode.kryonet.dispatch.server.PacketHandlerCommand;
import no.creekcode.kryonet.dispatch.server.ServerPacketRouter;
import no.creekcode.kryonet.handler.ChatHandlerCommand;
import no.creekcode.kryonet.handler.PingHandlerCommand;
import no.creekcode.kryonet.handler.PongHandlerCommand;
import no.creekcode.kryonet.handler.RoomResolver;
import no.creekcode.kryonet.handler.RttListener;
import no.creekcode.kryonet.internal.NetworkKryoServer;
import no.creekcode.kryonet.packets.ChatMessage;
import no.creekcode.kryonet.packets.Ping;
import no.creekcode.kryonet.packets.PlayerPosition;
import no.creekcode.kryonet.packets.Pong;
import no.creekcode.kryonet.registry.FrameworkPacketRegistry;
import no.creekcode.kryonet.registry.UserPacketRegistry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class NetworkServerBuilder {

    private boolean frameworkPackets;
    private final UserPacketRegistry userRegistry = new UserPacketRegistry();
    private final Set<Class<?>> unreliableTypes = new HashSet<>();
    private final PacketCommandRegistry commandRegistry = new PacketCommandRegistry();
    private final List<INetworkServer.ServerListener> extraListeners = new ArrayList<>();

    private boolean handlePing;
    private boolean handlePong;
    private RttListener rttListener;
    private boolean handleChat;
    private RoomResolver roomResolver;

    NetworkServerBuilder() {}

    public NetworkServerBuilder withFrameworkPackets() {
        frameworkPackets = true;
        unreliableTypes.add(Ping.class);
        unreliableTypes.add(Pong.class);
        unreliableTypes.add(PlayerPosition.class);
        return this;
    }

    public NetworkServerBuilder withPacket(Class<?> packetClass) {
        userRegistry.register(packetClass);
        return this;
    }

    public NetworkServerBuilder unreliable(Class<?>... classes) {
        for (Class<?> c : classes) {
            unreliableTypes.add(c);
        }
        return this;
    }

    public NetworkServerBuilder withFrameworkHandler(Class<?> packetClass) {
        if (packetClass == Ping.class) {
            handlePing = true;
        } else if (packetClass == Pong.class) {
            handlePong = true;
        } else if (packetClass == ChatMessage.class) {
            handleChat = true;
        } else {
            throw new IllegalArgumentException("No built-in handler for: " + packetClass.getName());
        }
        return this;
    }

    public NetworkServerBuilder withRttListener(RttListener listener) {
        rttListener = listener;
        return this;
    }

    public NetworkServerBuilder withRoomResolver(RoomResolver resolver) {
        roomResolver = resolver;
        return this;
    }

    public NetworkServerBuilder withHandler(Class<?> packetClass, PacketHandlerCommand command) {
        commandRegistry.register(packetClass, command);
        return this;
    }

    /**
     * Merges externally prepared packet-command mappings into this builder.
     *
     * <p>This keeps command registration encapsulated in the network module while
     * still allowing applications to build and reuse command maps from outside.</p>
     */
    public NetworkServerBuilder withCommandRegistry(PacketCommandRegistry registry) {
        commandRegistry.mergeFrom(registry);
        return this;
    }

    public NetworkServerBuilder addListener(INetworkServer.ServerListener listener) {
        extraListeners.add(listener);
        return this;
    }

    public INetworkServer build() {
        if ((handlePing || handlePong || handleChat) && !frameworkPackets) {
            throw new IllegalStateException("withFrameworkPackets() must be called before withFrameworkHandler()");
        }
        if (handleChat && roomResolver == null) {
            throw new IllegalStateException("withRoomResolver() is required when handling ChatMessage");
        }

        NetworkKryoServer networkServer = new NetworkKryoServer(kryo -> {
            if (frameworkPackets) {
                FrameworkPacketRegistry.registerAll(kryo);
            }
            userRegistry.applyTo(kryo);
        });

        ServerConnector connector = new ServerConnector(networkServer, new HashSet<>(unreliableTypes));
        ServerPacketRouter router = new ServerPacketRouter();

        if (handlePing) {
            router.register(Ping.class, new PingHandlerCommand(connector));
        }
        if (handlePong) {
            router.register(Pong.class, new PongHandlerCommand(rttListener));
        }
        if (handleChat) {
            router.register(ChatMessage.class, new ChatHandlerCommand(connector, roomResolver));
        }
        commandRegistry.applyTo(router);

        networkServer.addListener(new INetworkServer.ServerListenerAdapter() {
            @Override
            public void onReceived(INetworkServer.PlayerConnection connection, Object packet) {
                router.dispatch(connection, packet);
            }
        });

        for (INetworkServer.ServerListener listener : extraListeners) {
            networkServer.addListener(listener);
        }

        return networkServer;
    }
}
