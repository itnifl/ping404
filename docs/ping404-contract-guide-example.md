# Ping 404 and the Contract Component Guide

What is essential vs optional in `ping404-contract.puml`, and what each component's intent is.

---

## Essentials for The Protocol

These are **required** for client and server to communicate at all.

| Component | Intent |
|---|---|
| **LoginRequest** | Client sends player name to authenticate/join. Without this, the server can't identify who connected. |
| **LoginResponse** | Server replies with success/failure and assigns a `playerId`. The client needs this to know it's accepted and which ID it owns. |
| **PlayerPosition** | Core gameplay packet - transmits mallet x/y coordinates. This is the real-time game state that makes Air Hockey work. |
| **PlayerJoined** | Server broadcasts when a new player enters. Clients need this to spawn the opponent's mallet. |
| **PlayerLeft** | Server broadcasts when a player disconnects. Clients need this to remove the opponent and handle the game state. |
| **PacketRegistry** | Registers all packet classes with Kryo in a strict, identical order on both sides. If registration order differs, deserialization fails silently - packets arrive as garbage. |
| **NetworkConfig** (ports) | `TCP_PORT=27960`, `UDP_PORT=27961` - client and server must agree on where to connect. Without shared port constants, nothing connects. |

---

## Optional for Nice-to-Have Features

These add polish or diagnostics but aren't needed for the core game loop.

| Component | Intent | Why Optional |
|---|---|---|
| **Ping** | Client sends a timestamped probe to measure latency. | The game works without latency measurement. Useful for diagnostics and displaying ping to the player. |
| **Pong** | Server echoes the Ping back with its own timestamp. Client calculates round-trip time. | Companion to Ping - only needed if you implement latency measurement. |
| **ChatMessage** | Carries text messages between players with a `messageType` enum. | Air Hockey doesn't require chat. It's a social feature, not a gameplay one. |
| **PlayerList** | Sends the full list of connected players. | With only 2 players in Air Hockey, PlayerJoined/PlayerLeft already cover who's in the game. More relevant for a lobby or spectator system. |

---

## Implementation Convenience, ie. Not Protocol

These live in `core/` for code sharing, but they define **how** the code is structured internally, not **what** goes over the wire.

| Component | Intent | Why Not Protocol |
|---|---|---|
| **NetworkListener** | Client-side observer interface (`onConnected`, `onDisconnected`, `onReceived`). Lets multiple client classes react to network events. | This is a code organization pattern (Observer). The server doesn't need to know about it - it's internal to the client. |
| **ServerListener** | Server-side observer interface (`onClientConnected`, `onClientDisconnected`, `onReceived`). Same pattern, server side. | Internal to the server. The client never sees this interface. |
| **NetworkClient** | Facade wrapping KryoNet's `Client`. Provides `connect()`, `sendTCP()`, `sendUDP()`, etc. | A convenience wrapper. You could use KryoNet directly. The server doesn't care how the client sends - only what it sends. |
| **NetworkServer** | Facade wrapping KryoNet's `Server`. Provides `start()`, `sendToAllTCP()`, etc. | Same - a convenience wrapper. The protocol is defined by the packets, not the API surface. |
| **NetworkConfig** (non-port constants) | `MAX_PLAYERS=100`, `POSITION_UPDATE_RATE=1/20`, `CONNECTION_TIMEOUT=5000`. | These are tuning knobs and implementation details. Changing them doesn't break the protocol - the packets still serialize the same way. |
