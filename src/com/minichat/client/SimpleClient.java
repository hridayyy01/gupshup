package com.minichat.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import com.minichat.common.Protocol;

public class SimpleClient {
    private static final String HOST = "localhost";
    private static final int PORT = 5000;

    public static void main(String[] args) throws IOException, InterruptedException {
        try (Socket socket = new Socket(HOST, PORT);
             BufferedReader serverIn = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter serverOut = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
            BufferedReader keyboard = new BufferedReader(
                    new InputStreamReader(System.in, StandardCharsets.UTF_8));
            if (!sendLogin(keyboard, serverOut)) {
                return;
            }

            AtomicBoolean running = new AtomicBoolean(true);
            Thread serverReader = new Thread(() -> readServer(serverIn, running), "server-reader");
            Thread keyboardReader = new Thread(
                    () -> readKeyboard(keyboard, serverOut, socket, running), "keyboard-reader");
            keyboardReader.setDaemon(true);
            serverReader.start();
            keyboardReader.start();
            serverReader.join();
        }
    }

    private static boolean sendLogin(BufferedReader keyboard, PrintWriter serverOut) throws IOException {
        System.out.print("Name: ");
        System.out.flush();
        String name = keyboard.readLine();
        if (name == null || name.isBlank()) {
            System.out.println("username is empty");
            return false;
        }
        serverOut.println(Protocol.login(name.trim()));
        System.out.println("Type a message, or /help for commands.");
        return true;
    }

    private static void readServer(BufferedReader serverIn, AtomicBoolean running) {
        try {
            String line;
            while (running.get() && (line = serverIn.readLine()) != null) {
                display(Protocol.parse(line));
            }
        } catch (IOException ignored) {
            // Keyboard thread closed the socket on quit, or the server dropped the connection.
        } finally {
            running.set(false);
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

    private static void readKeyboard(BufferedReader keyboard, PrintWriter serverOut, Socket socket,
                                     AtomicBoolean running) {
        try {
            String line;
            while (running.get() && (line = keyboard.readLine()) != null) {
                if (!handleLine(line.trim(), serverOut, socket, running)) {
                    return;
                }
            }
        } catch (IOException ignored) {
        } finally {
            running.set(false);
        }
    }

    private static boolean handleLine(String line, PrintWriter serverOut, Socket socket, AtomicBoolean running)
            throws IOException {
        if (line.isEmpty()) {
            return true;
        }
        if ("/quit".equalsIgnoreCase(line) || "exit".equalsIgnoreCase(line)) {
            serverOut.println(Protocol.quit());
            running.set(false);
            socket.close();
            return false;
        }
        if ("/help".equalsIgnoreCase(line)) {
            printHelp();
            return true;
        }
        if ("/online".equalsIgnoreCase(line)) {
            serverOut.println(Protocol.onlineRequest());
            return true;
        }
        if (isWhisper(line)) {
            sendWhisper(line, serverOut);
            return true;
        }
        if (line.startsWith("/")) {
            System.out.println("Unknown command. Type /help.");
            return true;
        }
        serverOut.println(Protocol.msg(line));
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
