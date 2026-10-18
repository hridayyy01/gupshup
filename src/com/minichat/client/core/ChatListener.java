package com.minichat.client.core;

import java.util.List;

public interface ChatListener {
    void onConnected();

    void onDisconnected();

    void onReconnecting(int attempt);

    void onMessage(String sender, String text, String time);

    void onPrivateMessage(String from, String text, String time);

    void onPrivateSent(String to, String text, String time);

    void onUserJoined(String name);

    void onUserLeft(String name);

    void onOnlineList(List<String> names);

    void onError(String message);
}
