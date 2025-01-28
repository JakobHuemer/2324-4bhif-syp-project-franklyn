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
        set(model => {
          model.jobServiceModel.jobs = [];
          model.jobServiceModel.jobLogs = [];
        });
      },
      error: err => console.error(err)
    });
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

  updateAllJobs(): void {
    store.value.jobServiceModel.jobs.forEach(job => {
      this.webApi.getJobStatus(job.id);
    });
  }
}
