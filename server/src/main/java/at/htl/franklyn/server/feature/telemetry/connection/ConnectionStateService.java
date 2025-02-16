package at.htl.franklyn.server.feature.telemetry.connection;

import at.htl.franklyn.server.feature.exam.ExamState;
import at.htl.franklyn.server.feature.telemetry.participation.ParticipationRepository;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ConnectionStateService {
    @Inject
    ConnectionStateRepository stateRepository;

    @Inject
    ParticipationRepository participationRepository;

    public Uni<Void> insertConnectedIfOngoing(UUID participationId, boolean state) {
        Context ctx = Vertx.currentContext();
        return participationRepository
                .findByIdWithExam(participationId)
                .onItem().ifNotNull().transform(participation ->
                        participation.getExam().getState() == ExamState.ONGOING
                                ? participation
                                : null
                )
                .onItem().ifNotNull()
                .transform(participation -> new ConnectionState(
                        LocalDateTime.now(),
                        participation,
                        state
                ))
                .onItem().ifNotNull().transformToUni(cs -> stateRepository.persist(cs))
                // Most of the time when calling .broadcast disconnect mutiny switches vertx-worker
                // Hibernate however does not like this and give errors similar to
                // "Detected use of the reactive Session from a different Thread than the one which was used to open the reactive Session"
                // In order to counteract this, we pin the emission to the vertx thread hibernate wants
                .emitOn(r -> ctx.runOnContext(ignored -> r.run()))
                .replaceWithVoid();
    }

    public Uni<Void> insertConnectedIfOngoing(String participationId, boolean state) {
        return insertConnectedIfOngoing(UUID.fromString(participationId), state);
    }

    public Uni<List<String>> getTimedoutParticipants(int timeout) {
        return stateRepository.getTimedoutParticipants(timeout);
    }
}
