import {inject, Injectable} from '@angular/core';
import {StoreService} from "./store.service";
import {set} from "../model";
import {ExamineeService} from "./examinee.service";
import {distinctUntilChanged, map} from "rxjs";
import {WebApiService} from "./web-api.service";
import {ExamService} from "./exam.service";

@Injectable({
  providedIn: 'root'
})
export class ScheduleService {
  private store = inject(StoreService).store;
  private examineeRepo = inject(ExamineeService);
  private examRepo = inject(ExamService);
  protected webApi = inject(WebApiService);

  constructor() {
    this.store.pipe(
      map(model => model.timer.patrolSpeed),
      distinctUntilChanged()
    ).subscribe(() => {
      this.startPatrolInterval();
    })

    this.store.pipe(
      map(model => model.timer.nextClientTime),
      distinctUntilChanged()
    ).subscribe(() => {
      this.startUpdateDataScheduleInterval();
    })
  }

  //region stop intervals
  stopGettingServerMetrics() {
    if (!this.store.value.timer.serverMetricsTimerId) {
      clearInterval(this.store.value.timer.serverMetricsTimerId);
    }

    set((model) => {
      model.timer.serverMetricsTimerId = undefined;
    })
  }

  stopUpdateDataScheduleInterval() {
    if (!this.store.value.timer.updateDataScheduleTimerId) {
      clearInterval(this.store.value.timer.updateDataScheduleTimerId);
    }

    set((model) => {
      model.timer.updateDataScheduleTimerId = undefined;
    });
  }

  stopPatrolInterval() {
    if (!this.store.value.timer.patrolScheduleTimer) {
      clearInterval(this.store.value.timer.patrolScheduleTimer);
    }

    set((model) => {
      model.timer.patrolScheduleTimer = undefined;
    });
  }
  //endregion

  //region start intervals
  startUpdateDataScheduleInterval() {
    this.stopUpdateDataScheduleInterval();

    if (!this.store.value.timer.updateDataScheduleTimerId) {
      this.store.value.timer.updateDataScheduleTimerId =  setInterval(() => {
        this.examineeRepo.updateScreenshots();
        this.examRepo.reloadAllExams();

        if (this.store.value.examDashboardData.curExamId){
          // Do not check if exam ongoing since we also want to get
          // examinees for the video viewer when the exam is not ongoing
          this.webApi.getExamineesFromServer(
            this.store.value.examDashboardData.curExamId
          );
        }
      }, this.store.value.timer.nextClientTimeMilliseconds) as unknown as number;
    }
  }

  startPatrolInterval() {
    this.stopPatrolInterval();

    if (this.store.value.timer.patrolScheduleTimer === undefined) {
      set((model) => {
        model.timer.patrolScheduleTimer = setInterval(() => {
          this.examineeRepo.newPatrolExaminee();
        }, this.store.value.timer.patrolSpeedMilliseconds) as unknown as number;
      });
    }
  }

  startGettingServerMetrics() {
    this.stopGettingServerMetrics();

    if (this.store.value.timer.serverMetricsTimerId === undefined) {
      set((model) => {
        model.timer.serverMetricsTimerId = setInterval(async () => {
          await this.webApi.getServerMetrics();
        }, this.store.value.timer.reloadDashboardIntervalMilliseconds);
      });
    }
  }
  //endregion
}
