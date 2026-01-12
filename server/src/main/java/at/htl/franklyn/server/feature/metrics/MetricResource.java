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

    @Inject
    ProfilingMetricsService profilingMetricsService;

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

    @GET
    @Path("/profiling")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getProfilingMetrics() {
        ProfilingMetricsDto dto = new ProfilingMetricsDto(
                // Queue status
                profilingMetricsService.getQueueSize(),
                profilingMetricsService.getActiveRequests(),
                profilingMetricsService.getTimeoutsTotal(),
                profilingMetricsService.getUploadsTotal(),
                profilingMetricsService.getUploadsPerSecond(),
                profilingMetricsService.getConnectedClients(),

                // Video jobs
                profilingMetricsService.getVideoJobsQueued(),
                profilingMetricsService.getVideoJobsActive(),

                // Image sizes
                profilingMetricsService.getAverageImageUploadSizeBytes(),
                profilingMetricsService.getAverageImageDownloadSizeBytes(),

                // Latency histograms
                ProfilingMetricsDto.HistogramDataDto.from(
                        profilingMetricsService.getScreenshotRequestLatencySnapshot()),
                ProfilingMetricsDto.HistogramDataDto.from(
                        profilingMetricsService.getImageDecodeSnapshot()),
                ProfilingMetricsDto.HistogramDataDto.from(
                        profilingMetricsService.getImageEncodeSnapshot()),
                ProfilingMetricsDto.HistogramDataDto.from(
                        profilingMetricsService.getBetaFrameMergeSnapshot()),
                ProfilingMetricsDto.HistogramDataDto.from(
                        profilingMetricsService.getImageFileReadSnapshot()),
                ProfilingMetricsDto.HistogramDataDto.from(
                        profilingMetricsService.getImageSaveTotalSnapshot()),
                ProfilingMetricsDto.HistogramDataDto.from(
                        profilingMetricsService.getWebsocketMessageSendSnapshot()),
                ProfilingMetricsDto.HistogramDataDto.from(
                        profilingMetricsService.getWebsocketPingLatencySnapshot()),
                ProfilingMetricsDto.HistogramDataDto.from(
                        profilingMetricsService.getImageDownloadSnapshot()),
                ProfilingMetricsDto.HistogramDataDto.from(
                        profilingMetricsService.getVideoFfmpegSnapshot()),
                ProfilingMetricsDto.HistogramDataDto.from(
                        profilingMetricsService.getVideoZipSnapshot()),

                // Vert.x thread pool metrics
                ProfilingMetricsDto.VertxPoolMetricsDto.from(
                        profilingMetricsService.getVertxWorkerPoolMetrics()),
                ProfilingMetricsDto.VertxPoolMetricsDto.from(
                        profilingMetricsService.getVertxEventLoopMetrics())
        );

        return Response.ok(dto).build();
    }
}
