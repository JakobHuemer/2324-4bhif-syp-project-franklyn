import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpHeaders} from "@angular/common/http";
import {environment} from "../../../env/environment";
import {lastValueFrom, Observable} from "rxjs";
import {
  set,
  ServerMetricsDto,
  ServerMetrics,
  ExamineeDto,
  Examinee,
  ExamDto,
  Exam,
  ExamState,
  CreateExam, Job, JobDto, JobState
} from "../model";

@Injectable({
  providedIn: 'root'
})
export class WebApiService {
  private httpClient = inject(HttpClient);
  private headers: HttpHeaders = new HttpHeaders().set('Accept', 'application/json');

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

  //region Job-WebApi calls

  getAllExamVideos(examId: number): void {
    this.httpClient.post<JobDto>(
      `${environment.serverBaseUrl}/telemetry/by-exam/${examId}/video/generate-all`,
      {headers: this.headers})
      .subscribe({
        "next": (jobDto) => this.manageJob(jobDto, true),
        "error": (err) => console.error(err),
      });
  }

  getExamExamineeVideo(examId: number, examineeId: number, shouldDownload: boolean): void {
    this.httpClient.post<JobDto>(
      `${environment.serverBaseUrl}/telemetry/by-user/${examineeId}/${examId}/video/generate`,
      {headers: this.headers})
      .subscribe({
        "next": (jobDto) => this.manageJob(jobDto, shouldDownload),
        "error": (err) => console.error(err),
      });
  }

  getJobStatus(jobId: number) {
    this.httpClient.get<JobDto>(
      `${environment.serverBaseUrl}/telemetry/jobs/video/${jobId}`,
      {headers: this.headers})
      .subscribe({
        "next": (jobDto) => this.manageJob(jobDto, false),
        "error": (err) => console.error(err),
      });
  }

  private manageJob(job: JobDto, shouldDownload: boolean): void {
    set((model) => {
      let jobState: JobState = JobState.QUEUED;

      switch (job.state) {
        case "ONGOING":
          jobState = JobState.ONGOING;
          break;
        case "FAILED":
          jobState = JobState.FAILED;
          break;
        case "DONE":
          jobState = JobState.DONE;
          break;
        case "DELETED":
          jobState = JobState.DELETED;
          break;
      }

      let index = model.jobServiceModel.jobs
        .findIndex(j => j.id === job.id);

      if (index === -1) {
        model.jobServiceModel.jobs.push({
          examineeId: undefined,
          id: job.id,
          state: jobState,
          shouldDownload: shouldDownload,
          logs: [{
            state: jobState,
            message: '', //TODO: get Job message as well
            timestamp: new Date(Date.now()),
          }]
        });
      } else {
        model.jobServiceModel.jobs[index].state = jobState;
        model.jobServiceModel.jobs[index].logs.push({
          state: jobState,
          message: '', //TODO: get Job message as well
          timestamp: new Date(Date.now())
        });
      }
    });
  }

  //endregion

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
            })
            .sort((a, b) => {
              if (a.lastname > b.lastname) {
                return 1;
              } else if (a.lastname === b.lastname) {
                return (a.firstname > b.firstname ? 1 : -1);
              } else {
                return -1;
              }
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
            })
            .sort((a, b) => {
              if (a.lastname > b.lastname) {
                return 1;
              } else if (a.lastname === b.lastname) {
                return (a.firstname > b.firstname ? 1 : -1);
              } else {
                return -1;
              }
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
    return exams
      .sort((a, b) => {
        if (a.title > b.title) {
          return 1;
        } else if (a.title === b.title) {
          return (a.plannedStart > b.plannedStart ? 1 : -1);
        } else {
          return -1;
        }
      });
  };

  //endregion
}
