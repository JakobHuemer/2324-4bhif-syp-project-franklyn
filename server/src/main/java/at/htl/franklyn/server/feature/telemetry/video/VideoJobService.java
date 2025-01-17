package at.htl.franklyn.server.feature.telemetry.video;

import at.htl.franklyn.server.feature.exam.ExamRepository;
import at.htl.franklyn.server.feature.telemetry.participation.ParticipationRepository;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

@ApplicationScoped
public class VideoJobService {
    @Inject
    VideoJobRepository videoJobRepository;

    @Inject
    ParticipationRepository participationRepository;

    @Inject
    ExamRepository examRepository;

    @ConfigProperty(name = "video.path")
    String videoFolderPath;

    @Inject
    Vertx vertx;

    private Path getVideoFolderPath(long jobId) {
        return Paths.get(
                videoFolderPath,
                Long.toString(jobId)
        );
    }

    public Uni<VideoJob> queueVideoJob(
            Long userId,
            Long examId
    ) {
        return participationRepository.getByExamAndExaminee(userId, examId)
                .onItem().ifNull().fail()
                .chain(participation ->
                        videoJobRepository.persistAndFlush(
                                new VideoJob(
                                        LocalDateTime.now(),
                                        VideoJobState.QUEUED,
                                        VideoJobType.SINGLE,
                                        participation.getExam(),
                                        participation.getExaminee(),
                                        null
                                )
                        )
                );
    }

    public Uni<VideoJob> queueBatchVideoJob(
            Long examId
    ) {
        return examRepository.findById(examId)
                .onItem().ifNull().fail()
                .chain(exam ->
                        videoJobRepository.persistAndFlush(
                                new VideoJob(
                                        LocalDateTime.now(),
                                        VideoJobState.QUEUED,
                                        VideoJobType.BATCH,
                                        exam,
                                        null,
                                        null
                                )
                        )
                );
    }

    public Uni<Void> deleteVideoJob(long jobId) {
        String folderPath = getVideoFolderPath(jobId).toString();
        return vertx.fileSystem()
                .exists(folderPath)
                .onItem().transformToUni(exists -> {
                    if (exists) {
                        return vertx.fileSystem().deleteRecursive(folderPath, true);
                    }
                    return Uni.createFrom().voidItem();
                })
                .chain(ignored -> videoJobRepository.deleteById(jobId))
                .replaceWithVoid();
    }
}
