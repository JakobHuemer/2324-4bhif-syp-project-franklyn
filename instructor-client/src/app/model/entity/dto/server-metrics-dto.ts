export interface ServerMetricsDto {
  cpu_usage_percent: number,
  total_disk_space_in_bytes: number,
  remaining_disk_space_in_bytes: number,
  saved_screenshots_size_in_bytes: number,
  saved_videos_size_in_bytes: number,
  max_available_memory_in_bytes: number,
  total_used_memory_in_bytes: number,
}
