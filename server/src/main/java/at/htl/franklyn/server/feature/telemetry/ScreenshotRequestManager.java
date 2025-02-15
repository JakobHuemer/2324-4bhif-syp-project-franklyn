package at.htl.franklyn.server.feature.telemetry;

import at.htl.franklyn.server.feature.telemetry.command.ExamineeCommandSocket;
import at.htl.franklyn.server.feature.telemetry.image.FrameType;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class ScreenshotRequestManager {
    private static class UserInterval {
        public long wantedIntervalSeconds;
        public LocalDateTime lastScreenshotTimestamp;
    }

    @Inject
    ManagedExecutor executor;
    @Inject
    ExamineeCommandSocket commandSocket;

    @ConfigProperty(name = "screenshots.max-concurrent-requests", defaultValue = "1")
    int maxConcurrentRequests;
    @ConfigProperty(name = "screenshots.upload-timeout", defaultValue = "1500")
    int uploadTimeoutMs;

    final int MIN_WAIT = 250;

    AtomicBoolean isRunning = new AtomicBoolean(false);
    AtomicLong additionalDelaySum = new AtomicLong(0);
    AtomicLong additionalDelayMeasurements = new AtomicLong(0);

    // participation id, capture interval
    ConcurrentHashMap<UUID, UserInterval> userIntervals = new ConcurrentHashMap<>();
    ConcurrentLinkedQueue<UUID> queuedUsers = new ConcurrentLinkedQueue<>();
    ConcurrentHashMap<UUID, CompletableFuture<Void>> activeRequests = new ConcurrentHashMap<>();
    ConcurrentHashMap<UUID, CompletableFuture<Void>> forcedAlphaRequests = new ConcurrentHashMap<>();

    public void onStartup(@Observes StartupEvent ev) {
        // TODO: Load from database?
    }

    public void registerStudent(UUID participationId, long intervalSeconds) {
        var interval = new UserInterval();
        interval.wantedIntervalSeconds = intervalSeconds;
        interval.lastScreenshotTimestamp = null;

        userIntervals.put(participationId, interval);
        queuedUsers.add(participationId);
        if (!isRunning.compareAndExchangeRelease(false, true)) {
            executor.execute(this::processRequests);
        }
    }

    public void unregisterStudent(UUID participationId) {
        userIntervals.remove(participationId);
        queuedUsers.remove(participationId);
    }

    public boolean notifyStudentScreenshot(UUID participationID) {
        var normalCompletable = activeRequests.remove(participationID);
        var forcedAlphaCompletable = forcedAlphaRequests.remove(participationID);
        if (normalCompletable == null && forcedAlphaCompletable == null) {
            return false;
        }

        if (normalCompletable != null) {
            normalCompletable.complete(null);
        }
        if (forcedAlphaCompletable != null) {
            forcedAlphaCompletable.complete(null);
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

    private void processRequests() {
        // todo calculate sleep to next screenshot with some avg
        // nothing available 1s
        List<Uni<Void>> requests = new ArrayList<>(maxConcurrentRequests);
        List<UUID> processedUsers = new ArrayList<>(maxConcurrentRequests);
        while (!Thread.currentThread().isInterrupted()) {
            long start = System.currentTimeMillis();
            for (int i = 0; i < maxConcurrentRequests; i++) {
                UUID user = queuedUsers.poll();

                if (user != null) {
                    processedUsers.add(user);
                    UserInterval userInterval = userIntervals.get(user);
                    // start uploadTimeout ms sooner to counteract duration of upload a bit
                    if (userInterval.lastScreenshotTimestamp == null
                            || ChronoUnit.SECONDS.between(userInterval.lastScreenshotTimestamp, LocalDateTime.now()) >= userInterval.wantedIntervalSeconds - uploadTimeoutMs) {
                        CompletableFuture<Void> screenshotUploadComplete = new CompletableFuture<>();
                        activeRequests.put(user, screenshotUploadComplete);
                        requests.add(
                                commandSocket
                                        .requestFrame(user, FrameType.UNSPECIFIED)
                                        .chain(ignored -> Uni.createFrom().completionStage(screenshotUploadComplete))
                                        .invoke(ignored -> {
                                            var delay = ChronoUnit.SECONDS.between(userInterval.lastScreenshotTimestamp, LocalDateTime.now());
                                            var delaySum = additionalDelaySum.addAndGet(Math.max(delay, 0));
                                            var measurements = additionalDelayMeasurements.incrementAndGet();
                                            if (measurements % 1000 == 0) {
                                                Log.infof("Average additional delay: %d", (double)delaySum / measurements);
                                            }
                                            userInterval.lastScreenshotTimestamp = LocalDateTime.now();
                                        })
                                        .onFailure().recoverWithNull()
                        );
                    }
                }
            }

            queuedUsers.addAll(processedUsers);
            processedUsers.clear();

            if (!requests.isEmpty()) {
                try {
                    Uni.join()
                            .all(requests)
                            .andCollectFailures()
                            .onFailure().recoverWithNull()
                            .await()
                            .atMost(Duration.ofMillis(uploadTimeoutMs));
                } catch (Exception ignored) {} // timeout exception is ignored

                activeRequests.clear(); // make sure timed out requests are sorted out
                requests.clear();
            }

            long end = System.currentTimeMillis();

            if (end - start < MIN_WAIT) {
                Uni.createFrom()
                        .voidItem()
                        .onItem()
                        .delayIt()
                        .by(Duration.ofMillis(MIN_WAIT - (end- start)))
                        .await()
                        .indefinitely();
            }
        }
    }
}
