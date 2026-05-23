package no.ntnu.ping404.utils;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PreferencesManager}.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>Singleton thread-safety behavior</li>
 *   <li>Default values for all preferences</li>
 *   <li>Null input handling</li>
 *   <li>Win score boundary validation</li>
 *   <li>Clear and reset functionality</li>
 * </ul>
 */
class PreferencesManagerTest {

    private Application mockApp;
    private Preferences mockPrefs;
    private Map<String, Object> prefsStore;

    @BeforeEach
    void setUp() {
        PreferencesManager.reset();

        prefsStore = new HashMap<>();
        mockPrefs = mock(Preferences.class);
        mockApp = mock(Application.class);

        when(mockApp.getPreferences("ping404_settings")).thenReturn(mockPrefs);

        when(mockPrefs.getString(anyString(), anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            String def = inv.getArgument(1);
            return prefsStore.containsKey(key) ? (String) prefsStore.get(key) : def;
        });

        when(mockPrefs.getInteger(anyString(), anyInt())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            int def = inv.getArgument(1);
            return prefsStore.containsKey(key) ? (Integer) prefsStore.get(key) : def;
        });

        doAnswer(inv -> {
            prefsStore.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(mockPrefs).putString(anyString(), anyString());

        doAnswer(inv -> {
            prefsStore.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(mockPrefs).putInteger(anyString(), anyInt());

        doAnswer(inv -> {
            prefsStore.clear();
            return null;
        }).when(mockPrefs).clear();

        Gdx.app = mockApp;
    }

    @AfterEach
    void tearDown() {
        PreferencesManager.reset();
        Gdx.app = null;
    }

    @Nested
    @DisplayName("Singleton behavior")
    class SingletonTests {

        @Test
        @DisplayName("getInstance returns same instance on repeated calls")
        void getInstanceReturnsSameInstance() {
            PreferencesManager first = PreferencesManager.getInstance();
            PreferencesManager second = PreferencesManager.getInstance();

            assertSame(first, second);
        }

        @Test
        @DisplayName("reset clears singleton allowing new instance creation")
        void resetClearsSingleton() {
            PreferencesManager first = PreferencesManager.getInstance();
            PreferencesManager.reset();
            PreferencesManager second = PreferencesManager.getInstance();

            assertNotSame(first, second);
        }
    }

    @Nested
    @DisplayName("Display name preferences")
    class DisplayNameTests {

        @Test
        @DisplayName("getDisplayName returns empty string by default")
        void getDisplayNameReturnsDefault() {
            String result = PreferencesManager.getInstance().getDisplayName();

            assertEquals("", result);
        }

        @Test
        @DisplayName("setDisplayName persists and retrieves value")
        void setDisplayNamePersists() {
            PreferencesManager manager = PreferencesManager.getInstance();

            manager.setDisplayName("TestPlayer");
            String result = manager.getDisplayName();

            assertEquals("TestPlayer", result);
            verify(mockPrefs).flush();
        }

        @Test
        @DisplayName("setDisplayName trims whitespace")
        void setDisplayNameTrimsWhitespace() {
            PreferencesManager manager = PreferencesManager.getInstance();

            manager.setDisplayName("  Padded Name  ");
            String result = manager.getDisplayName();

            assertEquals("Padded Name", result);
        }

        @Test
        @DisplayName("setDisplayName with null uses default empty string")
        void setDisplayNameHandlesNull() {
            PreferencesManager manager = PreferencesManager.getInstance();

            manager.setDisplayName(null);
            String result = manager.getDisplayName();

            assertEquals("", result);
        }
    }

    @Nested
    @DisplayName("Last server preferences")
    class LastServerTests {

        @Test
        @DisplayName("getLastServer returns 127.0.0.1 by default")
        void getLastServerReturnsDefault() {
            String result = PreferencesManager.getInstance().getLastServer();

            assertEquals(PreferencesManager.DEFAULT_SERVER_IP, result);
        }

        @Test
        @DisplayName("setLastServer persists and retrieves value")
        void setLastServerPersists() {
            PreferencesManager manager = PreferencesManager.getInstance();

            manager.setLastServer("192.168.1.100");
            String result = manager.getLastServer();

            assertEquals("192.168.1.100", result);
            verify(mockPrefs).flush();
        }

        @Test
        @DisplayName("setLastServer trims whitespace")
        void setLastServerTrimsWhitespace() {
            PreferencesManager manager = PreferencesManager.getInstance();

            manager.setLastServer("  10.0.2.2  ");
            String result = manager.getLastServer();

            assertEquals("10.0.2.2", result);
        }

        @Test
        @DisplayName("setLastServer with null uses default 127.0.0.1")
        void setLastServerHandlesNull() {
            PreferencesManager manager = PreferencesManager.getInstance();

            manager.setLastServer(null);
            String result = manager.getLastServer();

            assertEquals(PreferencesManager.DEFAULT_SERVER_IP, result);
        }
    }

    @Nested
    @DisplayName("Win score preferences")
    class WinScoreTests {

        @Test
        @DisplayName("getWinScore returns 5 by default")
        void getWinScoreReturnsDefault() {
            int result = PreferencesManager.getInstance().getWinScore();

            assertEquals(5, result);
        }

        @Test
        @DisplayName("setWinScore persists and retrieves value")
        void setWinScorePersists() {
            PreferencesManager manager = PreferencesManager.getInstance();

            manager.setWinScore(10);
            int result = manager.getWinScore();

            assertEquals(10, result);
            verify(mockPrefs).flush();
        }

        @Test
        @DisplayName("setWinScore clamps value below MIN_WIN_SCORE to 1")
        void setWinScoreClampsMinimum() {
            PreferencesManager manager = PreferencesManager.getInstance();

            manager.setWinScore(0);

            verify(mockPrefs).putInteger("win_score", 1);
        }

        @Test
        @DisplayName("setWinScore clamps negative value to 1")
        void setWinScoreClampsNegative() {
            PreferencesManager manager = PreferencesManager.getInstance();

            manager.setWinScore(-5);

            verify(mockPrefs).putInteger("win_score", 1);
        }

        @Test
        @DisplayName("setWinScore clamps value above MAX_WIN_SCORE to 15")
        void setWinScoreClampsMaximum() {
            PreferencesManager manager = PreferencesManager.getInstance();

            manager.setWinScore(100);

            verify(mockPrefs).putInteger("win_score", 15);
        }

        @Test
        @DisplayName("setWinScore allows boundary value MIN_WIN_SCORE=1")
        void setWinScoreAllowsMinBoundary() {
            PreferencesManager manager = PreferencesManager.getInstance();

            manager.setWinScore(1);

            verify(mockPrefs).putInteger("win_score", 1);
        }

        @Test
        @DisplayName("setWinScore allows boundary value MAX_WIN_SCORE=15")
        void setWinScoreAllowsMaxBoundary() {
            PreferencesManager manager = PreferencesManager.getInstance();

            manager.setWinScore(15);

            verify(mockPrefs).putInteger("win_score", 15);
        }
    }

    @Nested
    @DisplayName("Clear functionality")
    class ClearTests {

        @Test
        @DisplayName("clear removes all stored preferences")
        void clearRemovesAll() {
            PreferencesManager manager = PreferencesManager.getInstance();
            manager.setDisplayName("Test");
            manager.setLastServer("10.0.0.1");
            manager.setWinScore(7);

            manager.clear();

            verify(mockPrefs).clear();
            verify(mockPrefs, atLeast(1)).flush();
        }
    }
}
