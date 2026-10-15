package com.minichat.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
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
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
    }

    @Override
    public void run() {
        String name = null;
        try {
            String line = in.readLine();
            if (line == null) {
                return;
            }
            name = commands.login(this, Protocol.parse(line));
            if (name == null) {
                return;
            }
            while ((line = in.readLine()) != null) {
                if (!commands.handle(this, name, Protocol.parse(line))) {
                    return;
                }
            }
        } catch (IOException ignored) {
            // Client closed the connection while we were blocked in readLine.
        } finally {
            if (name != null) {
                commands.leave(name);
            }
            close();
        }
    }

    public void send(String message) {
        // Another client's thread calls this. Only one thread may write this socket at a time.
        synchronized (out) {
            out.println(message);
        }
    }

    private void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
