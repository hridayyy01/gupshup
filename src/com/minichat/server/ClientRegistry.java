package com.minichat.server;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;

public class ClientRegistry {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private final ConcurrentHashMap<ClientHandler, Boolean> clients = new ConcurrentHashMap<>();

    public void add(ClientHandler client) {
        clients.put(client, Boolean.TRUE);
    }

    public void remove(ClientHandler client) {
        clients.remove(client);
    }

    public void broadcast(String message, ClientHandler sender) {
        String text = "[" + LocalTime.now().format(TIME) + "] " + message;
        for (ClientHandler client : clients.keySet()) {
            if (client != sender) {
                client.send(text);
            }
        }
    }
}
