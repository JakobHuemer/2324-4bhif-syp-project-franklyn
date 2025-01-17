import {JobState} from "./job-state";

export interface Job {
  id: number,
  state: JobState,
}
