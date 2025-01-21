package at.htl.franklyn.server.feature.telemetry.video;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class VideoJobRepository implements PanacheRepository<VideoJob> {
    public Uni<VideoJob> getNextJob() {
        return find(
                """
                select vj
                    from VideoJob vj
                    where vj.state = ?1
                        and vj.queueTimestamp = (
                            select min(v.queueTimestamp)
                                from VideoJob v
                                where v.state = ?1
                        )
                """, VideoJobState.QUEUED)
                .firstResult();
    }

    public Uni<Void> completeJob(long jobId, String artifactPath) {
        return update("state = ?1, artifactPath = ?3 where id = ?2", VideoJobState.DONE, jobId, artifactPath)
                .replaceWithVoid();
    }

    public Uni<Void> failJob(long jobId) {
        return update("state = ?1 where id = ?2", VideoJobState.FAILED, jobId).replaceWithVoid();
    }

    public Uni<List<VideoJob>> getVideoJobsOfExam(long examId) {
        return find("""
                select vj from VideoJob vj where vj.exam.id = ?1
                """, examId)
                .list();
    }


}
