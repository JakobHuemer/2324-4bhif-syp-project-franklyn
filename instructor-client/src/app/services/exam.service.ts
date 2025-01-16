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

  createNewExam(exam: CreateExam): Promise<Observable<Exam>> {
    return this.webApi.createNewExam(exam);
  }

  isCurDashboardExam(id: number) {
    return (this.store.value.examDashboardData.curExamId === id);
  }

  setCurExam(exam: Exam): void {
    set((model) => {
      model.curExamId = exam?.id;
    });

    if (exam.state === ExamState.CREATED) {
      this.webApi.startExamByIdFromServer(exam);
    }

    this.examineeSvc.updateScreenshots();
    this.webApi.getExamineesFromServer(exam.id);
    this.webApi.getExamsFromServer();
  }

  setCurVideoExam(exam: Exam): void {
    set((model) => {
      model.curVideoExamId = exam?.id;
    });

    if (exam.state === ExamState.CREATED) {
      this.webApi.startExamByIdFromServer(exam);
    }

    this.webApi.getVideoExamineesFromServer(exam.id);
    this.webApi.getExamsFromServer();
  }

  stopExam(examId: number | undefined): void {
    let myExamId: number;

    if (examId) {
      myExamId = examId;
    } else if (this.store.value.curExamId) {
      myExamId = this.store.value.curExamId;
    } else {
      return;
    }

    if (myExamId === this.store.value.curExamId) {
      this.examineeSvc.resetExaminees();

      set((model) => {
        model.curExamId = undefined;
      })
    }

    this.webApi.completeExamByIdFromServer(myExamId);
    this.webApi.getExamsFromServer();
  }
}
