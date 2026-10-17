package com.minichat.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.minichat.common.Protocol;

public class SimpleClient {
    private static final String HOST = "localhost";
    private static final int PORT = 5000;
    private static final long PING_INTERVAL_MILLIS = 10_000;
    private static final long INITIAL_BACKOFF_MILLIS = 1_000;
    private static final long MAX_BACKOFF_MILLIS = 30_000;

    public static void main(String[] args) throws IOException, InterruptedException {
        BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String name = readName(keyboard);
        if (name == null) {
            return;
        }

        AtomicBoolean quit = new AtomicBoolean(false);
        AtomicReference<PrintWriter> serverOut = new AtomicReference<>();
        AtomicReference<Socket> socketRef = new AtomicReference<>();
        Thread keyboardReader = new Thread(
                () -> readKeyboard(keyboard, serverOut, socketRef, quit), "keyboard-reader");
        keyboardReader.setDaemon(true);
        keyboardReader.start();

        long backoff = INITIAL_BACKOFF_MILLIS;
        while (!quit.get()) {
            try {
                boolean userQuit = runSession(name, serverOut, socketRef, quit);
                backoff = INITIAL_BACKOFF_MILLIS;
                if (userQuit || quit.get()) {
                    break;
                }
            } catch (IOException ignored) {
                // Connect failed. Keep the current backoff.
            }
            if (quit.get()) {
                break;
            }
            System.out.println("reconnecting in " + (backoff / 1000) + "s");
            long waited = 0;
            while (waited < backoff && !quit.get()) {
                long step = Math.min(200, backoff - waited);
                Thread.sleep(step);
                waited += step;
            }
            backoff = Math.min(backoff * 2, MAX_BACKOFF_MILLIS);
        }
    }

    private static String readName(BufferedReader keyboard) throws IOException {
        System.out.print("Name: ");
        System.out.flush();
        String name = keyboard.readLine();
        if (name == null || name.isBlank()) {
            System.out.println("username is empty");
            return null;
        }
        System.out.println("Type a message, or /help for commands.");
        return name.trim();
    }

    private static boolean runSession(String name, AtomicReference<PrintWriter> serverOut,
                                      AtomicReference<Socket> socketRef, AtomicBoolean quit) throws IOException {
        try (Socket socket = new Socket(HOST, PORT);
             BufferedReader serverIn = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
            socketRef.set(socket);
            serverOut.set(out);
            out.println(Protocol.login(name));
            Thread ping = new Thread(() -> sendPings(out, quit), "ping");
            ping.setDaemon(true);
            ping.start();
            try {
                String line;
                while (!quit.get() && (line = serverIn.readLine()) != null) {
                    display(Protocol.parse(line));
                }
            } catch (IOException ignored) {
                return quit.get();
            } finally {
                ping.interrupt();
            }
            return quit.get();
        } finally {
            serverOut.set(null);
            socketRef.set(null);
        }
    }

    private static void sendPings(PrintWriter out, AtomicBoolean quit) {
        try {
            while (!quit.get()) {
                Thread.sleep(PING_INTERVAL_MILLIS);
                out.println(Protocol.ping());
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static void display(Protocol.Message message) {
        switch (message.command()) {
            case Protocol.ERROR -> System.out.println(message.argument(0));
            case Protocol.MSG -> System.out.println("[" + message.argument(2) + "] "
                    + message.argument(0) + ": " + message.argument(1));
            case Protocol.PM -> System.out.println("(pm) " + message.argument(0) + ": " + message.argument(1));
            case Protocol.SENT -> System.out.println("(pm to " + message.argument(0) + ") " + message.argument(1));
            case Protocol.ONLINE -> System.out.println("online: " + onlineNames(message));
            case Protocol.JOIN -> System.out.println("* " + message.argument(0) + " joined");
            case Protocol.LEAVE -> System.out.println("* " + message.argument(0) + " left");
            case Protocol.SERVER -> System.out.println("server: " + message.argument(0));
            default -> {
            }
        }
    }

    private static String onlineNames(Protocol.Message message) {
        if (message.arguments().isEmpty()) {
            return "(nobody)";
        }
        return String.join(", ", message.arguments());
    }

    private static void readKeyboard(BufferedReader keyboard, AtomicReference<PrintWriter> serverOut,
                                     AtomicReference<Socket> socketRef, AtomicBoolean quit) {
        try {
            String line;
            while (!quit.get() && (line = keyboard.readLine()) != null) {
                if (!handleLine(line.trim(), serverOut, socketRef, quit)) {
                    return;
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static boolean handleLine(String line, AtomicReference<PrintWriter> serverOut,
                                      AtomicReference<Socket> socketRef, AtomicBoolean quit) throws IOException {
        if (line.isEmpty()) {
            return true;
        }
        if ("/quit".equalsIgnoreCase(line) || "exit".equalsIgnoreCase(line)) {
            quit.set(true);
            PrintWriter out = serverOut.get();
            if (out != null) {
                out.println(Protocol.quit());
            }
            Socket socket = socketRef.get();
            if (socket != null) {
                socket.close();
            }
            return false;
        }
        if ("/help".equalsIgnoreCase(line)) {
            printHelp();
            return true;
        }
        PrintWriter out = serverOut.get();
        if (out == null) {
            System.out.println("not connected");
            return true;
        }
        if ("/online".equalsIgnoreCase(line)) {
            out.println(Protocol.onlineRequest());
            return true;
        }
        if (isWhisper(line)) {
            sendWhisper(line, out);
            return true;
        }
        if (line.startsWith("/")) {
            System.out.println("Unknown command. Type /help.");
            return true;
        }
        if (line.length() > Protocol.MAX_TEXT_LENGTH) {
            System.out.println("message too long");
            return true;
        }
        out.println(Protocol.msg(line));
        return true;
    }

    private static boolean isWhisper(String line) {
        return line.equalsIgnoreCase("/w") || line.regionMatches(true, 0, "/w ", 0, 3);
    }

    private static void sendWhisper(String line, PrintWriter serverOut) {
        String rest = line.length() > 3 ? line.substring(3).trim() : "";
        int splitAt = rest.indexOf(' ');
        if (splitAt < 0) {
            System.out.println("usage: /w name message");
            return;
        }
        String target = rest.substring(0, splitAt);
        String text = rest.substring(splitAt + 1).trim();
        if (target.isEmpty() || text.isEmpty()) {
            System.out.println("usage: /w name message");
            return;
        }
        if (text.length() > Protocol.MAX_TEXT_LENGTH) {
            System.out.println("message too long");
            return;
        }
        serverOut.println(Protocol.privateMessage(target, text));
    }

    private static void printHelp() {
        System.out.println("""
                /w name message   private message
                /online           show who is online
                /help             show this help
                /quit             disconnect
                Any other line is sent to everyone.""");
    }
}
