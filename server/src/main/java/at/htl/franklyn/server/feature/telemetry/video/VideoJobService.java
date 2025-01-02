package at.htl.franklyn.server.feature.telemetry.video;

import at.htl.franklyn.server.feature.exam.ExamRepository;
import at.htl.franklyn.server.feature.telemetry.participation.ParticipationRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;

@ApplicationScoped
public class VideoJobService {
    @Inject
    VideoJobRepository videoJobRepository;

    @Inject
    ParticipationRepository participationRepository;

    @Inject
    ExamRepository examRepository;

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
}
