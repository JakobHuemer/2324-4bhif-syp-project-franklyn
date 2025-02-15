package at.htl.franklyn.server.feature.telemetry;

import at.htl.franklyn.server.feature.telemetry.command.ExamineeCommandSocket;
import at.htl.franklyn.server.feature.telemetry.connection.ConnectionStateService;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ConcurrentHashSet;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class PingPongRequestManager {
    private static class ClientData {
        UUID participationId;
        Long lastPingTimestampMillis;
    }
    private static class PingTimeoutException extends Exception { }

    @Inject
    ExamineeCommandSocket commandSocket;
    @Inject
    ConnectionStateService stateService;

    @ConfigProperty(name = "websocket.ping.interval-millis", defaultValue = "5000")
    int pingIntervalMs;
    @ConfigProperty(name = "websocket.client-timeout-millis", defaultValue = "2000")
    int pingTimeoutMs;
    @ConfigProperty(name = "websocket.ping.max-concurrent-requests", defaultValue = "10")
    int maximumConcurrentRequests;

    ConcurrentHashMap<UUID, CompletableFuture<Void>> activeRequests = new ConcurrentHashMap<>();
    ConcurrentHashSet<UUID> clientsStagedForRemoval = new ConcurrentHashSet<>();
    ConcurrentLinkedQueue<ClientData> clients = new ConcurrentLinkedQueue<>();
    // 64bit number containing the important state for this class
    // first 32 bits -> number of currently active requests
    // second 32 bits -> number of available clients waiting to be processed
    AtomicLong state = new AtomicLong(0);

    public void registerClient(UUID client) {
        ClientData data = new ClientData();
        data.participationId = client;
        data.lastPingTimestampMillis = null;

        clients.add(data);
        addClient();
        Log.infof("client %s registered", client);

        tryScheduleNext();
    }

    public void unregisterClient(UUID client) {
        Log.infof("client %s unregistered", client);
        clientsStagedForRemoval.add(client);
    }

    public void notifyClientPongReceived(UUID client) {
        CompletableFuture<Void> requestCompletion = activeRequests.remove(client);
        if (requestCompletion != null) {
            requestCompletion.complete(null);
        }
    }

    private boolean tryScheduleNext() {
        if (!reserveClientAndRequest()) {
            Log.infof("Reserving client and request failed ):");
            return false;
        }

        // We are 100% sure to have a client and one request available
        ClientData user = clients.poll();
        assert user != null; // somebody broke contract and accessed clients without state

        long wait = user.lastPingTimestampMillis != null
                ? Math.max(pingIntervalMs - (System.currentTimeMillis() - user.lastPingTimestampMillis), 1)
                : 1;

        Log.infof("Scheduling with wait: %d ms", wait);

        // Dispatch Uni which pings and handles the result
        Context ctx = Vertx.currentContext();
        Uni.createFrom()
                .voidItem()
                .onItem().delayIt().by(Duration.ofMillis(wait))
                .invoke(test -> Log.infof("Delay done"))
                .chain(ignored -> {
                    Log.infof("Pinging %s", user.participationId);
                    CompletableFuture<Void> requestCompletion = new CompletableFuture<>();
                    activeRequests.put(user.participationId, requestCompletion);
                    return commandSocket.sendPing(user.participationId)
                            .chain(ignored2 -> Uni.createFrom().completionStage(requestCompletion))
                            .onItem().transform(ignored2 -> true)
                            .ifNoItem().after(Duration.ofMillis(pingTimeoutMs)).failWith(PingTimeoutException::new)
                            .emitOn(r -> ctx.runOnContext(ignored2 -> r.run()));
                })
                .onFailure(PingTimeoutException.class).recoverWithItem(false)
                .emitOn(r -> ctx.runOnContext(ignored -> r.run()))
                .call(isConnected -> insertConnectionState(user.participationId, isConnected))
                .emitOn(r -> ctx.runOnContext(ignored -> r.run()))
                .call(isConnected ->
                        isConnected
                                ? Uni.createFrom().voidItem()
                                : commandSocket.timeoutDisconnect(user.participationId).emitOn(r -> ctx.runOnContext(ignored -> r.run()))
                )
                .emitOn(r -> ctx.runOnContext(ignored -> r.run()))
                .onFailure().recoverWithNull()
                .invoke(ignored -> {
                    if (clientsStagedForRemoval.contains(user.participationId)) {
                        releaseRequest();
                    } else {
                        user.lastPingTimestampMillis = System.currentTimeMillis();
                        clients.add(user);
                        releaseClientAndRequest();
                    }
                })
                .subscribe().with(ignored -> {
                    Log.infof("Rescheduling! Result: %b", ignored);
                    // reschedule, we want to (possibly) get the client at the front,
                    // not the one we already have (at the back)
                    tryScheduleNext();
                });

        return true;
    }

    @WithTransaction
    Uni<Void> insertConnectionState(UUID pId, boolean state) {
        Log.infof("Inserting connection state");
        return stateService.insertConnectedIfOngoing(pId, state);
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
