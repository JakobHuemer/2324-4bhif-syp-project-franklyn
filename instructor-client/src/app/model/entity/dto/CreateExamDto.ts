export interface CreateExamDto {
  title: string
  start: Date,
  end: Date,
  screencapture_interval_seconds: number,
  startBeforeEnd: boolean
}
