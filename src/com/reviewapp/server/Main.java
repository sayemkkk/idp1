package com.reviewapp.server;

/**
 * Application entry point. Starts the embedded HTTP server and installs a
 * shutdown hook so the server (and its executor threads) stops cleanly.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        try {
            WebServer server = new WebServer();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down...");
                server.stop();
            }, "shutdown-hook"));
            server.start();
        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
