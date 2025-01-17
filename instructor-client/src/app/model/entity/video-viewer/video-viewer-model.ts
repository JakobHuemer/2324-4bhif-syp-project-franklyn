import {Examinee} from "../patrol-mode/examinee";
import {Patrol} from "../patrol-mode/patrol";

export interface VideoViewerModel {
  readonly curExamId: number | undefined,
  examinees: Examinee[],
  readonly examinee: Examinee | undefined,
  readonly patrol: Patrol,
}
