package no.ntnu.ping404.screens;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Screen;
import no.ntnu.ping404.network.packets.LoginResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HostScreen and JoinScreen packet handling (PR #133 review fixes).
 * 
 * <p>Tests cover:</p>
 * <ul>
 *   <li>LoginResponse.failure() handling</li>
 *   <li>Network disconnect recovery</li>
 * </ul>
 */
class LobbyScreenPacketHandlingTest {

    private static class RecordingGame extends Game {
        private Screen lastScreen;

        @Override
        public void create() {}

        @Override
        public void setScreen(Screen screen) {
            this.lastScreen = screen;
        }

        Screen getLastScreen() {
            return lastScreen;
        }
    }

    @Nested
    @DisplayName("HostScreen LoginResponse handling")
    class HostScreenLoginResponseTests {

        @Test
        @Tag("FR4.5")
        @DisplayName("LoginResponse.failure sets status message and returns to SETUP phase")
        void loginFailure_setsStatusAndReturnsToSetup() throws Exception {
            RecordingGame game = new RecordingGame();
            HostScreen screen = new HostScreen(game, "Alice", 5, "127.0.0.1");

            // Simulate receiving a failure response
            LoginResponse failure = LoginResponse.failure("Room is full");
            screen.onReceived(failure);

            // Verify phase returned to SETUP
            Field phaseField = HostScreen.class.getDeclaredField("phase");
            phaseField.setAccessible(true);
            Object phase = phaseField.get(screen);
            assertEquals("SETUP", phase.toString(), "Phase should return to SETUP on login failure");

            // Verify status message was set
            Field statusField = HostScreen.class.getDeclaredField("statusMessage");
            statusField.setAccessible(true);
            String status = (String) statusField.get(screen);
            assertTrue(status.contains("Room is full"), "Status should contain failure reason");
        }

        @Test
        @Tag("FR4.5")
        @DisplayName("LoginResponse.failure with null message uses default")
        void loginFailure_nullMessage_usesDefault() throws Exception {
            RecordingGame game = new RecordingGame();
            HostScreen screen = new HostScreen(game, "Alice", 5, "127.0.0.1");

            LoginResponse failure = new LoginResponse();
            failure.success = false;
            failure.message = null;
            screen.onReceived(failure);

            Field statusField = HostScreen.class.getDeclaredField("statusMessage");
            statusField.setAccessible(true);
            String status = (String) statusField.get(screen);
            assertFalse(status.isEmpty(), "Should use default message when failure.message is null");
        }
    }

    @Nested
    @DisplayName("JoinScreen LoginResponse handling")
    class JoinScreenLoginResponseTests {

        @Test
        @Tag("FR4.5")
        @DisplayName("LoginResponse.failure sets status message and returns to INPUT state")
        void loginFailure_setsStatusAndReturnsToInput() throws Exception {
            RecordingGame game = new RecordingGame();
            JoinScreen screen = new JoinScreen(game, "Bob", "room-123", "127.0.0.1");

            LoginResponse failure = LoginResponse.failure("Room not found");
            screen.onReceived(failure);

            Field stateField = JoinScreen.class.getDeclaredField("state");
            stateField.setAccessible(true);
            Object state = stateField.get(screen);
            assertEquals("INPUT", state.toString(), "State should return to INPUT on login failure");

            Field statusField = JoinScreen.class.getDeclaredField("statusMessage");
            statusField.setAccessible(true);
            String status = (String) statusField.get(screen);
            assertTrue(status.contains("Room not found"), "Status should contain failure reason");
        }

        @Test
        @Tag("FR4.5")
        @DisplayName("LoginResponse.failure with null message uses default")
        void loginFailure_nullMessage_usesDefault() throws Exception {
            RecordingGame game = new RecordingGame();
            JoinScreen screen = new JoinScreen(game, "Bob", "room-123", "127.0.0.1");

            LoginResponse failure = new LoginResponse();
            failure.success = false;
            failure.message = null;
            screen.onReceived(failure);

            Field statusField = JoinScreen.class.getDeclaredField("statusMessage");
            statusField.setAccessible(true);
            String status = (String) statusField.get(screen);
            assertFalse(status.isEmpty(), "Should use default message when failure.message is null");
        }
    }

    @Nested
    @DisplayName("Network disconnect recovery")
    class NetworkDisconnectTests {

        @Test
        @Tag("FR4.1")
        @DisplayName("HostScreen onDisconnected returns to SETUP phase with message")
        void hostScreen_onDisconnected_recoversToSetup() throws Exception {
            RecordingGame game = new RecordingGame();
            HostScreen screen = new HostScreen(game, "Alice", 5, "127.0.0.1");

            // Simulate disconnect
            screen.onDisconnected();

            Field phaseField = HostScreen.class.getDeclaredField("phase");
            phaseField.setAccessible(true);
            Object phase = phaseField.get(screen);
            assertEquals("SETUP", phase.toString(), "Phase should return to SETUP on disconnect");

            Field statusField = HostScreen.class.getDeclaredField("statusMessage");
            statusField.setAccessible(true);
            String status = (String) statusField.get(screen);
            assertTrue(status.toLowerCase().contains("disconnect"), "Should show disconnect message");
        }

        @Test
        @Tag("FR4.1")
        @DisplayName("JoinScreen onDisconnected returns to INPUT state with message")
        void joinScreen_onDisconnected_recoversToInput() throws Exception {
            RecordingGame game = new RecordingGame();
            JoinScreen screen = new JoinScreen(game, "Bob", "room-123", "127.0.0.1");

            // Simulate disconnect
            screen.onDisconnected();

            Field stateField = JoinScreen.class.getDeclaredField("state");
            stateField.setAccessible(true);
            Object state = stateField.get(screen);
            assertEquals("INPUT", state.toString(), "State should return to INPUT on disconnect");

            Field statusField = JoinScreen.class.getDeclaredField("statusMessage");
            statusField.setAccessible(true);
            String status = (String) statusField.get(screen);
            assertTrue(status.toLowerCase().contains("disconnect"), "Should show disconnect message");
        }
    }

    @Nested
    @DisplayName("Session token handling")
    class SessionTokenTests {

        @Test
        @Tag("A1")
        @Tag("FR4.3")
        @DisplayName("HostScreen stores session token from successful LoginResponse")
        void hostScreen_storesSessionToken() throws Exception {
            RecordingGame game = new RecordingGame();
            HostScreen screen = new HostScreen(game, "Alice", 5, "127.0.0.1");

            LoginResponse success = LoginResponse.success(1, 5, "token-abc-123", "room-1");
            screen.onReceived(success);

            Field tokenField = HostScreen.class.getDeclaredField("sessionToken");
            tokenField.setAccessible(true);
            String token = (String) tokenField.get(screen);
            assertEquals("token-abc-123", token, "Session token must be stored from LoginResponse");
        }

        @Test
        @Tag("A1")
        @Tag("FR4.3")
        @DisplayName("JoinScreen stores session token from successful LoginResponse")
        void joinScreen_storesSessionToken() throws Exception {
            RecordingGame game = new RecordingGame();
            JoinScreen screen = new JoinScreen(game, "Bob", "room-1", "127.0.0.1");

            LoginResponse success = LoginResponse.success(2, 5, "token-xyz-456", "room-1");
            screen.onReceived(success);

            Field tokenField = JoinScreen.class.getDeclaredField("sessionToken");
            tokenField.setAccessible(true);
            String token = (String) tokenField.get(screen);
            assertEquals("token-xyz-456", token, "Session token must be stored from LoginResponse");
        }

        @Test
        @Tag("A1")
        @Tag("FR4.3")
        @DisplayName("HostScreen preserves session token across successful logins")
        void hostScreen_preservesTokenAcrossLogins() throws Exception {
            RecordingGame game = new RecordingGame();
            HostScreen screen = new HostScreen(game, "Alice", 5, "127.0.0.1");

            // First login response
            LoginResponse first = LoginResponse.success(1, 5, "token-first", "room-1");
            screen.onReceived(first);

            Field tokenField = HostScreen.class.getDeclaredField("sessionToken");
            tokenField.setAccessible(true);
            String firstToken = (String) tokenField.get(screen);
            assertEquals("token-first", firstToken);

            // Subsequent login response (e.g., after reconnect)
            LoginResponse second = LoginResponse.success(1, 5, "token-second", "room-1");
            screen.onReceived(second);

            String secondToken = (String) tokenField.get(screen);
            assertEquals("token-second", secondToken, "Session token must be updated on subsequent logins");
            assertNotEquals(firstToken, secondToken, "New token must replace old token");
        }

        @Test
        @Tag("A1")
        @Tag("FR4.3")
        @DisplayName("JoinScreen transitions to WAITING state and stores token on success")
        void joinScreen_transitionsToWaitingWithToken() throws Exception {
            RecordingGame game = new RecordingGame();
            JoinScreen screen = new JoinScreen(game, "Bob", "room-1", "127.0.0.1");

            LoginResponse success = LoginResponse.success(2, 5, "token-waiting", "room-1");
            screen.onReceived(success);

            // Verify state changed to WAITING
            Field stateField = JoinScreen.class.getDeclaredField("state");
            stateField.setAccessible(true);
            Object state = stateField.get(screen);
            assertEquals("WAITING", state.toString(), "State should be WAITING after successful login");

            // Verify token stored
            Field tokenField = JoinScreen.class.getDeclaredField("sessionToken");
            tokenField.setAccessible(true);
            String token = (String) tokenField.get(screen);
            assertNotNull(token, "Session token must be stored");
            assertEquals("token-waiting", token);
        }

        @Test
        @Tag("A1")
        @Tag("FR4.3")
        @DisplayName("Session token is null initially before first login")
        void sessionToken_nullBeforeLogin() throws Exception {
            RecordingGame game = new RecordingGame();
            HostScreen hostScreen = new HostScreen(game, "Alice", 5, "127.0.0.1");
            JoinScreen joinScreen = new JoinScreen(game, "Bob", "room-1", "127.0.0.1");

            Field hostTokenField = HostScreen.class.getDeclaredField("sessionToken");
            hostTokenField.setAccessible(true);
            assertNull(hostTokenField.get(hostScreen), "HostScreen token should be null before login");

            Field joinTokenField = JoinScreen.class.getDeclaredField("sessionToken");
            joinTokenField.setAccessible(true);
            assertNull(joinTokenField.get(joinScreen), "JoinScreen token should be null before login");
        }
    }
}
