package com.minichat.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import com.minichat.client.core.ChatConnection;
import com.minichat.client.core.ChatListener;

public class ChatFrame extends JFrame implements ChatListener {
    private static final Color MINE = new Color(0x1565C0);
    private static final Color THEIRS = new Color(0x212121);
    private static final Color SYSTEM = new Color(0x757575);
    private static final Color PRIVATE = new Color(0x6A1B9A);
    private static final Color ERROR = new Color(0xB00020);
    private static final Color CONNECTED = new Color(0x2E7D32);
    private static final Color RECONNECTING = new Color(0xEF6C00);
    private static final Color DISCONNECTED = new Color(0xC62828);

    private final ChatConnection connection;
    private final String username;
    private final DefaultListModel<String> onlineModel = new DefaultListModel<>();
    private final JList<String> onlineList = new JList<>(onlineModel);
    private final JLabel onlineHeader = new JLabel("Online (0)");
    private final JTextPane transcript = new JTextPane();
    private final JTextField input = new JTextField();
    private final JButton sendButton = new JButton("Send");
    private final JLabel statusText = new JLabel("Connected");
    private final JLabel banner = new JLabel("Connection lost. Reconnecting...");
    private final StatusDot statusDot = new StatusDot(CONNECTED);

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
        root.add(buildTranscript(), BorderLayout.CENTER);
        root.add(buildInput(), BorderLayout.SOUTH);
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
        west.add(new JScrollPane(onlineList), BorderLayout.CENTER);
        return west;
    }

    private JScrollPane buildTranscript() {
        transcript.setEditable(false);
        transcript.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        return new JScrollPane(transcript);
    }

    private JPanel buildInput() {
        JPanel south = new JPanel(new BorderLayout(8, 0));
        south.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));
        input.addActionListener(event -> send());
        sendButton.addActionListener(event -> send());
        south.add(input, BorderLayout.CENTER);
        south.add(sendButton, BorderLayout.EAST);
        return south;
    }

    private void send() {
        String text = input.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        input.setText("");
        connection.sendMessage(text);
    }

    @Override
    public void onConnected() {
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
        onEdt(() -> appendMessage(sender, text, time, username.equals(sender)));
    }

    @Override
    public void onPrivateMessage(String from, String text, String time) {
        onEdt(() -> appendPrivate(from, text, time));
    }

    @Override
    public void onUserJoined(String name) {
        onEdt(() -> {
            addOnline(name);
            appendSystem("* " + name + " joined");
        });
    }

    @Override
    public void onUserLeft(String name) {
        onEdt(() -> {
            onlineModel.removeElement(name);
            refreshOnlineHeader();
            appendSystem("* " + name + " left");
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
        });
    }

    @Override
    public void onError(String message) {
        onEdt(() -> appendError(message));
    }

    private void setConnected(boolean connected) {
        input.setEnabled(connected);
        sendButton.setEnabled(connected);
        banner.setVisible(!connected);
        if (connected) {
            statusText.setText("Connected");
            statusDot.setColor(CONNECTED);
            input.requestFocusInWindow();
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

    private void appendMessage(String sender, String text, String time, boolean mine) {
        SimpleAttributeSet style = new SimpleAttributeSet();
        StyleConstants.setForeground(style, mine ? MINE : THEIRS);
        StyleConstants.setAlignment(style, mine ? StyleConstants.ALIGN_RIGHT : StyleConstants.ALIGN_LEFT);
        append("[" + clock(time) + "] " + sender + ": " + text, style);
    }

    private void appendPrivate(String from, String text, String time) {
        SimpleAttributeSet style = new SimpleAttributeSet();
        StyleConstants.setForeground(style, PRIVATE);
        StyleConstants.setItalic(style, true);
        String prefix = clock(time).isEmpty() ? "" : "[" + clock(time) + "] ";
        append(prefix + "(pm) " + from + ": " + text, style);
    }

    private void appendSystem(String text) {
        SimpleAttributeSet style = new SimpleAttributeSet();
        StyleConstants.setForeground(style, SYSTEM);
        StyleConstants.setItalic(style, true);
        append(text, style);
    }

    private void appendError(String text) {
        SimpleAttributeSet style = new SimpleAttributeSet();
        StyleConstants.setForeground(style, ERROR);
        StyleConstants.setItalic(style, true);
        append(text, style);
    }

    private void append(String text, SimpleAttributeSet style) {
        StyledDocument document = transcript.getStyledDocument();
        try {
            int start = document.getLength();
            document.insertString(start, text + "\n", style);
            document.setParagraphAttributes(start, text.length() + 1, style, false);
            transcript.setCaretPosition(document.getLength());
        } catch (BadLocationException ignored) {
        }
    }

    private static String clock(String time) {
        if (time == null || time.isBlank()) {
            return "";
        }
        return time.length() >= 5 ? time.substring(0, 5) : time;
    }

    private void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
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
