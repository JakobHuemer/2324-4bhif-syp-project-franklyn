import {Component, inject, Input} from '@angular/core';
import {StoreService} from "../../../services/store.service";
import {Examinee, JobState} from "../../../model";
import {environment} from "../../../../../env/environment";
import {AsyncPipe} from "@angular/common";
import {distinctUntilChanged, map} from "rxjs";

@Component({
    selector: 'app-video-examinee',
  imports: [
    AsyncPipe
  ],
    templateUrl: './video-examinee.component.html',
    styleUrl: './video-examinee.component.css'
})
export class VideoExamineeComponent {
  private store = inject(StoreService).store;

  @Input() examId: number | undefined;
  @Input() examinee: Examinee | undefined;
  @Input() jobId: number | undefined;

  protected job = this.store
    .pipe(
      map(model =>
        model.jobServiceModel.jobs
          .filter(job =>
            !job.shouldDownload &&
            job.state === JobState.DONE &&
            job.id === this.jobId
          ).at(0)),
      distinctUntilChanged()
    );

  getVideoUrl(): string {
    return `${environment.serverBaseUrl}/video/${this.examinee?.firstname}-${this.examinee?.lastname}?cache=${this.store.value.patrolModeModel.cacheBuster.cachebustNum}`; //examinee gets checked in the html
  }

  showVideo(): boolean {
    return this.examinee !== undefined &&
      this.store.value.videoViewerModel.patrol.patrolExaminee?.firstname === this.examinee.firstname &&
      this.store.value.videoViewerModel.patrol.patrolExaminee?.lastname === this.examinee.lastname;
  }
}
