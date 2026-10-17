package com.minichat.client.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.minichat.common.Protocol;

public final class ChatConnection {
    private static final long PING_INTERVAL_MILLIS = 10_000;
    private static final long INITIAL_BACKOFF_MILLIS = 1_000;
    private static final long MAX_BACKOFF_MILLIS = 30_000;

    private final ChatListener listener;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicReference<PrintWriter> out = new AtomicReference<>();
    private volatile Socket socket;

    public ChatConnection(ChatListener listener) {
        this.listener = listener;
    }

    public void connect(String host, int port, String username) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        Thread thread = new Thread(() -> run(host, port, username), "chat-connection");
        thread.setDaemon(true);
        thread.start();
    }

    public void sendMessage(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (text.length() > Protocol.MAX_TEXT_LENGTH) {
            listener.onError("message too long");
            return;
        }
        send(Protocol.msg(text));
    }

    public void sendPrivate(String to, String text) {
        if (to == null || to.isBlank() || text == null || text.isBlank()) {
            listener.onError("usage: /w name message");
            return;
        }
        if (text.length() > Protocol.MAX_TEXT_LENGTH) {
            listener.onError("message too long");
            return;
        }
        send(Protocol.privateMessage(to.trim(), text.trim()));
    }

    public void requestOnlineList() {
        send(Protocol.onlineRequest());
    }

    public void disconnect() {
        running.set(false);
        PrintWriter writer = out.get();
        if (writer != null) {
            writer.println(Protocol.quit());
        }
        closeSocket();
    }

    private void run(String host, int port, String username) {
        int attempt = 0;
        while (running.get()) {
            boolean opened = false;
            try {
                opened = session(host, port, username);
            } catch (IOException ignored) {
                opened = false;
            }
            if (opened) {
                listener.onDisconnected();
                attempt = 0;
            }
            if (!running.get()) {
                break;
            }
            attempt++;
            listener.onReconnecting(attempt);
            if (!sleep(backoffFor(attempt))) {
                break;
            }
        }
    }

    private boolean session(String host, int port, String username) throws IOException {
        try (Socket connected = new Socket(host, port);
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(connected.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(connected.getOutputStream(), true, StandardCharsets.UTF_8)) {
            socket = connected;
            out.set(writer);
            writer.println(Protocol.login(username));
            listener.onConnected();
            Thread ping = new Thread(() -> sendPings(writer), "chat-ping");
            ping.setDaemon(true);
            ping.start();
            try {
                String line;
                while (running.get() && (line = in.readLine()) != null) {
                    dispatch(Protocol.parse(line));
                }
            } catch (IOException ignored) {
                return true;
            } finally {
                ping.interrupt();
            }
            return true;
        } finally {
            out.set(null);
            socket = null;
        }
    }

    private void dispatch(Protocol.Message message) {
        switch (message.command()) {
            case Protocol.MSG -> listener.onMessage(message.argument(0), message.argument(1), message.argument(2));
            case Protocol.PM -> listener.onPrivateMessage(
                    message.argument(0), message.argument(1), message.argument(2));
            case Protocol.SENT -> listener.onPrivateMessage(
                    message.argument(0), message.argument(1), message.argument(2));
            case Protocol.ONLINE -> listener.onOnlineList(message.arguments());
            case Protocol.JOIN -> listener.onUserJoined(message.argument(0));
            case Protocol.LEAVE -> listener.onUserLeft(message.argument(0));
            case Protocol.ERROR -> listener.onError(message.argument(0));
            case Protocol.SERVER -> listener.onError("server: " + message.argument(0));
            default -> {
            }
        }
    }

    private void sendPings(PrintWriter writer) {
        try {
            while (running.get()) {
                Thread.sleep(PING_INTERVAL_MILLIS);
                writer.println(Protocol.ping());
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void send(String line) {
        PrintWriter writer = out.get();
        if (writer == null) {
            listener.onError("not connected");
            return;
        }
        writer.println(line);
    }

    private boolean sleep(long millis) {
        long waited = 0;
        while (waited < millis && running.get()) {
            long step = Math.min(200, millis - waited);
            try {
                Thread.sleep(step);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            waited += step;
        }
        return running.get();
    }

    private void closeSocket() {
        Socket current = socket;
        if (current == null) {
            return;
        }
        try {
            current.close();
        } catch (IOException ignored) {
        }
    }

    private static long backoffFor(int attempt) {
        long backoff = INITIAL_BACKOFF_MILLIS;
        for (int i = 1; i < attempt; i++) {
            backoff = Math.min(backoff * 2, MAX_BACKOFF_MILLIS);
        }
        return backoff;
    }
}
