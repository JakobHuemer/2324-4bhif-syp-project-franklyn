export interface JobDto {
  id: number,
  state: string
  exam_id: number,
  examinee_id: number | undefined,
  created_at: string,
  finished_at: string,
  error_message: string | null,
}
