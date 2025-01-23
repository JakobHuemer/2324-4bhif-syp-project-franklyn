import {environment} from "../../../../../env/environment";

export class Timer {
  constructor() {
    this.patrolSpeedProp = environment.patrolSpeed;
    this.nextClientScheduleTimeProp = environment.nextClientScheduleTime;
    this.reloadDashboardIntervalProp = environment.reloadDashboardInterval;
  }

  private patrolSpeedProp: number;
  private patrolScheduleTimerProp: number | undefined;

  private nextClientScheduleTimeProp: number;
  private clientScheduleTimerIdProp: number | undefined;

  private reloadDashboardIntervalProp: number;
  private serverMetricsTimerIdProp: number | undefined

  //region <unformatted time-getter and setter>

  get patrolSpeed() {
    return this.patrolSpeedProp;
  }

  get nextClientTime() {
    return this.nextClientScheduleTimeProp;
  }

  get reloadDashboardInterval() {
    return this.reloadDashboardIntervalProp;
  }

  set patrolSpeed(val) {
    this.patrolSpeedProp = val;
  }

  set nextClientTime(val) {
    this.nextClientScheduleTimeProp = val;
  }

  set reloadDashboardInterval(val) {
    this.reloadDashboardIntervalProp = val;
  }
  //endregion

  //region <formatted time-getter>

  get patrolSpeedMilliseconds() {
    return this.patrolSpeedProp*1000;
  }

  get nextClientTimeMilliseconds() {
    return this.nextClientScheduleTimeProp*1000;
  }

  get reloadDashboardIntervalMilliseconds() {
    return this.reloadDashboardIntervalProp*1000;
  }
  //endregion

  //region <timer getter and setter>
  get patrolScheduleTimer() {
    return this.patrolScheduleTimerProp;
  }

  get updateDataScheduleTimerId() {
    return this.clientScheduleTimerIdProp;
  }

  get serverMetricsTimerId() {
    return this.serverMetricsTimerIdProp;
  }

  set patrolScheduleTimer(val) {
    this.patrolScheduleTimerProp = val;
  }

  set updateDataScheduleTimerId(val) {
    this.clientScheduleTimerIdProp = val;
  }

  set serverMetricsTimerId(val) {
    this.serverMetricsTimerIdProp = val;
  }
  //endregion
}
