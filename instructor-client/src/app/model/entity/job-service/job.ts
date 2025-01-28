import {JobState} from "./job-state";

export interface Job {
  id: number,
  state: JobState,
  examId: number,
  examineeId: number | undefined,
  createdAt: Date,
  finishedAt: Date | undefined,
}
