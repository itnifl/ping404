package no.ntnu.ping404.utils;

import no.ntnu.ping404.model.Player;
import no.ntnu.ping404.model.Puck;
import no.ntnu.ping404.model.GameState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Handles collision detection and response (rebound logic).
 */
public class CollisionDetector {

    public enum GoalResult {
        NONE,
        PLAYER_1_GOAL,
        PLAYER_2_GOAL
    }

    /**
     * Result from one deterministic collision tick.
     */
    public static class TickResult {
        private final boolean wallCollision;
        private final boolean paddleCollision;
        private final GoalResult goalResult;

        public TickResult(boolean wallCollision, boolean paddleCollision, GoalResult goalResult) {
            this.wallCollision = wallCollision;
            this.paddleCollision = paddleCollision;
            this.goalResult = goalResult;
        }

        public boolean hasWallCollision() {
            return wallCollision;
        }

        public boolean hasPaddleCollision() {
            return paddleCollision;
        }

        public GoalResult getGoalResult() {
            return goalResult;
        }
    }

    private static final float PADDLE_BOUNCE_OFFSET_FACTOR = 0.35f;
    private static final float POSITION_EPSILON = 0.01f;
    private static final int MAX_SUBSTEPS = 16;
    private static final float MIN_RELATIVE_COLLISION_SPEED = Constants.INITIAL_PUCK_SPEED * 0.35f;

    private static final Map<Player, PositionSample> LAST_PLAYER_POSITIONS = new WeakHashMap<>();

    private record PositionSample(float x, float y) {}
    private record Velocity(float x, float y) {}

    private CollisionDetector() {
    }

    /**
     * Handles puck collisions with horizontal and vertical walls.
     * Used by GameScreen for client-side rendering.
     */
    public static void handleWallCollisions(Puck puck, float fieldWidth, float fieldHeight) {
        float x = puck.getX();
        float y = puck.getY();
        float r = puck.getRadius();

        if (y - r <= 0f) {
            puck.setY(r + POSITION_EPSILON);
            puck.setVelocityY(Math.abs(puck.getVelocityY()));
        } else if (y + r >= fieldHeight) {
            puck.setY(fieldHeight - r - POSITION_EPSILON);
            puck.setVelocityY(-Math.abs(puck.getVelocityY()));
        }

        float goalTop = Constants.goalTop(fieldHeight);
        float goalBottom = Constants.goalBottom(fieldHeight);
        if (!intersectsGoalLane(puck.getY(), r, goalTop, goalBottom)) {
            if (x - r <= 0f) {
                puck.setX(r + POSITION_EPSILON);
                puck.setVelocityX(Math.abs(puck.getVelocityX()));
            } else if (x + r >= fieldWidth) {
                puck.setX(fieldWidth - r - POSITION_EPSILON);
                puck.setVelocityX(-Math.abs(puck.getVelocityX()));
            }
        }
    }

    /**
     * Handles collision between puck and a circular mallet.
     * Used by GameScreen for client-side rendering.
     *
     * @return {@code true} if a collision was detected and resolved
     */
    public static boolean handleMalletCollision(Puck puck, float malletX, float malletY) {
        float dx = puck.getX() - malletX;
        float dy = puck.getY() - malletY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        float minDist = Constants.MALLET_RADIUS + puck.getRadius();

        if (distance < minDist && distance > 0) {
            float nx = dx / distance;
            float ny = dy / distance;

            puck.setX(malletX + nx * minDist);
            puck.setY(malletY + ny * minDist);

            float dotProduct = puck.getVelocityX() * nx + puck.getVelocityY() * ny;
            puck.setVelocityX((puck.getVelocityX() - 2 * dotProduct * nx) * 1.05f);
            puck.setVelocityY((puck.getVelocityY() - 2 * dotProduct * ny) * 1.05f);
            return true;
        }
        return false;
    }

    /**
     * Checks if the puck has collided with the top or bottom walls.
     *
     * @param puck   The puck to check.
     * @param state  The game state containing field dimensions.
     * @return true if it hit the top or bottom wall.
     */
    public static boolean checkPuckTopBottomWallCollision(Puck puck, GameState state) {
        float puckY = puck.getY();
        float puckRadius = puck.getRadius();
        float fieldHeight = state.getFieldHeight();

        return (puckY - puckRadius <= 0) || (puckY + puckRadius >= fieldHeight);
    }

    /**
     * Checks if the puck has collided with the left or right side walls (outside the goal area).
     *
     * @param puck   The puck to check.
     * @param state  The game state containing field dimensions.
     * @return true if it hit a side wall that is not a goal.
     */
    public static boolean checkPuckSideWallCollision(Puck puck, GameState state) {
        float puckX = puck.getX();
        float puckY = puck.getY();
        float puckRadius = puck.getRadius();
        float fieldWidth = state.getFieldWidth();
        float fieldHeight = state.getFieldHeight();

        float goalTop = Constants.goalTop(fieldHeight);
        float goalBottom = Constants.goalBottom(fieldHeight);

        if (!intersectsGoalLane(puckY, puckRadius, goalTop, goalBottom)) {
            return (puckX - puckRadius <= 0) || (puckX + puckRadius >= fieldWidth);
        }
        return false;
    }

