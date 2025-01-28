import {BehaviorSubject} from "rxjs";
import {Draft, produce} from "immer";
import {CreateTestModel} from "./entity/create-test/create-test-index";
import {ExamDashboardModel} from "./entity/exam-dashboard/exam-dashboard-index";
import {MetricsDashboardModel} from "./entity/metrics-dashboard/metrics-dashboard-index";
import {PatrolModeModel} from "./entity/patrol-mode/patrol-mode-index";
import {ScheduleServiceModel, Timer} from "./entity/schedule-service/schedule-service-index";
import {VideoViewerModel} from "./entity/video-viewer/video-viewer-index";
import {environment} from "../../../env/environment";
import {JobServiceModel} from "./entity/job-service/job-service-model";
import {ToastModel} from "./entity/toast/toast-model";

export interface Model {
    createTestModel: CreateTestModel,
    examDashboardModel: ExamDashboardModel,
    metricsDashboardModel: MetricsDashboardModel,
    patrolModeModel: PatrolModeModel,
    scheduleServiceModel: ScheduleServiceModel,
    videoViewerModel: VideoViewerModel,
    jobServiceModel:JobServiceModel,
    toastModel: ToastModel,
    readonly resetText: string,
}

const initialState: Model = {
  createTestModel: {
    createExam: {
      title: "",
      start: new Date(Date.now()),
      end: new Date(Date.now() + 3000000), // add 50 minutes
      screencapture_interval_seconds: environment.patrolSpeed
    },
    createdExam: false,
    schoolUnits: [],
    eveningSchoolUnits: []
  },
  examDashboardModel: {
    exams: [],
    curExamId: undefined
  },
  metricsDashboardModel: {
    serverMetrics: {
      cpuUsagePercent: 0,
      totalDiskSpaceInBytes: 0,
      remainingDiskSpaceInBytes: 0,
      savedScreenshotsSizeInBytes: 0,
      savedVideosSizeInBytes: 0,
      maxAvailableMemoryInBytes: 0,
      totalUsedMemoryInBytes: 0
    },
    diagramBackgroundColor: "#f0f0f0",
    diagramTextColor: "#36363a",
    cpuUtilisationColor: "#0d6efd",
    diskUsageScreenshotColor: "rgb(54, 162, 235)",
    diskUsageVideoColor: "rgb(255, 99, 132)",
    diskUsageOtherColor: "rgb(255, 205, 86)",
    memoryUtilisationColor: "#0d6efd"
  },
  patrolModeModel: {
    curExamId: undefined,
    examinees: [],
    patrol: {
      isPatrolModeOn: false,
      patrolExaminee: undefined
    },
    cacheBuster: {
      cachebustNum: 0
    }
  },
  scheduleServiceModel: {
    timer: new Timer()
  },
  videoViewerModel: {
    curExamId: undefined,
    examinees: [],
    examinee: undefined,
    jobId: undefined
  },
  jobServiceModel: {
    jobs: [],
  },
  toastModel: {
    toasts: []
  },
  resetText: ""
};

export const store = new BehaviorSubject<Model>(initialState);

export function set(recipe: (model: Draft<Model>) => void) {
    const nextState = produce(store.value, recipe);
    store.next(nextState);
}
