import {Examinee} from "../patrol-mode/examinee";
import {Patrol} from "../patrol-mode/patrol";

export interface VideoViewerModel {
  curExamId: number | undefined,
  jobId: number | undefined,
  examinees: Examinee[],
  examinee: Examinee | undefined,
}