    /**
     * Performs one deterministic collision tick for the puck and all paddles.
     *
     * Order per substep: wall collision -> paddle collision -> goal detection.
     *
     * @param state game state containing puck, players and field dimensions.
     * @param deltaTime tick delta in seconds.
     * @return outcome for the processed tick.
     */
    public static TickResult resolveTick(GameState state, float deltaTime) {
        return resolveTick(state.getPuck(), state, state.getPlayers().values(), deltaTime);
    }

    /**
     * Performs one deterministic collision tick for the puck and provided paddles.
     *
     * @param puck puck instance to update.
     * @param state game state containing field dimensions.
     * @param players players to include in paddle collision checks.
     * @param deltaTime tick delta in seconds.
     * @return outcome for the processed tick.
     */
    public static TickResult resolveTick(Puck puck, GameState state, Iterable<Player> players, float deltaTime) {
        if (deltaTime <= 0f) {
            return new TickResult(false, false, GoalResult.NONE);
        }

        List<Player> sortedPlayers = new ArrayList<>();
        for (Player player : players) {
            sortedPlayers.add(player);
        }
        sortedPlayers.sort(Comparator.comparingInt(Player::getId));
        Map<Player, Velocity> playerVelocities = estimatePlayerVelocities(sortedPlayers, deltaTime);

        int substeps = computeSubsteps(puck, deltaTime);
        float stepDelta = deltaTime / substeps;

        boolean wallCollision = false;
        boolean paddleCollision = false;

        for (int i = 0; i < substeps; i++) {
            puck.update(stepDelta);

            wallCollision |= resolveWallCollision(puck, state);
            paddleCollision |= resolvePaddleCollisions(puck, sortedPlayers, playerVelocities);

            GoalResult goalResult = checkGoal(puck, state);
            if (goalResult != GoalResult.NONE) {
                return new TickResult(wallCollision, paddleCollision, goalResult);
            }
        }

        return new TickResult(wallCollision, paddleCollision, GoalResult.NONE);
    }

    /**
     * Checks if the puck has crossed the left or right goal lines within the goal area.
     */
    private static GoalResult checkGoal(Puck puck, GameState state) {
        float puckX = puck.getX();
        float puckY = puck.getY();
        float puckRadius = puck.getRadius();
        float fieldWidth = state.getFieldWidth();
        float fieldHeight = state.getFieldHeight();

        float goalTop = Constants.goalTop(fieldHeight);
        float goalBottom = Constants.goalBottom(fieldHeight);

        if (intersectsGoalLane(puckY, puckRadius, goalTop, goalBottom)) {
            if (puckX + puckRadius <= 0) {
                return GoalResult.PLAYER_1_GOAL;
            } else if (puckX - puckRadius >= fieldWidth) {
                return GoalResult.PLAYER_2_GOAL;
            }
        }
        return GoalResult.NONE;
    }

    private static int computeSubsteps(Puck puck, float deltaTime) {
        float travelDistance = speedMagnitude(puck.getVelocityX(), puck.getVelocityY()) * deltaTime;
        float maxStepDistance = Math.max(1f, puck.getRadius() * Constants.PUCK_SUBSTEP_DISTANCE_FACTOR);
        int substeps = (int) Math.ceil(travelDistance / maxStepDistance);
        if (substeps < 1) {
            return 1;
        }
        return Math.min(substeps, MAX_SUBSTEPS);
    }

    private static boolean resolveWallCollision(Puck puck, GameState state) {
        boolean collided = false;

        float radius = puck.getRadius();
        float x = puck.getX();
        float y = puck.getY();

        if (y - radius <= 0f) {
            puck.setY(radius + POSITION_EPSILON);
            puck.setVelocityY(Math.abs(puck.getVelocityY()));
            collided = true;
        } else if (y + radius >= state.getFieldHeight()) {
            puck.setY(state.getFieldHeight() - radius - POSITION_EPSILON);
            puck.setVelocityY(-Math.abs(puck.getVelocityY()));
            collided = true;
        }

        float goalTop = Constants.goalTop(state.getFieldHeight());
        float goalBottom = Constants.goalBottom(state.getFieldHeight());
        boolean inGoalLane = intersectsGoalLane(y, radius, goalTop, goalBottom);

        if (!inGoalLane) {
            if (x - radius <= 0f) {
                puck.setX(radius + POSITION_EPSILON);
                puck.setVelocityX(Math.abs(puck.getVelocityX()));
                collided = true;
            } else if (x + radius >= state.getFieldWidth()) {
                puck.setX(state.getFieldWidth() - radius - POSITION_EPSILON);
                puck.setVelocityX(-Math.abs(puck.getVelocityX()));
                collided = true;
            }
        }

        return collided;
    }

