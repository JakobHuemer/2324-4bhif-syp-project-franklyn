import {inject, Injectable} from '@angular/core';
import {WebApiService} from "./web-api.service";
import {CreateExam, Exam, ExamState, set} from "../model";
import {StoreService} from "./store.service";
import {Observable} from "rxjs";
import {ExamineeService} from "./examinee.service";

@Injectable({
  providedIn: 'root'
})
export class ExamService {
  private store = inject(StoreService).store;
  private webApi = inject(WebApiService);
  private examineeSvc = inject(ExamineeService);

  constructor() {
    this.reloadAllExams();
  }

  reloadAllExams(): void {
    this.webApi.getExamsFromServer();
  }

  get(predicate?: ((item: Exam) => boolean) | undefined): Exam[] {
    if (predicate) return this.get().filter(predicate);
    return this.store.value.examDashboardModel.exams;
  }

  setCurDashboardExam(exam: Exam) {
    let exams = this.get((e) =>
      e.id === exam.id
    );

    let newCurExam: Exam | undefined;

    if (exams.length === 1)
      newCurExam = exams[0];

    set((model) => {
      model.examDashboardModel.curExamId = newCurExam?.id;
    });
  }

  createNewExam(exam: CreateExam): Promise<Observable<Exam>> {
    return this.webApi.createNewExam(exam);
  }

  isCurDashboardExam(id: number) {
    return (this.store.value.examDashboardModel.curExamId === id);
  }

  setCurExam(exam: Exam): void {
    set((model) => {
      model.patrolModeModel.curExamId = exam?.id;
    });

    if (exam.state === ExamState.CREATED) {
      this.webApi.startExamByIdFromServer(exam);
    }

    this.examineeSvc.updateScreenshots();
    this.webApi.getExamineesFromServer(exam.id);
    this.webApi.getExamsFromServer();
  }

  setCurVideoExam(exam: Exam): void {
    if (this.store.value.videoViewerModel.curExamId !== exam?.id) {
      set((model) => {
        model.videoViewerModel.examinees = [];
        model.videoViewerModel.examinee = undefined;
        model.videoViewerModel.curExamId = exam?.id;
      });
    }

    this.webApi.getVideoExamineesFromServer(exam.id);
    this.webApi.getExamsFromServer();
  }

  stopExam(examId: number | undefined): void {
    let myExamId: number;

    if (examId) {
      myExamId = examId;
    } else if (this.store.value.patrolModeModel.curExamId) {
      myExamId = this.store.value.patrolModeModel.curExamId;
    } else {
      return;
    }

    if (myExamId === this.store.value.patrolModeModel.curExamId) {
      this.examineeSvc.resetExaminees();

      set((model) => {
        model.patrolModeModel.curExamId = undefined;
      })
    }

    this.webApi.completeExamByIdFromServer(myExamId);
    this.webApi.getExamsFromServer();
  }
}
