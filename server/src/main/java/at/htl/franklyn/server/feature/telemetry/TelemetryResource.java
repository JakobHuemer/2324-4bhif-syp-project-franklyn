package at.htl.franklyn.server.feature.telemetry;

import at.htl.franklyn.server.common.ExceptionFilter;
import at.htl.franklyn.server.feature.telemetry.image.FrameType;
import at.htl.franklyn.server.feature.telemetry.image.ImageService;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.RestForm;

import java.io.InputStream;
import java.util.UUID;

@Path("/telemetry")
public class TelemetryResource {
    @Inject
    ImageService imageService;

    @POST
    @Path("/by-session/{sessionId}/screen/upload/alpha")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @WithTransaction
    public Uni<Response> saveAlphaFrame(
            @PathParam("sessionId") String sessionId,
            @RestForm("image") @PartType(MediaType.APPLICATION_OCTET_STREAM) InputStream alphaFrame
    ) {
        return Uni.createFrom()
                .item(sessionId)
                .onItem().transform(UUID::fromString)
                .onFailure(ExceptionFilter.NO_WEBAPP).transform(e -> new WebApplicationException(
                        "invalid sessionId / participationId", Response.Status.BAD_REQUEST
                ))
                .chain(session -> imageService.saveFrameOfSession(session, alphaFrame, FrameType.ALPHA))
                .onFailure(ExceptionFilter.NO_WEBAPP).transform(e -> {
                    Log.warnf("Could not save frame of %s (Reason: %s)", sessionId, e.getMessage());
                    return new WebApplicationException(
                            "Unable to save frame", Response.Status.BAD_REQUEST
                    );
                })
                .onItem().transform(v -> Response.ok().build());
    }

    @POST
    @Path("/by-session/{sessionId}/screen/upload/beta")
    @WithTransaction
    public Uni<Response> saveBetaFrame(
            @PathParam("sessionId") String sessionId,
            @RestForm("image") @PartType(MediaType.APPLICATION_OCTET_STREAM) InputStream betaFrame
    ) {
        return Uni.createFrom()
                .item(sessionId)
                .onItem().transform(UUID::fromString)
                .onFailure(ExceptionFilter.NO_WEBAPP).transform(e -> new WebApplicationException(
                        "invalid sessionId / participationId", Response.Status.BAD_REQUEST
                ))
                .chain(session -> imageService.saveFrameOfSession(session, betaFrame, FrameType.BETA))
                .onFailure(ExceptionFilter.NO_WEBAPP).transform(e -> {
                    Log.warnf("Could not save frame of %s (Reason: %s)", sessionId, e.getMessage());
                    return new WebApplicationException(
                            "Unable to save frame", Response.Status.BAD_REQUEST
                    );
                })
                .onItem().transform(v -> Response.ok().build());
    }

    @GET
    @Path("/by-user/{userId}/{examId}/screen/download")
    @Produces("image/png") // Hardcoded since MediaType enum does not have image/png
    @WithSession
    public Uni<Response> downloadFrame(
            @PathParam("userId") Long userId,
            @PathParam("examId") Long examId
    ) {
        return Uni.createFrom()
                .item(userId)
                .onItem().ifNull().failWith(
                    new WebApplicationException(
                        "Missing userId",
                        Response.Status.BAD_REQUEST
                    )
                )
                .replaceWith(examId)
                .onItem().ifNull().failWith(
                        new WebApplicationException(
                                "Missing examId",
                                Response.Status.BAD_REQUEST
                        )
                )
                .chain(ignored -> imageService.loadLatestFrameOfUser(userId, examId))
                .onItem().transform(buf -> Response.ok(buf).build())
                .onFailure(IllegalStateException.class).transform(e -> new WebApplicationException(
                    "No available screenshot found",
                    Response.Status.NOT_FOUND
                ))
                .onFailure(ExceptionFilter.NO_WEBAPP).transform(e -> {
                    Log.warnf("Could not load screenshot for user: %d | exam: %d", userId, examId);
                    return new WebApplicationException(
                            "Could not load screenshot for user",
                            Response.Status.BAD_REQUEST
                    );
                });
    }
}
