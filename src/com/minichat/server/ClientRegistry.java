package com.minichat.server;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.minichat.common.Protocol;

public class ClientRegistry {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final Set<ClientHandler> connections = ConcurrentHashMap.newKeySet();
    private final HistoryStore history;

    public ClientRegistry(HistoryStore history) {
        this.history = history;
    }

    public boolean add(String name, ClientHandler client) {
        return clients.putIfAbsent(name, client) == null;
    }

    public void announceJoin(String name) {
        sendAll(Protocol.join(name));
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
        String line = Protocol.chatMessage(sender, text, LocalTime.now().format(TIME));
        history.appendPublic(line);
        sendAll(line);
    }

    public void track(ClientHandler client) {
        connections.add(client);
    }

    public void untrack(ClientHandler client) {
        connections.remove(client);
    }

    public void disconnectAll(String notice) {
        List<ClientHandler> open = new ArrayList<>(connections);
        clients.clear();
        connections.clear();
        for (ClientHandler client : open) {
            client.send(notice);
            client.disconnect();
        }
    }

    private void sendAll(String line) {
        for (ClientHandler client : clients.values()) {
            client.send(line);
        }
    }
}
