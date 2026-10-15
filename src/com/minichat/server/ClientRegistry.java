package com.minichat.server;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.minichat.common.Protocol;

public class ClientRegistry {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();

    public boolean add(String name, ClientHandler client) {
        if (clients.putIfAbsent(name, client) != null) {
            return false;
        }
        sendAll(Protocol.join(name));
        return true;
    }

    public void remove(String name) {
        if (clients.remove(name) != null) {
            sendAll(Protocol.leave(name));
        }
    }

    public ClientHandler find(String name) {
        return clients.get(name);
    }

    public List<String> names() {
        List<String> names = new ArrayList<>(clients.keySet());
        Collections.sort(names);
        return names;
    }

    public void broadcast(String sender, String text) {
        sendAll(Protocol.chatMessage(sender, text, LocalTime.now().format(TIME)));
    }

    private void sendAll(String line) {
        for (ClientHandler client : clients.values()) {
            client.send(line);
        }
    }
}
