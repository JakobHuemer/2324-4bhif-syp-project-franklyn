package at.htl.franklyn.server.feature.exam;

import at.htl.franklyn.server.common.ExceptionFilter;
import at.htl.franklyn.server.feature.examinee.ExamineeDto;
import at.htl.franklyn.server.feature.examinee.ExamineeService;
import at.htl.franklyn.server.feature.telemetry.participation.ParticipationService;
import at.htl.franklyn.server.feature.telemetry.video.VideoJobDto;
import at.htl.franklyn.server.feature.telemetry.video.VideoJobRepository;
import at.htl.franklyn.server.feature.telemetry.video.VideoJobType;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.vertx.core.ContextAwareScheduler;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.net.URI;

@Path("/exams")
public class ExamResource {
    @Inject
    ExamService examService;

    @Inject
    ExamineeService examineeService;

    @Inject
    ExamRepository examRepository;

    @Inject
    ParticipationService participationService;

    @Inject
    VideoJobRepository videoJobRepository;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTransaction
    public Uni<Response> createExam(@Valid ExamDto examDto, @Context UriInfo uriInfo) {
        return examService.createExam(examDto)
                .onItem().transformToUni(exam -> examService.transformToDto(exam))
                .onItem().transform(e -> {
                    URI uri = uriInfo
                            .getAbsolutePathBuilder()
                            .path(Long.toString(e.id()))
                            .build();

                    return Response
                            .created(uri)
                            .entity(e)
                            .build();
                });
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{id}")
    @WithSession
    public Uni<Response> getExamById(@PathParam("id") long id) {
        return examRepository.findById(id)
                .onItem().ifNull().failWith(
                        new WebApplicationException(
                                String.format("No exam with the id %d could be found!", id),
                                Response.Status.NOT_FOUND
                        )
                )
                .onItem().ifNotNull().transformToUni(exam -> examService.transformToDto(exam))
                .onItem().transform(examInfoDto -> Response.ok(examInfoDto).build())
                .onFailure(ExceptionFilter.NO_WEBAPP)
                .transform(e -> {
                    Log.errorf("Failed to fetch student count (Reason: %s)", e.getMessage());
                    return new WebApplicationException("Internal server error", Response.Status.INTERNAL_SERVER_ERROR);
                });
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @WithSession
    public Uni<Response> getAllExams() {
        return examRepository.listAllWithExamineeCounts()
                .onItem().transform(exams -> Response.ok(exams).build())
                .onFailure(ExceptionFilter.NO_WEBAPP)
                .transform(e -> {
                    Log.errorf("Failed to fetch student count (Reason: %s)", e.getMessage());
                    return new WebApplicationException("Internal server error", Response.Status.INTERNAL_SERVER_ERROR);
                });
    }

    @DELETE
    @Path("{id}")
    @WithTransaction
    public Uni<Response> deleteExamById(@PathParam("id") long id) {
        return examRepository
                .findById(id)
                .onItem().ifNull().failWith(
                        new WebApplicationException(
                                String.format("No exam with the id %d could be found!", id),
                                Response.Status.NOT_FOUND
                        )
                )
                .onItem().transformToUni(e -> examService.deleteTelemetry(e).replaceWith(e))
                .onFailure(ExceptionFilter.NO_WEBAPP)
                .transform(e -> {
                    Log.warnf("Could not delete Telemetry of exam %d. (Reason: %s)", id, e.getMessage());
                    return new WebApplicationException("Unable to delete exam telemetry.", Response.Status.BAD_REQUEST);
                })
                .onItem().transformToUni(e -> examRepository.deleteById(id))
                .onFailure(ExceptionFilter.NO_WEBAPP).transform(e -> {
                    Log.warnf("Could not delete exam %d. (Reason: %s)", id, e.getMessage());
                    return new WebApplicationException("Unable to delete exam.", Response.Status.BAD_REQUEST);
                })
                .onItem().transform(v -> Response.noContent().build());
    }

    @GET
    @Path("{id}/examinees")
    @WithSession
    public Uni<Response> getExamineesOfExam(@PathParam("id") long id) {
        return examService.exists(id)
                .onItem().transform(exists -> exists ? id : null)
                .onItem().ifNull().failWith(
                        new WebApplicationException(
                                String.format("No exam with the id %d could be found!", id),
                                Response.Status.NOT_FOUND
                        )
                )
                .onItem().transformToUni(ignored -> examService.getExamineesOfExam(id))
                .onItem().transform(exam -> Response.ok(exam).build())
                .onFailure(ExceptionFilter.NO_WEBAPP).transform(e -> {
                    Log.errorf("Could not get examinees of exam %d. (Reason: %s)", id, e.getMessage());
                    return new WebApplicationException("Unable to get examinees", Response.Status.INTERNAL_SERVER_ERROR);
                });
    }

    @GET
    @Path("{id}/videojobs")
    @WithSession
    public Uni<Response> getVideoJobsOfExam(@PathParam("id") long id) {
        return examService.exists(id)
                .onItem().transform(exists -> exists ? id : null)
                .onItem().ifNull().failWith(
                        new WebApplicationException(
                                String.format("No exam with the id %d could be found!", id),
                                Response.Status.NOT_FOUND
                        )
                )
                .onItem().transformToUni(ignored -> videoJobRepository.getVideoJobsOfExam(id))
                .onItem().transform(vjs -> vjs
                        .stream()
                        .map(job -> new VideoJobDto(
                                        job.getId(),
                                        job.getState(),
                                        job.getExam().getId(),
                                        job.getType() == VideoJobType.SINGLE ? job.getExaminee().getId() : null,
                                        job.getCreatedAt(),
                                        job.getFinishedAt(),
                                        job.getErrorMessage()
                                )
                        )
                        .toList()
                )
                .onItem().transform(videoJobs -> Response.ok(videoJobs).build())
                .onFailure(ExceptionFilter.NO_WEBAPP).transform(e -> {
                    Log.errorf("Could not get video jobs of exam %d. (Reason: %s)", id, e.getMessage());
                    return new WebApplicationException("Unable to get video jobs", Response.Status.INTERNAL_SERVER_ERROR);
                });
    }

    @POST
    @WithTransaction
    @Path("/join/{pin}")
    public Uni<Response> joinExam(@PathParam("pin") int pin, @Valid ExamineeDto examineeDto, @Context UriInfo uriInfo) {
        return Uni.createFrom()
                .item(examineeDto)
                .onItem().ifNull().failWith(
                        new WebApplicationException(
                                "Missing examinee body",
                                Response.Status.BAD_REQUEST
                        )
                )
                .chain(ignored -> examService.isValidPIN(pin))
                .onItem().transform(valid -> valid ? true : null)
                .onItem().ifNull().failWith(
                        new WebApplicationException(
                                "Invalid pin",
                                Response.Status.BAD_REQUEST
                        )
                )
                .onItem().ifNotNull()
                .transformToUni(ignored -> examineeService
                        .getOrCreateExaminee(examineeDto.firstname(), examineeDto.lastname()))
                .onFailure(ExceptionFilter.NO_WEBAPP).transform(e -> {
                    Log.errorf("Could not get or create examinee %s. (Reason: %s)", examineeDto, e.getMessage());
                    return new WebApplicationException(
                            "Can not register for exam", Response.Status.INTERNAL_SERVER_ERROR
                    );
                })
                .chain(examinee -> examService.findByPIN(pin)
                        .chain(exam -> participationService.getOrCreateParticipation(examinee, exam))
                )
                .onFailure(ExceptionFilter.NO_WEBAPP).transform(e -> {
                    Log.errorf("Could not get or create participation for %s. (Reason: %s)", examineeDto, e.getMessage());
                    return new WebApplicationException(
                            "Can not register for exam", Response.Status.INTERNAL_SERVER_ERROR
                    );
                })
                .onItem().transform(p -> {
                    URI uri = uriInfo
                            .getBaseUriBuilder()
                            .path("/connect/")
                            .path(p.getId().toString())
                            .build();

                    return Response
                            .created(uri)
                            .build();
                });
    }

    @POST
    @WithTransaction
    @Path("/{id}/start")
    public Uni<Response> startExam(@PathParam("id") long id) {
        return examRepository.findById(id)
                .onItem().ifNull().failWith(
                        new WebApplicationException(
                                String.format("No exam with the id %d could be found!", id),
                                Response.Status.NOT_FOUND
                        )
                )
                .chain(e -> examService.startExam(e))
                .onFailure(ExceptionFilter.NO_WEBAPP).transform(e -> {
                    Log.errorf("Could not start exam %d (Reason: %s)", id, e.getMessage());
                    return new WebApplicationException(
                            "Could not start exam", Response.Status.INTERNAL_SERVER_ERROR
                    );
                })
                .onItem().transform(t -> Response.ok().build());
    }

    @POST
    @Path("/{id}/complete")
    @WithTransaction
    public Uni<Response> completeExam(@PathParam("id") long id) {
        // This is the default scheduled executor of Mutiny:
        var delegate = Infrastructure.getDefaultWorkerPool();
        // This makes sure we re-use the correct Vert.x duplicated context to please hibernate
        var scheduler = ContextAwareScheduler.delegatingTo(delegate).withCurrentContext();

        return examRepository.findById(id)
                .onItem().ifNull().failWith(
                        new WebApplicationException(
                                String.format("No exam with the id %d could be found!", id),
                                Response.Status.NOT_FOUND
                        )
                )
                .chain(e -> examService.completeExam(e))
                .onFailure(ExceptionFilter.NO_WEBAPP).transform(e -> {
                    Log.errorf("Could not complete exam %d (Reason: %s)", id, e.getMessage());
                    return new WebApplicationException(
                            "Could not start exam", Response.Status.INTERNAL_SERVER_ERROR
                    );
                })
                .onItem()
                .transform(x -> Response.ok().build())
                .emitOn(scheduler);
    }

    @DELETE
    @WithTransaction
    @Path("/{id}/telemetry")
    public Uni<Response> deleteTelemetryOfExam(@PathParam("id") long id) {
        return examRepository
                .findById(id)
                .onItem().ifNull().failWith(
                        new WebApplicationException(
                                String.format("No exam with the id %d could be found!", id),
                                Response.Status.NOT_FOUND
                        )
                )
                .chain(e -> examService.deleteTelemetry(e))
                .onFailure(ExceptionFilter.NO_WEBAPP).transform(e -> {
                    Log.errorf("Could not delete telemetry of exam %d (Reason: %s)", id, e.getMessage());
                    return new WebApplicationException(
                            "Could not delete exam telemetry", Response.Status.INTERNAL_SERVER_ERROR
                    );
                })
                .onItem().transform(v -> Response.noContent().build());
    }
}
