import {inject, Injectable} from '@angular/core';
import {WebApiService} from "./web-api.service";
import {set} from "../model";
import {Exam} from "../model/entity/Exam";
import {StoreService} from "./store.service";
import {Location} from "@angular/common";
import {CreateExam} from "../model/entity/CreateExam";
import {Observable} from "rxjs";
import {ExamineeService} from "./examinee.service";
import {ExamState} from "../model/entity/Exam-State";

@Injectable({
  providedIn: 'root'
})
export class ExamService {
  private store = inject(StoreService).store;
  private location = inject(Location);
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
    return this.store.value.examDashboardData.exams;
  }

  setCurDashboardExam(exam: Exam) {
    let exams = this.get((e) =>
      e.id === exam.id
    );

    let newCurExam: Exam | undefined;

    if (exams.length === 1)
      newCurExam = exams[0];

    set((model) => {
      model.examDashboardData.curExamId = newCurExam?.id;
    });
  }

  deleteSpecificExam(exam: Exam): void {
    this.webApi.deleteExamByIdFromServer(exam.id);
  }

  createNewExam(exam: CreateExam): Promise<Observable<Exam>> {
    return this.webApi.createNewExam(exam);
  }

  isCurDashboardExam(id: number) {
    return (this.store.value.examDashboardData.curExamId === id);
  }

  setCurExam(exam: Exam): void {
    this.stopCurExam();

    set((model) => {
      model.curExamId = exam?.id;
    });

    if (exam.state !== ExamState.ONGOING) {
      this.webApi.startExamByIdFromServer(exam);
    }

    this.examineeSvc.updateScreenshots();
    if (this.store.value.examDashboardData.curExamId)
      this.webApi.getExamineesFromServer(
        this.store.value.examDashboardData.curExamId
      );
  }

  stopCurExam(): void {
    if (this.store.value.curExamId) {
      this.webApi.completeExamByIdFromServer(this.store.value.curExamId);
      this.examineeSvc.resetExaminees();

      set((model) => {
        model.curExamId = undefined;
      })
    }
  }
}
