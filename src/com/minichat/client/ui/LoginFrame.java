package com.minichat.client.ui;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

import com.minichat.client.core.ChatConnection;
import com.minichat.client.core.ChatListener;

public class LoginFrame extends JFrame {
    private static final Color ERROR_COLOR = new Color(0xB00020);

    private final JTextField hostField = new JTextField("localhost", 18);
    private final JTextField portField = new JTextField("5000", 18);
    private final JTextField usernameField = new JTextField(18);
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton connectButton = new JButton("Connect");
    private ChatConnection connection;
    private LoginWait loginWait;
    private boolean handedOff;

    public LoginFrame() {
        super("Pigeon");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setContentPane(buildForm());
        getRootPane().setDefaultButton(connectButton);
        connectButton.addActionListener(event -> submit());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                if (!handedOff && connection != null) {
                    connection.disconnect();
                }
            }
        });
        pack();
        setLocationRelativeTo(null);
        setMinimumSize(getSize());
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 2;
        constraints.anchor = GridBagConstraints.LINE_START;
        constraints.insets = new Insets(0, 0, 16, 0);
        JLabel title = new JLabel("Pigeon");
        title.setFont(title.getFont().deriveFont(24f));
        form.add(title, constraints);

        constraints.gridwidth = 1;
        constraints.insets = new Insets(4, 0, 4, 12);
        constraints.gridy = 1;
        form.add(new JLabel("Server"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(4, 0, 4, 0);
        form.add(hostField, constraints);

        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.insets = new Insets(4, 0, 4, 12);
        form.add(new JLabel("Port"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(4, 0, 4, 0);
        form.add(portField, constraints);

        constraints.gridx = 0;
        constraints.gridy = 3;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.insets = new Insets(4, 0, 4, 12);
        form.add(new JLabel("Username"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(4, 0, 4, 0);
        form.add(usernameField, constraints);

        constraints.gridx = 0;
        constraints.gridy = 4;
        constraints.gridwidth = 2;
        constraints.insets = new Insets(12, 0, 0, 0);
        statusLabel.setForeground(ERROR_COLOR);
        form.add(statusLabel, constraints);

        constraints.gridy = 5;
        constraints.anchor = GridBagConstraints.CENTER;
        constraints.fill = GridBagConstraints.NONE;
        constraints.insets = new Insets(16, 0, 0, 0);
        form.add(connectButton, constraints);
        return form;
    }

    private void submit() {
        String host = hostField.getText().trim();
        String username = usernameField.getText().trim();
        String portText = portField.getText().trim();
        String error = validate(host, portText, username);
        if (error != null) {
            showError(error);
            return;
        }
        int port = Integer.parseInt(portText);
        setFormEnabled(false);
        showStatus("Connecting...");
        loginWait = new LoginWait(username);
        connection = new ChatConnection(loginWait);
        loginWait.bind(connection);
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                connection.connect(host, port, username);
                return loginWait.await();
            }

            @Override
            protected void done() {
                if (!isDisplayable() || handedOff) {
                    return;
                }
                String failure;
                try {
                    failure = get();
                } catch (Exception ex) {
                    failure = "connection failed";
                }
                if (failure == null) {
                    openChat(username);
                    return;
                }
                setFormEnabled(true);
                showError(failure);
            }
        }.execute();
    }

    private void openChat(String username) {
        handedOff = true;
        ChatFrame chat = new ChatFrame(connection, username);
        loginWait.handoff(chat);
        Thread list = new Thread(connection::requestOnlineList, "online-list");
        list.setDaemon(true);
        list.start();
        chat.setVisible(true);
        dispose();
    }

    private static String validate(String host, String portText, String username) {
        if (host.isEmpty()) {
            return "server host is empty";
        }
        if (username.isEmpty()) {
            return "username is empty";
        }
        if (username.indexOf('|') >= 0) {
            return "username cannot contain |";
        }
        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException ex) {
            return "port must be a number from 1 to 65535";
        }
        if (port < 1 || port > 65535) {
            return "port must be a number from 1 to 65535";
        }
        return null;
    }

    private void setFormEnabled(boolean enabled) {
        hostField.setEnabled(enabled);
        portField.setEnabled(enabled);
        usernameField.setEnabled(enabled);
        connectButton.setEnabled(enabled);
    }

    private void showError(String message) {
        statusLabel.setForeground(ERROR_COLOR);
        statusLabel.setText(message);
    }

    private void showStatus(String message) {
        statusLabel.setForeground(new Color(0x444444));
        statusLabel.setText(message);
    }

    private static final class LoginWait implements ChatListener {
        private final String username;
        private final CountDownLatch done = new CountDownLatch(1);
        private final AtomicReference<String> failure = new AtomicReference<>();
        private final AtomicBoolean succeeded = new AtomicBoolean();
        private final List<Consumer<ChatListener>> pending = new ArrayList<>();
        private ChatConnection connection;
        private ChatListener target;
        private boolean live;

        private LoginWait(String username) {
            this.username = username;
        }

        private void bind(ChatConnection connection) {
            this.connection = connection;
        }

        private String await() {
            try {
                done.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                stop("connection failed");
                return "connection failed";
            }
            return failure.get();
        }

        private void handoff(ChatListener next) {
            List<Consumer<ChatListener>> copy;
            synchronized (pending) {
                target = next;
                live = true;
                copy = new ArrayList<>(pending);
                pending.clear();
            }
            for (Consumer<ChatListener> event : copy) {
                event.accept(next);
            }
        }

        private void deliver(Consumer<ChatListener> event) {
            ChatListener current;
            synchronized (pending) {
                if (!live) {
                    pending.add(event);
                    return;
                }
                current = target;
            }
            event.accept(current);
        }

        private void succeed() {
            if (succeeded.compareAndSet(false, true)) {
                done.countDown();
            }
        }

        private void stop(String message) {
            if (succeeded.get() || !failure.compareAndSet(null, message)) {
                return;
            }
            if (connection != null) {
                connection.disconnect();
            }
            done.countDown();
        }

        @Override
        public void onConnected() {
            if (succeeded.get()) {
                deliver(ChatListener::onConnected);
            }
        }

        @Override
        public void onDisconnected() {
            if (succeeded.get()) {
                deliver(ChatListener::onDisconnected);
                return;
            }
            stop("connection closed");
        }

        @Override
        public void onReconnecting(int attempt) {
            if (succeeded.get()) {
                deliver(listener -> listener.onReconnecting(attempt));
                return;
            }
            stop("could not connect");
        }

        @Override
        public void onMessage(String sender, String text, String time) {
            deliver(listener -> listener.onMessage(sender, text, time));
        }

        @Override
        public void onPrivateMessage(String from, String text, String time) {
            deliver(listener -> listener.onPrivateMessage(from, text, time));
        }

        @Override
        public void onPrivateSent(String to, String text, String time) {
            deliver(listener -> listener.onPrivateSent(to, text, time));
        }

        @Override
        public void onUserJoined(String name) {
            deliver(listener -> listener.onUserJoined(name));
            if (username.equals(name)) {
                succeed();
            }
        }

        @Override
        public void onUserLeft(String name) {
            deliver(listener -> listener.onUserLeft(name));
        }

        @Override
        public void onOnlineList(List<String> names) {
            List<String> copy = List.copyOf(names);
            deliver(listener -> listener.onOnlineList(copy));
        }

        @Override
        public void onError(String message) {
            if (succeeded.get()) {
                deliver(listener -> listener.onError(message));
                return;
            }
            stop(message);
        }
    }
}
