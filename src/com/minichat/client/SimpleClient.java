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
            BufferedReader keyboard = new BufferedReader(
                    new InputStreamReader(System.in, StandardCharsets.UTF_8));
            chat(keyboard, serverIn, serverOut);
        }
    }

    private static void chat(BufferedReader keyboard, BufferedReader serverIn, PrintWriter serverOut)
            throws IOException {
        String line;
        while ((line = keyboard.readLine()) != null) {
            serverOut.println(line);
            String reply = serverIn.readLine();
            if (reply == null) {
                break;
            }
            System.out.println(reply);
            if ("exit".equals(line)) {
                break;
            }
        }
    }
}
