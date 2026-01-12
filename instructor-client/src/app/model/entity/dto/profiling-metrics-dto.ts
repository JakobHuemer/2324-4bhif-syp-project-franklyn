export interface ProfilingMetricsDto {
  queue_size: number;
  active_requests: number;
  timeouts_total: number;
  uploads_total: number;
  uploads_per_second: number;
  connected_clients: number;
  video_jobs_queued: number;
  video_jobs_active: number;
  average_image_upload_size_bytes: number;
  average_image_download_size_bytes: number;
  screenshot_request_latency: HistogramDataDto;
  image_decode: HistogramDataDto;
  image_encode: HistogramDataDto;
  beta_frame_merge: HistogramDataDto;
  image_file_read: HistogramDataDto;
  image_save_total: HistogramDataDto;
  websocket_message_send: HistogramDataDto;
  websocket_ping_latency: HistogramDataDto;
  image_download: HistogramDataDto;
  video_ffmpeg: HistogramDataDto;
  video_zip: HistogramDataDto;
  vertx_worker_pool: VertxPoolMetricsDto;
  vertx_event_loop: VertxPoolMetricsDto;
}

export interface HistogramDataDto {
  p50_ms: number;
  p90_ms: number;
  p99_ms: number;
  max_ms: number;
  count: number;
}

export interface VertxPoolMetricsDto {
  pool_size: number;
  in_use: number;
  ratio: number;
  queue_size: number;
  completed: number;
}
