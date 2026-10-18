package com.minichat.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

public class ChatPanel extends JPanel {
    private static final Color MINE = new Color(0x1565C0);
    private static final Color THEIRS = new Color(0x212121);
    private static final Color SYSTEM = new Color(0x757575);
    private static final Color ERROR = new Color(0xB00020);

    private final JTextPane transcript = new JTextPane();
    private final JTextField input = new JTextField();
    private final JButton sendButton = new JButton("Send");
    private final Consumer<String> onSend;
    private boolean connected = true;
    private boolean peerOnline = true;

    public ChatPanel(Consumer<String> onSend) {
        super(new BorderLayout());
        this.onSend = onSend;
        transcript.setEditable(false);
        transcript.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(new JScrollPane(transcript), BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(8, 0));
        south.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        input.addActionListener(event -> send());
        sendButton.addActionListener(event -> send());
        south.add(input, BorderLayout.CENTER);
        south.add(sendButton, BorderLayout.EAST);
        add(south, BorderLayout.SOUTH);
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
        refreshInput();
    }

    public void setPeerOnline(boolean peerOnline) {
        this.peerOnline = peerOnline;
        refreshInput();
    }

    public void focusInput() {
        if (input.isEnabled()) {
            input.requestFocusInWindow();
        }
    }

    public void appendMessage(String sender, String text, String time, boolean mine) {
        SimpleAttributeSet style = new SimpleAttributeSet();
        StyleConstants.setForeground(style, mine ? MINE : THEIRS);
        StyleConstants.setAlignment(style, mine ? StyleConstants.ALIGN_RIGHT : StyleConstants.ALIGN_LEFT);
        append("[" + clock(time) + "] " + sender + ": " + text, style);
    }

    public void appendSystem(String text) {
        SimpleAttributeSet style = new SimpleAttributeSet();
        StyleConstants.setForeground(style, SYSTEM);
        StyleConstants.setItalic(style, true);
        append(text, style);
    }

    public void appendError(String text) {
        SimpleAttributeSet style = new SimpleAttributeSet();
        StyleConstants.setForeground(style, ERROR);
        StyleConstants.setItalic(style, true);
        append(text, style);
    }

    private void send() {
        String text = input.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        input.setText("");
        onSend.accept(text);
    }

    private void refreshInput() {
        boolean enabled = connected && peerOnline;
        input.setEnabled(enabled);
        sendButton.setEnabled(enabled);
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
}
