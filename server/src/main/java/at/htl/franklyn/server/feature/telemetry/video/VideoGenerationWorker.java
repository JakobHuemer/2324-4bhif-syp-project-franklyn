package at.htl.franklyn.server.feature.telemetry.video;

import at.htl.franklyn.server.feature.telemetry.image.ImageRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.vertx.core.ContextAwareScheduler;
import io.vertx.mutiny.core.Context;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

@ApplicationScoped
public class VideoGenerationWorker {
    private static final String VIDEO_FORMAT = "mp4";

    @ConfigProperty(name = "video.path")
    String videoFolderPath;

    @Inject
    VideoJobRepository videoJobRepository;

    @Inject
    ImageRepository imageRepository;

    @Inject
    Vertx vertx;

    /**
     * Check if new videos are available that need to be generated
     *
     * @return Nothing
     */
    @Scheduled(every = "${video.video-generation-poll-seconds}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @WithTransaction
    Uni<Void> tryGenerateVideo() {
        // This is the default scheduled executor of Mutiny:
        var delegate = Infrastructure.getDefaultWorkerPool();
        // This makes sure we re-use the correct Vert.x duplicated context to please hibernate
        var scheduler = ContextAwareScheduler.delegatingTo(delegate).withCurrentContext();

        return videoJobRepository.getNextJob()
                // If item is null -> nothing to do
                .onItem().ifNotNull().transformToUni(job -> {
                    Log.infof("Picking up next video job: %d", job.getId());

                    return switch (job.getType()) {
                        case SINGLE -> convertSingle(job.getId(), job.getExam().getId(), job.getExaminee().getId())
                                .emitOn(scheduler);
                        case BATCH -> {
                            // TODO
                            Log.error("batch video job unimplemented");
                            yield null;
                        }
                    };
                })
                .replaceWithVoid()
                .emitOn(scheduler);
    }

    private Path getVideoFolderPath(long jobId) {
        return Paths.get(
                videoFolderPath,
                Long.toString(jobId)
        );
    }

    Uni<Void> convertSingle(long jobId, long examId, long userId) {
        final Path videoPath = Paths.get(
                getVideoFolderPath(jobId).toAbsolutePath().toString(),
                String.format("e%d-u%d.%s", examId, userId, VIDEO_FORMAT)
        ).toAbsolutePath();
        Context ctx = Vertx.currentContext();

        return imageRepository
                .getAllImagesByExamAndUser(examId, userId)
                .chain(images -> vertx.fileSystem()
                        .createTempFile("franklyn-", "-videojob")
                        .call(tmpFile ->
                                vertx.fileSystem().writeFile(
                                        tmpFile,
                                        Buffer.buffer(
                                                images.stream()
                                                        .map(image -> String.format("file '%s'\nduration 1", image.getPath()))
                                                        .collect(Collectors.joining("\n"))
                                        )
                                )
                                .emitOn(ctx::runOnContext)
                        )
                        .emitOn(ctx::runOnContext)
                )
                .call(ignored -> vertx.fileSystem()
                        .exists(getVideoFolderPath(jobId).toString())
                        .chain(exists -> {
                            if (!exists) {
                                return vertx.fileSystem()
                                        .mkdirs(getVideoFolderPath(jobId).toString())
                                        .emitOn(ctx::runOnContext);
                            }
                            return Uni.createFrom().voidItem();
                        })
                        .emitOn(ctx::runOnContext)
                )
                .chain(tmpFile -> Uni.createFrom()
                        .completionStage(() ->
                                CompletableFuture.supplyAsync(() -> {
                                    try {
                                        ProcessBuilder pb = new ProcessBuilder(
                                                "ffmpeg",
                                                "-y",
                                                "-f", "concat",
                                                "-safe", "0",
                                                "-i", tmpFile,
                                                "-c:v", "libx264",
                                                "-pix_fmt", "yuv420p",
                                                videoPath.toAbsolutePath().toString()
                                        );

                                        pb.inheritIO();
                                        Process p = pb.start();
                                        int exitCode = p.waitFor();
                                        if (exitCode != 0) {
                                            throw new RuntimeException("ffmpeg exited with non zero status code");
                                        }

                                        return exitCode;
                                    } catch (IOException | InterruptedException | RuntimeException e) {
                                        Log.error("Failed to generate video: ", e);
                                        Log.errorf("Command used: ffmpeg -y -f concat -safe 0 -i %s -c:v libx264 -pix_fmt yuv420p %s", tmpFile, videoPath.toAbsolutePath());
                                        throw new CompletionException(e);
                                    }
                                })
                        )
                        .emitOn(ctx::runOnContext)
                        .replaceWith(tmpFile)
                )
                .chain(tmpFile -> vertx.fileSystem()
                        .delete(tmpFile)
                        .emitOn(ctx::runOnContext)
                )
                .onItem().transformToUni(ignored -> {
                    Log.infof("Finished video job %d (artifact: %s)", jobId, videoPath.toAbsolutePath());
                    return videoJobRepository.completeJob(jobId, videoPath.toAbsolutePath().toString())
                            .emitOn(ctx::runOnContext);
                })
                .onFailure().call(failure -> {
                    Log.infof("Video job %d failed! Reason: %s", jobId, failure.getMessage());
                    return videoJobRepository.failJob(jobId)
                            .emitOn(ctx::runOnContext);
                })
                .emitOn(ctx::runOnContext);
    }
}
