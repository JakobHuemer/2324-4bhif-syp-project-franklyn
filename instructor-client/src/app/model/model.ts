import {BehaviorSubject} from "rxjs";
import {Draft, produce} from "immer";
import {CacheBuster} from "./entity/CacheBuster";
import {ExamineeData} from "./entity/ExamineeData";
import {Patrol} from "./entity/Patrol";
import {ServerMetrics} from "./entity/ServerMetrics";
import {Timer} from "./entity/Timer";

export interface Model {
  readonly cacheBuster: CacheBuster,
  readonly examineeData: ExamineeData,
  readonly patrol: Patrol,
  readonly serverMetrics: ServerMetrics,
  readonly timer: Timer,
  readonly resetText: string
}

const initialState: Model = {
  cacheBuster: {
    cachebustNum: 0
  },
  examineeData: {
    examinees: []
  },
  patrol: {
    isPatrolModeOn: false,
    patrolExaminee: undefined
  },
  serverMetrics: {
    cpuUsagePercent: 0,
    totalDiskSpaceInBytes: 0,
    remainingDiskSpaceInBytes: 0,
    savedScreenshotsSizeInBytes: 0,
    maxAvailableMemoryInBytes: 0,
    totalUsedMemoryInBytes: 0,
    alphaUploadsCount: 0,
    alphaUploadAvgDurationMs: 0,
    alphaUploadBytesTotal: 0,
    alphaUploadErrors: 0,
    betaUploadsCount: 0,
    betaUploadAvgDurationMs: 0,
    betaUploadBytesTotal: 0,
    betaChangedPixelsTotal: 0,
    betaUploadErrors: 0,
    requestedAlphaFrames: 0,
    screenshotFetchCount: 0,
    screenshotFetchAvgMs: 0,
    screenshotScaledFetchCount: 0,
    screenshotScaledFetchAvgMs: 0,
    screenshotErrors: 0,
    videoDownloadsCount: 0,
    videoDownloadAvgMs: 0,
    videoDownloadErrors: 0,
    screenshotsFileCount: 0,
    diagramBackgroundColor: "#f0f0f0",
    diagramTextColor: "#36363a",
    cpuUtilisationColor: "#0d6efd",
    diskUsageColor: "#0d6efd",
    memoryUtilisationColor: "#0d6efd"
  },
  timer: new Timer(),
  resetText: ""
};

export const store = new BehaviorSubject<Model>(initialState);

export function set(recipe: (model: Draft<Model>)=>void) {
  const nextState = produce(store.value, recipe);
  store.next(nextState);
}
