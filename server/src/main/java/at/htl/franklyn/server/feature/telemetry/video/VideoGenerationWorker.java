package at.htl.franklyn.server.feature.telemetry.video;

import at.htl.franklyn.server.feature.exam.ExamRepository;
import at.htl.franklyn.server.feature.examinee.ExamineeRepostiory;
import at.htl.franklyn.server.feature.telemetry.image.ImageRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.unchecked.Unchecked;
import io.smallrye.mutiny.vertx.core.ContextAwareScheduler;
import io.vertx.mutiny.core.Context;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

    @Inject
    ExamRepository examRepository;
    @Inject
    ExamineeRepostiory examineeRepostiory;

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
                    Log.infof("Picking up next video job: %d (type %s)", job.getId(), job.getType());

                    return (switch (job.getType()) {
                        case SINGLE -> convertSingle(job.getId(), job.getExam().getId(), job.getExaminee().getId())
                                .emitOn(scheduler);
                        case BATCH -> convertBatch(job.getId(), job.getExam().getId())
                                .emitOn(scheduler);
                    })
                    .onItem().transformToUni(path -> {
                        Log.infof("Finished video job %d (artifact: %s)", job.getId(), path);
                        return videoJobRepository.completeJob(job.getId(), path)
                                .emitOn(scheduler);
                    })
                    .onFailure().recoverWithUni(failure -> {
                        Log.infof("Video job %d failed! Reason: %s", job.getId(), failure.getMessage());
                        return videoJobRepository.failJob(job.getId())
                                .emitOn(scheduler);
                    });
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

    Uni<String> convertSingle(long jobId, long examId, long userId) {
        final Path[] videoPath = new Path[1];
        Context ctx = Vertx.currentContext();

        return examineeRepostiory.findById(userId)
                .onItem().invoke(user -> {
                    videoPath[0] = Paths.get(
                            getVideoFolderPath(jobId).toAbsolutePath().toString(),
                            String.format("%s-%s-e%d-u%d.%s",
                                    // sanitize names so only numbers, letters and underscores are allowed
                                    user.getFirstname().replaceAll("\\W+", "_"),
                                    user.getLastname().replaceAll("\\W+", "_"),
                                    examId,
                                    userId,
                                    VIDEO_FORMAT
                            )
                    ).toAbsolutePath();
                })
                .chain(ignored -> imageRepository.getAllImagesByExamAndUser(examId, userId)
                        .onItem().transform(Unchecked.function(images -> {
                            if (images.isEmpty()) {
                                throw new NoImagesAvailableException("No images available for examinee");
                            }
                            return images;
                        }))
                        .emitOn(ctx::runOnContext))
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
                                                videoPath[0].toAbsolutePath().toString()
                                        );

                                        pb.inheritIO();
                                        Process p = pb.start();
                                        int exitCode = p.waitFor();
                                        if (exitCode != 0) {
                                            throw new CompletionException(new RuntimeException("ffmpeg exited with non zero status code"));
                                        }

                                        return exitCode;
                                    } catch (IOException | InterruptedException | RuntimeException e) {
                                        Log.error("Failed to generate video: ", e);
                                        Log.errorf("Command used: ffmpeg -y -f concat -safe 0 -i %s -c:v libx264 -pix_fmt yuv420p %s", tmpFile, videoPath[0].toAbsolutePath());
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
                .replaceWith(() -> videoPath[0].toAbsolutePath().toString())
                .emitOn(ctx::runOnContext);
    }

    Uni<String> convertBatch(long jobId, long examId) {
        Context ctx = Vertx.currentContext();
        final Path zipPath = Paths.get(
                getVideoFolderPath(jobId).toAbsolutePath().toString(),
                "videos.zip"
        ).toAbsolutePath();

        return examineeRepostiory.getExamineesOfExam(examId)
                .onItem().transform(examinees -> examinees.stream()
                        .map(examinee -> convertSingle(jobId, examId, examinee.getId())
                                .onFailure(NoImagesAvailableException.class).recoverWithNull()
                                .emitOn(ctx::runOnContext)
                        ).toList()
                )
                .chain(tasks -> Uni.join()
                        .all(tasks)
                        .usingConcurrencyOf(1)
                        .andFailFast()
                        .emitOn(ctx::runOnContext)
                )
                .chain(paths -> Uni.createFrom().completionStage(() -> CompletableFuture.supplyAsync(() -> {
                    // For the life of me I could not convert this to run asynchronously using vertx + mutiny
                    // Thus a hack using completable futures must suffice
                    // I know this is a massive skill issue on my side but the complexity of reactive certainly doesn't help
                    try {
                        final FileOutputStream fos = new FileOutputStream(zipPath.toFile());
                        try (ZipOutputStream zos = new ZipOutputStream(fos)) {
                            for (String file : paths) {
                                if (file != null) {
                                    File video = new File(file);
                                    try (FileInputStream fis = new FileInputStream(video)) {
                                        ZipEntry zipEntry = new ZipEntry(video.getName());
                                        zos.putNextEntry(zipEntry);

                                        byte[] bytes = new byte[4096];
                                        int length;
                                        while((length = fis.read(bytes)) >= 0) {
                                            zos.write(bytes, 0, length);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                    return paths;
                })))
                .chain(paths -> {
                    var tasks = paths.stream().map(path ->
                            path == null ? Uni.createFrom().voidItem() : vertx.fileSystem().delete(path)
                    ).toList();

                    if (tasks.isEmpty()) {
                        return Uni.createFrom().voidItem();
                    }

                    return Uni.join()
                            .all(tasks)
                            .andCollectFailures()
                            .replaceWithVoid();
                })
                .replaceWith(() -> zipPath.toAbsolutePath().toString())
                .emitOn(ctx::runOnContext);
    }
}
