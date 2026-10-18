package com.minichat.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import com.minichat.client.core.ChatConnection;
import com.minichat.client.core.ChatListener;

public class ChatFrame extends JFrame implements ChatListener {
    private static final Color ERROR = new Color(0xB00020);
    private static final Color CONNECTED = new Color(0x2E7D32);
    private static final Color RECONNECTING = new Color(0xEF6C00);
    private static final Color DISCONNECTED = new Color(0xC62828);

    private final ChatConnection connection;
    private final String username;
    private final DefaultListModel<String> onlineModel = new DefaultListModel<>();
    private final JList<String> onlineList = new JList<>(onlineModel);
    private final JLabel onlineHeader = new JLabel("Online (0)");
    private final JTabbedPane tabs = new JTabbedPane();
    private final ChatPanel group = new ChatPanel(this::sendGroup);
    private final Map<String, ChatPanel> privateTabs = new HashMap<>();
    private final Map<ChatPanel, TabTitle> titles = new HashMap<>();
    private final JLabel statusText = new JLabel("Connected");
    private final JLabel banner = new JLabel("Connection lost. Reconnecting...");
    private final StatusDot statusDot = new StatusDot(CONNECTED);
    private boolean connected = true;

    public ChatFrame(ChatConnection connection, String username) {
        super("Pigeon — " + username);
        this.connection = connection;
        this.username = username;
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setContentPane(buildUi());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                connection.disconnect();
            }
        });
        setSize(760, 520);
        setLocationRelativeTo(null);
        setConnected(true);
    }

    private JPanel buildUi() {
        JPanel root = new JPanel(new BorderLayout());
        root.add(buildStatus(), BorderLayout.NORTH);
        root.add(buildOnline(), BorderLayout.WEST);
        root.add(buildTabs(), BorderLayout.CENTER);
        return root;
    }

    private JPanel buildStatus() {
        JPanel status = new JPanel(new BorderLayout(8, 0));
        status.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        JPanel line = new JPanel(new BorderLayout(8, 0));
        line.setOpaque(false);
        line.add(statusDot, BorderLayout.WEST);
        line.add(statusText, BorderLayout.CENTER);
        status.add(line, BorderLayout.NORTH);
        banner.setOpaque(true);
        banner.setBackground(new Color(0xFDECEA));
        banner.setForeground(ERROR);
        banner.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        banner.setVisible(false);
        status.add(banner, BorderLayout.SOUTH);
        return status;
    }

    private JPanel buildOnline() {
        JPanel west = new JPanel(new BorderLayout(0, 6));
        west.setPreferredSize(new Dimension(168, 0));
        west.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 8));
        onlineHeader.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
        west.add(onlineHeader, BorderLayout.NORTH);
        onlineList.setFixedCellHeight(22);
        onlineList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    openSelectedUser();
                }
            }
        });
        west.add(new JScrollPane(onlineList), BorderLayout.CENTER);
        return west;
    }

    private JTabbedPane buildTabs() {
        tabs.addTab("Group", group);
        titles.put(group, installTitle(group, "Group", false));
        tabs.addChangeListener(event -> clearUnread(selectedPanel()));
        return tabs;
    }

    private void sendGroup(String text) {
        sendAsync(() -> connection.sendMessage(text));
    }

    private void sendAsync(Runnable send) {
        Thread thread = new Thread(send, "chat-send");
        thread.setDaemon(true);
        thread.start();
    }

    private void openSelectedUser() {
        String name = onlineList.getSelectedValue();
        if (name == null || name.equals(username)) {
            return;
        }
        openPrivate(name, true);
    }

    private ChatPanel openPrivate(String name, boolean focus) {
        ChatPanel existing = privateTabs.get(name);
        if (existing != null) {
            if (focus) {
                tabs.setSelectedComponent(existing);
                existing.focusInput();
            }
            return existing;
        }
        ChatPanel panel = new ChatPanel(text -> sendAsync(() -> connection.sendPrivate(name, text)));
        panel.setConnected(connected);
        panel.setPeerOnline(onlineModel.contains(name));
        privateTabs.put(name, panel);
        int selected = tabs.getSelectedIndex();
        tabs.insertTab(name, null, panel, null, tabs.getTabCount());
        titles.put(panel, installTitle(panel, name, true));
        if (focus) {
            tabs.setSelectedComponent(panel);
            panel.focusInput();
        } else if (selected >= 0) {
            tabs.setSelectedIndex(selected);
        }
        return panel;
    }

    private TabTitle installTitle(ChatPanel panel, String name, boolean closable) {
        TabTitle title = new TabTitle(name, closable, () -> closePrivate(name, panel));
        int index = tabs.indexOfComponent(panel);
        tabs.setTabComponentAt(index, title);
        return title;
    }

    private void closePrivate(String name, ChatPanel panel) {
        int index = tabs.indexOfComponent(panel);
        if (index > 0) {
            tabs.remove(index);
        }
        privateTabs.remove(name);
        titles.remove(panel);
    }

    private void note(ChatPanel panel) {
        if (panel == selectedPanel()) {
            return;
        }
        TabTitle title = titles.get(panel);
        if (title != null) {
            title.addUnread();
        }
    }

    private void clearUnread(ChatPanel panel) {
        if (panel == null) {
            return;
        }
        TabTitle title = titles.get(panel);
        if (title != null) {
            title.clearUnread();
        }
    }

    private ChatPanel selectedPanel() {
        return tabs.getSelectedComponent() instanceof ChatPanel panel ? panel : group;
    }

    @Override
    public void onConnected() {
        connection.requestOnlineList();
        onEdt(() -> setConnected(true));
    }

    @Override
    public void onDisconnected() {
        onEdt(() -> {
            setConnected(false);
            statusText.setText("Disconnected");
            statusDot.setColor(DISCONNECTED);
            banner.setText("Disconnected");
        });
    }

    @Override
    public void onReconnecting(int attempt) {
        onEdt(() -> {
            setConnected(false);
            statusText.setText("Reconnecting...");
            statusDot.setColor(RECONNECTING);
            banner.setText("Connection lost. Reconnecting...");
        });
    }

    @Override
    public void onMessage(String sender, String text, String time) {
        onEdt(() -> {
            group.appendMessage(sender, text, time, username.equals(sender));
            note(group);
        });
    }

    @Override
    public void onPrivateMessage(String from, String text, String time) {
        onEdt(() -> {
            ChatPanel panel = openPrivate(from, false);
            panel.appendMessage(from, text, time, false);
            note(panel);
        });
    }

    @Override
    public void onPrivateSent(String to, String text, String time) {
        onEdt(() -> {
            ChatPanel panel = privateTabs.get(to);
            if (panel == null) {
                panel = openPrivate(to, false);
            }
            panel.appendMessage(username, text, time, true);
            note(panel);
        });
    }

    @Override
    public void onUserJoined(String name) {
        onEdt(() -> {
            addOnline(name);
            group.appendSystem("* " + name + " joined");
            note(group);
            ChatPanel panel = privateTabs.get(name);
            if (panel != null) {
                panel.appendSystem(name + " is online");
                panel.setPeerOnline(true);
            }
        });
    }

    @Override
    public void onUserLeft(String name) {
        onEdt(() -> {
            onlineModel.removeElement(name);
            refreshOnlineHeader();
            group.appendSystem("* " + name + " left");
            note(group);
            ChatPanel panel = privateTabs.get(name);
            if (panel != null) {
                panel.appendSystem(name + " is offline");
                panel.setPeerOnline(false);
            }
        });
    }

    @Override
    public void onOnlineList(List<String> names) {
        List<String> copy = List.copyOf(names);
        onEdt(() -> {
            onlineModel.clear();
            for (String name : copy) {
                onlineModel.addElement(name);
            }
            refreshOnlineHeader();
            for (Map.Entry<String, ChatPanel> entry : privateTabs.entrySet()) {
                entry.getValue().setPeerOnline(onlineModel.contains(entry.getKey()));
            }
        });
    }

    @Override
    public void onError(String message) {
        onEdt(() -> selectedPanel().appendError(message));
    }

    private void setConnected(boolean connected) {
        this.connected = connected;
        group.setConnected(connected);
        for (ChatPanel panel : privateTabs.values()) {
            panel.setConnected(connected);
        }
        banner.setVisible(!connected);
        if (connected) {
            statusText.setText("Connected");
            statusDot.setColor(CONNECTED);
            selectedPanel().focusInput();
        }
    }

    private void addOnline(String name) {
        if (!onlineModel.contains(name)) {
            onlineModel.addElement(name);
        }
        refreshOnlineHeader();
    }

    private void refreshOnlineHeader() {
        onlineHeader.setText("Online (" + onlineModel.getSize() + ")");
    }

    private void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }

    private static final class TabTitle extends JPanel {
        private final JLabel label;
        private final String name;
        private final Font plain;
        private final Font bold;
        private int unread;

        private TabTitle(String name, boolean closable, Runnable onClose) {
            super(new FlowLayout(FlowLayout.LEADING, 4, 0));
            this.name = name;
            setOpaque(false);
            label = new JLabel(name);
            plain = label.getFont().deriveFont(Font.PLAIN);
            bold = label.getFont().deriveFont(Font.BOLD);
            label.setFont(plain);
            add(label);
            if (!closable) {
                return;
            }
            JButton close = new JButton("x");
            close.setMargin(new Insets(0, 2, 0, 2));
            close.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 2));
            close.setContentAreaFilled(false);
            close.setFocusable(false);
            close.addActionListener(event -> onClose.run());
            add(close);
        }

        private void addUnread() {
            unread++;
            label.setText(name + " (" + unread + ")");
            label.setFont(bold);
        }

        private void clearUnread() {
            unread = 0;
            label.setText(name);
            label.setFont(plain);
        }
    }

    private static final class StatusDot extends JPanel {
        private Color color;

        private StatusDot(Color color) {
            this.color = color;
            setOpaque(false);
            setPreferredSize(new Dimension(12, 12));
        }

        private void setColor(Color color) {
            this.color = color;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);
            g.fillOval(1, 1, 10, 10);
            g.dispose();
        }
    }
}
