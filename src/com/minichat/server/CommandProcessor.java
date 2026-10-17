package com.minichat.server;

import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.minichat.common.Protocol;

public final class CommandProcessor {
    private static final int MAX_MESSAGES = 5;
    private static final long WINDOW_NANOS = TimeUnit.SECONDS.toNanos(3);

    private final ClientRegistry registry;
    private final HistoryStore history;
    private final ConcurrentHashMap<String, ArrayDeque<Long>> sentAt = new ConcurrentHashMap<>();

    public CommandProcessor(ClientRegistry registry, HistoryStore history) {
        this.registry = registry;
        this.history = history;
    }

    public String login(ClientHandler client, Protocol.Message message) {
        if (!Protocol.LOGIN.equals(message.command())) {
            client.send(Protocol.error("first command must be LOGIN"));
            return null;
        }
        String name = message.argument(0).trim();
        if (name.isEmpty()) {
            client.send(Protocol.error("username is empty"));
            return null;
        }
        if (!registry.add(name, client)) {
            client.send(Protocol.error("username is taken"));
            return null;
        }
        for (String line : history.recentPublic()) {
            client.send(line);
        }
        registry.announceJoin(name);
        ServerLog.LOG.info(name + " logged in");
        return name;
    }

    public void leave(String name) {
        sentAt.remove(name);
        if (registry.remove(name)) {
            ServerLog.LOG.info(name + " left");
        }
    }

    public void closed(ClientHandler client) {
        registry.untrack(client);
    }

    public boolean handle(ClientHandler client, String name, Protocol.Message message) {
        return switch (message.command()) {
            case Protocol.QUIT -> false;
            case Protocol.MSG -> {
                publicMessage(client, name, message);
                yield true;
            }
            case Protocol.PM -> {
                privateMessage(client, name, message);
                yield true;
            }
            case Protocol.ONLINE -> {
                client.send(Protocol.online(registry.names()));
                yield true;
            }
            case Protocol.PING -> {
                client.send(Protocol.pong());
                yield true;
            }
            default -> {
                client.send(Protocol.error("unknown command"));
                yield true;
            }
        };
    }

    private void publicMessage(ClientHandler client, String sender, Protocol.Message message) {
        String text = message.argument(0);
        if (text.isEmpty() || !accepted(client, sender, text)) {
            return;
        }
        registry.broadcast(sender, text);
    }

    private void privateMessage(ClientHandler sender, String from, Protocol.Message message) {
        String target = message.argument(0).trim();
        String text = message.argument(1).trim();
        if (target.isEmpty() || text.isEmpty()) {
            sender.send(Protocol.error("usage: /w name message"));
            return;
        }
        if (!accepted(sender, from, text)) {
            return;
        }
        if (target.equals(from)) {
            sender.send(Protocol.error("you cannot message yourself"));
            return;
        }
        ClientHandler recipient = registry.find(target);
        if (recipient == null) {
            sender.send(Protocol.error("no such user: " + target));
            return;
        }
        history.appendPrivate(from, target, text);
        recipient.send(Protocol.privateMessage(from, text));
        sender.send(Protocol.privateMessageSent(target, text));
    }

    private boolean accepted(ClientHandler client, String name, String text) {
        if (!permit(name)) {
            client.send(Protocol.error("slow down"));
            return false;
        }
        if (text.length() > Protocol.MAX_TEXT_LENGTH) {
            client.send(Protocol.error("message too long"));
            return false;
        }
        return true;
    }

    private boolean permit(String name) {
        long now = System.nanoTime();
        ArrayDeque<Long> times = sentAt.computeIfAbsent(name, key -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() > WINDOW_NANOS) {
                times.removeFirst();
            }
            if (times.size() >= MAX_MESSAGES) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }
}
