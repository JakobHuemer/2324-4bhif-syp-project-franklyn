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

  startJob() {
    if (this.exam !== undefined && this.examinee !== undefined) {
      this.jobSvc.getExamExamineeVideos(this.exam, this.examinee);
    }
  }
}
