package no.ntnu.ping404.model;

import no.ntnu.ping404.utils.Constants;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerTest {

    @Test
    @Tag("FR2.1")
    void testMovePaddleUp() {
        Player player = new Player(1, "Test");
        float initialY = 100f;
        float dt = 0.1f;
        player.setPaddleY(initialY);

        player.movePaddleUp(dt);

        float expectedY = initialY + (Constants.PADDLE_DEFAULT_SPEED * dt);
        assertEquals(expectedY, player.getPaddleY());
    }

    @Test
    @Tag("FR2.1")
    void testMovePaddleDown() {
        Player player = new Player(1, "Test");
        float initialY = 100f;
        float dt = 0.1f;
        player.setPaddleY(initialY);

        player.movePaddleDown(dt);

        float expectedY = initialY - (Constants.PADDLE_DEFAULT_SPEED * dt);
        assertEquals(expectedY, player.getPaddleY());
    }

    @Test
    @Tag("FR2.2")
    void testTopBoundaryClamp() {
        Player player = new Player(1, "Test");
        float topLimit = Constants.DEFAULT_FIELD_HEIGHT - (Constants.PADDLE_HEIGHT / 2);
        
        player.setPaddleY(topLimit - 5f);
        player.movePaddleUp(1.0f); 

        assertEquals(topLimit, player.getPaddleY());
    }

    @Test
    @Tag("FR2.2")
    void testBottomBoundaryClamp() {
        Player player = new Player(1, "Test");
        float bottomLimit = Constants.PADDLE_HEIGHT / 2;

        player.setPaddleY(bottomLimit + 5f);
        player.movePaddleDown(1.0f);

        assertEquals(bottomLimit, player.getPaddleY());
    }
}