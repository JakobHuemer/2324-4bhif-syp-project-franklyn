package at.htl.franklyn.server.feature.metrics;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for collecting detailed profiling metrics to identify performance bottlenecks.
 * These metrics are designed for debugging and dynamic analysis during operation.
 */
@ApplicationScoped
public class ProfilingMetricsService {

    @Inject
    MeterRegistry registry;

    // === Screenshot Pipeline Gauges ===
    private final AtomicInteger screenshotQueueSize = new AtomicInteger(0);
    private final AtomicInteger screenshotActiveRequests = new AtomicInteger(0);
    private final AtomicLong screenshotTimeoutsTotal = new AtomicLong(0);
    private final AtomicLong screenshotUploadsTotal = new AtomicLong(0);
    private long lastUploadCountTimestamp = System.currentTimeMillis();
    private long lastUploadCount = 0;
    private volatile double uploadsPerSecond = 0.0;

    // === WebSocket Gauges ===
    private final AtomicInteger websocketConnectedClients = new AtomicInteger(0);

    // === Image Size Tracking ===
    private final AtomicLong totalImageBytesUploaded = new AtomicLong(0);
    private final AtomicLong imageUploadCount = new AtomicLong(0);
    private final AtomicLong totalImageBytesDownloaded = new AtomicLong(0);
    private final AtomicLong imageDownloadCount = new AtomicLong(0);

    // === Video Generation Gauges ===
    private final AtomicInteger videoJobsQueued = new AtomicInteger(0);
    private final AtomicInteger videoJobsActive = new AtomicInteger(0);

    // === Timers (will be initialized in init) ===
    private Timer screenshotRequestLatencyTimer;
    private Timer imageDecodeTimer;
    private Timer imageEncodeTimer;
    private Timer betaFrameMergeTimer;
    private Timer imageFileReadTimer;
    private Timer imageSaveTotalTimer;
    private Timer websocketMessageSendTimer;
    private Timer websocketPingLatencyTimer;
    private Timer imageDownloadTimer;
    private Timer videoFfmpegTimer;
    private Timer videoZipTimer;

    // === Counters ===
    private Counter imageDownloadRequestsCounter;

