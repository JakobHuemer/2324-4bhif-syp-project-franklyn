import {Component, inject, OnDestroy} from '@angular/core';
import {StoreService} from "../../../services/store.service";
import {ExamineeListComponent} from "../../entity-lists/examinee-list/examinee-list.component";
import {FormsModule} from "@angular/forms";
import {PatrolPageExamineeComponent} from "../../entity-components/patrol-page-examinee/patrol-page-examinee.component";
import {distinctUntilChanged, map} from "rxjs";
import {AsyncPipe, Location} from "@angular/common";
import {ExamService} from "../../../services/exam.service";
import {ExamState, set} from "../../../model";
import {ScheduleService} from "../../../services/schedule.service";

@Component({
    selector: 'app-patrol-mode',
  imports: [
    ExamineeListComponent,
    PatrolPageExamineeComponent,
    FormsModule,
    AsyncPipe,
  ],
    templateUrl: './patrol-mode.component.html',
    styleUrl: './patrol-mode.component.css'
})
export class PatrolModeComponent {
  protected store = inject(StoreService).store;
  protected examSvc = inject(ExamService);
  protected scheduleSvc = inject(ScheduleService);
  protected location = inject(Location);

  protected curExam = inject(StoreService)
    .store
    .pipe(
      map(model =>
        model.examDashboardModel.exams
          .filter(exam =>
            exam.id === model.patrolModeModel.curExamId &&
            exam.state === ExamState.ONGOING)
          .at(0)),
      distinctUntilChanged()
    );

  constructor() {
    this.location.onUrlChange((url) => {
      if (url !== "/patrol-mode") {
        this.scheduleSvc.stopPatrolInterval();
      } else {
        if (this.store.value.patrolModeModel.patrol.isPatrolModeOn) {
          this.scheduleSvc.startPatrolInterval();
        }
      }
    });
  }

  getPatrolModeOnState():string {
    let returnString: string = "off";

    if (this.store.value.patrolModeModel.patrol.isPatrolModeOn) {
      return "on";
    }

    return returnString;
  }

  getPatrolModeOnStateClass():string {
    let returnString: string = "text-danger";

    if (this.store.value.patrolModeModel.patrol.isPatrolModeOn) {
      return "text-success";
    }

    return returnString;
  }

  stopCurExam() {
    this.examSvc.stopExam(this.store.value.patrolModeModel.curExamId);
  }

  setPatrolMode() {
    set((model) => {
      model.patrolModeModel.patrol.isPatrolModeOn = !model.patrolModeModel
        .patrol
        .isPatrolModeOn;

      if (model.patrolModeModel.patrol.isPatrolModeOn) {
        this.scheduleSvc.startPatrolInterval();
      } else {
        this.scheduleSvc.stopPatrolInterval();
      }
    });
  }
}
