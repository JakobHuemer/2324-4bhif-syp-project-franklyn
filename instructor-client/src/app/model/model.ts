import {BehaviorSubject} from "rxjs";
import {Draft, produce} from "immer";
import {CacheBuster} from "./entity/CacheBuster";
import {ExamineeData} from "./entity/ExamineeData";
import {Patrol} from "./entity/Patrol";
import {ServerMetrics} from "./entity/ServerMetrics";
import {Timer} from "./entity/Timer";
import {ExamData} from "./entity/ExamData";
import {CreateExam} from "./entity/CreateExam";
import {environment} from "../../../env/environment";
import {SchoolUnit} from "./entity/SchoolUnit";
import {Exam} from "./entity/Exam";
import {Examinee} from "./entity/Examinee";

export interface Model {
  readonly cacheBuster: CacheBuster,
  readonly curExamId: number | undefined,
  readonly examineeData: ExamineeData,
  readonly curVideoExamId: number | undefined,
  readonly videoExamineeData: ExamineeData,
  readonly videoExaminee: Examinee | undefined,
  readonly examDashboardData: ExamData,
  readonly patrol: Patrol,
  readonly serverMetrics: ServerMetrics,
  readonly timer: Timer,
  readonly resetText: string,
  readonly createExam: CreateExam
  readonly createdExam: boolean;
  readonly schoolUnits: SchoolUnit[];
  readonly eveningSchoolUnits: SchoolUnit[];
}

const initialState: Model = {
  cacheBuster: {
    cachebustNum: 0
  },
  curExamId: undefined,
  curVideoExamId: undefined,
  examineeData: {
    examinees: []
  },
  videoExamineeData: {
    examinees: []
  },
  examDashboardData: {
    exams: [],
    curExamId: undefined
  },
  patrol: {
    isPatrolModeOn: false,
    patrolExaminee: undefined
  },
  videoExaminee: undefined,
  serverMetrics: {
    cpuUsagePercent: 0,
    totalDiskSpaceInBytes: 0,
    remainingDiskSpaceInBytes: 0,
    savedScreenshotsSizeInBytes: 0,
    savedVideosSizeInBytes: 0,
    maxAvailableMemoryInBytes: 0,
    totalUsedMemoryInBytes: 0,
    diagramBackgroundColor: "#f0f0f0",
    diagramTextColor: "#36363a",
    cpuUtilisationColor: "#0d6efd",
    diskUsageScreenshotColor: "rgb(54, 162, 235)",
    diskUsageVideoColor: "rgb(255, 99, 132)",
    diskUsageOtherColor: "rgb(255, 205, 86)",
    memoryUtilisationColor: "#0d6efd"
  },
  timer: new Timer(),
  resetText: "",
  createExam: {
    title: "",
    start: new Date(Date.now()),
    end: new Date(Date.now()+ 3000000), // add 50 minutes
    screencapture_interval_seconds: environment.patrolSpeed
  },
  createdExam: false,
  schoolUnits: [],
  eveningSchoolUnits: []
};

export const store = new BehaviorSubject<Model>(initialState);

export function set(recipe: (model: Draft<Model>)=>void) {
  const nextState = produce(store.value, recipe);
  store.next(nextState);
}
