package at.htl.franklyn.server.feature.metrics;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/metrics")
public class MetricResource {
    @Inject
    MetricsService metricsService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSystemMetrics() {
        ServerMetricsDto serverMetricsDto = new ServerMetricsDto(
                metricsService.getSystemCpuUsagePercentage(),
                metricsService.getTotalDiskSpaceInBytes(),
                metricsService.getFreeDiskSpaceInBytes(),
                metricsService.getScreenshotsFolderSizeInBytes(),
                metricsService.getVideosFolderSizeInBytes(),
                metricsService.getTotalMemoryInBytes(),
                metricsService.getUsedMemoryInBytes()
        );

        return Response.ok(serverMetricsDto).build();
    }
}
