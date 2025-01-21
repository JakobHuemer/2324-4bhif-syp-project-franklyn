import {JobState} from "./job-state";

export interface JobLog {
  state: JobState,
  message: string,
  timestamp: Date,
}
