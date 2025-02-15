package at.htl.franklyn.server.feature.telemetry.command;

import at.htl.franklyn.server.feature.telemetry.ScreenshotRequestManager;
import at.htl.franklyn.server.feature.telemetry.command.disconnect.DisconnectClientCommand;
import at.htl.franklyn.server.feature.telemetry.command.screenshot.RequestScreenshotCommand;
import at.htl.franklyn.server.feature.telemetry.command.screenshot.RequestScreenshotPayload;
import at.htl.franklyn.server.feature.telemetry.connection.ConnectionStateService;
import at.htl.franklyn.server.feature.telemetry.image.FrameType;
import at.htl.franklyn.server.feature.telemetry.participation.ParticipationRepository;
import at.htl.franklyn.server.feature.telemetry.participation.ParticipationService;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.websockets.next.*;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import jakarta.inject.Inject;
import net.bytebuddy.pool.TypePool;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@WebSocket(path = "/connect/{participationId}")
public class ExamineeCommandSocket {
    @Inject
    ConnectionStateService stateService;

    @Inject
    ParticipationRepository participationRepository;

    @Inject
    OpenConnections openConnections;

    @Inject
    ScreenshotRequestManager screenshotRequestManager;

    @ConfigProperty(name = "websocket.client-timeout-seconds")
    int clientTimeoutSeconds;

    // Key: Session Id, Value: ConnectionId
    private final ConcurrentHashMap<String, String> connections = new ConcurrentHashMap<>();

    @OnOpen
    @WithSession
    public Uni<Void> onOpen(WebSocketConnection connection, @PathParam("participationId") String participationId) {
        try {
            var parsedId = UUID.fromString(participationId);
            return participationRepository.findByIdWithExam(parsedId)
                    .onItem().invoke(participation -> {
                        if (participation != null) {
                            connections.put(participationId, connection.id());
                            screenshotRequestManager.registerStudent(parsedId, participation.getExam().getScreencaptureInterval());
                            Log.infof("%s has connected.", participationId);
                        } else {
                            // TODO: Close connection for unauthorized people?
                            Log.warnf("An invalid participation id was sent (%s). Is someone tampering with the client?",
                                    participationId);
                        }
                    })
                    .replaceWithVoid();
        } catch (IllegalArgumentException ignored) { }
        return Uni.createFrom().voidItem();
    }

