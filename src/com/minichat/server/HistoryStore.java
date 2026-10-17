package com.minichat.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import com.minichat.common.Protocol;

public final class HistoryStore {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int RECENT_LIMIT = 20;

    private final Path historyFile;
    private final Path privateDir;
    // Client threads append at the same time. This lock keeps each write to one line.
    private final ReentrantLock writeLock = new ReentrantLock();

    public HistoryStore() {
        this(Path.of("data"));
    }

    public HistoryStore(Path dataDir) {
        this.historyFile = dataDir.resolve("history.log");
        this.privateDir = dataDir.resolve("private");
    }

    public void appendPublic(String line) {
        append(historyFile, line);
    }

    public void appendPrivate(String from, String to, String text) {
        String line = Protocol.build(Protocol.PM, from, text, LocalTime.now().format(TIME));
        append(pairFile(from, to), line);
    }

    public List<String> recentPublic() {
        writeLock.lock();
        try {
            if (Files.notExists(historyFile)) {
                return List.of();
            }
            List<String> lines = new ArrayList<>();
            for (String line : Files.readAllLines(historyFile, StandardCharsets.UTF_8)) {
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
            int from = Math.max(0, lines.size() - RECENT_LIMIT);
            return List.copyOf(lines.subList(from, lines.size()));
        } catch (IOException e) {
            System.err.println("could not read " + historyFile + ": " + e.getMessage());
            return List.of();
        } finally {
            writeLock.unlock();
        }
    }

    private void append(Path file, String line) {
        writeLock.lock();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(
                    file,
                    line + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("could not append " + file + ": " + e.getMessage());
        } finally {
            writeLock.unlock();
        }
    }

    private Path pairFile(String userA, String userB) {
        String first = userA.compareTo(userB) <= 0 ? userA : userB;
        String second = userA.compareTo(userB) <= 0 ? userB : userA;
        Path file = privateDir.resolve(safe(first) + "_" + safe(second) + ".log");
        if (!file.normalize().startsWith(privateDir.normalize())) {
            return privateDir.resolve("unknown_pair.log");
        }
        return file;
    }

    private static String safe(String name) {
        StringBuilder out = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char current = name.charAt(i);
            if (current == '/' || current == '\\' || current < ' ') {
                out.append('_');
            } else {
                out.append(current);
            }
        }
        return out.isEmpty() ? "_" : out.toString();
    }
}
