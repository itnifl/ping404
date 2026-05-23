package no.ntnu.ping404.screens;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Gdx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for GameRendererFactory platform detection logic.
 * 
 * <p>Note: Factory creation tests are not included because LibGDX rendering 
 * classes (ShapeRenderer, SpriteBatch, etc.) cannot be mocked due to native
 * OpenGL dependencies.
 */
@Tag("Unit")
@ExtendWith(MockitoExtension.class)
class GameRendererFactoryTest {

    @Mock
    private Application mockApp;

    private Application originalApp;

    @BeforeEach
    void setUp() {
        originalApp = Gdx.app;
        Gdx.app = mockApp;
    }

    @AfterEach
    void tearDown() {
        Gdx.app = originalApp;
    }

    @Nested
    @DisplayName("Platform Detection")
    class PlatformDetection {

        @Test
        @DisplayName("isDesktopPlatform returns true for Desktop application type")
        void isDesktopPlatform_returnsTrue_forDesktop() {
            when(mockApp.getType()).thenReturn(ApplicationType.Desktop);

            assertTrue(GameRendererFactory.isDesktopPlatform());
        }

        @Test
        @DisplayName("isDesktopPlatform returns false for Android application type")
        void isDesktopPlatform_returnsFalse_forAndroid() {
            when(mockApp.getType()).thenReturn(ApplicationType.Android);

            assertFalse(GameRendererFactory.isDesktopPlatform());
        }

        @Test
        @DisplayName("isDesktopPlatform returns false for iOS application type")
        void isDesktopPlatform_returnsFalse_forIOS() {
            when(mockApp.getType()).thenReturn(ApplicationType.iOS);

            assertFalse(GameRendererFactory.isDesktopPlatform());
        }

        @Test
        @DisplayName("isDesktopPlatform returns false when Gdx.app is null")
        void isDesktopPlatform_returnsFalse_whenAppIsNull() {
            Gdx.app = null;

            assertFalse(GameRendererFactory.isDesktopPlatform());
        }

        @Test
        @DisplayName("isAndroidPlatform returns true for Android application type")
        void isAndroidPlatform_returnsTrue_forAndroid() {
            when(mockApp.getType()).thenReturn(ApplicationType.Android);

            assertTrue(GameRendererFactory.isAndroidPlatform());
        }

        @Test
        @DisplayName("isAndroidPlatform returns false for Desktop application type")
        void isAndroidPlatform_returnsFalse_forDesktop() {
            when(mockApp.getType()).thenReturn(ApplicationType.Desktop);

            assertFalse(GameRendererFactory.isAndroidPlatform());
        }

        @Test
        @DisplayName("isAndroidPlatform returns false when Gdx.app is null")
        void isAndroidPlatform_returnsFalse_whenAppIsNull() {
            Gdx.app = null;

            assertFalse(GameRendererFactory.isAndroidPlatform());
        }
    }
}
