package at.htl.franklyn.server.feature.telemetry;

import at.htl.franklyn.server.feature.telemetry.command.ExamineeCommandSocket;
import at.htl.franklyn.server.feature.telemetry.image.FrameType;
import io.smallrye.mutiny.Uni;
import io.vertx.core.impl.ConcurrentHashSet;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class ScreenshotRequestManager {
    private static class ClientData {
        UUID participationId;
        public long wantedIntervalMs;
        public Long lastScreenshotTimestampMillis;
    }
    private static class ScreenshotTimeoutException extends Exception { }

    @Inject
    ExamineeCommandSocket commandSocket;

    @ConfigProperty(name = "screenshots.max-concurrent-requests", defaultValue = "1")
    int maximumConcurrentRequests;
    @ConfigProperty(name = "screenshots.upload-timeout", defaultValue = "1500")
    int uploadTimeoutMs;

    ConcurrentHashMap<UUID, CompletableFuture<Void>> activeRequests = new ConcurrentHashMap<>();
    ConcurrentHashMap<UUID, CompletableFuture<Void>> forcedAlphaRequests = new ConcurrentHashMap<>();
    ConcurrentHashSet<UUID> clientsStagedForRemoval = new ConcurrentHashSet<>();
    ConcurrentLinkedQueue<ClientData> clients = new ConcurrentLinkedQueue<>();
    // 64bit number containing the important state for this class
    // first 32 bits -> number of currently active requests
    // second 32 bits -> number of available clients waiting to be processed
    AtomicLong state = new AtomicLong(0);

    public void registerClient(UUID client, long screenshotIntervalSeconds) {
        ClientData data = new ClientData();
        data.participationId = client;
        data.wantedIntervalMs = screenshotIntervalSeconds * 1000;
        data.lastScreenshotTimestampMillis = null;

        clients.add(data);
        addClient();

        tryScheduleNext();
    }

    public void unregisterClient(UUID client) {
        clientsStagedForRemoval.add(client);
    }

    public boolean notifyScreenshotReceived(UUID client) {
        CompletableFuture<Void> requestCompletion = activeRequests.remove(client);
        CompletableFuture<Void> alphaCompletion = forcedAlphaRequests.remove(client);
        if (requestCompletion == null && alphaCompletion == null) {
            return false;
        }

        if (requestCompletion != null) {
            requestCompletion.complete(null);
        }
        if (alphaCompletion != null) {
            alphaCompletion.complete(null);
        }
        return true;
    }

    public Uni<Void> forceRequestNewAlpha(UUID user) {
        CompletableFuture<Void> screenshotUploadComplete = new CompletableFuture<>();
        forcedAlphaRequests.put(user, screenshotUploadComplete);
        return commandSocket
                .requestFrame(user, FrameType.ALPHA)
                .chain(ignored -> Uni.createFrom().completionStage(screenshotUploadComplete))
                .onFailure().recoverWithNull();
    }

    private boolean tryScheduleNext() {
        if (!reserveClientAndRequest()) {
            return false;
        }

        // We are 100% sure to have a client and one request available
        ClientData user = clients.poll();
        assert user != null; // somebody broke contract and accessed clients without state

        long wait = user.lastScreenshotTimestampMillis != null
                ? Math.max(user.wantedIntervalMs - (System.currentTimeMillis() - user.lastScreenshotTimestampMillis), 1)
                : 1;

        // Dispatch Uni which pings and handles the result
        Uni.createFrom()
                .voidItem()
                .onItem().delayIt().by(Duration.ofMillis(wait))
                .chain(ignored -> {
                    CompletableFuture<Void> screenshotUploadComplete = new CompletableFuture<>();
                    activeRequests.put(user.participationId, screenshotUploadComplete);
                    return commandSocket.requestFrame(user.participationId, FrameType.UNSPECIFIED)
                            .chain(ignored2 -> Uni.createFrom().completionStage(screenshotUploadComplete))
                            .onItem().transform(ignored2 -> true)
                            .ifNoItem().after(Duration.ofMillis(uploadTimeoutMs)).failWith(ScreenshotTimeoutException::new);
                })
                .onFailure(ScreenshotTimeoutException.class).recoverWithItem(false)
                .invoke(ignored -> {
                    if (clientsStagedForRemoval.contains(user.participationId)) {
                        releaseRequest();
                    } else {
                        user.lastScreenshotTimestampMillis = System.currentTimeMillis();
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
