package com.minichat.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.minichat.common.Protocol;

public final class LoadTest {
    private static final int CLIENTS = 100;
    private static final int POOL_SIZE = 50;

    private LoadTest() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("before: new Thread() per client, " + CLIENTS + " dummy clients");
        print(flood(threadPerClient()));

        System.out.println("after: fixed pool " + POOL_SIZE + ", " + CLIENTS + " dummy clients");
        print(flood(fixedPool()));
    }

    private static CountedExecutor threadPerClient() {
        AtomicInteger created = new AtomicInteger();
        ExecutorService pool = Executors.newThreadPerTaskExecutor(runnable -> {
            int id = created.incrementAndGet();
            return new Thread(runnable, "unbounded-" + id);
        });
        return new CountedExecutor(pool, created);
    }

    private static CountedExecutor fixedPool() {
        AtomicInteger created = new AtomicInteger();
        ExecutorService pool = EchoServer.fixedPool(POOL_SIZE, runnable -> {
            int id = created.incrementAndGet();
            return new Thread(runnable, "pool-" + id);
        });
        return new CountedExecutor(pool, created);
    }

    private static Result flood(CountedExecutor clients) throws Exception {
        EchoServer server = new EchoServer(new ServerSocket(0, CLIENTS * 2), clients.pool, 30_000);
        Thread acceptThread = new Thread(server::serve, "accept");
        acceptThread.setDaemon(true);
        acceptThread.start();

        ExecutorService callers = Executors.newFixedThreadPool(CLIENTS);
        CountDownLatch attempted = new CountDownLatch(CLIENTS);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        long started = System.nanoTime();
        try {
            for (int id = 0; id < CLIENTS; id++) {
                int clientId = id;
                callers.execute(() -> connect(
                        server.port(), clientId, accepted, rejected, failed, attempted, release));
            }
            if (!attempted.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for dummy clients");
            }
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            return new Result(clients.created.get(), accepted.get(), rejected.get(), failed.get(), elapsedMillis);
        } finally {
            release.countDown();
            callers.shutdownNow();
            server.shutdown();
            acceptThread.join(2_000);
        }
    }

    private static void connect(int port, int id, AtomicInteger accepted, AtomicInteger rejected,
                                AtomicInteger failed, CountDownLatch attempted, CountDownLatch release) {
        boolean counted = false;
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5_000);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out.println(Protocol.login("user-" + id));
            String line = in.readLine();
            boolean holding = false;
            if (Protocol.error("server full").equals(line)) {
                rejected.incrementAndGet();
            } else if (line != null && line.startsWith(Protocol.JOIN + "|")) {
                accepted.incrementAndGet();
                holding = true;
            } else {
                failed.incrementAndGet();
            }
            attempted.countDown();
            counted = true;
            if (holding) {
                release.await(5, TimeUnit.SECONDS);
            }
        } catch (IOException e) {
            if (!counted) {
                failed.incrementAndGet();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (!counted) {
                attempted.countDown();
            }
        }
    }

    private static void print(Result result) {
        System.out.println("  threads created: " + result.threadsCreated);
        System.out.println("  accepted:        " + result.accepted);
        System.out.println("  rejected:        " + result.rejected);
        System.out.println("  failed:          " + result.failed);
        System.out.println("  time:            " + result.elapsedMillis + " ms");
    }

    private static final class CountedExecutor {
        private final ExecutorService pool;
        private final AtomicInteger created;

        private CountedExecutor(ExecutorService pool, AtomicInteger created) {
            this.pool = pool;
            this.created = created;
        }
    }

    private record Result(int threadsCreated, int accepted, int rejected, int failed, long elapsedMillis) {
    }
}
