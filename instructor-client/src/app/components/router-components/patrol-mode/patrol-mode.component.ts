import {Component, inject} from '@angular/core';
import {StoreService} from "../../../services/store.service";
import {ExamineeListComponent} from "../../entity-lists/examinee-list/examinee-list.component";
import {FormsModule} from "@angular/forms";
import {PatrolPageExamineeComponent} from "../../entity-components/patrol-page-examinee/patrol-page-examinee.component";
import {distinctUntilChanged, map} from "rxjs";
import {AsyncPipe, Location} from "@angular/common";
import {ExamService} from "../../../services/exam.service";
import {RouterLink} from "@angular/router";

@Component({
    selector: 'app-patrol-mode',
  imports: [
    ExamineeListComponent,
    PatrolPageExamineeComponent,
    FormsModule,
    AsyncPipe,
    RouterLink
  ],
    templateUrl: './patrol-mode.component.html',
    styleUrl: './patrol-mode.component.css'
})
export class PatrolModeComponent {
  protected store = inject(StoreService).store;
  protected examSvc = inject(ExamService);
  protected location = inject(Location);

  protected curExam = inject(StoreService)
    .store
    .pipe(
      map(model =>
        model.examDashboardData.exams
          .filter(exam =>
            exam.id === model.curExamId)
          .at(0)),
      distinctUntilChanged()
    );

  getPatrolModeOnState():string {
    let returnString: string = "off";

    if (this.store.value.patrol.isPatrolModeOn) {
      return "on";
    }

    return returnString;
  }

  getPatrolModeOnStateClass():string {
    let returnString: string = "text-danger";

    if (this.store.value.patrol.isPatrolModeOn) {
      return "text-success";
    }

    return returnString;
  }

  stopCurExam() {
    this.examSvc.stopCurExam();
  }
}
