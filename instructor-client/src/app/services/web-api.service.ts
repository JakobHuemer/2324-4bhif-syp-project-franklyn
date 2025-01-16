import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpHeaders} from "@angular/common/http";
import {environment} from "../../../env/environment";
import {lastValueFrom, Observable} from "rxjs";
import {StoreService} from './store.service';
import {
  set,
  ServerMetricsDto,
  ServerMetrics,
  ExamineeDto,
  Examinee,
  ExamDto,
  Exam,
  ExamState,
  CreateExam
} from "../model";

@Injectable({
  providedIn: 'root'
})
export class WebApiService {
  private httpClient = inject(HttpClient);
  private headers: HttpHeaders = new HttpHeaders().set('Accept', 'application/json');
  private store = inject(StoreService).store;

  public resetServer(): void {
    this.httpClient.post(
      `${environment.serverBaseUrl}/state/reset`,
      {}
    ).subscribe({
      error: err => console.error(err),
    });
  }

  public async getServerMetrics(): Promise<void> {
    const serverMetricsDto = await lastValueFrom(
      this.httpClient
        .get<ServerMetricsDto>(
          `${environment.serverBaseUrl}/metrics`,
          {headers: this.headers}
        )
    );

    const serverMetrics: ServerMetrics = {
      cpuUsagePercent: serverMetricsDto.cpu_usage_percent,
      totalDiskSpaceInBytes: serverMetricsDto.total_disk_space_in_bytes,
      remainingDiskSpaceInBytes: serverMetricsDto
        .remaining_disk_space_in_bytes,
      savedScreenshotsSizeInBytes: serverMetricsDto
        .saved_screenshots_size_in_bytes,
      savedVideosSizeInBytes: serverMetricsDto
        .saved_videos_size_in_bytes,
      maxAvailableMemoryInBytes: serverMetricsDto
        .max_available_memory_in_bytes,
      totalUsedMemoryInBytes: serverMetricsDto
        .total_used_memory_in_bytes
    }

    set((model) => {
      model.metricsDashboardModel.serverMetrics = serverMetrics;
    });
  }

  //region Examinee-WebApi calls

  public getExamineesFromServer(examId: number): void {
    this.httpClient.get<ExamineeDto[]>(
      `${environment.serverBaseUrl}/exams/${examId}/examinees`,
      {headers: this.headers})
      .subscribe({
        "next": (examinees) => set((model) => {
          model.patrolModeModel.examinees = examinees.map(
            (eDto) => {
              let examinee: Examinee = {
                id: eDto.id,
                firstname: eDto.firstname,
                lastname: eDto.lastname,
                isConnected: eDto.is_connected
              };

              return examinee;
            });
        }),
        "error": (err) => console.error(err),
      });
  }

  public getVideoExamineesFromServer(examId: number): void {
    this.httpClient.get<ExamineeDto[]>(
      `${environment.serverBaseUrl}/exams/${examId}/examinees`,
      {headers: this.headers})
      .subscribe({
        "next": (examinees) => set((model) => {
          model.videoViewerModel.examinees = examinees.map(
            (eDto) => {
              let examinee: Examinee = {
                id: eDto.id,
                firstname: eDto.firstname,
                lastname: eDto.lastname,
                isConnected: eDto.is_connected
              };

              return examinee;
            });
        }),
        "error": (err) => console.error(err),
      });
  }

  //endregion

  //region Exam-WebApi calls

