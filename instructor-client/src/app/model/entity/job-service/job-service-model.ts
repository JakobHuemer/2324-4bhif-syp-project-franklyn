import {Job} from "./job";
import {JobLog} from "./job-log";

export interface JobServiceModel {
  jobs: Job[],
  jobLogs: JobLog[],
}
