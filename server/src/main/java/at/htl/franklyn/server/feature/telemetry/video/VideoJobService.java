package at.htl.franklyn.server.feature.telemetry.video;

import at.htl.franklyn.server.feature.telemetry.image.Image;
import at.htl.franklyn.server.feature.telemetry.image.ImageRepository;
import at.htl.franklyn.server.feature.telemetry.participation.ParticipationRepository;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class VideoJobService {
    @Inject
    VideoGenerationJobManager videoGenerationJobManager;

    @Inject
    VideoJobRepository videoJobRepository;

    @Inject
    ParticipationRepository participationRepository;

    public Uni<VideoJob> startVideoJob(
            Long userId,
            Long examId
    ) {

        Log.info("in VideoJobService.startVideoJob");

        return participationRepository.getByExamAndExaminee(userId, examId)
                .onItem().ifNull().fail()
                .chain(ignored -> videoJobRepository.persist(new VideoJob(VideoJobState.ONGOING, null)))
                .chain(videoJob ->
                        videoGenerationJobManager
                                .startVideoGenerationJob(examId, userId, videoJob.getId())
                                .replaceWith(videoJob)
                );
    }
}
