package com.minichat.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public final class ServerLog {
    public static final Logger LOG = Logger.getLogger("com.minichat.server");
    private static boolean started;

    private ServerLog() {
    }

    public static synchronized void start() throws IOException {
        if (started) {
            return;
        }
        Files.createDirectories(Path.of("logs"));
        LOG.setUseParentHandlers(false);
        LOG.setLevel(Level.INFO);
        FileHandler handler = new FileHandler("logs/server.log", true);
        handler.setEncoding("UTF-8");
        handler.setFormatter(new SimpleFormatter());
        LOG.addHandler(handler);
        started = true;
    }
}