    public void init(@Observes StartupEvent ev) {
        // Register gauges
        Gauge.builder("profiling.screenshot.queue_size", screenshotQueueSize, AtomicInteger::get)
                .description("Number of clients waiting in queue for screenshot request")
                .register(registry);

        Gauge.builder("profiling.screenshot.active_requests", screenshotActiveRequests, AtomicInteger::get)
                .description("Currently active concurrent screenshot requests")
                .register(registry);

        Gauge.builder("profiling.screenshot.timeouts_total", screenshotTimeoutsTotal, AtomicLong::get)
                .description("Total number of screenshot request timeouts")
                .register(registry);

        Gauge.builder("profiling.screenshot.uploads_total", screenshotUploadsTotal, AtomicLong::get)
                .description("Total number of successful screenshot uploads")
                .register(registry);

        Gauge.builder("profiling.websocket.connected_clients", websocketConnectedClients, AtomicInteger::get)
                .description("Number of currently connected WebSocket clients")
                .register(registry);

        Gauge.builder("profiling.video.jobs_queued", videoJobsQueued, AtomicInteger::get)
                .description("Number of video jobs waiting in queue")
                .register(registry);

        Gauge.builder("profiling.video.jobs_active", videoJobsActive, AtomicInteger::get)
                .description("Number of video jobs currently being processed")
                .register(registry);

        // Register timers with percentile histograms
        screenshotRequestLatencyTimer = Timer.builder("profiling.screenshot.request_latency")
                .description("Time from CAPTURE_SCREEN command to upload completion")
                .publishPercentileHistogram()
                .publishPercentiles(0.5, 0.9, 0.99)
                .register(registry);

        imageDecodeTimer = Timer.builder("profiling.image.decode_duration")
                .description("Time spent decoding uploaded image (ImageIO.read)")
                .publishPercentileHistogram()
                .publishPercentiles(0.5, 0.9, 0.99)
                .register(registry);

        imageEncodeTimer = Timer.builder("profiling.image.encode_duration")
                .description("Time spent encoding image to file (ImageIO.write)")
                .publishPercentileHistogram()
                .publishPercentiles(0.5, 0.9, 0.99)
                .register(registry);

        betaFrameMergeTimer = Timer.builder("profiling.image.beta_merge_duration")
                .description("Time spent merging beta frame onto alpha")
                .publishPercentileHistogram()
                .publishPercentiles(0.5, 0.9, 0.99)
                .register(registry);

        imageFileReadTimer = Timer.builder("profiling.image.file_read_duration")
                .description("Time spent reading alpha frame from disk")
                .publishPercentileHistogram()
                .publishPercentiles(0.5, 0.9, 0.99)
                .register(registry);

        imageSaveTotalTimer = Timer.builder("profiling.image.save_total_duration")
                .description("Total time for saveFrameOfSession operation")
                .publishPercentileHistogram()
                .publishPercentiles(0.5, 0.9, 0.99)
                .register(registry);

        websocketMessageSendTimer = Timer.builder("profiling.websocket.message_send_duration")
                .description("Time to send a WebSocket message")
                .publishPercentileHistogram()
                .publishPercentiles(0.5, 0.9, 0.99)
                .register(registry);

        websocketPingLatencyTimer = Timer.builder("profiling.websocket.ping_latency")
                .description("WebSocket ping/pong round-trip time")
                .publishPercentileHistogram()
                .publishPercentiles(0.5, 0.9, 0.99)
                .register(registry);

        imageDownloadTimer = Timer.builder("profiling.image.download_duration")
                .description("Time to serve an image download request")
                .publishPercentileHistogram()
                .publishPercentiles(0.5, 0.9, 0.99)
                .register(registry);

        videoFfmpegTimer = Timer.builder("profiling.video.ffmpeg_duration")
                .description("Time spent in ffmpeg video encoding")
                .publishPercentileHistogram()
                .publishPercentiles(0.5, 0.9, 0.99)
                .register(registry);

        videoZipTimer = Timer.builder("profiling.video.zip_duration")
                .description("Time spent creating video zip file")
                .publishPercentileHistogram()
                .publishPercentiles(0.5, 0.9, 0.99)
                .register(registry);

        imageDownloadRequestsCounter = Counter.builder("profiling.image.download_requests_total")
                .description("Total number of image download requests")
                .register(registry);
    }

    // === Screenshot Pipeline Methods ===

    public void setQueueSize(int size) {
        screenshotQueueSize.set(size);
    }

    public void setActiveRequests(int count) {
        screenshotActiveRequests.set(count);
    }

    public void incrementTimeouts() {
        screenshotTimeoutsTotal.incrementAndGet();
    }

    public void recordSuccessfulUpload() {
        screenshotUploadsTotal.incrementAndGet();
        updateUploadsPerSecond();
    }

