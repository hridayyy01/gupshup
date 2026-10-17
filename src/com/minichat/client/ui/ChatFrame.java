package com.minichat.client.ui;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.WindowConstants;

import com.minichat.client.core.ChatConnection;

public class ChatFrame extends JFrame {
    private final ChatConnection connection;

    public ChatFrame(ChatConnection connection, String username) {
        super("Pigeon — " + username);
        this.connection = connection;
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(760, 520);
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                connection.disconnect();
            }
        });
    }
}
