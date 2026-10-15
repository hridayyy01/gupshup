package com.minichat.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class SimpleClient {
    private static final String HOST = "localhost";
    private static final int PORT = 5000;

    public static void main(String[] args) throws IOException {
        try (Socket socket = new Socket(HOST, PORT);
             BufferedReader serverIn = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter serverOut = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
            Thread reader = new Thread(() -> readIncoming(serverIn), "server-reader");
            reader.setDaemon(true);
            reader.start();

            BufferedReader keyboard = new BufferedReader(
                    new InputStreamReader(System.in, StandardCharsets.UTF_8));
            sendOutgoing(keyboard, serverOut);
        }
    }

    private static void readIncoming(BufferedReader serverIn) {
        try {
            String line;
            while ((line = serverIn.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException ignored) {
            // Main thread closed the socket on exit.
        }
    }

    private static void sendOutgoing(BufferedReader keyboard, PrintWriter serverOut) throws IOException {
        String line;
        while ((line = keyboard.readLine()) != null && !"exit".equals(line)) {
            serverOut.println(line);
        }
    }
}
