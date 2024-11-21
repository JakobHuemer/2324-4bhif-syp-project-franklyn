package at.htl.franklyn.server.feature.telemetry.video;

import at.htl.franklyn.server.feature.telemetry.image.Image;
import at.htl.franklyn.server.feature.telemetry.image.ImageRepository;
import io.quarkus.logging.Log;
import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.quartz.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@ApplicationScoped
public class VideoGenerationJobManager {
    private static final String FRANKLYN_VIDEO_GENERATION_JOB_GROUP = "franklynVideoGenerationJobGroup";
    private static final String FRANKLYN_VIDEO_GENERATION_JOB_PREFIX = "franklynVideoGenerationJob";
    private static final String FRANKLYN_VIDEO_GENERATION_JOB_EXAM_ID_JOB_DATA_KEY = "examId";
    private static final String FRANKLYN_VIDEO_GENERATION_JOB_User_ID_JOB_DATA_KEY = "userId";

    @Inject
    Scheduler quartz;

    private String getVideoGenerationJobIdentity(long examId) {
        return String.format("%s%d", FRANKLYN_VIDEO_GENERATION_JOB_PREFIX, examId);
    }

    public Uni<Void> startVideoGenerationJob(Long examId, Long userId, Long jobId) {
        JobDetail job = JobBuilder.newJob(VideoGenerationJob.class)
                .withIdentity(getVideoGenerationJobIdentity(jobId), FRANKLYN_VIDEO_GENERATION_JOB_GROUP)
                .usingJobData(FRANKLYN_VIDEO_GENERATION_JOB_EXAM_ID_JOB_DATA_KEY, examId)
                .usingJobData(FRANKLYN_VIDEO_GENERATION_JOB_User_ID_JOB_DATA_KEY, userId)
                .storeDurably(true)
                .build();

        try {
            quartz.addJob(job, true);
            quartz.triggerJob(job.getKey());
        } catch (SchedulerException e) {
            Log.error("Could not create video job", e);
            return Uni.createFrom().failure(e);
        }

        return Uni.createFrom().voidItem();
    }

    public static class VideoGenerationJob implements Job {
        @Inject
        Mutiny.SessionFactory sf;

        @Inject
        Vertx vertx;

        @Inject
        ImageRepository imageRepository;

        @Override
        public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
            JobDataMap dataMap = jobExecutionContext.getJobDetail().getJobDataMap();
            long examId = dataMap.getLong(FRANKLYN_VIDEO_GENERATION_JOB_EXAM_ID_JOB_DATA_KEY);
            long userId = dataMap.getLong(FRANKLYN_VIDEO_GENERATION_JOB_User_ID_JOB_DATA_KEY);
            String jobId = dataMap.getString(FRANKLYN_VIDEO_GENERATION_JOB_GROUP);

            String tempFilePath = String.format("temp-paths-%s.txt", jobId);
            String videoFilePath = String.format("video-%s.mp4", jobId);

            Log.info(tempFilePath);

            Log.info("jobId: " + jobId);
            Log.info("making video");

            Context context = VertxContext.getOrCreateDuplicatedContext(vertx);
            VertxContextSafetyToggle.setContextSafe(context, true);
            context.runOnContext(event -> {
                sf.withSession(session -> imageRepository
                                .getAllImagesByExamAndUser(examId, userId)
                                .chain(images ->
                                     Uni.createFrom().completionStage(
                                         vertx.fileSystem().writeFile(
                                             tempFilePath,
                                             Buffer.buffer(ImagesToString(images))
                                         ).toCompletionStage()
                                     )
                                )
                                .chain(ignored -> Uni.createFrom().item(
                                        String.format(
                                                "ffmpeg -y -f concat -safe 0 -i %s -c:v libx264 -pix_fmt yuv420p %s",
                                                tempFilePath, videoFilePath
                                        )
                                    )
                                )
                                .chain(command -> Uni.createFrom().completionStage(
                                        () -> CompletableFuture.supplyAsync(() -> {
                                            int exitValue = 500; // Server error status

                                            Log.info(command);

                                            try {
                                                // Use ProcessBuilder for better control
                                                ProcessBuilder builder = new ProcessBuilder(command.split(" "));
                                                builder.redirectErrorStream(true); // Merge stderr into stdout for easier handling
                                                Process process = builder.start();

                                                // Read the process output
                                                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                                                    String line;
                                                    while ((line = reader.readLine()) != null) {
                                                        System.out.println(line); // Log or process output as needed
                                                    }
                                                }

                                                // Wait for process completion
                                                exitValue = process.waitFor();
                                            } catch (Exception e) {
                                                Log.error(e);
                                            }

                                            return exitValue;
                                        })
                                ))
                                //.chain(ignored -> executeFFmpeg(tempFilePath, videoFilePath))
//                                .chain(ignored ->
//                                    Uni.createFrom().completionStage(
//                                        vertx.fileSystem().delete(
//                                                tempFilePath
//                                        ).toCompletionStage()
//                                    )
//                                )
                                .emitOn(r -> context.runOnContext(ignored -> r.run()))
                        )
                        .subscribe().with(ignored -> {});
            });

        }

        private String ImagesToString(List<Image> images) {
            return images.stream()
                    .map(image -> "file '" + image.getPath() + "'\nduration 1")
                    .collect(Collectors.joining("\n"));
        }

        /*
        private Uni<Integer> executeFFmpeg(String tempFilePath, String outputPath) {
            String ffmpegCommand = String.format(
                    "ffmpeg -y -f concat -safe 0 -i %s -c:v libx264 -pix_fmt yuv420p %s",
                    tempFilePath, outputPath
            );


            // run blocking task in uni
            return Uni.createFrom().completionStage(
                    () -> CompletableFuture.supplyAsync(() -> {
                        try {
                            Runtime r = Runtime.getRuntime();

                            Process p = r.exec(ffmpegCommand);

                            p.waitFor();

                            int exitValue = p.exitValue();

                            p.destroy();

                            return exitValue;
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
            );
        }
        */
    }
}
