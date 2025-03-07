import {Component, inject} from '@angular/core';
import {StoreService} from "../../../services/store.service";
import {distinctUntilChanged, map} from "rxjs";
import {AsyncPipe} from "@angular/common";
import {DownloadExamineeComponent} from "../../entity-components/download-examinee/download-examinee.component";
import {JobService} from "../../../services/job.service";
import {ToastService} from "../../../services/toast.service";

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
  protected jobSvc = inject(JobService);
  protected store = inject(StoreService).store;
  protected toastSvc = inject(ToastService);
  protected examinees = this.store
    .pipe(
      map(model => model.videoViewerModel.examinees),
      distinctUntilChanged()
    );

  protected curExam = inject(StoreService)
    .store
    .pipe(
      map(model =>
        model.examDashboardModel.exams
          .filter(exam =>
            exam.id === model.videoViewerModel.curExamId)
          .at(0)),
      distinctUntilChanged()
    );

  protected startDownloadAllJob() {
    let exam = this.store.value.examDashboardModel.exams
      .find(e =>
        e.id === this.store.value.videoViewerModel.curExamId
      );

    if (exam !== undefined) {
      let id = this.toastSvc.addToast(
        "Job Started",
        `The Job to generate the videos for the exam '${exam.title}' has been started.`,
        "success"
      );
      this.jobSvc.getAllExamVideos(exam, id);
    }
  }
}