    private synchronized void updateUploadsPerSecond() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastUploadCountTimestamp;
        if (elapsed >= 1000) { // Update every second
            long currentCount = screenshotUploadsTotal.get();
            uploadsPerSecond = (currentCount - lastUploadCount) * 1000.0 / elapsed;
            lastUploadCount = currentCount;
            lastUploadCountTimestamp = now;
        }
    }

    public double getUploadsPerSecond() {
        return uploadsPerSecond;
    }

    public Timer.Sample startScreenshotRequestTimer() {
        return Timer.start(registry);
    }

    public void stopScreenshotRequestTimer(Timer.Sample sample) {
        sample.stop(screenshotRequestLatencyTimer);
    }

    // === Image Processing Methods ===

    public Timer.Sample startImageDecodeTimer() {
        return Timer.start(registry);
    }

    public void stopImageDecodeTimer(Timer.Sample sample) {
        sample.stop(imageDecodeTimer);
    }

    public Timer.Sample startImageEncodeTimer() {
        return Timer.start(registry);
    }

    public void stopImageEncodeTimer(Timer.Sample sample) {
        sample.stop(imageEncodeTimer);
    }

    public Timer.Sample startBetaFrameMergeTimer() {
        return Timer.start(registry);
    }

    public void stopBetaFrameMergeTimer(Timer.Sample sample) {
        sample.stop(betaFrameMergeTimer);
    }

    public Timer.Sample startImageFileReadTimer() {
        return Timer.start(registry);
    }

    public void stopImageFileReadTimer(Timer.Sample sample) {
        sample.stop(imageFileReadTimer);
    }

    public Timer.Sample startImageSaveTotalTimer() {
        return Timer.start(registry);
    }

    public void stopImageSaveTotalTimer(Timer.Sample sample) {
        sample.stop(imageSaveTotalTimer);
    }

    public void recordImageUploadSize(long bytes) {
        totalImageBytesUploaded.addAndGet(bytes);
        imageUploadCount.incrementAndGet();
    }

    public double getAverageImageUploadSizeBytes() {
        long count = imageUploadCount.get();
        return count > 0 ? (double) totalImageBytesUploaded.get() / count : 0;
    }

    // === WebSocket Methods ===

    public void incrementConnectedClients() {
        websocketConnectedClients.incrementAndGet();
    }

    public void decrementConnectedClients() {
        websocketConnectedClients.decrementAndGet();
    }

    public Timer.Sample startWebsocketMessageSendTimer() {
        return Timer.start(registry);
    }

    public void stopWebsocketMessageSendTimer(Timer.Sample sample) {
        sample.stop(websocketMessageSendTimer);
    }

    public Timer.Sample startWebsocketPingTimer() {
        return Timer.start(registry);
    }

    public void stopWebsocketPingTimer(Timer.Sample sample) {
        sample.stop(websocketPingLatencyTimer);
    }

    // === Image Download Methods ===

    public Timer.Sample startImageDownloadTimer() {
        return Timer.start(registry);
    }

    public void stopImageDownloadTimer(Timer.Sample sample) {
        sample.stop(imageDownloadTimer);
        imageDownloadRequestsCounter.increment();
    }

    public void recordImageDownloadSize(long bytes) {
        totalImageBytesDownloaded.addAndGet(bytes);
        imageDownloadCount.incrementAndGet();
    }

    public double getAverageImageDownloadSizeBytes() {
        long count = imageDownloadCount.get();
        return count > 0 ? (double) totalImageBytesDownloaded.get() / count : 0;
    }

    public double getImageDownloadRequestsPerSecond() {
        // Calculate based on counter rate
        return imageDownloadRequestsCounter.count() / Math.max(1, 
            (System.currentTimeMillis() - lastUploadCountTimestamp) / 1000.0);
    }

    // === Video Generation Methods ===

    public void setVideoJobsQueued(int count) {
        videoJobsQueued.set(count);
    }

    public void incrementVideoJobsActive() {
        videoJobsActive.incrementAndGet();
    }

    public void decrementVideoJobsActive() {
        videoJobsActive.decrementAndGet();
    }

    public Timer.Sample startVideoFfmpegTimer() {
        return Timer.start(registry);
    }

    public void stopVideoFfmpegTimer(Timer.Sample sample) {
        sample.stop(videoFfmpegTimer);
    }

    public Timer.Sample startVideoZipTimer() {
        return Timer.start(registry);
    }

    public void stopVideoZipTimer(Timer.Sample sample) {
        sample.stop(videoZipTimer);
    }

    // === Snapshot Methods for API ===

    public HistogramData getScreenshotRequestLatencySnapshot() {
        return getHistogramData(screenshotRequestLatencyTimer);
    }

    public HistogramData getImageDecodeSnapshot() {
        return getHistogramData(imageDecodeTimer);
    }

    public HistogramData getImageEncodeSnapshot() {
        return getHistogramData(imageEncodeTimer);
    }

    public HistogramData getBetaFrameMergeSnapshot() {
        return getHistogramData(betaFrameMergeTimer);
    }

    public HistogramData getImageFileReadSnapshot() {
        return getHistogramData(imageFileReadTimer);
    }

    public HistogramData getImageSaveTotalSnapshot() {
        return getHistogramData(imageSaveTotalTimer);
    }

    public HistogramData getWebsocketMessageSendSnapshot() {
        return getHistogramData(websocketMessageSendTimer);
    }

    public HistogramData getWebsocketPingLatencySnapshot() {
        return getHistogramData(websocketPingLatencyTimer);
    }

    public HistogramData getImageDownloadSnapshot() {
        return getHistogramData(imageDownloadTimer);
    }

    public HistogramData getVideoFfmpegSnapshot() {
        return getHistogramData(videoFfmpegTimer);
    }

    public HistogramData getVideoZipSnapshot() {
        return getHistogramData(videoZipTimer);
    }

    private HistogramData getHistogramData(Timer timer) {
        if (timer == null) {
            return new HistogramData(0, 0, 0, 0, 0);
        }
        HistogramSnapshot snapshot = timer.takeSnapshot();
        ValueAtPercentile[] percentiles = snapshot.percentileValues();
        
        double p50 = 0, p90 = 0, p99 = 0;
        for (ValueAtPercentile vp : percentiles) {
            if (vp.percentile() == 0.5) p50 = vp.value(TimeUnit.MILLISECONDS);
            else if (vp.percentile() == 0.9) p90 = vp.value(TimeUnit.MILLISECONDS);
            else if (vp.percentile() == 0.99) p99 = vp.value(TimeUnit.MILLISECONDS);
        }
        
        return new HistogramData(
                p50,
                p90,
                p99,
                snapshot.max(TimeUnit.MILLISECONDS),
                snapshot.count()
        );
    }

    public int getQueueSize() {
        return screenshotQueueSize.get();
    }

    public int getActiveRequests() {
        return screenshotActiveRequests.get();
    }

    public long getTimeoutsTotal() {
        return screenshotTimeoutsTotal.get();
    }

    public long getUploadsTotal() {
        return screenshotUploadsTotal.get();
    }

    public int getConnectedClients() {
        return websocketConnectedClients.get();
    }

    public int getVideoJobsQueued() {
        return videoJobsQueued.get();
    }

    public int getVideoJobsActive() {
        return videoJobsActive.get();
    }

    // === Vert.x Thread Pool Metrics ===

    /**
     * Gets Vert.x worker pool metrics from Micrometer registry.
     * Returns metrics for the worker thread pool utilization.
     */
    public VertxPoolMetrics getVertxWorkerPoolMetrics() {
        return getVertxPoolMetrics("worker");
    }

    /**
     * Gets Vert.x event loop metrics from Micrometer registry.
     */
    public VertxPoolMetrics getVertxEventLoopMetrics() {
        return getVertxPoolMetrics("eventloop");
    }

    private VertxPoolMetrics getVertxPoolMetrics(String poolType) {
        double poolSize = getGaugeValue("vertx.pool.size", "pool_type", poolType);
        double inUse = getGaugeValue("vertx.pool.inUse", "pool_type", poolType);
        double ratio = getGaugeValue("vertx.pool.ratio", "pool_type", poolType);
        double queueSize = getGaugeValue("vertx.pool.queue.size", "pool_type", poolType);
        long completed = getCounterValue("vertx.pool.completed", "pool_type", poolType);

        return new VertxPoolMetrics(
                (int) poolSize,
                (int) inUse,
                ratio,
                (int) queueSize,
                completed
        );
    }

    private double getGaugeValue(String name, String tagKey, String tagValue) {
        Gauge gauge = registry.find(name).tag(tagKey, tagValue).gauge();
        return gauge != null ? gauge.value() : 0.0;
    }

    private long getCounterValue(String name, String tagKey, String tagValue) {
        Counter counter = registry.find(name).tag(tagKey, tagValue).counter();
        return counter != null ? (long) counter.count() : 0L;
    }

    /**
     * Data class for histogram percentile data
     */
    public record HistogramData(double p50, double p90, double p99, double max, long count) {}

    /**
     * Data class for Vert.x pool metrics
     */
    public record VertxPoolMetrics(
            int poolSize,
            int inUse,
            double ratio,
            int queueSize,
            long completed
    ) {}
}
