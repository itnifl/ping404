package no.ntnu.ping404.model;

import no.ntnu.ping404.utils.Constants;

/**
 * Represents the puck (ball) in the game.
 * Tracks position, velocity, and provides movement and reset logic.
 */
public class Puck {

    private float x;
    private float y;
    private float velocityX;
    private float velocityY;
    private float radius;
    private float speed;

    /** No-arg constructor required for Kryo serialization. */
    public Puck() {
        this.radius = Constants.PUCK_RADIUS;
        this.speed = Constants.INITIAL_PUCK_SPEED;
    }

    /**
     * Creates a new Puck at the specified position.
     *
     * @param startX the initial X position
     * @param startY the initial Y position
     */
    public Puck(float startX, float startY) {
        this();
        this.x = startX;
        this.y = startY;
    }

    /**
     * Sets the position of the puck.
     *
     * @param x the X coordinate
     * @param y the Y coordinate
     */
    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Update puck position based on velocity and delta time.
     */
    public void update(float deltaTime) {
        x += velocityX * deltaTime;
        y += velocityY * deltaTime;
    }

    /**
     * Reset puck to center with a random launch direction.
     */
    public void reset(float centerX, float centerY) {
        this.x = centerX;
        this.y = centerY;
        double angle = (Math.random() * Math.PI / 2) - Math.PI / 4;
        int direction = Math.random() > 0.5 ? 1 : -1;
        this.velocityX = (float) (Math.cos(angle) * speed * direction);
        this.velocityY = (float) (Math.sin(angle) * speed);
    }

    /**
     * Reverse horizontal direction (e.g. after paddle hit).
     */
    public void bounceX() {
        velocityX = -velocityX;
    }

    /**
     * Reverse vertical direction (e.g. after wall hit).
     */
    public void bounceY() {
        velocityY = -velocityY;
    }

    /**
     * Check and handle wall collisions (top/bottom). (FR2.4)
     * Clamps puck inside field and reverses Y velocity on hit.
     *
     * @param fieldHeight the height of the playing field
     * @return true if a wall bounce occurred
     */
    public boolean bounceOffWalls(float fieldHeight) {
        boolean bounced = false;

        if (y - radius <= 0) {
            y = radius;
            bounceY();
            bounced = true;
        }

        if (y + radius >= fieldHeight) {
            y = fieldHeight - radius;
            bounceY();
            bounced = true;
        }

        return bounced;
    }

    /**
     * Check and handle mallet collision. (FR2.5)
     * Adjusts puck velocity based on mallet contact point and speed.
     *
     * @param mallet the player (mallet) to check collision against
     * @param malletRadius the radius of the mallet
     * @return true if a mallet bounce occurred
     */
    public boolean bounceOffMallet(Player mallet, float malletRadius) {
        float dx = x - mallet.getX();
        float dy = y - mallet.getY();
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        float collisionDistance = radius + malletRadius;

        if (distance > collisionDistance || distance == 0) {
            return false;
        }

        // Normalize collision vector
        float nx = dx / distance;
        float ny = dy / distance;

        // Push puck out of mallet (prevent overlap)
        x = mallet.getX() + nx * collisionDistance;
        y = mallet.getY() + ny * collisionDistance;

        // Reflect velocity along collision normal
        float dotProduct = velocityX * nx + velocityY * ny;

        // Only bounce if puck is moving toward the mallet
        if (dotProduct >= 0) {
            return false;
        }

        velocityX = velocityX - 2 * dotProduct * nx;
        velocityY = velocityY - 2 * dotProduct * ny;

        // Normalize speed to maintain consistent puck speed
        float currentSpeed = (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY);
        if (currentSpeed > 0) {
            velocityX = (velocityX / currentSpeed) * speed;
            velocityY = (velocityY / currentSpeed) * speed;
        }

        return true;
    }

    /**
     * Check if the puck has passed left or right edge (goal scored). (FR2.9)
     *
     * @param fieldWidth the width of the playing field
     * @return Score.PLAYER_1 if player 1 scored (puck right), Score.PLAYER_2 if player 2 scored (puck left), 0 otherwise
     */
    public int checkGoal(float fieldWidth) {
        if (x - radius <= 0) {
            return Score.PLAYER_2;
        }
        if (x + radius >= fieldWidth) {
            return Score.PLAYER_1;
        }
        return Score.NO_WINNER;
    }


    public float getX() { return x; }

    public void setX(float x) { this.x = x; }

    public float getY() { return y; }

    public void setY(float y) { this.y = y; }

    public float getVelocityX() { return velocityX; }

    public void setVelocityX(float velocityX) { this.velocityX = velocityX; }

    public float getVelocityY() { return velocityY; }

    public void setVelocityY(float velocityY) { this.velocityY = velocityY; }

    public float getRadius() { return radius; }

    public void setRadius(float radius) { this.radius = radius; }

    public float getSpeed() { return speed; }

    public void setSpeed(float speed) { this.speed = speed; }

    @Override
    public String toString() {
        return "Puck{x=" + x + ", y=" + y + ", vx=" + velocityX + ", vy=" + velocityY + "}";
    }
}
