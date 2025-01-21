import {Component, inject, Input} from '@angular/core';
import {Exam, Examinee, set} from "../../../model";
import {JobService} from "../../../services/job.service";

@Component({
    selector: 'app-download-examinee',
    imports: [],
    templateUrl: './download-examinee.component.html',
    styleUrl: './download-examinee.component.css'
})
export class DownloadExamineeComponent {
  private jobSvc = inject(JobService);
  @Input() exam: Exam | undefined;
  @Input() examinee: Examinee | undefined;

  showVideoOfExaminee() {
    set((model) => {
      model.videoViewerModel.patrol.patrolExaminee = this.examinee;
      model.patrolModeModel.cacheBuster.cachebustNum++;
    })

    if (this.exam !== undefined && this.examinee !== undefined) {
      this.jobSvc.getExamExamineeVideos(this.exam, this.examinee, false);
    }
  }

  startDownloadJob() {
    if (this.exam !== undefined && this.examinee !== undefined) {
      this.jobSvc.getExamExamineeVideos(this.exam, this.examinee, true);
    }
  }
}
