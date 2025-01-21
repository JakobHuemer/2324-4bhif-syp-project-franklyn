import {JobState} from "./job-state";
import {JobLog} from "./job-log";

export interface Job {
  id: number,
  state: JobState,
  shouldDownload: boolean,
  examineeId: number | undefined,
  logs: JobLog[],
}
