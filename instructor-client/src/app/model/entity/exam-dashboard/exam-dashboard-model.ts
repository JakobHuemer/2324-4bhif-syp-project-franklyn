import {Exam} from "./exam";

export interface ExamDashboardModel {
  exams: Exam[],
  curExamId: number | undefined
}
