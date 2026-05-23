package no.ntnu.ping404.screens;

/**
 * Interface for screens that need to respond to Android lifecycle events.
 *
 * <p>When the app is backgrounded (e.g., home button, incoming call), LibGDX
 * calls {@link com.badlogic.gdx.Game#pause()}. Screens implementing this
 * interface can perform cleanup such as sending a pause request to the server
 * or saving state (FR4.1, A1).</p>
 *
 * <p>When the app returns to foreground, {@link #onAppResume()} is called,
 * allowing the screen to attempt reconnection or resume gameplay (FR4.2, A1).</p>
 *
 * @see Ping404Game#pause()
 * @see Ping404Game#resume()
 */
public interface LifecycleAwareScreen {
    void onAppPause();
    void onAppResume();
}
