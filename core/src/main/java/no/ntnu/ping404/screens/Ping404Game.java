package no.ntnu.ping404.screens;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import no.ntnu.ping404.audio.AudioManager;
import no.ntnu.ping404.utils.PreferencesManager;

/**
 * Main game class for PING-404.
 * Entry point that sets the initial screen and manages screen transitions.
 *
 * <p>Screen routing (FR1.1, FR3.3, M4):</p>
 * <ul>
 *   <li>{@code create()} --> {@link MenuScreen} (FR1.1: main menu on launch)</li>
 *   <li>{@link MenuScreen} --> {@link HostScreen}, {@link JoinScreen}, {@link SettingsScreen}</li>
 *   <li>{@link HostScreen}/{@link JoinScreen} --> {@link GameScreen} (match start)</li>
 *   <li>{@link GameScreen} --> {@link GameOverScreen} (match end)</li>
 *   <li>{@link GameOverScreen} --> {@link MenuScreen} (FR3.3: return to menu)</li>
 * </ul>
 *
 * @see Game#setScreen(com.badlogic.gdx.Screen)
 */
public class Ping404Game extends Game {

    /**
     * Initializes the audio manager and shows the main menu (FR1.1).
     */
    @Override
    public void create() {
        try {
            initializeAudio();
        } catch (RuntimeException exception) {
            logAudioStartupFailure(exception);
        }

        setScreen(createInitialScreen());
    }

    /**
     * Initializes startup audio resources.
     * Extracted for testability so startup fallback behaviour can be verified.
     */
    protected void initializeAudio() {
        AudioManager.initialize();
    }

    /**
     * Creates the first screen shown after the application starts.
     */
    protected Screen createInitialScreen() {
        return new MenuScreen(this);
    }

    /**
     * Logs a non-fatal startup failure without blocking the initial screen.
     *
     * @param exception the encountered exception
     */
    protected void logAudioStartupFailure(RuntimeException exception) {
        if (Gdx.app != null) {
            Gdx.app.error("Ping404Game", "Startup continued after audio initialization failed", exception);
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        AudioManager.dispose();
        PreferencesManager.reset();
    }

    /**
     * Called when the application is paused (e.g., Android backgrounded).
     * Notifies the current screen if it implements lifecycle awareness (FR4.1, A1).
     */
    @Override
    public void pause() {
        super.pause();
        Screen current = getScreen();
        if (current instanceof LifecycleAwareScreen lifecycleScreen) {
            lifecycleScreen.onAppPause();
        }
    }

    /**
     * Called when the application resumes from a paused state.
     * Notifies the current screen if it implements lifecycle awareness (FR4.2, A1).
     */
    @Override
    public void resume() {
        super.resume();
        Screen current = getScreen();
        if (current instanceof LifecycleAwareScreen lifecycleScreen) {
            lifecycleScreen.onAppResume();
        }
    }
}
