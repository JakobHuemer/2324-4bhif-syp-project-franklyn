import {Component, inject, Input} from '@angular/core';
import {Job, JobState, set} from "../../../model";
import {StoreService} from "../../../services/store.service";
import {AsyncPipe, DatePipe} from "@angular/common";
import {environment} from "../../../../../env/environment";
import {distinctUntilChanged, map} from "rxjs";

@Component({
  selector: 'app-job',
  imports: [
    AsyncPipe,
    DatePipe
  ],
  templateUrl: './job.component.html',
  styleUrl: './job.component.css'
})
export class JobComponent {
  @Input() job: Job | undefined;

  protected store = inject(StoreService).store;
  protected exam = this.store
    .pipe(
      map(model => model.examDashboardModel.exams
        .filter(e => e.id === this.job?.examId)
        .at(0)
      ),
      distinctUntilChanged()
    );

  protected examinee = this.store
    .pipe(
      map(model => model.videoViewerModel.examinees
        .filter(e => e.id === this.job?.examineeId)
        .at(0)
      ),
      distinctUntilChanged()
    );

  showVideoOfExaminee() {
    if (this.job !== undefined) {
      let vidExaminee = this.store.value
        .videoViewerModel
        .examinees
        .find(e => e.id === this.job!.examineeId);

      set((model) => {
        model.videoViewerModel.examinee = vidExaminee;
        model.videoViewerModel.jobId = this.job!.id;
        model.patrolModeModel.cacheBuster.cachebustNum++;
      })
    }
  }

  getDownloadUrl(): string {
    return `${environment.serverBaseUrl}/telemetry/jobs/video/${this.job?.id}/download`;
  }

  protected readonly JobState = JobState;

  getStateString(state: JobState) {
    switch (state) {
      case JobState.ONGOING:
        return "Ongoing";
      case JobState.FAILED:
        return "Failed";
      case JobState.DONE:
        return "Done";
      case JobState.DELETED:
        return "Deleted";
      default:
        return "Queued";
    }
  }
}
