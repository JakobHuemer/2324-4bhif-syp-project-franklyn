import {JobState} from "./job-state";

export interface JobLog {
  jobId: number,
  state: JobState,
  message: string,
  timestamp: Date,
}
