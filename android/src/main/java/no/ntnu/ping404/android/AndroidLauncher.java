package no.ntnu.ping404.android;

import android.os.Bundle;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import no.ntnu.ping404.screens.Ping404Game;

/**
 * Android launcher - extends AndroidApplication.
 * Entry point for the Android app that initializes the LibGDX game (FR1.1).
 *
 * <p>Lifecycle handling (FR4.1, FR4.2, A1):</p>
 * <ul>
 *   <li>App background: LibGDX calls {@link Ping404Game#pause()}</li>
 *   <li>App foreground: LibGDX calls {@link Ping404Game#resume()}</li>
 * </ul>
 *
 * @see Ping404Game
 */
public class AndroidLauncher extends AndroidApplication {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        // Keep screen on during gameplay to prevent unintended pause
        config.useWakelock = true;
        initialize(new Ping404Game(), config);
    }
}
