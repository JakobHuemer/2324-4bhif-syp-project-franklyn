import {Component, inject, Input} from '@angular/core';
import {Exam, Examinee, set} from "../../../model";
import {JobService} from "../../../services/job.service";
import {ToastService} from "../../../services/toast.service";

@Component({
    selector: 'app-download-examinee',
    imports: [],
    templateUrl: './download-examinee.component.html',
    styleUrl: './download-examinee.component.css'
})
export class DownloadExamineeComponent {
  private readonly jobSvc = inject(JobService);
  private readonly toastSvc = inject(ToastService);
  @Input() exam: Exam | undefined;
  @Input() examinee: Examinee | undefined;

  startJob() {
    if (this.exam !== undefined && this.examinee !== undefined) {
      let id = this.toastSvc.addToast(
        "Job Started",
        `The Job to generate the video for '${this.examinee?.firstname} ${this.examinee?.lastname}' has been started.`,
        "success"
      );

      this.jobSvc.getExamExamineeVideos(
        this.exam,
        this.examinee,
        id
      );
    }
  }
}