    @OnClose
    @WithTransaction
    public Uni<Void> onClose(@PathParam("participationId") String participationId) {
        Log.infof("%s has lost connection.", participationId);
        connections.remove(participationId);
        try {
            screenshotRequestManager.unregisterStudent(UUID.fromString(participationId));
        } catch (IllegalArgumentException ignored) { }
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

    @Scheduled(every = "{websocket.ping.interval}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @WithTransaction
    public Uni<Void> broadcastPing() {
        final Buffer magic = Buffer.buffer(new byte[]{4, 9, 1});
        Context ctx = Vertx.currentContext();
        return cleanupDeadExaminees()
                .chain(ignored -> {
                    List<Uni<Void>> results = openConnections
                            .stream()
                            .map(c ->
                                    c.sendPing(magic)
                                            .onFailure().invoke(e -> Log.warnf(
                                                    "Ping request to %s failed! Is the server overloaded? (Reason: %s)",
                                                    c.pathParam("participationId"),
                                                    e.getMessage()
                                            ))
                            )
                            .toList();

                    // Uni.join().all(...) can only be called with non-empty lists
                    return !results.isEmpty()
                            ? Uni.join()
                                .all(results)
                                .andCollectFailures()
                                .emitOn(r -> ctx.runOnContext(v -> r.run()))
                            : Uni.createFrom().voidItem()
                                .emitOn(r -> ctx.runOnContext(v -> r.run()));
                })
                .replaceWithVoid()
                .emitOn(r -> ctx.runOnContext(ignored -> r.run()));
    }

    public Uni<Void> cleanupDeadExaminees() {
        Context ctx = Vertx.currentContext();
        return stateService.getTimedoutParticipants(clientTimeoutSeconds)
                .chain(examineeIds -> {
                    // make sure db insertion happens sequentially. for more information see here:
                    // https://github.com/hibernate/hibernate-reactive/issues/1607
                    Uni<Void> result = Uni.createFrom().voidItem();

                    for (String pId : examineeIds) {
                        WebSocketConnection s = openConnections
                                .stream()
                                .filter(c -> c.pathParam("participationId").equals(pId))
                                .findFirst()
                                .orElse(null);

                        Log.infof("Disconnecting %s (Reason: Timed out)", pId);

                        result = result.call(ignored ->
                                stateService.insertConnectedIfOngoing(pId, false)
                                        .onFailure().retry().atMost(2) // retry when database insert fails
                                        .call(ignored2 ->
                                                s != null && s.isOpen()
                                                        ? s.close()
                                                        : Uni.createFrom().voidItem()
                                        )
                                        .onFailure().recoverWithNull() // No hard failure if one disconnect fails
                                        .emitOn(r -> ctx.runOnContext(ignored2 -> r.run()))
                        );
                    }

                    return result
                            .emitOn(r -> ctx.runOnContext(ignored -> r.run()));
                }).replaceWithVoid();
    }

    public Uni<Void> requestFrame(UUID participationId, FrameType type) {
        Context ctx = Vertx.currentContext();
        final RequestScreenshotCommand screenshotCommand =
                new RequestScreenshotCommand(new RequestScreenshotPayload(type));
        return Uni.createFrom()
                .item(connections.get(participationId.toString()))
                .onItem().ifNull()
                    .failWith(() -> new IllegalArgumentException(String.format("%s is not connected", participationId)))
                .onItem().transform(connId -> openConnections.findByConnectionId(connId).orElse(null))
                .onItem().ifNull()
                    .failWith(() -> new IllegalArgumentException(String.format("%s is not connected", participationId)))
                .chain(conn ->
                        conn.sendText(screenshotCommand)
                                .emitOn(r -> ctx.runOnContext(ignored -> r.run()))
                                .onFailure().retry().atMost(2)
                                .ifNoItem().after(Duration.ofMillis(1000)).fail()
                                .onFailure().invoke(e ->
                                        Log.warnf(
                                                "Screenshot request to %s failed! Is the server overloaded? (Reason: %s)",
                                                conn.pathParam("participationId"),
                                                e.getMessage()
                                        )
                                )
                                .onFailure().recoverWithNull()
                )
                .emitOn(r -> ctx.runOnContext(ignored -> r.run()));
    }

    public Uni<Void> broadcastDisconnect(List<UUID> participationIds) {
        Context ctx = Vertx.currentContext();
        var participants = participationIds.stream()
                .map(id -> {
                    String connectionId = connections.get(id.toString());
                    if (connectionId != null) {
                        var connection = openConnections.findByConnectionId(connectionId);
                        if (connection.isPresent()) {
                            return connection.get().sendText(new DisconnectClientCommand())
                                    .onFailure().invoke(e ->
                                        Log.warnf( "Disconnect request to %s failed! The client might already be unreachable (Reason: %s)",
                                                connection.get().pathParam("participationId"),
                                                e.getMessage()
                                        )
                                    )
                                    .emitOn(r -> ctx.runOnContext(ignored -> r.run()));
                        }
                    }
                    return Uni.createFrom().voidItem();
                })
                .toList();

        if (participants.isEmpty()) {
            return Uni.createFrom().voidItem();
        }

        return Uni.join()
                .all(participants)
                .usingConcurrencyOf(1)
                .andCollectFailures()
                .onFailure().recoverWithNull()
                .emitOn(r -> ctx.runOnContext(ignored -> r.run()))
                .replaceWithVoid();
    }
}
