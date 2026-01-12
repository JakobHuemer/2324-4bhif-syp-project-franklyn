export interface ProfilingMetrics {
  queueSize: number;
  activeRequests: number;
  timeoutsTotal: number;
  uploadsTotal: number;
  uploadsPerSecond: number;
  connectedClients: number;
  videoJobsQueued: number;
  videoJobsActive: number;
  averageImageUploadSizeBytes: number;
  averageImageDownloadSizeBytes: number;
  screenshotRequestLatency: HistogramData;
  imageDecode: HistogramData;
  imageEncode: HistogramData;
  betaFrameMerge: HistogramData;
  imageFileRead: HistogramData;
  imageSaveTotal: HistogramData;
  websocketMessageSend: HistogramData;
  websocketPingLatency: HistogramData;
  imageDownload: HistogramData;
  videoFfmpeg: HistogramData;
  videoZip: HistogramData;
  vertxWorkerPool: VertxPoolMetrics;
  vertxEventLoop: VertxPoolMetrics;
}

export interface HistogramData {
  p50Ms: number;
  p90Ms: number;
  p99Ms: number;
  maxMs: number;
  count: number;
}

export interface VertxPoolMetrics {
  poolSize: number;
  inUse: number;
  ratio: number;
  queueSize: number;
  completed: number;
}
