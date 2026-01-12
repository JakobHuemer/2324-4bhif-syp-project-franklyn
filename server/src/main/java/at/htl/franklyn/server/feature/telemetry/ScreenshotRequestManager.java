package at.htl.franklyn.server.feature.telemetry;

import at.htl.franklyn.server.feature.metrics.ProfilingMetricsService;
import at.htl.franklyn.server.feature.telemetry.command.ExamineeCommandSocket;
import at.htl.franklyn.server.feature.telemetry.image.FrameType;
import io.micrometer.core.instrument.Timer;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ScreenshotRequestManager extends ThrottledRequestManager<ScreenshotRequestManager.ClientData, UUID>{
    protected static class ClientData extends ClientDataBase<UUID> {
        public long wantedIntervalMs;
        public Timer.Sample requestTimerSample;
    }

    @Inject
    ExamineeCommandSocket commandSocket;
    
    @Inject
    ProfilingMetricsService profilingMetricsService;

    @ConfigProperty(name = "screenshots.max-concurrent-requests", defaultValue = "1")
    int maximumConcurrentRequests;
    @ConfigProperty(name = "screenshots.upload-timeout", defaultValue = "1500")
    int uploadTimeoutMs;
    @ConfigProperty(name = "screenshots.always-allow-uploads", defaultValue = "false")
    boolean alwaysAllowUploads;

    ConcurrentHashMap<UUID, CompletableFuture<Void>> forcedAlphaRequests = new ConcurrentHashMap<>();
    // Track request timers by client ID
    ConcurrentHashMap<UUID, Timer.Sample> activeRequestTimers = new ConcurrentHashMap<>();

    public void onStartup(@Observes StartupEvent ev) {
        init(maximumConcurrentRequests, uploadTimeoutMs, ClientData.class);
        setProfilingMetrics(profilingMetricsService);
    }

    public void registerClient(UUID id, long intervalSeconds) {
        ClientData data = super.registerClient(id);
        data.wantedIntervalMs = intervalSeconds * 1000;
    }

    @Override
    public void unregisterClient(UUID id) {
        super.unregisterClient(id);
        // Clean up any pending timer
        activeRequestTimers.remove(id);
    }

    @Override
    public boolean notifyClientRequestReceived(UUID client) {
        // Stop the request latency timer when upload is received
        Timer.Sample sample = activeRequestTimers.remove(client);
        if (sample != null && profilingMetricsService != null) {
            profilingMetricsService.stopScreenshotRequestTimer(sample);
            profilingMetricsService.recordSuccessfulUpload();
        }
        
        var completionSuccessful = super.notifyClientRequestReceived(client);

        CompletableFuture<Void> alphaCompletion = forcedAlphaRequests.remove(client);
        if(alphaCompletion != null) {
            alphaCompletion.complete(null);
            return true;
        }

        // Currently only used in unit tests
        // since those do not actually connect a websocket client but still upload images and build videos
        // since this class is non-deterministic it is very hard to test it otherwise, better ideas are welcome
        if (alwaysAllowUploads) {
            return true;
        }

        return completionSuccessful;
    }

    @Override
    protected long calculateWaitMillis(ClientData client) {
        return client.lastResponseTimestampMillis != null
                ? Math.max((client.wantedIntervalMs - (System.currentTimeMillis() - client.lastResponseTimestampMillis)) - uploadTimeoutMs, 1)
                : 1;
    }

    @Override
    protected Uni<Void> request(ClientData client) {
        // Start timing when we send the capture request
        if (profilingMetricsService != null) {
            Timer.Sample sample = profilingMetricsService.startScreenshotRequestTimer();
            activeRequestTimers.put(client.id, sample);
        }
        
        return commandSocket.requestFrame(client.id, FrameType.UNSPECIFIED)
                .onFailure().recoverWithNull();
    }

    @Override
    protected Uni<Void> handleResponse(ClientData client, boolean clientReached) {
        if (!clientReached && profilingMetricsService != null) {
            // Request timed out
            profilingMetricsService.incrementTimeouts();
            activeRequestTimers.remove(client.id);
        }
        return Uni.createFrom().voidItem();
    }

    public Uni<Void> forceRequestNewAlpha(UUID user) {
        CompletableFuture<Void> screenshotUploadComplete = new CompletableFuture<>();
        forcedAlphaRequests.put(user, screenshotUploadComplete);
        return commandSocket
                .requestFrame(user, FrameType.ALPHA)
                .chain(ignored -> Uni.createFrom().completionStage(screenshotUploadComplete))
                .onFailure().recoverWithNull();
    }
}
