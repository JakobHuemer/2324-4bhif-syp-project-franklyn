export interface ExamDto {
  id: number,
  title: string,
  pin: number,
  state: string,
  planned_start: string,
  planned_end: string,
  actual_start: string | null,
  actual_end: string | null,
  screencapture_interval_seconds: number,
  registered_students_num: number
}
