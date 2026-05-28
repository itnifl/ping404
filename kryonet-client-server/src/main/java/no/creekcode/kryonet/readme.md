# kryonet-client-server usage guide

This package provides a reusable KryoNet framework for multiplayer game networking.

## Context

| Field | Value |
|---|---|
| Module | kryonet-client-server |
| Public namespace | no.creekcode.kryonet |
| Role | Standalone networking framework used by game modules |
| Transport support | TCP and UDP |
| Primary integration style | Builder based API |

It includes:

- Packet registration with stable ordering
- Server and client builders
- Typed packet dispatching
- Built-in Ping, Pong, and Chat handlers
- Configurable TCP or UDP transport routing

## API quick reference

| Area | Entry point | Typical use |
|---|---|---|
| Server build | `NetworkFramework.serverBuilder()` | Configure packets, handlers, and start server |
| Client build | `NetworkFramework.clientBuilder()` | Configure packets/listeners and connect client |
| Shared packets | `withFrameworkPackets()` | Register framework packet set on both ends |
| Custom packets | `withPacket(MyPacket.class)` | Register game-specific packet types |
| Direct server handlers | `withHandler(PacketType.class, handler)` | Inline packet handling per packet type |
| Encapsulated handlers | `withCommandRegistry(registry)` | Apply reusable packet command mappings |
| UDP routing | `unreliable(PacketType.class)` | Mark latency-sensitive packets for UDP |
| Client thread dispatch | `withThreadDispatcher(dispatcher)` | Dispatch packet callbacks to render or UI thread |

## 1) Add dependency

In your root project settings include the module:

```gradle
include 'kryonet-client-server'
```

In your consumer module:

```gradle
dependencies {
    implementation project(':kryonet-client-server')
}
```

## 2) Packet registration model

The framework keeps registration deterministic:

1. Framework packets are registered first
2. User packets are registered second

Use:

- `withFrameworkPackets()` to register built-in packets
- `withPacket(MyPacket.class)` for each custom packet

## 3) Build a server

Minimal server with framework packets and Ping handling:

```java
import no.creekcode.kryonet.builder.NetworkFramework;
import no.creekcode.kryonet.core.INetworkServer;
import no.creekcode.kryonet.packets.Ping;

INetworkServer server = NetworkFramework.serverBuilder()
        .withFrameworkPackets()
        .withFrameworkHandler(Ping.class)
        .build();

server.start(27960, 27961);
```

## 4) Build a client

Minimal client that listens for Pong:

```java
import no.creekcode.kryonet.builder.NetworkFramework;
import no.creekcode.kryonet.core.INetworkClient;
import no.creekcode.kryonet.observer.NetworkListener;
import no.creekcode.kryonet.packets.Pong;

INetworkClient client = NetworkFramework.clientBuilder()
        .withFrameworkPackets()
        .onPacket(Pong.class, pong -> {
            System.out.println("RTT ms: " + pong.getRoundTripTime());
        })
        .addListener(new NetworkListener.Adapter() {
            @Override
            public void onConnected() {
                System.out.println("Connected");
            }
        })
        .build();

client.connect("localhost", 27960, 27961);
```

## 5) Register custom packets and handlers

### Server-side (direct registration)

```java
import no.creekcode.kryonet.builder.NetworkFramework;
import no.creekcode.kryonet.core.INetworkServer;

INetworkServer server = NetworkFramework.serverBuilder()
        .withFrameworkPackets()
        .withPacket(MyCustomRequest.class)
        .withPacket(MyCustomEvent.class)
        .withHandler(MyCustomRequest.class, (connection, packet) -> {
            MyCustomRequest req = (MyCustomRequest) packet;
            connection.sendToTCP(new MyCustomEvent(req.value));
        })
        .build();
```

### Server-side (encapsulated command registry)

