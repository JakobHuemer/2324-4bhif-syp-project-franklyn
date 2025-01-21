import {Component, Input} from '@angular/core';
import {Examinee, Job, set} from "../../../model";
import {environment} from "../../../../../env/environment";

@Component({
    selector: 'app-download-examinee',
    imports: [],
    templateUrl: './download-examinee.component.html',
    styleUrl: './download-examinee.component.css'
})
export class DownloadExamineeComponent {
  @Input() examinee: Examinee | undefined;
  @Input() job!: Job | undefined;

  showVideoOfExaminee() {
    set((model) => {
      model.videoViewerModel.patrol.patrolExaminee = this.examinee;
      model.patrolModeModel.cacheBuster.cachebustNum++;
    })
  }

  getDownloadUrl(): string {
    return `${environment.serverBaseUrl}/video/download/${this.examinee?.firstname}-${this.examinee?.lastname}`
  }
}
