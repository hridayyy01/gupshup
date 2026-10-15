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
        System.out.println("Type a message, or exit to quit.");
        return true;
    }

    private static void readServer(BufferedReader serverIn, AtomicBoolean running) {
        try {
            String line;
            while (running.get() && (line = serverIn.readLine()) != null) {
                Protocol.Message message = Protocol.parse(line);
                if (Protocol.ERROR.equals(message.command())) {
                    System.out.println(message.argument(0));
                    running.set(false);
                    return;
                }
                if (Protocol.MSG.equals(message.command())) {
                    System.out.println("[" + message.argument(2) + "] "
                            + message.argument(0) + ": " + message.argument(1));
                }
            }
        } catch (IOException ignored) {
            // Keyboard thread closed the socket on quit, or the server dropped the connection.
        } finally {
            running.set(false);
        }
    }

    private static void readKeyboard(BufferedReader keyboard, PrintWriter serverOut, Socket socket,
                                     AtomicBoolean running) {
        try {
            String line;
            while (running.get() && (line = keyboard.readLine()) != null) {
                if (isQuit(line)) {
                    serverOut.println(Protocol.quit());
                    running.set(false);
                    socket.close();
                    return;
                }
                if (!line.isEmpty()) {
                    serverOut.println(Protocol.msg(line));
                }
            }
        } catch (IOException ignored) {
        } finally {
            running.set(false);
        }
    }

    private static boolean isQuit(String line) {
        return "exit".equalsIgnoreCase(line) || Protocol.QUIT.equalsIgnoreCase(line);
    }
}
