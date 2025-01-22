import {Examinee} from "./examinee";

export interface Patrol {
  isPatrolModeOn: boolean;
  patrolExaminee: Examinee | undefined;
}
