package at.htl.franklyn.server.feature.exam;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;


@ApplicationScoped
public class ExamCleanupSchedule {
    @ConfigProperty(name = "exam.cleanup.state.maxAgeInDays", defaultValue = "1")
    int maxAgeInDays;

    @Inject
    ExamRepository examRepository;

    @Inject
    ExamService examService;

    /**
     * Exams ONGOING for more than 1 day are closed automatically by this cleanup State job.
     * @return Nothing
     */
    @Scheduled(cron= "{exam.cleanup.state.cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @WithTransaction
    Uni<Void> cleanupExamState() {
        Log.info("Running exam state cleanup (autocomplete) job");
        return examRepository.findExamsOngoingFor(maxAgeInDays)
                .invoke(exams -> {
                    if (!exams.isEmpty()) {
                        Log.infof("Trying to complete %d exams which have been ONGOING for more than a day.",
                                exams.size());
                    }
                })
                .toMulti()
                .flatMap(exams -> Multi.createFrom().iterable(exams))
                .onItem().transformToUniAndConcatenate(exam ->
                        examService.completeExam(exam)
                                .onItem().invoke(ignored -> {
                                    Log.infof("Successfully autocompleted exam '%s' (id = %d)",
                                            exam.getTitle(), exam.getId());
                                })
                                .onFailure().recoverWithUni(e -> {
                                    Log.errorf("Could not complete exam %d. (Reason: %s)",
                                            exam.getId(),
                                            e.getMessage());
                                    return Uni.createFrom().voidItem();
                                })
                )
                .toUni();
    }
}
