import {ExamState} from "./Exam-State";

export interface Exam {
  id: number,
  title: string,
  pin: number,
  state: ExamState,
  plannedStart: Date,
  plannedEnd: Date,
  actualStart: Date | undefined,
  actualEnd: Date | undefined,
  screencaptureIntervalSeconds: number
}