  public getExamsFromServer(): void {
    this.httpClient.get<ExamDto[]>(
      `${environment.serverBaseUrl}/exams`,
      {headers: this.headers})
      .subscribe({
        "next": (exams) => {
          set((model) => {
            model.examDashboardModel.exams = exams.map(
              eDto => {
                let examState: ExamState = ExamState.CREATED

                switch (eDto.state) {
                  case "ONGOING":
                    examState = ExamState.ONGOING;
                    break;
                  case "DONE":
                    examState = ExamState.DONE;
                    break;
                  case "DELETED":
                    examState = ExamState.DELETED;
                    break;
                }

                let exam: Exam = {
                  id: eDto.id,
                  title: eDto.title,
                  pin: eDto.pin,
                  state: examState,
                  plannedStart: new Date(eDto.planned_start),
                  plannedEnd: new Date(eDto.planned_end),
                  actualStart: undefined,
                  actualEnd: undefined,
                  screencaptureIntervalSeconds: eDto
                    .screencapture_interval_seconds,
                  amountOfExaminees: eDto.registered_students_num
                }

                const timeZoneOffsetMinutes = (-1) * exam.plannedStart
                  .getTimezoneOffset();

                exam.plannedStart = new Date(
                  exam.plannedStart.getTime()
                  + timeZoneOffsetMinutes * 60000
                );
                exam.plannedEnd = new Date(
                  exam.plannedEnd.getTime()
                  + timeZoneOffsetMinutes * 60000
                );

                if (eDto.actual_start !== null) {
                  exam.actualStart = new Date(
                    new Date(eDto.actual_start).getTime()
                    + timeZoneOffsetMinutes * 60000
                  );
                }

                if (eDto.actual_end !== null) {
                  exam.actualEnd = new Date(
                    new Date(eDto.actual_end).getTime()
                    + timeZoneOffsetMinutes * 60000
                  );
                }

                return exam;
              }
            );

            model.examDashboardModel.exams = this.sortExams(
              model.examDashboardModel.exams
            );

            if (model.examDashboardModel.exams
              .find(e =>
                e.id === model.examDashboardModel.curExamId) === undefined
            ) {
              model.examDashboardModel.curExamId = undefined;
            }

            if (model.examDashboardModel.exams
                .find(e => e.id === model
                  .patrolModeModel.curExamId)
              === undefined) {
              model.patrolModeModel.curExamId = undefined;
              model.patrolModeModel.examinees = [];
              model.patrolModeModel.patrol.patrolExaminee = undefined;
            }

            if (model.examDashboardModel.exams
                .find(e => e.id === model
                  .videoViewerModel.curExamId)
              === undefined) {
              model.videoViewerModel.curExamId = undefined;
              model.videoViewerModel.examinees = [];
              model.videoViewerModel.examinee = undefined;
            }

            if (model.examDashboardModel.exams.length >= 1 &&
              !model.examDashboardModel.curExamId) {
              model.examDashboardModel.curExamId = model
                .examDashboardModel
                .exams[0]
                .id;
            }
          });
        },
        "error": (err) => console.error(err),
      });
  }

  public async createNewExam(exam: CreateExam): Promise<Observable<Exam>> {
    let newExam: CreateExam = {
      title: exam.title,
      start: exam.start,
      end: exam.end,
      "screencapture_interval_seconds": exam.screencapture_interval_seconds
    };

    return this.httpClient.post<Exam>(
      `${environment.serverBaseUrl}/exams`,
      newExam
    );
  }

  public deleteExamByIdFromServer(id: number): void {
    this.httpClient.delete(
      `${environment.serverBaseUrl}/exams/${id}`,
      {headers: this.headers})
      .subscribe({
        next: (response) => {
          console.log(response); // as tooltip
          this.getExamsFromServer();
        },
        error: (error) => {
          console.log(error); // as tooltip
        }
      });
  }

  public startExamByIdFromServer(exam: Exam): void {
    this.httpClient.post(
      `${environment.serverBaseUrl}/exams/${exam.id}/start`,
      {headers: this.headers})
      .subscribe({
        next: (response) => {
          console.log(response); // as tooltip
          this.getExamsFromServer();
        },
        error: (error) => {
          console.log(error); // as tooltip
        }
      });
  }

  public completeExamByIdFromServer(id: number): void {
    this.httpClient.post(
      `${environment.serverBaseUrl}/exams/${id}/complete`,
      {headers: this.headers})
      .subscribe({
        next: (response) => {
          console.log(response); // as tooltip
          this.getExamsFromServer();
        },
        error: (error) => {
          console.log(error); // as tooltip
        }
      });
  }

  private sortExams(exams: Exam[]): Exam[] {
    return exams.sort((a, b) => {
      if (a.plannedStart === b.plannedStart) {
        return (a.title < b.title) ? 1 : -1;
      }

      return (a.plannedStart < b.plannedStart) ? 1 : -1
    })
  };

  //endregion
}
