package at.htl.franklyn.server.feature.exam;

import at.htl.franklyn.server.common.Limits;
import at.htl.franklyn.server.feature.examinee.ExamineeDto;
import at.htl.franklyn.server.feature.telemetry.ScreenshotRequestManager;
import at.htl.franklyn.server.feature.telemetry.command.ExamineeCommandSocket;
import at.htl.franklyn.server.feature.telemetry.connection.ConnectionStateRepository;
import at.htl.franklyn.server.feature.telemetry.image.ImageService;
import at.htl.franklyn.server.feature.telemetry.participation.Participation;
import at.htl.franklyn.server.feature.telemetry.participation.ParticipationRepository;
import at.htl.franklyn.server.feature.telemetry.video.VideoJobRepository;
import at.htl.franklyn.server.feature.telemetry.video.VideoJobService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@ApplicationScoped
public class ExamService {
    @Inject
    ExamRepository examRepository;

    @Inject
    ParticipationRepository participationRepository;

    @Inject
    ConnectionStateRepository connectionStateRepository;

    @Inject
    ImageService imageService;

    @Inject
    VideoJobService videoJobService;

    @Inject
    VideoJobRepository videoJobRepository;

    @Inject
    ExamineeCommandSocket commandSocket;

    @Inject
    ScreenshotRequestManager screenshotRequestManager;

    /**
     * Creates a new Exam from a Dto.
     * This business logic method assigns additional parameters which are not included in the dto.
     * e.g. The pin and state parameters.
     * Finally, The Exam is persisted and returned to the caller.
     * @param examDto exam dto from which a new exam is built
     * @return the newly created exam
     */
    public Uni<Exam> createExam(ExamDto examDto) {
        return getFreePIN()
                .onItem().transform(p -> {
                    Exam exam = new Exam();
                    exam.setTitle(examDto.title());
                    exam.setPlannedStart(examDto.start());
                    exam.setPlannedEnd(examDto.end());
                    exam.setScreencaptureInterval(examDto.screencaptureIntervalSeconds());

                    // Initial exam state is always created
                    exam.setState(ExamState.CREATED);
                    exam.setPin(p); // TODO: Fix potential Race condition
                    return exam;
                })
                .chain(examRepository::persist);

    }

    /**
     * Returns wether or not an exam with the given id exists
     * @param id exam id to check
     * @return true - when an exam with the given id exists otherwise false
     */
    public Uni<Boolean> exists(long id) {
        return examRepository.count("from Exam where id = ?1", id)
                .onItem().transform(c -> c != 0);
    }

    /**
     * Queries a list of examinees for an exam with the given id
     * @param id exam id for which to query students for
     * @return list of examinee DTOs holding most of the relevant data of an exminee needed during the exam
     */
    public Uni<List<ExamineeDto>> getExamineesOfExam(long id) {
        return examRepository.getExamineesOfExamWithConnectionState(id);
    }

    /**
     * Logically starts an exam. This includes starting telemetry collection and setting the state to ongoing
     * @param e exam to be started
     * @return boolean indication whether the exam could be started or not
     */
    public Uni<Void> startExam(Exam e) {
        if (e.getState() != ExamState.CREATED) {
            return Uni.createFrom().failure(new IllegalStateException("Invalid exam state for startExam"));
        }

        return examRepository
                .update("state = ?1, actualStart = ?2 where id = ?3",
                        ExamState.ONGOING,
                        LocalDateTime.now(),
                        e.getId())
                .chain(ignored -> participationRepository.getParticipationsOfExam(e))
                // make sure examinees are not immediately disconnected, when the exam starts and they are already connected to the socket
                .call(participations -> connectionStateRepository
                        .changeConnectionStatesOfMany(participations, true))
                .replaceWithVoid();
    }

