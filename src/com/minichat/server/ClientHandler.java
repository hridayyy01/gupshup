package com.minichat.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

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
        registry.add(this);
        try {
            String line;
            while ((line = in.readLine()) != null) {
                registry.broadcast(line, this);
            }
        } catch (IOException ignored) {
            // Client closed the connection while we were blocked in readLine.
        } finally {
            registry.remove(this);
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
