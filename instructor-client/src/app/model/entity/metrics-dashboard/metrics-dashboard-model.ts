import {ServerMetrics} from "./server-metrics";

export interface MetricsDashboardModel {
  readonly serverMetrics: ServerMetrics,
  diagramBackgroundColor: string,
  diagramTextColor: string,
  cpuUtilisationColor: string,
  diskUsageScreenshotColor: string,
  diskUsageVideoColor: string,
  diskUsageOtherColor: string,
  memoryUtilisationColor: string,
}
