import {Component, inject, Input} from '@angular/core';
import {StoreService} from "../../../services/store.service";
import {Examinee} from "../../../model";
import {environment} from "../../../../../env/environment";

@Component({
    selector: 'app-video-examinee',
  imports: [],
    templateUrl: './video-examinee.component.html',
    styleUrl: './video-examinee.component.css'
})
export class VideoExamineeComponent {
  protected store = inject(StoreService).store;

  @Input() examId: number | undefined;
  @Input() examinee: Examinee | undefined;

  getVideoUrl(): string {
    return `${environment.serverBaseUrl}/telemetry/jobs/video/${this.store.value.videoViewerModel.jobId}/download`; //examinee gets checked in the html
  }

  showVideo(): boolean {
    return this.examinee !== undefined &&
      this.store.value.videoViewerModel.examinee?.firstname === this.examinee.firstname &&
      this.store.value.videoViewerModel.examinee?.lastname === this.examinee.lastname;
  }
}
