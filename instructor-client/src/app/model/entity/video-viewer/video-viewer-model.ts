import {Examinee} from "../patrol-mode/examinee";

export interface VideoViewerModel {
  readonly curExamId: number | undefined,
  examinees: Examinee[],
  readonly examinee: Examinee | undefined,
}
