package at.htl.franklyn.server.boundary.Dto;

public record ServerMetricsDto(
        double cpuUsagePercent,
        double totalDiskSpaceInBytes,
        double remainingDiskSpaceInBytes,
        double savedScreenshotsSizeInBytes,
        double maxAvailableMemoryInBytes,
        double totalUsedMemoryInBytes,
        long alphaUploadsCount,
        double alphaUploadAvgDurationMs,
        double alphaUploadBytesTotal,
        long alphaUploadErrors,
        long betaUploadsCount,
        double betaUploadAvgDurationMs,
        double betaUploadBytesTotal,
        double betaChangedPixelsTotal,
        long betaUploadErrors,
        long requestedAlphaFrames,
        long screenshotFetchCount,
        double screenshotFetchAvgMs,
        long screenshotScaledFetchCount,
        double screenshotScaledFetchAvgMs,
        long screenshotErrors,
        long videoDownloadsCount,
        double videoDownloadAvgMs,
        long videoDownloadErrors,
        long screenshotsFileCount
) {
}
