package no.ntnu.ping404.screens;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.math.Rectangle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for screen navigation routing (Issue #44).
 *
 * <p>These tests verify the screen transition contracts defined in {@link Ping404Game}:</p>
 * <ul>
 *   <li>FR1.1: App launches to MenuScreen (main menu)</li>
 *   <li>FR3.3: GameOverScreen returns to MenuScreen</li>
 *   <li>M4: Screen transitions are modular and consistent</li>
 * </ul>
 *
 * @see Ping404Game
 * @see MenuScreen
 * @see GameOverScreen
 */
class ScreenNavigationContractTest {

    private static class RecordingGame extends Game {
        private Screen lastScreen;

        @Override
        public void create() {
        }

        @Override
        public void setScreen(Screen screen) {
            this.lastScreen = screen;
        }

        Screen getLastScreen() {
            return lastScreen;
        }
    }

    private static class RecordingPing404Game extends Ping404Game {
        private Screen lastScreen;

        @Override
        public void setScreen(Screen screen) {
            this.lastScreen = screen;
        }

        Screen getLastScreen() {
            return lastScreen;
        }
    }

    private static class ThrowingAudioPing404Game extends Ping404Game {
        private Screen lastScreen;

        @Override
        public void setScreen(Screen screen) {
            this.lastScreen = screen;
        }

        @Override
        protected void initializeAudio() {
            throw new RuntimeException("Simulated audio startup failure");
        }

        Screen getLastScreen() {
            return lastScreen;
        }
    }

    private static class FixedWorldMenuScreen extends MenuScreen {
        private final float worldWidth;
        private final float worldHeight;

        FixedWorldMenuScreen(Game game, float worldWidth, float worldHeight) {
            super(game);
            this.worldWidth = worldWidth;
            this.worldHeight = worldHeight;
        }

        @Override
        protected float getWorldWidth() {
            return worldWidth;
        }

        @Override
        protected float getWorldHeight() {
            return worldHeight;
        }
    }

    @Nested
    @DisplayName("FR1.1: App launch routes to main menu")
    class AppLaunchTests {

        @Test
        @Tag("FR1.1")
        @Tag("TC1")
        @DisplayName("Ping404Game.create() sets MenuScreen as initial screen")
        void createSetsMenuScreen() {
            RecordingPing404Game game = new RecordingPing404Game();

            game.create();

            assertNotNull(game.getLastScreen());
            assertInstanceOf(MenuScreen.class, game.getLastScreen());
        }

        @Test
        @Tag("FR1.1")
        @DisplayName("Ping404Game.create() still reaches MenuScreen when audio initialization fails")
        void createContinuesToMenuWhenAudioInitializationFails() {
            ThrowingAudioPing404Game game = new ThrowingAudioPing404Game();

            game.create();

            assertNotNull(game.getLastScreen());
            assertInstanceOf(MenuScreen.class, game.getLastScreen());
        }
    }

    @Nested
    @DisplayName("MenuScreen navigation")
    class MenuScreenNavigationTests {

        @Test
        @Tag("FR1.1")
        @DisplayName("Host Game button navigates to HostScreen")
        void hostButtonNavigatesToHostScreen() {
            RecordingGame game = new RecordingGame();
            MenuScreen screen = new MenuScreen(game);
            screen.onTextInputComplete("name", "Alice");
            screen.onTextInputComplete("ip", "127.0.0.1");

            screen.navigateToHost();

            assertNotNull(game.getLastScreen());
            assertInstanceOf(HostScreen.class, game.getLastScreen());
        }

        @Test
        @Tag("FR1.1")
        @DisplayName("Join Game button navigates to JoinScreen")
        void joinButtonNavigatesToJoinScreen() {
            RecordingGame game = new RecordingGame();
            MenuScreen screen = new MenuScreen(game);
            screen.onTextInputComplete("name", "Alice");
            screen.onTextInputComplete("session", "room-1");
            screen.onTextInputComplete("ip", "127.0.0.1");

            screen.navigateToJoin();

            assertNotNull(game.getLastScreen());
            assertInstanceOf(JoinScreen.class, game.getLastScreen());
        }

        @Test
        @Tag("FR1.1")
        @DisplayName("Settings button navigates to SettingsScreen")
        void settingsButtonNavigatesToSettingsScreen() {
            RecordingGame game = new RecordingGame();
            MenuScreen screen = new MenuScreen(game);

            screen.navigateToSettings();

            assertNotNull(game.getLastScreen());
            assertInstanceOf(SettingsScreen.class, game.getLastScreen());
        }

        @Test
        @Tag("FR1.6")
        @DisplayName("Win score selector clamps within allowed range")
        void winScoreSelectorClampsRange() {
            RecordingGame game = new RecordingGame();
            MenuScreen screen = new MenuScreen(game);

            screen.adjustWinScore(-1000);
            assertEquals(3, screen.getWinScore());

            screen.adjustWinScore(1000);
            assertEquals(15, screen.getWinScore());
        }

        @Test
        @Tag("FR1.1")
        @DisplayName("MenuScreen resize before show does not throw")
        void resizeBeforeShowDoesNotThrow() {
            RecordingGame game = new RecordingGame();
            MenuScreen screen = new MenuScreen(game);

            assertDoesNotThrow(() -> screen.resize(1920, 1080));
        }

        @Test
        @Tag("FR1.1")
        @DisplayName("MenuScreen layout centers buttons using the active world width")
        void menuLayoutCentersButtonsUsingActiveWorldWidth() {
            RecordingGame game = new RecordingGame();
            FixedWorldMenuScreen screen = new FixedWorldMenuScreen(game, 1422f, 800f);

            invokeMenuLayout(screen);

            Rectangle nameButton = extractRectangle(screen, "nameButton");
            Rectangle hostButton = extractRectangle(screen, "hostButton");

            assertNotNull(nameButton);
            assertNotNull(hostButton);
            assertEquals(1422f / 2f, nameButton.x + nameButton.width / 2f, 0.001f);
            assertEquals(1422f / 2f, hostButton.x + hostButton.width / 2f, 0.001f);
        }
    }

    @Nested
    @DisplayName("FR3.3: GameOverScreen returns to menu")
    class GameOverNavigationTests {

        @Test
        @Tag("FR3.3")
        @Tag("TC8")
        @DisplayName("Back to Menu button navigates to MenuScreen")
        void menuButtonNavigatesToMenuScreen() {
            RecordingGame game = new RecordingGame();
            GameOverScreen screen = new GameOverScreen(game, "Alice", "Bob", 5, 3, true);

            screen.navigateToMenu();

            assertNotNull(game.getLastScreen());
            assertInstanceOf(MenuScreen.class, game.getLastScreen());
        }
    }

    @Nested
    @DisplayName("GameScreen transitions")
    class GameScreenTransitionTests {

        @Test
        @Tag("FR3.1")
        @DisplayName("Win condition navigates to GameOverScreen with playerWon=true")
        void winConditionShowsGameOverWithWin() {
            RecordingGame game = new RecordingGame();
            GameScreen screen = new GameScreen(game, "Alice", "Bob", false);

            screen.onReceived(new no.ntnu.ping404.network.packets.GameOver(1, "Alice", 5, 3));

            assertNotNull(game.getLastScreen());
            assertInstanceOf(GameOverScreen.class, game.getLastScreen());
            assertTrue(extractPlayerWon((GameOverScreen) game.getLastScreen()));
        }

        @Test
        @Tag("FR3.1")
        @DisplayName("Lose condition navigates to GameOverScreen with playerWon=false")
        void loseConditionShowsGameOverWithLose() {
            RecordingGame game = new RecordingGame();
            GameScreen screen = new GameScreen(game, "Alice", "Bob", false);

            screen.onReceived(new no.ntnu.ping404.network.packets.GameOver(2, "Bob", 3, 5));

            assertNotNull(game.getLastScreen());
            assertInstanceOf(GameOverScreen.class, game.getLastScreen());
            assertFalse(extractPlayerWon((GameOverScreen) game.getLastScreen()));
        }

        @Test
        @Tag("FR3.3")
        @DisplayName("Quit button navigates to MenuScreen")
        void quitButtonNavigatesToMenuScreen() {
            RecordingGame game = new RecordingGame();
            GameScreen screen = new GameScreen(game, "Alice", "Bob", false);

            screen.navigateToMenu();

            assertNotNull(game.getLastScreen());
            assertInstanceOf(MenuScreen.class, game.getLastScreen());
        }
    }

    private static boolean extractPlayerWon(GameOverScreen screen) {
        try {
            Field field = GameOverScreen.class.getDeclaredField("playerWon");
            field.setAccessible(true);
            return field.getBoolean(screen);
        } catch (ReflectiveOperationException e) {
            fail("Failed to inspect GameOverScreen.playerWon: " + e.getMessage());
            return false;
        }
    }

    private static void invokeMenuLayout(MenuScreen screen) {
        try {
            Method method = MenuScreen.class.getDeclaredMethod("layoutButtons");
            method.setAccessible(true);
            method.invoke(screen);
        } catch (ReflectiveOperationException e) {
            fail("Failed to invoke MenuScreen.layoutButtons: " + e.getMessage());
        }
    }

    private static Rectangle extractRectangle(MenuScreen screen, String fieldName) {
        try {
            Field field = MenuScreen.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (Rectangle) field.get(screen);
        } catch (ReflectiveOperationException e) {
            fail("Failed to inspect MenuScreen." + fieldName + ": " + e.getMessage());
            return null;
        }
    }
}
