package com.minichat.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.minichat.client.core.ChatConnection;
import com.minichat.client.core.ChatListener;
import com.minichat.common.Protocol;

public class SimpleClient {
    private static final String HOST = "localhost";
    private static final int PORT = 5000;

    public static void main(String[] args) throws IOException {
        BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String name = readName(keyboard);
        if (name == null) {
            return;
        }
        ChatConnection connection = new ChatConnection(new ConsoleListener());
        connection.connect(HOST, PORT, name);
        readKeyboard(keyboard, connection);
        connection.disconnect();
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

    private static void readKeyboard(BufferedReader keyboard, ChatConnection connection) throws IOException {
        String line;
        while ((line = keyboard.readLine()) != null) {
            if (!handleLine(line.trim(), connection)) {
                return;
            }
        }
    }

    private static boolean handleLine(String line, ChatConnection connection) {
        if (line.isEmpty()) {
            return true;
        }
        if ("/quit".equalsIgnoreCase(line) || "exit".equalsIgnoreCase(line)) {
            connection.disconnect();
            return false;
        }
        if ("/help".equalsIgnoreCase(line)) {
            printHelp();
            return true;
        }
        if ("/online".equalsIgnoreCase(line)) {
            connection.requestOnlineList();
            return true;
        }
        if (isWhisper(line)) {
            sendWhisper(line, connection);
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
        connection.sendMessage(line);
        return true;
    }

    private static boolean isWhisper(String line) {
        return line.equalsIgnoreCase("/w") || line.regionMatches(true, 0, "/w ", 0, 3);
    }

    private static void sendWhisper(String line, ChatConnection connection) {
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
        connection.sendPrivate(target, text);
    }

    private static void printHelp() {
        System.out.println("""
                /w name message   private message
                /online           show who is online
                /help             show this help
                /quit             disconnect
                Any other line is sent to everyone.""");
    }

    private static final class ConsoleListener implements ChatListener {
        @Override
        public void onConnected() {
        }

        @Override
        public void onDisconnected() {
        }

        @Override
        public void onReconnecting(int attempt) {
            System.out.println("reconnecting in " + (backoffSeconds(attempt)) + "s");
        }

        @Override
        public void onMessage(String sender, String text, String time) {
            System.out.println("[" + time + "] " + sender + ": " + text);
        }

        @Override
        public void onPrivateMessage(String from, String text, String time) {
            System.out.println("(pm) " + from + ": " + text);
        }

        @Override
        public void onPrivateSent(String to, String text, String time) {
            System.out.println("(pm to " + to + ") " + text);
        }

        @Override
        public void onUserJoined(String name) {
            System.out.println("* " + name + " joined");
        }

        @Override
        public void onUserLeft(String name) {
            System.out.println("* " + name + " left");
        }

        @Override
        public void onOnlineList(List<String> names) {
            if (names.isEmpty()) {
                System.out.println("online: (nobody)");
                return;
            }
            System.out.println("online: " + String.join(", ", names));
        }

        @Override
        public void onError(String message) {
            System.out.println(message);
        }

        private static int backoffSeconds(int attempt) {
            long backoff = 1_000;
            for (int i = 1; i < attempt; i++) {
                backoff = Math.min(backoff * 2, 30_000);
            }
            return (int) (backoff / 1000);
        }
    }
}