    private static Map<Player, Velocity> estimatePlayerVelocities(List<Player> players, float deltaTime) {
        Map<Player, Velocity> velocities = new IdentityHashMap<>();
        synchronized (LAST_PLAYER_POSITIONS) {
            for (Player player : players) {
                PositionSample previous = LAST_PLAYER_POSITIONS.get(player);
                float velocityX = 0f;
                float velocityY = 0f;

                if (previous != null) {
                    velocityX = (player.getX() - previous.x()) / deltaTime;
                    velocityY = (player.getY() - previous.y()) / deltaTime;
                }

                velocities.put(player, new Velocity(velocityX, velocityY));
                LAST_PLAYER_POSITIONS.put(player, new PositionSample(player.getX(), player.getY()));
            }
        }
        return velocities;
    }

    private static boolean resolvePaddleCollisions(Puck puck, List<Player> players, Map<Player, Velocity> playerVelocities) {
        for (Player player : players) {
            Velocity playerVelocity = playerVelocities.getOrDefault(player, new Velocity(0f, 0f));
            if (resolveSinglePaddleCollision(puck, player, playerVelocity)) {
                return true;
            }
        }
        return false;
    }

    private static boolean resolveSinglePaddleCollision(Puck puck, Player player, Velocity playerVelocity) {
        float halfW = Constants.PADDLE_WIDTH / 2f;
        float halfH = Constants.PADDLE_HEIGHT / 2f;

        float left = player.getX() - halfW;
        float right = player.getX() + halfW;
        float bottom = player.getY() - halfH;
        float top = player.getY() + halfH;

        float puckX = puck.getX();
        float puckY = puck.getY();

        float closestX = clamp(puckX, left, right);
        float closestY = clamp(puckY, bottom, top);

        float dx = puckX - closestX;
        float dy = puckY - closestY;
        float radius = puck.getRadius();
        float distanceSq = (dx * dx) + (dy * dy);

        if (distanceSq > radius * radius) {
            return false;
        }

        float nx;
        float ny;
        float distance = (float) Math.sqrt(Math.max(distanceSq, 0f));

        if (distance > 0f) {
            nx = dx / distance;
            ny = dy / distance;
        } else {
            float vx = puck.getVelocityX();
            float vy = puck.getVelocityY();
            float incomingSpeed = speedMagnitude(vx, vy);
            if (incomingSpeed > 0f) {
                nx = -vx / incomingSpeed;
                ny = -vy / incomingSpeed;
            } else {
                nx = player.getX() < puckX ? 1f : -1f;
                ny = 0f;
            }
        }

        puck.setX(closestX + nx * (radius + POSITION_EPSILON));
        puck.setY(closestY + ny * (radius + POSITION_EPSILON));

        float inVx = puck.getVelocityX();
        float inVy = puck.getVelocityY();
        float relativeInX = inVx - playerVelocity.x();
        float relativeInY = inVy - playerVelocity.y();
        float relativeInSpeed = Math.max(speedMagnitude(relativeInX, relativeInY), MIN_RELATIVE_COLLISION_SPEED);

        float dot = relativeInX * nx + relativeInY * ny;
        float relativeReflectedX = relativeInX - (2f * dot * nx);
        float relativeReflectedY = relativeInY - (2f * dot * ny);

        float contactOffset = clamp((puck.getY() - player.getY()) / halfH, -1f, 1f);
        float tangentX = -ny;
        float tangentY = nx;
        relativeReflectedX += tangentX * relativeInSpeed * PADDLE_BOUNCE_OFFSET_FACTOR * contactOffset;
        relativeReflectedY += tangentY * relativeInSpeed * PADDLE_BOUNCE_OFFSET_FACTOR * contactOffset;

        float relativeOutSpeed = speedMagnitude(relativeReflectedX, relativeReflectedY);
        if (relativeOutSpeed > 0f) {
            float normalize = relativeInSpeed / relativeOutSpeed;
            relativeReflectedX *= normalize;
            relativeReflectedY *= normalize;
        }

        if ((relativeReflectedX * nx + relativeReflectedY * ny) <= 0f) {
            relativeReflectedX = nx * relativeInSpeed;
            relativeReflectedY = ny * relativeInSpeed;
        }

        float reflectedX = relativeReflectedX + playerVelocity.x();
        float reflectedY = relativeReflectedY + playerVelocity.y();

        float outSpeed = speedMagnitude(reflectedX, reflectedY);
        if (outSpeed < MIN_RELATIVE_COLLISION_SPEED) {
            float normalize = MIN_RELATIVE_COLLISION_SPEED / Math.max(outSpeed, POSITION_EPSILON);
            reflectedX *= normalize;
            reflectedY *= normalize;
        }

        puck.setVelocityX(reflectedX);
        puck.setVelocityY(reflectedY);
        return true;
    }

    private static float speedMagnitude(float vx, float vy) {
        return (float) Math.sqrt((vx * vx) + (vy * vy));
    }

    /**
     * Checks if the puck (circle) overlaps with the goal lane (vertical range from goalBottom to goalTop).
     * Returns true if any part of the puck is within the goal opening.
     */
    private static boolean intersectsGoalLane(float puckY, float radius, float goalTop, float goalBottom) {
        return (puckY - radius <= goalTop) && (puckY + radius >= goalBottom);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }
}
