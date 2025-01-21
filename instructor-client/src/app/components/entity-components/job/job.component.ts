import {Component, inject, Input} from '@angular/core';
import {Exam, Examinee, ExamState, Job, JobLog} from "../../../model";
import {StoreService} from "../../../services/store.service";
import {distinctUntilChanged, filter, map, Observable} from "rxjs";
import {AsyncPipe, DatePipe} from "@angular/common";

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
  @Input() jobLog: JobLog | undefined;

  protected job: Observable<{
    job: Job,
    exam: Exam | undefined,
    examinee: Examinee | undefined,
  } | undefined> = inject(StoreService)
    .store
    .pipe(
      map(model => {
        let j = model.jobServiceModel.jobs
          .filter(j => j.id === this.jobLog?.jobId)
          .at(0);

        let exam = model.examDashboardModel
          .exams.find(e => e.id === j?.examId);

        let examinee = model.videoViewerModel
          .examinees.find(e => e.id === j?.examineeId);

        return {
          job: j,
          exam: exam,
          examinee: examinee,
        };
      }),
      filter(j => j.job !== undefined),
      map(j => ({
        job: j.job!,
        exam: j.exam,
        examinee: j.examinee,
      })),
      distinctUntilChanged()
    );

  showVideoOfExaminee() {
    //TODO: Implement this
  }

  startDownloadJob() {
    //TODO: Implement this
  }
}
