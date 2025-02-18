import {inject, Injectable} from '@angular/core';
import {StoreService} from "./store.service";
import {set} from "../model";
import {ExamineeService} from "./examinee.service";
import {distinctUntilChanged, map} from "rxjs";
import {WebApiService} from "./web-api.service";
import {JobService} from "./job.service";

@Injectable({
  providedIn: 'root'
})
export class ScheduleService {
  private store = inject(StoreService).store;
  private examineeRepo = inject(ExamineeService);
  protected webApi = inject(WebApiService);
  protected jobSvc = inject(JobService);

  constructor() {
    this.store.pipe(
      map(model => model.scheduleServiceModel.timer.nextClientTime),
      distinctUntilChanged()
    ).subscribe(() => {
      this.startUpdateDataScheduleInterval();
    })

    this.store.pipe(
      map(model => model.scheduleServiceModel.timer.patrolSpeed),
      distinctUntilChanged()
    ).subscribe(() => {
      this.startPatrolInterval();
    })
  }

  //region stop intervals

  stopGettingServerMetrics() {
    if (this.store.value.scheduleServiceModel.timer.serverMetricsTimerId !== undefined) {
      window.clearInterval(
        this.store.value.scheduleServiceModel.timer.serverMetricsTimerId
      );
    }

    set((model) => {
      model.scheduleServiceModel.timer.serverMetricsTimerId = undefined;
    })
  }

  stopUpdateDataScheduleInterval() {
    if (this.store.value.scheduleServiceModel.timer.updateDataScheduleTimerId !== undefined) {
      window.clearInterval(
        this.store.value.scheduleServiceModel.timer.updateDataScheduleTimerId
      );
    }

    set((model) => {
      model.scheduleServiceModel.timer.updateDataScheduleTimerId = undefined;
    });
  }

  stopPatrolInterval() {
    if (this.store.value.scheduleServiceModel.timer.patrolScheduleTimer !== undefined) {
      window.clearInterval(
        this.store.value.scheduleServiceModel.timer.patrolScheduleTimer
      );
    }

    set((model) => {
      model.scheduleServiceModel.timer.patrolScheduleTimer = undefined;
    });
  }

  //endregion

  //region start intervals

  startUpdateDataScheduleInterval() {
    this.stopUpdateDataScheduleInterval();

    if (!this.store.value.scheduleServiceModel.timer.updateDataScheduleTimerId) {
      this.store.value.scheduleServiceModel.timer.updateDataScheduleTimerId = window.setInterval(() => {
        this.jobSvc.getAllJobs();

        if (!this.store.value.patrolModeModel.patrol.isPatrolModeOn &&
          this.store.value.patrolModeModel.curExamId !== undefined) {
          this.webApi.getExamineesFromServer(
            this.store.value.patrolModeModel.curExamId
          ).subscribe({
            next: () => {
              this.examineeRepo.newPatrolExaminee();
              this.examineeRepo.updateScreenshots();
            },
            error: err => console.error(err)
          });
        }
      }, this.store.value.scheduleServiceModel.timer.nextClientTimeMilliseconds);
    }
  }

  startPatrolInterval() {
    this.stopPatrolInterval();

    if (this.store.value.scheduleServiceModel.timer.patrolScheduleTimer === undefined) {
      set((model) => {
        model.scheduleServiceModel.timer.patrolScheduleTimer = window.setInterval(() => {
          if (this.store.value.patrolModeModel.curExamId) {
            // Do not check if exam ongoing since we also want to get
            // examinees for the video viewer when the exam is not ongoing
            this.webApi.getExamineesFromServer(
              this.store.value.patrolModeModel.curExamId
            ).subscribe({
              next: () => {
                this.examineeRepo.newPatrolExaminee();
                this.examineeRepo.updateScreenshots();
              },
              error: err => console.error(err)
            });
          }
        }, this.store.value.scheduleServiceModel.timer.patrolSpeedMilliseconds);
      });
    }
  }

  startGettingServerMetrics() {
    this.stopGettingServerMetrics();

    if (this.store.value.scheduleServiceModel.timer.serverMetricsTimerId === undefined) {
      set((model) => {
        model.scheduleServiceModel.timer.serverMetricsTimerId = window.setInterval(async () => {
          await this.webApi.getServerMetrics();
        }, this.store.value.scheduleServiceModel.timer.reloadDashboardIntervalMilliseconds);
      });
    }
  }

  //endregion
}
