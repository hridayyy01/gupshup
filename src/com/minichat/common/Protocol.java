package com.minichat.common;

import java.util.ArrayList;
import java.util.List;

public final class Protocol {
    public static final String LOGIN = "LOGIN";
    public static final String MSG = "MSG";
    public static final String QUIT = "QUIT";
    public static final String ERROR = "ERROR";
    public static final String PM = "PM";
    public static final String SENT = "SENT";
    public static final String ONLINE = "ONLINE";
    public static final String JOIN = "JOIN";
    public static final String LEAVE = "LEAVE";
    public static final String SERVER = "SERVER";
    public static final String PING = "PING";
    public static final String PONG = "PONG";
    public static final int MAX_TEXT_LENGTH = 1000;

    private Protocol() {
    }

    public static String login(String name) {
        return build(LOGIN, name);
    }

    public static String msg(String text) {
        return build(MSG, text);
    }

    public static String quit() {
        return build(QUIT);
    }

    public static String error(String reason) {
        return build(ERROR, reason);
    }

    public static String chatMessage(String sender, String text, String timestamp) {
        return build(MSG, sender, text, timestamp);
    }

    public static String privateMessage(String from, String text) {
        return build(PM, from, text);
    }

    public static String privateMessageSent(String to, String text) {
        return build(SENT, to, text);
    }

    public static String onlineRequest() {
        return build(ONLINE);
    }

    public static String online(List<String> names) {
        return build(ONLINE, names.toArray(String[]::new));
    }

    public static String join(String name) {
        return build(JOIN, name);
    }

    public static String leave(String name) {
        return build(LEAVE, name);
    }

    public static String server(String status) {
        return build(SERVER, status);
    }

    public static String ping() {
        return build(PING);
    }

    public static String pong() {
        return build(PONG);
    }

    public static String build(String command, String... arguments) {
        StringBuilder line = new StringBuilder(escape(command));
        for (String argument : arguments) {
            line.append('|').append(escape(argument));
        }
        return line.toString();
    }

    public static Message parse(String line) {
        List<String> fields = split(line);
        String command = fields.get(0);
        if (fields.size() == 1) {
            return new Message(command, List.of());
        }
        return new Message(command, List.copyOf(fields.subList(1, fields.size())));
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '\\' || current == '|') {
                escaped.append('\\');
            }
            escaped.append(current);
        }
        return escaped.toString();
    }

    private static List<String> split(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (escaped) {
                field.append(current);
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current == '|') {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(current);
            }
        }
        if (escaped) {
            field.append('\\');
        }
        fields.add(field.toString());
        return fields;
    }

    public record Message(String command, List<String> arguments) {
        public String argument(int index) {
            if (index < 0 || index >= arguments.size()) {
                return "";
            }
            return arguments.get(index);
        }
    }
}
