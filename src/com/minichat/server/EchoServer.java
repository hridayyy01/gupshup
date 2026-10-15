package com.minichat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class EchoServer {
    private static final int PORT = 5000;

    public static void main(String[] args) throws IOException {
        ClientRegistry registry = new ClientRegistry();
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Listening on port " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket, registry)).start();
            }
        }
    }
}
