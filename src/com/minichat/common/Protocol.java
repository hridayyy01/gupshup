package com.minichat.common;

import java.util.ArrayList;
import java.util.List;

public final class Protocol {
    public static final String LOGIN = "LOGIN";
    public static final String MSG = "MSG";
    public static final String QUIT = "QUIT";
    public static final String ERROR = "ERROR";

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
