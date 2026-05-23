package no.ntnu.ping404.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import no.ntnu.ping404.screens.Ping404Game;

/**
 * Desktop launcher - uses LwjglApplication for testing.
 */
public class DesktopLauncher {

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Ping404");
        config.setWindowedMode(480, 800); // Portrait for menus
        new Lwjgl3Application(new Ping404Game(), config);
    }
}
