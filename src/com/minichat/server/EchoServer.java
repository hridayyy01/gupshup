package com.minichat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.minichat.common.Protocol;

public class EchoServer {
    public static final int DEFAULT_PORT = 5000;
    public static final int DEFAULT_POOL_SIZE = 50;
    public static final int DEFAULT_READ_TIMEOUT_MILLIS = 30_000;

    private final ServerSocket serverSocket;
    private final ExecutorService pool;
    private final ClientRegistry registry;
    private final CommandProcessor commands;
    private final int readTimeoutMillis;
    private final AtomicBoolean stopped = new AtomicBoolean();

    public EchoServer(ServerSocket serverSocket, ExecutorService pool, int readTimeoutMillis) throws IOException {
        ServerLog.start();
        this.serverSocket = serverSocket;
        this.pool = pool;
        this.readTimeoutMillis = readTimeoutMillis;
        HistoryStore history = new HistoryStore(Path.of("data"));
        this.registry = new ClientRegistry(history);
        this.commands = new CommandProcessor(registry, history);
    }

    public static void main(String[] args) throws IOException {
        ServerLog.start();
        int port = argument(args, 0, DEFAULT_PORT, "port");
        int poolSize = argument(args, 1, DEFAULT_POOL_SIZE, "pool size");
        int readTimeoutMillis = argument(args, 2, DEFAULT_READ_TIMEOUT_MILLIS, "read timeout");
        EchoServer server = new EchoServer(
                new ServerSocket(port, Math.max(poolSize * 2, 50)),
                fixedPool(poolSize),
                readTimeoutMillis);
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown, "server-shutdown"));
        ServerLog.LOG.info("Listening on port " + port + ", pool " + poolSize);
        server.serve();
    }

    public static ExecutorService fixedPool(int poolSize) {
        AtomicInteger ids = new AtomicInteger();
        return fixedPool(poolSize, runnable -> new Thread(runnable, "client-" + ids.incrementAndGet()));
    }

    public static ExecutorService fixedPool(int poolSize, ThreadFactory threadFactory) {
        return new ThreadPoolExecutor(
                poolSize,
                poolSize,
                0L,
                TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    public int port() {
        return serverSocket.getLocalPort();
    }

    public void serve() {
        try {
            while (!stopped.get()) {
                acceptOne(serverSocket.accept());
            }
        } catch (IOException e) {
            if (!stopped.get()) {
                ServerLog.LOG.warning("accept failed: " + e.getMessage());
            }
        }
    }

    public void shutdown() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        ServerLog.LOG.info("shutting down");
        close(serverSocket);
        registry.disconnectAll(Protocol.server("shutting down"));
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void acceptOne(Socket socket) {
        ClientHandler handler = null;
        try {
            socket.setKeepAlive(true);
            socket.setSoTimeout(readTimeoutMillis);
            ServerLog.LOG.info("accepted " + socket.getRemoteSocketAddress());
            handler = new ClientHandler(socket, commands);
            registry.track(handler);
            pool.execute(handler);
        } catch (RejectedExecutionException full) {
            ServerLog.LOG.warning("server full, rejecting " + socket.getRemoteSocketAddress());
            registry.untrack(handler);
            handler.send(Protocol.error("server full"));
            handler.disconnect();
        } catch (IOException e) {
            if (handler != null) {
                registry.untrack(handler);
                handler.disconnect();
            } else {
                close(socket);
            }
        }
    }

    private static void close(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }

    private static int argument(String[] args, int index, int fallback, String label) {
        if (args.length <= index) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(args[index]);
            if (value <= 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException e) {
            ServerLog.LOG.severe(label + " must be a positive integer");
            System.exit(1);
            return fallback;
        }
    }
}
