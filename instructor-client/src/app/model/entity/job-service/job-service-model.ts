import {Job} from "./job";

export interface JobServiceModel {
  getAllVideosJob: Job | undefined,
  job: Job[]
}
