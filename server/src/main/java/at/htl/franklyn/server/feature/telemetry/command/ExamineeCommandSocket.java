package at.htl.franklyn.server.feature.telemetry.command;

import at.htl.franklyn.server.feature.telemetry.command.disconnect.DisconnectClientCommand;
import at.htl.franklyn.server.feature.telemetry.command.screenshot.RequestScreenshotCommand;
import at.htl.franklyn.server.feature.telemetry.command.screenshot.RequestScreenshotPayload;
import at.htl.franklyn.server.feature.telemetry.connection.ConnectionStateService;
import at.htl.franklyn.server.feature.telemetry.image.FrameType;
import at.htl.franklyn.server.feature.telemetry.participation.ParticipationService;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.websockets.next.*;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.buffer.Buffer;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@WebSocket(path = "/connect/{participationId}")
public class ExamineeCommandSocket {
    @Inject
    ConnectionStateService stateService;

    @Inject
    ParticipationService participationService;

    @Inject
    OpenConnections openConnections;

    @ConfigProperty(name = "websocket.client-timeout-seconds")
    int clientTimeoutSeconds;

    // Key: Session Id, Value: ConnectionId
    private final ConcurrentHashMap<String, String> connections = new ConcurrentHashMap<>();

    @OnOpen
    @WithSession
    public Uni<Void> onOpen(WebSocketConnection connection, @PathParam("participationId") String participationId) {
        return participationService.exists(participationId)
                .onItem().invoke(exists -> {
                    if (exists) {
                        connections.put(participationId, connection.id());
                        Log.infof("%s has connected.", participationId);
                    } else {
                        // TODO: Close connection for unauthorized people?
                        Log.warnf("An invalid participation id was sent (%s). Is someone tampering with the client?",
                                participationId);
                    }
                })
                .replaceWithVoid();
    }

    @OnClose
    @WithTransaction
    public Uni<Void> onClose(@PathParam("participationId") String participationId) {
        Log.infof("%s has lost connection.", participationId);
        connections.remove(participationId);
        return stateService.insertConnectedIfOngoing(participationId, false);
    }

    @OnError
    @WithTransaction
    public Uni<Void> onError(Exception e, @PathParam("participationId") String participationId) {
        Log.infof("%s has lost connection: %s", participationId, e);
        return stateService.insertConnectedIfOngoing(participationId, false);
    }

    @OnPongMessage
    @WithTransaction
    public Uni<Void> onPongMessage(WebSocketConnection connection, Buffer data) {
        String participationId = connection.pathParam("participationId");
        return stateService.insertConnectedIfOngoing(participationId, true);
    }

    @Scheduled(every = "{websocket.ping.interval}")
    public Uni<Void> broadcastPing() {
        final Buffer magic = Buffer.buffer(new byte[]{4, 9, 1});
        return Multi.createFrom().iterable(
                        openConnections.stream().map(c -> c.sendPing(magic)).toList()
                )
                .onItem().transformToUniAndConcatenate(u -> u)
                .toUni();
    }

    @Scheduled(every = "{websocket.cleanup.interval}")
    @WithTransaction
    public Uni<Void> cleanupDeadExaminees() {
        return stateService.getTimedoutParticipants(clientTimeoutSeconds)
                .onItem().transformToMulti(p -> Multi.createFrom().iterable(p))
                .onItem().transform(pId -> {
                    WebSocketConnection s = openConnections
                            .stream()
                            .filter(c -> c.pathParam("participationId").equals(pId))
                            .findFirst()
                            .orElse(null);

                    Log.infof("Disconnecting %s (Reason: Timed out)", pId);

                    return Uni.join().all(
                            stateService.insertConnectedIfOngoing(pId, false),
                            s != null && s.isOpen() ? s.close() : Uni.createFrom().voidItem()
                    ).andFailFast().replaceWithVoid();
                })
                .onItem().transformToUniAndConcatenate(a -> a)
                .toUni();
    }

    public Uni<Void> requestFrame(UUID participationId, FrameType type) {
        return Uni.createFrom()
                .item(connections.get(participationId.toString()))
                .onItem().ifNull()
                .failWith(() -> new IllegalArgumentException(String.format("%s is not connected", participationId)))
                .onItem().transform(connId -> openConnections.findByConnectionId(connId).orElse(null))
                .onItem().ifNull()
                .failWith(() -> new IllegalArgumentException(String.format("%s is not connected", participationId)))
                .onItem()
                .transformToUni(conn ->
                            conn.sendText(new RequestScreenshotCommand(new RequestScreenshotPayload(type)))
                            .onFailure().invoke(e -> Log.error("Send failed:", e))
                );
    }

    public Uni<Void> broadcastDisconnect(List<UUID> participationIds) {
        return Multi.createFrom().iterable(participationIds)
                .onItem().transform(uuid -> Optional.ofNullable(connections.get(uuid.toString())))
                .onItem().transform(connId -> connId.isPresent() ? openConnections.findByConnectionId(connId.get()) : Optional.<WebSocketConnection>empty())
                .onItem().transform(conn -> conn.isPresent() ? conn.get().sendText(new DisconnectClientCommand()) : Uni.createFrom().voidItem())
                .onItem().transformToUniAndConcatenate(u -> u)
                .toUni()
                .replaceWithVoid();
    }
}
