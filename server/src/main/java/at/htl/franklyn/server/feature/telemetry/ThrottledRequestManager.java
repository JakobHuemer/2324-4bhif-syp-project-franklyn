package at.htl.franklyn.server.feature.telemetry;

import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ConcurrentHashSet;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

public abstract class ThrottledRequestManager<T extends ThrottledRequestManager.ClientDataBase<K>, K> {
    protected static class ClientDataBase<K> {
        K id;
        Long lastResponseTimestampMillis;
    }
    private static class ResponseTimeoutException extends Exception { }

    private int maximumConcurrentRequests;
    private int requestTimeoutMilliseconds;
    private Class<T> dataClazz;

    protected void init(int maximumConcurrentRequests, int requestTimeoutMilliseconds, Class<T> dataClazz) {
        this.maximumConcurrentRequests = maximumConcurrentRequests;
        this.requestTimeoutMilliseconds = requestTimeoutMilliseconds;
        this.dataClazz = dataClazz;
    }

    ConcurrentHashMap<K, CompletableFuture<Void>> activeRequests = new ConcurrentHashMap<>();
    ConcurrentHashSet<K> clientsStagedForRemoval = new ConcurrentHashSet<>();
    ConcurrentLinkedQueue<T> clients = new ConcurrentLinkedQueue<>();
    // 64bit number containing the important state for this class
    // first 32 bits -> number of currently active requests
    // second 32 bits -> number of available clients waiting to be processed
    AtomicLong state = new AtomicLong(0);

    protected T registerClient(K id) {
        try {
            T data = dataClazz.getDeclaredConstructor().newInstance();
            data.id = id;
            data.lastResponseTimestampMillis = null;

            clients.add(data);
            addClient();

            tryScheduleNext();
            return data;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected void unregisterClient(K id) {
        clientsStagedForRemoval.add(id);
    }

    protected boolean notifyClientRequestReceived(K client) {
        CompletableFuture<Void> requestCompletion = activeRequests.remove(client);
        if (requestCompletion != null) {
            requestCompletion.complete(null);
            return true;
        }
        return false;
    }

    protected abstract long calculateWaitMillis(T client);
    protected abstract Uni<Void> request(T client);
    protected abstract Uni<Void> handleResponse(T client, boolean clientReached);

    private boolean tryScheduleNext() {
        if (!reserveClientAndRequest()) {
            return false;
        }

        // We are 100% sure to have a client and one request available
        T user = clients.poll();
        assert user != null; // somebody broke contract and accessed clients without state

        long wait = Math.max(calculateWaitMillis(user), 1);

        // Dispatch Uni which pings and handles the result
        Context ctx = Vertx.currentContext();
        Uni.createFrom()
                .voidItem()
                .onItem().delayIt().by(Duration.ofMillis(wait))
                .chain(ignored -> {
                    CompletableFuture<Void> requestCompletion = new CompletableFuture<>();
                    activeRequests.put(user.id, requestCompletion);
                    return request(user)
                            .chain(ignored2 -> Uni.createFrom().completionStage(requestCompletion))
                            .onItem().transform(ignored2 -> true)
                            .ifNoItem().after(Duration.ofMillis(requestTimeoutMilliseconds)).failWith(ResponseTimeoutException::new)
                            .emitOn(r -> ctx.runOnContext(ignored2 -> r.run()));
                })
                .onFailure(ResponseTimeoutException.class).recoverWithItem(false)
                .chain(clientReached -> handleResponse(user, clientReached))
                .emitOn(r -> ctx.runOnContext(ignored -> r.run()))
                .onFailure().recoverWithNull()
                .invoke(ignored -> {
                    if (clientsStagedForRemoval.contains(user.id)) {
                        releaseRequest();
                    } else {
                        user.lastResponseTimestampMillis = System.currentTimeMillis();
                        clients.add(user);
                        releaseClientAndRequest();
                    }
                })
                .subscribe().with(ignored -> {
                    // reschedule, we want to (possibly) get the client at the front,
                    // not the one we already have (at the back)
                    tryScheduleNext();
                });

        return true;
    }

    private boolean reserveClientAndRequest() {
        // https://stackoverflow.com/a/50278620
        while (true) {
            long currentState = state.get();
            int availableClients = getAvailableClients(currentState);
            int activeRequests = getActiveRequests(currentState);
            if (availableClients - 1 < 0 || activeRequests + 1 > maximumConcurrentRequests) {
                return false;
            }
            long newState = setAvailableClients(currentState, availableClients - 1);
            newState = setActiveRequests(newState, activeRequests + 1);
            if (state.compareAndSet(currentState, newState)) {
                return true;
            }
        }
    }

    private boolean releaseClientAndRequest() {
        // https://stackoverflow.com/a/50278620
        while (true) {
            long currentState = state.get();
            int availableClients = getAvailableClients(currentState);
            int activeRequests = getActiveRequests(currentState);
            assert activeRequests - 1 >= 0;
            long newState = setAvailableClients(currentState, availableClients + 1);
            newState = setActiveRequests(newState, activeRequests - 1);
            if (state.compareAndSet(currentState, newState)) {
                return true;
            }
        }
    }

    private void addClient() {
        // https://stackoverflow.com/a/50278620
        while (true) {
            long currentState = state.get();
            int availableClients = getAvailableClients(currentState);
            if (state.compareAndSet(currentState, setAvailableClients(currentState, availableClients + 1))) {
                return;
            }
        }
    }

    private void releaseRequest() {
        // https://stackoverflow.com/a/50278620
        while (true) {
            long currentState = state.get();
            int activeRequests = getActiveRequests(currentState);
            assert activeRequests - 1 >= 0;
            if (state.compareAndSet(currentState, setActiveRequests(currentState, activeRequests - 1))) {
                return;
            }
        }
    }

    private int getActiveRequests(long state) {
        return (int) ((state & 0xFFFFFFFF00000000L) >> 32);
    }

    private long setActiveRequests(long state, int requests) {
        return ((state & 0x00000000FFFFFFFFL) | (((long) requests) << 32));
    }

    private int getAvailableClients(long state) {
        return (int) (state & 0x00000000FFFFFFFFL);
    }

    private long setAvailableClients(long state, int clients) {
        return ((state & 0xFFFFFFFF00000000L) | ((long) clients));
    }
}
