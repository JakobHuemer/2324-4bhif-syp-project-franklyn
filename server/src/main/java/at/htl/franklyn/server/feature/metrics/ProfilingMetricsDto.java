package at.htl.franklyn.server.feature.metrics;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for exposing profiling metrics via REST API.
 * Contains all performance metrics needed for dynamic bottleneck analysis.
 */
public record ProfilingMetricsDto(
        // === Queue Status ===
        @JsonProperty("queue_size")
        int queueSize,

        @JsonProperty("active_requests")
        int activeRequests,

        @JsonProperty("timeouts_total")
        long timeoutsTotal,

        @JsonProperty("uploads_total")
        long uploadsTotal,

        @JsonProperty("uploads_per_second")
        double uploadsPerSecond,

        @JsonProperty("connected_clients")
        int connectedClients,

        // === Video Jobs ===
        @JsonProperty("video_jobs_queued")
        int videoJobsQueued,

        @JsonProperty("video_jobs_active")
        int videoJobsActive,

        // === Image Sizes ===
        @JsonProperty("average_image_upload_size_bytes")
        double averageImageUploadSizeBytes,

        @JsonProperty("average_image_download_size_bytes")
        double averageImageDownloadSizeBytes,

        // === Latency Histograms ===
        @JsonProperty("screenshot_request_latency")
        HistogramDataDto screenshotRequestLatency,

        @JsonProperty("image_decode")
        HistogramDataDto imageDecode,

        @JsonProperty("image_encode")
        HistogramDataDto imageEncode,

        @JsonProperty("beta_frame_merge")
        HistogramDataDto betaFrameMerge,

        @JsonProperty("image_file_read")
        HistogramDataDto imageFileRead,

        @JsonProperty("image_save_total")
        HistogramDataDto imageSaveTotal,

        @JsonProperty("websocket_message_send")
        HistogramDataDto websocketMessageSend,

        @JsonProperty("websocket_ping_latency")
        HistogramDataDto websocketPingLatency,

        @JsonProperty("image_download")
        HistogramDataDto imageDownload,

        @JsonProperty("video_ffmpeg")
        HistogramDataDto videoFfmpeg,

        @JsonProperty("video_zip")
        HistogramDataDto videoZip,

        // === Vert.x Thread Pool Metrics ===
        @JsonProperty("vertx_worker_pool")
        VertxPoolMetricsDto vertxWorkerPool,

        @JsonProperty("vertx_event_loop")
        VertxPoolMetricsDto vertxEventLoop
) {

    /**
     * DTO for histogram percentile data (all values in milliseconds)
     */
    public record HistogramDataDto(
            @JsonProperty("p50_ms")
            double p50Ms,

            @JsonProperty("p90_ms")
            double p90Ms,

            @JsonProperty("p99_ms")
            double p99Ms,

            @JsonProperty("max_ms")
            double maxMs,

            @JsonProperty("count")
            long count
    ) {
        public static HistogramDataDto from(ProfilingMetricsService.HistogramData data) {
            return new HistogramDataDto(
                    data.p50(),
                    data.p90(),
                    data.p99(),
                    data.max(),
                    data.count()
            );
        }
    }

    /**
     * DTO for Vert.x thread pool metrics
     */
    public record VertxPoolMetricsDto(
            @JsonProperty("pool_size")
            int poolSize,

            @JsonProperty("in_use")
            int inUse,

            @JsonProperty("ratio")
            double ratio,

            @JsonProperty("queue_size")
            int queueSize,

            @JsonProperty("completed")
            long completed
    ) {
        public static VertxPoolMetricsDto from(ProfilingMetricsService.VertxPoolMetrics data) {
            return new VertxPoolMetricsDto(
                    data.poolSize(),
                    data.inUse(),
                    data.ratio(),
                    data.queueSize(),
                    data.completed()
            );
        }
    }
}
