package at.htl.franklyn.server.services;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.system.DiskSpaceMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class MetricsService {
    private final MeterRegistry registry;
    private final MemoryMXBean memoryMXBean;
    private final File screenshotsFolder;

    private final Timer alphaUploadTimer;
    private final Counter alphaUploadBytes;
    private final Counter alphaUploadErrors;

    private final Timer betaUploadTimer;
    private final Counter betaUploadBytes;
    private final Counter betaUploadErrors;
    private final Counter betaNonZeroPixelCounter;

    private final Timer screenshotFetchTimer;
    private final Timer screenshotScaledFetchTimer;
    private final Counter screenshotFetchErrors;

    private final Timer videoDownloadTimer;
    private final Counter videoDownloadErrors;

    private final Counter requestedAlphaFrames;

    MetricsService(MeterRegistry registry, @ConfigProperty(name = "screenshots.path") String screenshotsDirPath) {
        this.registry = registry;
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.screenshotsFolder = new File(screenshotsDirPath);

        // Setup Micrometer Metrics
        new ProcessorMetrics().bindTo(registry);
        new DiskSpaceMetrics(new File(screenshotsDirPath)).bindTo(registry);

        this.alphaUploadTimer = registry.timer("upload.alpha.duration");
        this.alphaUploadBytes = registry.counter("upload.alpha.bytes");
        this.alphaUploadErrors = registry.counter("upload.alpha.errors");

        this.betaUploadTimer = registry.timer("upload.beta.duration");
        this.betaUploadBytes = registry.counter("upload.beta.bytes");
        this.betaUploadErrors = registry.counter("upload.beta.errors");
        this.betaNonZeroPixelCounter = registry.counter("upload.beta.changed.pixels");

        this.screenshotFetchTimer = registry.timer("patrol.screenshot.fetch.duration");
        this.screenshotScaledFetchTimer = registry.timer("patrol.screenshot.scaled.duration");
        this.screenshotFetchErrors = registry.counter("patrol.screenshot.errors");

        this.videoDownloadTimer = registry.timer("patrol.video.download.duration");
        this.videoDownloadErrors = registry.counter("patrol.video.download.errors");

        this.requestedAlphaFrames = registry.counter("upload.beta.alpha.requests");
    }

    public Timer.Sample startAlphaUploadTimer() {
        return Timer.start(registry);
    }

    public void recordAlphaUpload(Timer.Sample sample, long bytes) {
        alphaUploadBytes.increment(bytes);
        sample.stop(alphaUploadTimer);
    }

    public void recordAlphaUploadError(Timer.Sample sample) {
        if (sample != null) {
            sample.stop(alphaUploadTimer);
        }
        alphaUploadErrors.increment();
    }

    public Timer.Sample startBetaUploadTimer() {
        return Timer.start(registry);
    }

    public void recordBetaUpload(Timer.Sample sample, long bytes, long changedPixels) {
        betaUploadBytes.increment(bytes);
        betaNonZeroPixelCounter.increment(changedPixels);
        sample.stop(betaUploadTimer);
    }

    public void recordBetaUploadError(Timer.Sample sample) {
        if (sample != null) {
            sample.stop(betaUploadTimer);
        }
        betaUploadErrors.increment();
    }

    public Timer.Sample startScreenshotFetchTimer() {
        return Timer.start(registry);
    }

    public void recordScreenshotFetch(Timer.Sample sample) {
        sample.stop(screenshotFetchTimer);
    }

    public Timer.Sample startScreenshotScaledFetchTimer() {
        return Timer.start(registry);
    }

    public void recordScreenshotScaledFetch(Timer.Sample sample) {
        sample.stop(screenshotScaledFetchTimer);
    }

    public void recordScreenshotError(Timer.Sample sample) {
        if (sample != null) {
            sample.stop(screenshotFetchTimer);
        }
        screenshotFetchErrors.increment();
    }

    public Timer.Sample startVideoDownloadTimer() {
        return Timer.start(registry);
    }

    public void recordVideoDownload(Timer.Sample sample) {
        sample.stop(videoDownloadTimer);
    }

    public void recordVideoDownloadError(Timer.Sample sample) {
        if (sample != null) {
            sample.stop(videoDownloadTimer);
        }
        videoDownloadErrors.increment();
    }

    public void recordAlphaFrameRequestedFromClient() {
        requestedAlphaFrames.increment();
    }

    public double getSystemCpuUsagePercentage() {
        return registry.get("system.cpu.usage").gauge().value() * 100.0;
    }

    public double getTotalDiskSpaceInBytes() {
        return registry.get("disk.total").gauge().value();
    }

    public double getFreeDiskSpaceInBytes() {
        return registry.get("disk.free").gauge().value();
    }

    public long getScreenshotsFolderSizeInBytes() {
        return getFolderSize(screenshotsFolder);
    }

    public long getScreenshotsFolderFileCount() {
        return getFolderFileCount(screenshotsFolder);
    }

    public long getTotalMemoryInBytes() {
        return memoryMXBean.getHeapMemoryUsage().getMax();
    }

    public long getUsedMemoryInBytes() {
        return memoryMXBean.getHeapMemoryUsage().getUsed();
    }

    public long getAlphaUploadsCount() {
        return alphaUploadTimer.count();
    }

    public long getAlphaUploadErrors() {
        return (long) alphaUploadErrors.count();
    }

    public double getAlphaUploadAvgDurationMs() {
        double mean = alphaUploadTimer.mean(TimeUnit.MILLISECONDS);
        return Double.isNaN(mean) ? 0 : mean;
    }

    public double getAlphaUploadBytesTotal() {
        return alphaUploadBytes.count();
    }

    public long getBetaUploadsCount() {
        return betaUploadTimer.count();
    }

    public long getBetaUploadErrors() {
        return (long) betaUploadErrors.count();
    }

    public double getBetaUploadAvgDurationMs() {
        double mean = betaUploadTimer.mean(TimeUnit.MILLISECONDS);
        return Double.isNaN(mean) ? 0 : mean;
    }

    public double getBetaUploadBytesTotal() {
        return betaUploadBytes.count();
    }

    public double getBetaChangedPixelsTotal() {
        return betaNonZeroPixelCounter.count();
    }

    public long getScreenshotFetchCount() {
        return screenshotFetchTimer.count();
    }

    public double getScreenshotFetchAvgMs() {
        double mean = screenshotFetchTimer.mean(TimeUnit.MILLISECONDS);
        return Double.isNaN(mean) ? 0 : mean;
    }

    public long getScreenshotScaledFetchCount() {
        return screenshotScaledFetchTimer.count();
    }

    public double getScreenshotScaledFetchAvgMs() {
        double mean = screenshotScaledFetchTimer.mean(TimeUnit.MILLISECONDS);
        return Double.isNaN(mean) ? 0 : mean;
    }

    public long getScreenshotErrors() {
        return (long) screenshotFetchErrors.count();
    }

    public long getVideoDownloadsCount() {
        return videoDownloadTimer.count();
    }

    public double getVideoDownloadAvgMs() {
        double mean = videoDownloadTimer.mean(TimeUnit.MILLISECONDS);
        return Double.isNaN(mean) ? 0 : mean;
    }

    public long getVideoDownloadErrors() {
        return (long) videoDownloadErrors.count();
    }

    public long getRequestedAlphaFrames() {
        return (long) requestedAlphaFrames.count();
    }

    public long getFolderFileCount(File folder) {
        File[] children = folder.listFiles();
        if (children == null) {
            return 0;
        }

        long count = 0;
        for (File child : children) {
            if (child.isDirectory()) {
                count += getFolderFileCount(child);
            } else {
                count++;
            }
        }
        return count;
    }

    private long getFolderSize(File folder) {
        long bytes = 0;

        File[] children = folder.listFiles();
        if (children == null) {
            return 0;
        }

        for (File file : children) {
            if(file.isFile()) {
                bytes += file.length();
            } else {
                bytes += getFolderSize(file);
            }
        }

        return bytes;
    }
}
