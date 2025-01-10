export interface ServerMetrics {
  cpuUsagePercent: number,
  totalDiskSpaceInBytes: number, //TODO use
  remainingDiskSpaceInBytes: number,
  savedScreenshotsSizeInBytes: number,
  savedVideosSizeInBytes: number, //TODO use
  maxAvailableMemoryInBytes: number,
  totalUsedMemoryInBytes: number,
  diagramBackgroundColor: string,
  diagramTextColor: string,
  cpuUtilisationColor: string,
  diskUsageColor: string,
  memoryUtilisationColor: string,
}
