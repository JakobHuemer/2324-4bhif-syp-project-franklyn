package at.htl.franklyn.server.feature.exam;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;


@ApplicationScoped
public class ExamBackgroundJobs {
    @ConfigProperty(name = "exam.cleanup.state.maxAgeInDays", defaultValue = "1")
    int stateMaxAgeInDays;

    @ConfigProperty(name = "exam.cleanup.data.maxAgeInDays", defaultValue = "30")
    int dataMaxAgeInDays;

    @Inject
    ExamRepository examRepository;

    @Inject
    ExamService examService;

    /**
     * Exams ONGOING for more than 1 day are closed automatically by this cleanup State job.
     *
     * @return Nothing
     */
    @Scheduled(cron = "{exam.cleanup.cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @WithTransaction
    Uni<Void> cleanupExamState() {
        Context ctx = Vertx.currentContext();
        Log.info("Running exam state cleanup (autocomplete) job");
        return examRepository.findExamsOngoingFor(stateMaxAgeInDays)
                .invoke(exams -> {
                    if (!exams.isEmpty()) {
                        Log.infof("Trying to complete %d exams which have been ONGOING for more than a day.",
                                exams.size());
                    }
                })
                .chain(exams -> {
                    var result = Uni.createFrom().voidItem();

                    for (Exam e : exams) {
                        result = result.chain(ignored ->
                                examService.completeExam(e)
                                        .onItem().invoke(ignored2 -> {
                                            Log.infof("Successfully autocompleted exam '%s' (id = %d)",
                                                    e.getTitle(), e.getId());
                                        })
                                        .onFailure().recoverWithUni(err -> {
                                            Log.errorf("Could not complete exam %d. (Reason: %s)",
                                                    e.getId(),
                                                    err.getMessage());
                                            return Uni.createFrom().voidItem();
                                        }))
                                .emitOn(r -> ctx.runOnContext(ignored3 -> r.run()));
                    }

                    return result;
                })
                .emitOn(r -> ctx.runOnContext(ignored3 -> r.run()))
                .replaceWithVoid();
    }

    /**
     * Exams older than 1 month are deleted automatically by this cleanup job.
     *
     * @return Nothing
     */
    @Scheduled(cron = "{exam.cleanup.cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @WithTransaction
    Uni<Void> cleanupExamData() {
        Context ctx = Vertx.currentContext();
        Log.info("Running exam data cleanup (delete telemetry) job");
        return examRepository.findExamsOlderThan(dataMaxAgeInDays)
                .invoke(exams -> {
                    if (!exams.isEmpty()) {
                        Log.infof("Trying to delete data for %d exams which have been DONE for at least one month.",
                                exams.size());
                    }
                })
                .chain(exams -> {
                    var result = Uni.createFrom().voidItem();

                    for (Exam e : exams) {
                        result = result.chain(ignored ->
                                        examService.deleteTelemetry(e)
                                                .chain(ignored2 -> examRepository
                                                        .deleteById(e.getId())
                                                        .replaceWithVoid()
                                                        .emitOn(r -> ctx.runOnContext(ignored3 -> r.run()))
                                                )
                                                .onItem().invoke(ignored2 -> {
                                                    Log.infof("Successfully deleted exam '%s' (id = %d)",
                                                            e.getTitle(), e.getId());
                                                })
                                                .onFailure().recoverWithUni(err -> {
                                                    Log.errorf("Could not delete exam %d. (Reason: %s)",
                                                            e.getId(),
                                                            err.getMessage());
                                                    return Uni.createFrom().voidItem();
                                                }))
                                .emitOn(r -> ctx.runOnContext(ignored2 -> r.run()))
                        ;
                    }

                    return result;
                })
                .emitOn(r -> ctx.runOnContext(ignored3 -> r.run()))
                .replaceWithVoid();
    }

    /**
     * Exams which have their start/end time in the past are started automatically by job.
     *
     * @return Nothing
     */
    @Scheduled(cron = "{exam.start.cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @WithTransaction
    Uni<Void> startExams() {
        Context ctx = Vertx.currentContext();
        Log.debug("Running exam autostart job");
        return examRepository.findOverdueUnstartedExams()
                .invoke(exams -> {
                    if (!exams.isEmpty()) {
                        Log.infof("Trying to start %d exams which are scheduled to start.",
                                exams.size());
                    }
                })
                .chain(exams -> {
                    var result = Uni.createFrom().voidItem();

                    for (Exam e : exams) {
                        result = result.chain(ignored ->
                                        examService.startExam(e)
                                                .onItem().invoke(ignored2 -> {
                                                    Log.infof("Successfully started exam '%s' (id = %d)",
                                                            e.getTitle(), e.getId());
                                                })
                                                .onFailure().recoverWithUni(err -> {
                                                    Log.errorf("Could start delete exam %d. (Reason: %s)",
                                                            e.getId(),
                                                            err.getMessage());
                                                    return Uni.createFrom().voidItem();
                                                }))
                                .emitOn(r -> ctx.runOnContext(ignored2 -> r.run()))
                        ;
                    }

                    return result;
                })
                .emitOn(r -> ctx.runOnContext(ignored3 -> r.run()))
                .replaceWithVoid();
    }
}
