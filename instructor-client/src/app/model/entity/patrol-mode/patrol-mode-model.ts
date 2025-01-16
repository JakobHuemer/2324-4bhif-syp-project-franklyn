import {Patrol} from "./patrol";
import {CacheBuster} from "./cache-buster";
import {Examinee} from "./examinee";

export interface PatrolModeModel {
  readonly curExamId: number | undefined,
  examinees: Examinee[],
  readonly patrol: Patrol,
  readonly cacheBuster: CacheBuster,
}
