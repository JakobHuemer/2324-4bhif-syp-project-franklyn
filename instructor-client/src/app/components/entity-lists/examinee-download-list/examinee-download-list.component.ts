import {Component, inject, model} from '@angular/core';
import {environment} from "../../../../../env/environment";
import {StoreService} from "../../../services/store.service";
import {distinctUntilChanged, map, Observable} from "rxjs";
import {AsyncPipe} from "@angular/common";
import {DownloadExamineeComponent} from "../../entity-components/download-examinee/download-examinee.component";
import {Examinee, Job, JobState, set} from "../../../model";
import {JobService} from "../../../services/job.service";

@Component({
  selector: 'app-examinee-download-list',
  imports: [
    DownloadExamineeComponent,
    AsyncPipe
  ],
  templateUrl: './examinee-download-list.component.html',
  styleUrl: './examinee-download-list.component.css'
})
export class ExamineeDownloadListComponent {
  protected store = inject(StoreService).store;
  protected jobSvc = inject(JobService);
  protected examineeJobs: Observable<{
    examinee: Examinee,
    job: Job | undefined,
  }[]> = this.store
    .pipe(
      map(model => model.videoViewerModel.examinees
        .map(e => ({
          examinee: e,
          job: this.getJobForExaminee(e.id)
        }))),
      distinctUntilChanged()
    );
  protected allExamineeDownloadJob = this.store
    .pipe(
      map(model => model.jobServiceModel.jobs
        .filter(j =>
          j.examineeId === undefined &&
          j.shouldDownload
        ).at(0)),
      distinctUntilChanged()
    );

  setShouldDownload() {
    this.allExamineeDownloadJob.subscribe({
      "next": (job) => {
        if (job !== undefined) {
          set(model => {
            const index = model.jobServiceModel.jobs
              .findIndex(j => j.id === job.id);

            model.jobServiceModel.jobs[index].shouldDownload = false;
          });
        }
      },
      "error": (error) => console.error(error),
    });
  }

  getDownloadUrl(jobId: number): string {
    return `${environment.serverBaseUrl}/telemetry/jobs/video/${jobId}/download.zip`;
  }

  getJobForExaminee(examineeId: number): Job | undefined {
    return this.store.value.jobServiceModel.jobs
      .find(j =>
        j.state === JobState.DONE &&
        j.shouldDownload &&
        j.examineeId === examineeId
      );
  }

  startDownloadAllJob() {
    let exam = this.store.value.examDashboardModel.exams
      .find(e => e.id === this.store.value.videoViewerModel.curExamId);

    if (exam !== undefined) {
      this.jobSvc.getAllExamVideos(exam);
    }
  }

  protected readonly JobState = JobState;
}
