import {inject, Injectable} from '@angular/core';
import {Exam, Examinee, ExamState, set, store} from "../model";
import {WebApiService} from "./web-api.service";
import {StoreService} from "./store.service";
import {distinctUntilChanged, map} from "rxjs";

@Injectable({
  providedIn: 'root'
})
export class JobService {
  protected webApi = inject(WebApiService);
  protected store = inject(StoreService).store;

  constructor() {
    this.store.pipe(
      map(model => model.videoViewerModel.curExamId),
      distinctUntilChanged()
    ).subscribe({
      next: () => {
        this.getAllJobs();
      },
      error: err => console.error(err)
    });
  }

  getAllJobs() {
    if (this.store.value.videoViewerModel.curExamId !== undefined) {
      this.webApi.getAllJobsForExam(
        this.store.value.videoViewerModel.curExamId
      );
    }
  }

  getAllExamVideos(exam: Exam): void {
    this.webApi.getAllExamVideos(exam.id);
  }

  getExamExamineeVideos(
    exam: Exam,
    examinee: Examinee
  ): void {
    this.webApi.getExamExamineeVideo(
      exam.id,
      examinee.id
    );
  }
}
