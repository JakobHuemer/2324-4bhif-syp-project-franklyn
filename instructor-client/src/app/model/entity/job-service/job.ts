import {JobState} from "./job-state";

export interface Job {
  id: number,
  state: JobState,
  examId: number | undefined,
  examineeId: number | undefined,
}
