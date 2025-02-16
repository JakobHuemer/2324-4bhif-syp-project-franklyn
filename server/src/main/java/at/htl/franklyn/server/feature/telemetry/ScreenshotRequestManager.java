package at.htl.franklyn.server.feature.telemetry;

import at.htl.franklyn.server.feature.telemetry.command.ExamineeCommandSocket;
import at.htl.franklyn.server.feature.telemetry.image.FrameType;
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
    }

    @Inject
    ExamineeCommandSocket commandSocket;

    @ConfigProperty(name = "screenshots.max-concurrent-requests", defaultValue = "1")
    int maximumConcurrentRequests;
    @ConfigProperty(name = "screenshots.upload-timeout", defaultValue = "1500")
    int uploadTimeoutMs;
    @ConfigProperty(name = "screenshots.always-allow-uploads", defaultValue = "false")
    boolean alwaysAllowUploads;

    ConcurrentHashMap<UUID, CompletableFuture<Void>> forcedAlphaRequests = new ConcurrentHashMap<>();

    public void onStartup(@Observes StartupEvent ev) {
        init(maximumConcurrentRequests, uploadTimeoutMs, ClientData.class);
    }

    public void registerClient(UUID id, long intervalSeconds) {
        ClientData data = super.registerClient(id);
        data.wantedIntervalMs = intervalSeconds * 1000;
    }

    @Override
    public void unregisterClient(UUID id) {
        super.unregisterClient(id);
    }

    @Override
    public boolean notifyClientRequestReceived(UUID client) {
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
        return commandSocket.requestFrame(client.id, FrameType.UNSPECIFIED)
                .onFailure().recoverWithNull();
    }

    @Override
    protected Uni<Void> handleResponse(ClientData client, boolean clientReached) {
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
