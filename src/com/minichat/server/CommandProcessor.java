package com.minichat.server;

import com.minichat.common.Protocol;

public final class CommandProcessor {
    private final ClientRegistry registry;

    public CommandProcessor(ClientRegistry registry) {
        this.registry = registry;
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
        return name;
    }

    public void leave(String name) {
        registry.remove(name);
    }

    public void closed(ClientHandler client) {
        registry.untrack(client);
    }

    public boolean handle(ClientHandler client, String name, Protocol.Message message) {
        return switch (message.command()) {
            case Protocol.QUIT -> false;
            case Protocol.MSG -> {
                publicMessage(name, message);
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
            default -> {
                client.send(Protocol.error("unknown command"));
                yield true;
            }
        };
    }

    private void publicMessage(String sender, Protocol.Message message) {
        String text = message.argument(0);
        if (!text.isEmpty()) {
            registry.broadcast(sender, text);
        }
    }

    private void privateMessage(ClientHandler sender, String from, Protocol.Message message) {
        String target = message.argument(0).trim();
        String text = message.argument(1).trim();
        if (target.isEmpty() || text.isEmpty()) {
            sender.send(Protocol.error("usage: /w name message"));
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
        recipient.send(Protocol.privateMessage(from, text));
        sender.send(Protocol.privateMessageSent(target, text));
    }
}
