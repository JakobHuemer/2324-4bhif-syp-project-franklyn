import {ServerMetrics} from "./server-metrics";
import {ProfilingMetrics} from "./profiling-metrics";

export interface MetricsDashboardModel {
  readonly serverMetrics: ServerMetrics,
  readonly profilingMetrics: ProfilingMetrics | null,
  diagramBackgroundColor: string,
  diagramTextColor: string,
  cpuUtilisationColor: string,
  diskUsageScreenshotColor: string,
  diskUsageVideoColor: string,
  diskUsageOtherColor: string,
  memoryUtilisationColor: string,
}
