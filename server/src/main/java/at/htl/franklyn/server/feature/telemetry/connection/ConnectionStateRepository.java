package at.htl.franklyn.server.feature.telemetry.connection;

import at.htl.franklyn.server.feature.telemetry.participation.Participation;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ConnectionStateRepository implements PanacheRepository<ConnectionState> {
    public Uni<List<String>> getTimedoutParticipants(int timeout) {
        return getSession()
                .chain(session -> session
                        .createNamedQuery("ConnectionState.getTimedoutParticipants", String.class)
                        .setParameter(1, timeout)
                        .getResultList());
    }

    public Uni<Void> deleteStatesOfParticipation(Participation p) {
        return delete("participation.id = ?1", p.getId())
                .replaceWithVoid();
    }

    public Uni<Void> disconnectMany(List<Participation> participations) {
        return persist(participations
                .stream()
                .map(participation -> new ConnectionState(
                        LocalDateTime.now(),
                        participation,
                        false
                )));
    }
}
