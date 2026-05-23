package no.ntnu.ping404.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server application launcher.
 * Starts the game server and provides a simple console interface.
 */
public class ServerLauncher {
    private static final Logger logger = LoggerFactory.getLogger(ServerLauncher.class);

    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("  KryoNet Game Server");
        logger.info("========================================");
        logger.info("");

        GameServer server = new GameServer(new SessionStore());
        try {
            server.start();
        } catch (java.io.IOException e) {
            logger.error("Failed to start game server: {}", e.getMessage(), e);
            System.exit(1);
        }

        // Add shutdown hook for clean shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("\nShutting down server...");
            server.stop();
        }));

        // Simple console command interface
        logger.info("\nServer commands:");
        logger.info("  status - Show server status");
        logger.info("  players - List connected players");
        logger.info("  broadcast <message> - Send message to all players");
        logger.info("  quit - Stop the server");
        logger.info("");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while (server.isRunning() && (line = reader.readLine()) != null) {
                handleCommand(server, line.trim());
            }
        } catch (Exception e) {
            logger.error("Console error: " + e.getMessage(), e);
        }

        server.stop();
    }

    private static void handleCommand(GameServer server, String input) {
        if (input.isEmpty()) return;

        String[] parts = input.split(" ", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";

        switch (command) {
            case "status":
                logger.info("Server status: " + (server.isRunning() ? "RUNNING" : "STOPPED"));
                logger.info("Connected players: " + server.getNetworkServer().getConnectionCount());
                break;

            case "players":
                logger.info("Connected players:");
                server.getNetworkServer().forEachConnection((id, conn) -> {
                    String name = conn.getPlayerName();
                    if (name != null) {
                        logger.info("  [" + id + "] " + name +
                                " at (" + conn.getX() + ", " + conn.getY() + ")");
                    } else {
                        logger.info("  [" + id + "] (not logged in)");
                    }
                });
                break;

            case "broadcast":
            case "say":
                if (!args.isEmpty()) {
                    var msg = no.ntnu.ping404.network.packets.ChatMessage.system(args);
                    server.broadcast(msg);
                    logger.info("Broadcast sent: " + args);
                } else {
                    logger.info("Usage: broadcast <message>");
                }
                break;

            case "quit":
            case "exit":
            case "stop":
                logger.info("Stopping server...");
                server.stop();
                System.exit(0);
                break;

            case "help":
                logger.info("Commands: status, players, broadcast <msg>, quit");
                break;

            default:
                logger.info("Unknown command: " + command + " (type 'help' for commands)");
        }
    }
}
