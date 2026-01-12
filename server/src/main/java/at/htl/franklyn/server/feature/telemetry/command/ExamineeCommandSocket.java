package at.htl.franklyn.server.feature.telemetry.command;

import at.htl.franklyn.server.feature.metrics.ProfilingMetricsService;
import at.htl.franklyn.server.feature.telemetry.PingPongRequestManager;
import at.htl.franklyn.server.feature.telemetry.ScreenshotRequestManager;
import at.htl.franklyn.server.feature.telemetry.command.disconnect.DisconnectClientCommand;
import at.htl.franklyn.server.feature.telemetry.command.screenshot.RequestScreenshotCommand;
import at.htl.franklyn.server.feature.telemetry.command.screenshot.RequestScreenshotPayload;
import at.htl.franklyn.server.feature.telemetry.connection.ConnectionStateService;
import at.htl.franklyn.server.feature.telemetry.image.FrameType;
import at.htl.franklyn.server.feature.telemetry.participation.ParticipationRepository;
import at.htl.franklyn.server.feature.telemetry.participation.ParticipationService;
import io.micrometer.core.instrument.Timer;
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
    @Inject
    PingPongRequestManager pingRequestManager;
    
    @Inject
    ProfilingMetricsService profilingMetrics;

    // Key: Session Id, Value: ConnectionId
    private final ConcurrentHashMap<String, String> connections = new ConcurrentHashMap<>();
    
    // Track ping timers by client ID
    private final ConcurrentHashMap<UUID, Timer.Sample> activePingTimers = new ConcurrentHashMap<>();

    @OnOpen
    @WithSession
    public Uni<Void> onOpen(WebSocketConnection connection, @PathParam("participationId") String participationId) {
        try {
            var parsedId = UUID.fromString(participationId);
            return participationRepository.findByIdWithExam(parsedId)
                    .onItem().invoke(participation -> {
                        if (participation != null) {
                            connections.put(participationId, connection.id());
                            screenshotRequestManager.registerClient(parsedId, participation.getExam().getScreencaptureInterval());
                            pingRequestManager.registerClient(parsedId);
                            profilingMetrics.incrementConnectedClients();
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
            var uuid = UUID.fromString(participationId);
            screenshotRequestManager.unregisterClient(uuid);
            pingRequestManager.unregisterClient(uuid);
            profilingMetrics.decrementConnectedClients();
            activePingTimers.remove(uuid);
        } catch (IllegalArgumentException ignored) { }
        return stateService.insertConnectedIfOngoing(participationId, false);
    }

    @OnError
    @WithTransaction
    public Uni<Void> onError(Exception e, @PathParam("participationId") String participationId) {
        Log.infof("%s has lost connection: %s", participationId, e);
        try {
            profilingMetrics.decrementConnectedClients();
        } catch (Exception ignored) { }
        return stateService.insertConnectedIfOngoing(participationId, false);
    }

    @OnPongMessage
    public void onPongMessage(WebSocketConnection connection, Buffer data) {
        String participationId = connection.pathParam("participationId");
        try {
            UUID uuid = UUID.fromString(participationId);
            // Stop ping latency timer
            Timer.Sample sample = activePingTimers.remove(uuid);
            if (sample != null) {
                profilingMetrics.stopWebsocketPingTimer(sample);
            }
            pingRequestManager.notifyClientRequestReceived(uuid);
        } catch (IllegalArgumentException ignored) {}
    }

    public Uni<Void> sendPing(UUID client) {
        final Buffer magic = Buffer.buffer(new byte[]{4, 9, 1});
        WebSocketConnection c = openConnections
                .stream()
                .filter(conn -> conn.pathParam("participationId").equals(client.toString()))
                .findFirst()
                .orElse(null);
        if (c != null) {
            // Start ping latency timer
            Timer.Sample sample = profilingMetrics.startWebsocketPingTimer();
            activePingTimers.put(client, sample);
            
            return c.sendPing(magic)
                    .onFailure().invoke(e -> {
                        activePingTimers.remove(client);
                        Log.warnf(
                                "Ping request to %s failed! Is the server overloaded? (Reason: %s)",
                                client,
                                e.getMessage()
                        );
                    });
        } else {
            return Uni.createFrom().voidItem();
        }
    }

    public Uni<Void> timeoutDisconnect(UUID client) {
        WebSocketConnection conn = openConnections
                .stream()
                .filter(c -> c.pathParam("participationId").equals(client.toString()))
                .findFirst()
                .orElse(null);

        Log.infof("Disconnecting %s (Reason: Timed out)", client);

        if (conn != null && conn.isOpen()) {
            return conn.close();
        } else {
            return Uni.createFrom().voidItem();
        }
    }

    public Uni<Void> requestFrame(UUID participationId, FrameType type) {
        Context ctx = Vertx.currentContext();
        final RequestScreenshotCommand screenshotCommand =
                new RequestScreenshotCommand(new RequestScreenshotPayload(type));
        final Timer.Sample sendTimer = profilingMetrics.startWebsocketMessageSendTimer();
        
        return Uni.createFrom()
                .item(connections.get(participationId.toString()))
                .onItem().ifNull()
                    .failWith(() -> new IllegalArgumentException(String.format("%s is not connected", participationId)))
                .onItem().transform(connId -> openConnections.findByConnectionId(connId).orElse(null))
                .onItem().ifNull()
                    .failWith(() -> new IllegalArgumentException(String.format("%s is not connected", participationId)))
                .chain(conn ->
                        conn.sendText(screenshotCommand)
                                .invoke(ignored -> profilingMetrics.stopWebsocketMessageSendTimer(sendTimer))
                                .emitOn(r -> ctx.runOnContext(ignored -> r.run()))
                                .onFailure().invoke(e ->
                                        Log.warnf(
                                                "Screenshot request to %s failed! Is the server overloaded? (Reason: %s)",
                                                conn.pathParam("participationId"),
                                                e.getMessage()
                                        )
                                )
                                .onFailure().recoverWithNull()
                )
                .onFailure().recoverWithNull()
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
