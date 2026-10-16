package com.minichat.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import com.minichat.common.Protocol;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final CommandProcessor commands;
    private final BufferedReader in;
    private final PrintWriter out;

    public ClientHandler(Socket socket, CommandProcessor commands) throws IOException {
        this.socket = socket;
        this.commands = commands;
        try {
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
        } catch (IOException e) {
            disconnect();
            throw e;
        }
    }

    @Override
    public void run() {
        String name = null;
        try {
            String line = readLine();
            if (line == null) {
                return;
            }
            name = commands.login(this, Protocol.parse(line));
            if (name == null) {
                return;
            }
            while ((line = readLine()) != null) {
                if (!commands.handle(this, name, Protocol.parse(line))) {
                    return;
                }
            }
        } catch (IOException ignored) {
            // A real socket failure, or this socket was closed during shutdown.
        } finally {
            if (name != null) {
                commands.leave(name);
            }
            commands.closed(this);
            disconnect();
        }
    }

    private String readLine() throws IOException {
        while (true) {
            try {
                return in.readLine();
            } catch (SocketTimeoutException ignored) {
                // No complete line arrived before the deadline. The connection stays open.
            }
        }
    }

    public void send(String message) {
        // Another client's thread calls this. Only one thread may write this socket at a time.
        synchronized (out) {
            out.println(message);
        }
    }

    public void disconnect() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
