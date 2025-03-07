package at.htl.franklyn.server.feature.telemetry.image;

import at.htl.franklyn.server.feature.telemetry.participation.Participation;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class ImageRepository implements PanacheRepository<Image> {
    public Uni<Void> deleteImagesOfParticipation(Participation p) {
        return delete("participation.id = ?1", p.getId())
                .replaceWithVoid();
    }

    public Uni<Image> getImageByExamAndUser(long examId, long userId) {
        return find(
                """
                        select i from Image i join Participation p on (i.participation.id = p.id)
                            where p.exam.id = ?1 and p.examinee.id = ?2
                                and i.captureTimestamp = (
                                    select max(m.captureTimestamp)
                                        from Image m
                                        where m.participation.id = p.id
                                )
                    """, examId, userId)
                .firstResult();
    }

    public Uni<List<Image>> getAllImagesByExamAndUser(long examId, long userId) {
        return find(
                """
                        select i from Image i join Participation p on (i.participation.id = p.id)
                            where p.exam.id = ?1 and p.examinee.id = ?2
                            order by i.captureTimestamp asc
                    """, examId, userId)
                .list();
    }

    public Uni<Long> countImagesOfUserInExam(long examId, long userId) {
        return find(
                """
                        select i from Image i join Participation p on (i.participation.id = p.id)
                            where p.exam.id = ?1 and p.examinee.id = ?2
                    """, examId, userId)
                .count();
    }
}