```java
import no.creekcode.kryonet.builder.NetworkFramework;
import no.creekcode.kryonet.dispatch.server.PacketCommandRegistry;
import no.creekcode.kryonet.core.INetworkServer;

PacketCommandRegistry registry = new PacketCommandRegistry()
        .register(MyCustomRequest.class, (connection, packet) -> {
            MyCustomRequest req = (MyCustomRequest) packet;
            connection.sendToTCP(new MyCustomEvent(req.value));
        });

INetworkServer server = NetworkFramework.serverBuilder()
        .withFrameworkPackets()
        .withPacket(MyCustomRequest.class)
        .withPacket(MyCustomEvent.class)
        .withCommandRegistry(registry)
        .build();
```

### Client-side

```java
import no.creekcode.kryonet.builder.NetworkFramework;
import no.creekcode.kryonet.core.INetworkClient;

INetworkClient client = NetworkFramework.clientBuilder()
        .withFrameworkPackets()
        .withPacket(MyCustomRequest.class)
        .withPacket(MyCustomEvent.class)
        .onPacket(MyCustomEvent.class, event -> {
            System.out.println("Received value: " + event.value);
        })
        .build();
```

## 6) Use built-in framework handlers

Built-in server handlers are available for:

- `Ping.class`
- `Pong.class`
- `ChatMessage.class`

### Pong latency callback

```java
import no.creekcode.kryonet.packets.Pong;

var serverBuilder = NetworkFramework.serverBuilder()
        .withFrameworkPackets()
        .withFrameworkHandler(Pong.class)
        .withRttListener((connectionId, rttMs) -> {
            System.out.println("Conn=" + connectionId + " RTT=" + rttMs);
        });
```

### Chat room resolution callback

When using `ChatMessage.class`, provide a room resolver:

```java
import no.creekcode.kryonet.packets.ChatMessage;

var serverBuilder = NetworkFramework.serverBuilder()
        .withFrameworkPackets()
        .withFrameworkHandler(ChatMessage.class)
        .withRoomResolver(connectionId -> roomMembersFor(connectionId));
```

`roomMembersFor` should return all connection IDs in the sender room.

## 7) Configure unreliable UDP packet types

Use UDP for latency sensitive packets:

```java
INetworkServer server = NetworkFramework.serverBuilder()
        .withFrameworkPackets()
        .withPacket(PlayerInput.class)
        .unreliable(PlayerInput.class)
        .build();
```

Any packet class marked as unreliable is routed through UDP by `ServerConnector`.

## 8) Dispatch to game thread on client

Use a custom thread dispatcher for UI or render thread integration:

```java
import no.creekcode.kryonet.dispatch.client.ThreadDispatcher;

ThreadDispatcher gameThread = runnable -> postToGameThread(runnable);

INetworkClient client = NetworkFramework.clientBuilder()
        .withFrameworkPackets()
        .withThreadDispatcher(gameThread)
        .onPacket(MyStateSnapshot.class, snapshot -> applySnapshot(snapshot))
        .build();
```

## 9) Lifecycle and shutdown

Server:

```java
server.start(27960, 27961);
// ...
server.stop();
```

Client:

```java
client.connect("localhost", 27960, 27961);
// ...
client.disconnect();
client.dispose();
```

## 10) Common setup checklist

1. Register exactly the same packet classes on both server and client
2. Keep registration order identical across both ends
3. Call `withFrameworkPackets()` on both sides when using framework packets
4. Add user packet classes on both sides with `withPacket(...)`
5. Mark high frequency packets as unreliable if UDP is desired

## 11) Minimal end to end example

```java
INetworkServer server = NetworkFramework.serverBuilder()
        .withFrameworkPackets()
        .withFrameworkHandler(Ping.class)
        .build();

INetworkClient client = NetworkFramework.clientBuilder()
        .withFrameworkPackets()
        .onPacket(Pong.class, pong -> System.out.println(pong.getRoundTripTime()))
        .build();

server.start(27960, 27961);
client.connect("127.0.0.1", 27960, 27961);
client.sendUDP(new no.creekcode.kryonet.packets.Ping(1));
```

## Notes

- Prefer builders as the public entry point
- Internal transport classes are in `no.creekcode.kryonet.internal`
- Keep game specific logic in your own handlers and packet types
- Keep packet registration identical on client and server, including order
