package at.htl.franklyn.server.feature.exam;

import at.htl.franklyn.server.feature.examinee.ExamineeDto;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class ExamRepository implements PanacheRepository<Exam> {
    @Inject
    Mutiny.SessionFactory sf;

    public Uni<Set<Integer>> getPINsInUse() {
        return sf.withSession(session -> session
                .createQuery("select e.pin from Exam e where actualEnd is null", Integer.class)
                .getResultList()
                .onItem().transform(HashSet::new)
        );
    }

    public Uni<List<ExamineeDto>> getExamineesOfExamWithConnectionState(long id) {
        return sf.withSession(session ->
                session.createQuery(
                                """
                                        select new at.htl.franklyn.server.feature.examinee.ExamineeDto(
                                            e.firstname,
                                            e.lastname,
                                            COALESCE(cs.isConnected, false),
                                            e.id
                                        )
                                        from Participation p join Examinee e on (p.examinee.id = e.id)
                                            left join ConnectionState cs on (p.id = cs.participation.id and cs.pingTimestamp = (
                                                select max(c.pingTimestamp)
                                                    from ConnectionState c
                                                    where c.participation.id = p.id
                                            ))
                                        where p.exam.id = ?1
                                        """, ExamineeDto.class)
                        .setParameter(1, id)
                        .getResultList()
        );
    }

    public Uni<List<ExamInfoDto>> listAllWithExamineeCounts() {
        return sf.withSession(session ->
                session.createQuery(
                                """
                                        select new at.htl.franklyn.server.feature.exam.ExamInfoDto(
                                            e.id,
                                            e.plannedStart,
                                            e.plannedEnd,
                                            e.actualStart,
                                            e.actualEnd,
                                            e.title,
                                            lpad(cast(e.pin as String), 3, '0'),
                                            e.state,
                                            e.screencaptureInterval,
                                            (select count(*) from Participation p where p.exam.id = e.id)
                                        ) from Exam e
                                        """, ExamInfoDto.class)
                        .getResultList()
        );
    }

    public Uni<List<Exam>> findExamsOngoingFor(int days) {
        return list(
                """
                        select e
                        from Exam e
                        where (CURRENT_TIMESTAMP - e.actualStart) >= ?1
                            and e.state = ?2
                            and e.actualEnd is null
                        """, Duration.ofDays(days), ExamState.ONGOING
        );
    }
}
