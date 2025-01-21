import {Component, inject} from '@angular/core';
import {distinctUntilChanged, map} from "rxjs";
import {StoreService} from "../../../services/store.service";
import {AsyncPipe} from "@angular/common";
import {DownloadExamineeComponent} from "../../entity-components/download-examinee/download-examinee.component";
import {JobComponent} from "../../entity-components/job/job.component";

@Component({
  selector: 'app-job-list',
  imports: [
    AsyncPipe,
    JobComponent,
  ],
  templateUrl: './job-list.component.html',
  styleUrl: './job-list.component.css'
})
export class JobListComponent {
  protected jobLogs = inject(StoreService).store
    .pipe(
      map(model => model.jobServiceModel.jobLogs),
      distinctUntilChanged()
    );
}
