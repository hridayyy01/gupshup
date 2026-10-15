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
    private final ClientRegistry registry;
    private final BufferedReader in;
    private final PrintWriter out;

    public ClientHandler(Socket socket, ClientRegistry registry) throws IOException {
        this.socket = socket;
        this.registry = registry;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
    }

    @Override
    public void run() {
        String name = null;
        try {
            name = login();
            if (name == null) {
                return;
            }
            chat(name);
        } catch (IOException ignored) {
            // Client closed the connection while we were blocked in readLine.
        } finally {
            if (name != null) {
                registry.remove(name);
            }
            close();
        }
    }

    private String login() throws IOException {
        String line = in.readLine();
        if (line == null) {
            return null;
        }
        Protocol.Message message = Protocol.parse(line);
        if (!Protocol.LOGIN.equals(message.command())) {
            send(Protocol.error("first command must be LOGIN"));
            return null;
        }
        String name = message.argument(0).trim();
        if (name.isEmpty()) {
            send(Protocol.error("username is empty"));
            return null;
        }
        if (!registry.add(name, this)) {
            send(Protocol.error("username is taken"));
            return null;
        }
        return name;
    }

    private void chat(String name) throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            Protocol.Message message = Protocol.parse(line);
            if (Protocol.QUIT.equals(message.command())) {
                return;
            }
            if (Protocol.MSG.equals(message.command())) {
                String text = message.argument(0);
                if (!text.isEmpty()) {
                    registry.broadcast(name, text);
                }
                continue;
            }
            send(Protocol.error("unknown command"));
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
