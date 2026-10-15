package com.minichat.server;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;

import com.minichat.common.Protocol;

public class ClientRegistry {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();

    public boolean add(String name, ClientHandler client) {
        return clients.putIfAbsent(name, client) == null;
    }

    public void remove(String name) {
        clients.remove(name);
    }

    public void broadcast(String sender, String text) {
        String line = Protocol.chatMessage(sender, text, LocalTime.now().format(TIME));
        for (ClientHandler client : clients.values()) {
            client.send(line);
        }
    }
}
