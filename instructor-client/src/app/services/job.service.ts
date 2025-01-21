import {inject, Injectable} from '@angular/core';
import {Exam, Examinee, store} from "../model";
import {WebApiService} from "./web-api.service";
import {StoreService} from "./store.service";

@Injectable({
  providedIn: 'root'
})
export class JobService {
  protected webApi = inject(WebApiService);
  protected store = inject(StoreService).store;

  getAllExamVideos(exam: Exam): void {
    this.webApi.getAllExamVideos(exam.id);
  }

  getExamExamineeVideos(
    exam: Exam,
    examinee: Examinee,
    shouldDownload: boolean
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
