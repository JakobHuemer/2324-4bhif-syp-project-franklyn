package at.htl.franklyn.server.feature.telemetry;

import at.htl.franklyn.server.feature.telemetry.command.ExamineeCommandSocket;
import at.htl.franklyn.server.feature.telemetry.connection.ConnectionStateService;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDateTime;
import java.util.UUID;

@ApplicationScoped
public class PingPongRequestManager extends ThrottledRequestManager<PingPongRequestManager.ClientData, UUID> {
    protected static class ClientData extends ThrottledRequestManager.ClientDataBase<UUID> { }

    @Inject
    ExamineeCommandSocket commandSocket;
    @Inject
    ConnectionStateService stateService;

    @ConfigProperty(name = "websocket.ping.interval-millis", defaultValue = "5000")
    int pingIntervalMs;
    @ConfigProperty(name = "websocket.client-timeout-millis", defaultValue = "2000")
    int pingTimeoutMs;
    @ConfigProperty(name = "websocket.ping.max-concurrent-requests", defaultValue = "10")
    int maxRequests;

    public void onStartup(@Observes StartupEvent ev) {
        init(maxRequests, pingTimeoutMs, ClientData.class);
    }

    @Override
    public ClientData registerClient(UUID id) {
        return super.registerClient(id);
    }

    @Override
    public void unregisterClient(UUID id) {
        super.unregisterClient(id);
    }

    @Override
    public boolean notifyClientRequestReceived(UUID client) {
        return super.notifyClientRequestReceived(client);
    }

    @Override
    protected long calculateWaitMillis(ClientData client) {
        return client.lastResponseTimestampMillis != null
                ? Math.max((pingIntervalMs - (System.currentTimeMillis() - client.lastResponseTimestampMillis)) - pingTimeoutMs, 1)
                : 1;
    }

    @Override
    protected Uni<Void> request(ClientData client) {
        return commandSocket.sendPing(client.id)
                .onFailure().recoverWithNull();
    }

    @Override
    protected Uni<Void> handleResponse(ClientData client, boolean clientReached) {
        Context ctx = Vertx.currentContext();
        return insertConnectionState(client.id, clientReached)
                .emitOn(r -> ctx.runOnContext(ignored -> r.run()))
                .chain(ignored -> clientReached
                                ? Uni.createFrom().voidItem()
                                : commandSocket.timeoutDisconnect(client.id)
                                        .emitOn(r -> ctx.runOnContext(ignored2 -> r.run()))
                );
    }

    @WithTransaction
    Uni<Void> insertConnectionState(UUID pId, boolean state) {
        LocalDateTime pingTimestamp = LocalDateTime.now();
        state = !isStagedForRemoval(pId) && state;
        return stateService.insertConnectedIfOngoing(pId, state, pingTimestamp);
    }
}
