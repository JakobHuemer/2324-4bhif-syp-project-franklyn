export interface ServerMetrics {
  cpuUsagePercent: number,
  totalDiskSpaceInBytes: number,
  remainingDiskSpaceInBytes: number,
  savedScreenshotsSizeInBytes: number,
  savedVideosSizeInBytes: number,
  maxAvailableMemoryInBytes: number,
  totalUsedMemoryInBytes: number
}
