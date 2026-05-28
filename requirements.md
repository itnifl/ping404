# Ping 404 Requirements

This document summarizes and structures requirements from TDT4240_Requirements.pdf.

## 1. Context Capture

| Field | Summary |
|---|---|
| Project | Ping 404, online multiplayer Air Hockey |
| Course | TDT4240 Software Architecture |
| Team | Group 09 |
| Chosen COTS | LibGDX, KryoNet, Kryo |
| Core architecture | Client server, authoritative server for game state |
| Primary quality attribute | Modifiability |
| Secondary quality attributes | Performance, Availability, Usability |
| Game mode | Real time, two player, networked matches |

### 1.1 Scope Notes

| Topic | Context |
|---|---|
| Server responsibilities | Physics, collision detection, scoring, and rule enforcement |
| Client responsibilities | Send player input and render server state updates |
| Win condition | Configurable goal threshold set before game start |
| Interaction style | Fast paced gameplay with responsive controls and synchronized state |

## 2. Functional Requirements

### 2.1 FR1, Startup and Session Management

| ID | Requirement | Priority | Acceptance criteria |
|---|---|---|---|
| FR1.1 | Show main menu with Host Game, Join Game, and Settings | High | Menu loads and buttons respond to input |
| FR1.2 | Allow hosting a multiplayer session | High | Session is created and a session code is generated |
| FR1.3 | Allow joining by session code and player name | High | Player connects with a valid code |
| FR1.4 | Host can start only when both players are connected | High | Start button disabled until second player joins |
| FR1.5 | Support real time two player multiplayer | High | Both players see synchronized gameplay |
| FR1.6 | Host can configure win score before game start | Medium | Selected score limit applies to next match |

### 2.2 FR2, Core Gameplay Mechanics

| ID | Requirement | Priority | Acceptance criteria |
|---|---|---|---|
| FR2.1 | Player can move mallet to hit puck | High | Mallet follows player input |
| FR2.2 | Mallet stays within player half | High | Mallet cannot cross center line |
| FR2.3 | Puck moves continuously during gameplay | High | Puck remains in motion unless paused or after goal |
| FR2.4 | Puck rebounds from walls | High | Direction changes on wall collision |
| FR2.5 | Puck rebounds from mallet | High | Direction changes on mallet collision |
| FR2.6 | Puck cannot stay on one half for more than 7 seconds | Medium | Puck is reset to center after 7 seconds |
| FR2.7 | Goal detection scores for opponent | High | Score increments when puck crosses goal line |
| FR2.8 | Scoreboard visible during game | High | Updated score shown after each goal |
| FR2.9 | Match ends at configured score limit | High | Game Over screen is shown at score limit |
| FR2.10 | Reposition puck after goal | Medium | Puck appears on conceding player half |

### 2.3 FR3, Game Termination Logic

| ID | Requirement | Priority | Acceptance criteria |
|---|---|---|---|
| FR3.1 | Show Game Over message when match ends | High | End screen is shown when win condition is met |
| FR3.2 | Show final score at match end | High | Final score visible on Game Over screen |
| FR3.3 | Allow return to main menu after match | High | Main menu loads on selection |

### 2.4 FR4, Usability Features

| ID | Requirement | Priority | Acceptance criteria |
|---|---|---|---|
| FR4.1 | Allow pause | Medium | Gameplay stops for both players on pause |
| FR4.2 | Allow resume | Medium | Gameplay continues from paused state |
| FR4.3 | Allow exit to main menu | Medium | Match ends and main menu loads for both players |
| FR4.4 | Allow sound effects toggle during gameplay | Medium | Sound effects muted when disabled |
| FR4.5 | Allow user display name input | High | Entered name shown during match |

## 3. Quality Attribute Scenarios

### 3.1 Modifiability

| ID | Source | Stimulus | Environment | Response | Response measure |
|---|---|---|---|---|---|
| M1 | Developer | Change board size for another device | Design time | Update one configuration constant | At most 1 file and 5 lines changed |
| M2 | Developer | Add third player slot or perspective | Design time | Isolate changes to relevant session rules and roles | At most 3 classes changed, no rendering or physics changes |
| M3 | Product owner | Change winning score threshold | Development | Configure on server or pass at start | Client recompilation not required for runtime parameter |
| M4 | Developer | Replace KryoNet | Design time | Limit impact to facade layer | At most 4 classes changed |

### 3.2 Performance

| ID | Source | Stimulus | Environment | Response | Response measure |
|---|---|---|---|---|---|
| P1 | Player | Move mallet during gameplay | Normal operation | Server validates and broadcasts updates | End to end update under 100 ms on standard LAN |
| P2 | System | Keep rendering smooth despite network variance | Normal operation | Client interpolates between state updates | At least 60 FPS and no puck animation halt |
| P3 | System | Run two active game rooms simultaneously | Normal operation | Process packets and broadcast without delay | All clients receive updates within 50 ms |

### 3.3 Usability

| ID | Source | Stimulus | Environment | Response | Response measure |
|---|---|---|---|---|---|
| U1 | New player | First time join attempt | First launch | Guide user through server IP and connect with feedback | User joins within 60 seconds without external instructions |
| U2 | Player | Invalid touch outside legal area | Normal gameplay | Clamp mallet at center line boundary | No visual glitch and no error message |
| U3 | Player | Player reaches winning score | Normal gameplay | Stop match and show clear Game Over options | Game Over shown within 500 ms, rematch or menu in one tap |

## 4. COTS Components

| Component | License and version | Role and rationale |
|---|---|---|
| LibGDX | Apache 2.0, v1.12.1 | Cross platform rendering, input, audio, preferences |
| KryoNet | BSD, v2.22 | TCP and UDP networking for real time multiplayer |
| Kryo | BSD | Efficient binary serialization used by network layer |
| Android SDK | Apache 2.0 and proprietary | Mobile platform support, min API 24, target API 35 |
| Java JDK | GPL v2, 11+ | Runtime and toolchain compatibility |
| Gradle | Apache 2.0 | Build automation and dependency management |

## 5. Technical Constraints

| ID | Constraint type | Description |
|---|---|---|
| TC1 | Platform and connectivity | Android support and active WiFi or 4G required |
| TC2 | Performance targets | Target under 100 ms latency and about 20 Hz state updates |
| TC3 | Protocol | UDP for high frequency lag tolerant data, TCP for critical state |
| TC4 | Compatibility | Identical Kryo class registration order on client and server |
| TC5 | Concurrency | Server uses thread safe collections and race safe patterns |
| TC6 | Architecture | Required client server model with authoritative server |
| TC7 | Hosting | Dynamic hosting, local, cloud, or dedicated, no fixed deployment assumption |
| TC8 | Screen orientation | Landscape mode only |

## 6. Source and Traceability

| Source | Purpose |
|---|---|
| TDT4240_Requirements.pdf | Full original requirements and context |
| requirements.md | Implementation friendly tabular summary |

This summary preserves requirement IDs so tests and implementation tasks can reference the same identifiers.