    /**
     * Logically completes an exam. This includes stopping telemetry collection, disconnecting openbox clients
     * and setting the state to done
     * @param e exam to be completed
     * @return boolean indicating whether the exam could be completed successfully
     */
    public Uni<Void> completeExam(Exam e) {
        if (e.getState() != ExamState.ONGOING) {
            return Uni.createFrom().failure(new IllegalStateException("Invalid exam state for completeExam"));
        }

        return examRepository
                .update("state = ?1, actualEnd = ?2 where id = ?3",
                        ExamState.DONE,
                        LocalDateTime.now(),
                        e.getId())
                .chain(ignored -> participationRepository.getParticipationsOfExam(e))
                .call(participations -> {
                    List<UUID> pIds = participations.stream().map(Participation::getId).toList();
                    return commandSocket.broadcastDisconnect(pIds);
                })
                .call(participations -> connectionStateRepository
                        .changeConnectionStatesOfMany(participations, false))
                .replaceWithVoid();
    }

    /**
     * Deletes all telemetry stored for the given exam.
     * This effectively deletes all participations associated with an exam
     * and in turn all data associated with a participation (Images, ConnectionState, ...)
     * @param e Exam to delete telemetry for
     * @return Uni<Void> or Exception on failure to delete
     */
    public Uni<Void> deleteTelemetry(Exam e) {
        return videoJobRepository.getVideoJobsOfExam(e.getId())
                .onItem().transformToUni(vjs -> {
                    // make sure db insertion happens sequentially. for more information see here:
                    // https://github.com/hibernate/hibernate-reactive/issues/1607
                    Uni<Void> result = Uni.createFrom().voidItem();

                    for (var videoJob : vjs) {
                        result = result.call(ignored ->
                                videoJobService.deleteVideoJob(videoJob.getId())
                                        .onFailure().recoverWithNull()
                        );
                    }

                    return result;
                })
                .chain(ignored -> participationRepository.getParticipationsOfExam(e))
                .chain(p -> {
                    // make sure db insertion happens sequentially. for more information see here:
                    // https://github.com/hibernate/hibernate-reactive/issues/1607
                    Uni<Void> result = Uni.createFrom().voidItem();

                    for (Participation participation : p) {
                        result = result.call(ignored ->
                                connectionStateRepository.deleteStatesOfParticipation(participation)
                                        .call(v -> imageService.deleteAllFramesOfParticipation(participation))
                                        .call(v -> participationRepository.delete(participation))
                                        .onFailure().recoverWithNull()
                        );
                    }

                    return result;
                })
                .chain(ignored -> examRepository.update("state = ?1 where id = ?2",
                        ExamState.DELETED,
                        e.getId())
                )
                .replaceWithVoid();
    }

    /**
     * Checks whether the given pin belongs to an active exam, and thus is valid, or not
     * @param pin pin to check
     * @return true - pin is valid (belongs to an active exam) otherwise false
     */
    public Uni<Boolean> isValidPIN(int pin) {
        return examRepository.count("from Exam e where e.actualEnd is null and pin = ?1", pin)
                .onItem().transform(c -> c != 0);
    }

    /**
     * Returns the active exam with the given pin
     * @param pin pin to search for
     * @return the exam with the given pin
     */
    public Uni<Exam> findByPIN(int pin) {
        return examRepository
                .find("from Exam e where e.actualEnd is null and pin = ?1", pin)
                .firstResult();
    }

    public Uni<ExamInfoDto> transformToDto(Exam exam) {
        return participationRepository.getParticipationCountOfExam(exam.getId())
                .onItem().transform(examineeCount -> new ExamInfoDto(
                        exam.getId(),
                        exam.getPlannedStart(),
                        exam.getPlannedEnd(),
                        exam.getActualStart(),
                        exam.getActualEnd(),
                        exam.getTitle(),
                        String.format("%03d", exam.getPin()),
                        exam.getState(),
                        exam.getScreencaptureInterval(),
                        examineeCount
                ));
    }

    /**
     * Generates a new random PIN to be used for a new exam.
     * This function can theoretically loop endlessly if 1000 Exams are active at once.
     * @return a free PIN
     */
    private Uni<Integer> getFreePIN() {
        Random rnd = new Random();
        return examRepository.getPINsInUse()
                .onItem().transform(takenPINs -> {
                    int pin;
                    do {
                        pin = rnd.nextInt(Limits.EXAM_PIN_MIN_VALUE, Limits.EXAM_PIN_MAX_VALUE + 1);
                    } while(takenPINs.contains(pin));

                    return pin;
                });
    }
}
