import {Component, inject} from '@angular/core';
import {StoreService} from "../../../services/store.service";
import {
  ExamineeDownloadListComponent
} from "../../entity-lists/examinee-download-list/examinee-download-list.component";
import {VideoExamineeComponent} from "../../entity-components/video-examinee/video-examinee.component";
import {distinctUntilChanged, map} from "rxjs";
import {AsyncPipe} from "@angular/common";
import {RouterLink, RouterLinkActive} from "@angular/router";

@Component({
    selector: 'app-video-viewer',
  imports: [
    ExamineeDownloadListComponent,
    VideoExamineeComponent,
    AsyncPipe,
    RouterLink,
    RouterLinkActive,
  ],
    templateUrl: './video-viewer.component.html',
    styleUrl: './video-viewer.component.css'
})
export class VideoViewerComponent {
  protected store = inject(StoreService).store;

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
}
