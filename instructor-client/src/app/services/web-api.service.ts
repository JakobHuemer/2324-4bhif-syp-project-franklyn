import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpHeaders} from "@angular/common/http";
import {environment} from "../../../env/environment";
import {lastValueFrom, map, Observable} from "rxjs";
import {
  set,
  ServerMetricsDto,
  ServerMetrics,
  ExamineeDto,
  Examinee,
  ExamDto,
  Exam,
  ExamState,
  CreateExam, Job, JobDto, JobState,
  ProfilingMetricsDto,
  ProfilingMetrics,
  HistogramData,
  VertxPoolMetrics
} from "../model";
import {ToastService} from "./toast.service";

@Injectable({
  providedIn: 'root'
})
export class WebApiService {
  private toastSvc = inject(ToastService);
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

  public async getProfilingMetrics(): Promise<void> {
    try {
      const dto = await lastValueFrom(
        this.httpClient
          .get<ProfilingMetricsDto>(
            `${environment.serverBaseUrl}/metrics/profiling`,
            {headers: this.headers}
          )
      );

      const profilingMetrics: ProfilingMetrics = {
        queueSize: dto.queue_size,
        activeRequests: dto.active_requests,
        timeoutsTotal: dto.timeouts_total,
        uploadsTotal: dto.uploads_total,
        uploadsPerSecond: dto.uploads_per_second,
        connectedClients: dto.connected_clients,
        videoJobsQueued: dto.video_jobs_queued,
        videoJobsActive: dto.video_jobs_active,
        averageImageUploadSizeBytes: dto.average_image_upload_size_bytes,
        averageImageDownloadSizeBytes: dto.average_image_download_size_bytes,
        screenshotRequestLatency: this.mapHistogramData(dto.screenshot_request_latency),
        imageDecode: this.mapHistogramData(dto.image_decode),
        imageEncode: this.mapHistogramData(dto.image_encode),
        betaFrameMerge: this.mapHistogramData(dto.beta_frame_merge),
        imageFileRead: this.mapHistogramData(dto.image_file_read),
        imageSaveTotal: this.mapHistogramData(dto.image_save_total),
        websocketMessageSend: this.mapHistogramData(dto.websocket_message_send),
        websocketPingLatency: this.mapHistogramData(dto.websocket_ping_latency),
        imageDownload: this.mapHistogramData(dto.image_download),
        videoFfmpeg: this.mapHistogramData(dto.video_ffmpeg),
        videoZip: this.mapHistogramData(dto.video_zip),
        vertxWorkerPool: this.mapVertxPoolMetrics(dto.vertx_worker_pool),
        vertxEventLoop: this.mapVertxPoolMetrics(dto.vertx_event_loop),
      };

      set((model) => {
        model.metricsDashboardModel.profilingMetrics = profilingMetrics;
      });
    } catch (err) {
      console.error('Failed to fetch profiling metrics:', err);
    }
  }

  private mapHistogramData(dto: { p50_ms: number; p90_ms: number; p99_ms: number; max_ms: number; count: number }): HistogramData {
    return {
      p50Ms: dto.p50_ms,
      p90Ms: dto.p90_ms,
      p99Ms: dto.p99_ms,
      maxMs: dto.max_ms,
      count: dto.count
    };
  }

  private mapVertxPoolMetrics(dto: { pool_size: number; in_use: number; ratio: number; queue_size: number; completed: number }): VertxPoolMetrics {
    return {
      poolSize: dto.pool_size,
      inUse: dto.in_use,
      ratio: dto.ratio,
      queueSize: dto.queue_size,
      completed: dto.completed
    };
  }

  //region Job-WebApi calls

  getAllExamVideos(
    examId: number,
    startedToastId: number): void {
    this.httpClient.post(
      `${environment.serverBaseUrl}/telemetry/by-exam/${examId}/video/generate-all`,
      {headers: this.headers})
      .subscribe({
        "next": () => this.getAllJobsForExam(examId),
        "error": (err) => {
          console.error(err);
          this.toastSvc.addToast(
            "Error generating exam videos.",
            err.error,
            "error"
          );
          this.toastSvc.removeToast({
            id: startedToastId, message: "", title: "", type: ""
          });
        },
      });
  }

  getExamExamineeVideo(
    examId: number,
    examineeId: number,
    startedToastId: number
  ): void {
    this.httpClient.post(
      `${environment.serverBaseUrl}/telemetry/by-user/${examineeId}/${examId}/video/generate`,
      {headers: this.headers})
      .subscribe({
        "next": () => this.getAllJobsForExam(examId),
        "error": (err) => {
          console.error(err);
          this.toastSvc.addToast(
            "Error generating examinee video.",
            err.error,
            "error"
          );
          this.toastSvc.removeToast({
            id: startedToastId, message: "", title: "", type: ""
          });
        },
      });
  }

  getAllJobsForExam(curExamId: number) {
    this.httpClient.get<JobDto[]>(
      `${environment.serverBaseUrl}/exams/${curExamId}/videojobs`,
      {headers: this.headers})
      .subscribe({
        "next": (jobDto) => {
          set((model) => {
            model.jobServiceModel.jobs = this.sortJobs(
              jobDto.map(
                (jDto) => {
                  let jobState: JobState = JobState.QUEUED;

                  switch (jDto.state) {
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

                  let finishedAt: Date | undefined;

                  if (jDto.finished_at !== null) {
                    finishedAt =
                      new Date(
                        new Date(jDto.finished_at)
                          .getTime() + 60 * 60 * 1000
                      );
                  }

                  let job: Job = {
                    id: jDto.id,
                    state: jobState,
                    examId: curExamId,
                    examineeId: jDto.examinee_id,
                    createdAt: new Date(
                      new Date(jDto.created_at)
                        .getTime() + 60 * 60 * 1000
                    ),
                    finishedAt: finishedAt,
                    error_message: jDto.error_message,
                  };

                  return job;
                }
              )
            );
          });
        },
        "error": (err) => console.error(err),
      });
  }

  private sortJobs(jobs: Job[]): Job[] {
    return jobs
      .sort((a, b) => {
        let aTime = a.finishedAt ? a.finishedAt : a.createdAt;
        let bTime = b.finishedAt ? b.finishedAt : b.createdAt;

        if (aTime < bTime) {
          return 1;
        } else if (aTime === bTime) {
          return (a.id < b.id ? 1 : -1);
        } else {
          return -1;
        }
      });
  };

  //endregion

  //region Examinee-WebApi calls

  public getExamineesFromServer(examId: number): Observable<void> {
    return this.httpClient.get<ExamineeDto[]>(
      `${environment.serverBaseUrl}/exams/${examId}/examinees`,
      {headers: this.headers})
      .pipe(
        map(examinees => {
          set((model) => {
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
                if (a.isConnected !== b.isConnected) {
                  return a.isConnected ? 1 : -1;
                }

                const lastNameComparison = a.lastname
                  .localeCompare(b.lastname);

                if (lastNameComparison !== 0) {
                  return lastNameComparison;
                }

                return a.firstname.localeCompare(b.firstname);
              });

            model.patrolModeModel.patrol.patrolExaminee = model
              .patrolModeModel
              .examinees
              .find(e =>
                e.id === model.patrolModeModel.patrol.patrolExaminee?.id
              );
          });
        })
      );
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

              model.videoViewerModel.curExamId = model
                .examDashboardModel
                .curExamId;
              model.videoViewerModel.examinees = [];
              model.videoViewerModel.examinee = undefined;
              this.getVideoExamineesFromServer(model.videoViewerModel.curExamId);
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
      `${environment.serverBaseUrl}/exams/${id}/`,
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

  public deleteExamTelemetryByIdFromServer(id: number): void {
    this.httpClient.delete(
      `${environment.serverBaseUrl}/exams/${id}/telemetry`,
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
        const dateA = new Date(a.plannedStart);
        const dateB = new Date(b.plannedStart);

        dateA.setSeconds(0, 0);
        dateB.setSeconds(0, 0);

        if (dateA.getTime() > dateB.getTime()) {
          return 1;
        } else if (a.plannedStart === b.plannedStart) {
          return (a.title > b.title ? 1 : -1);
        } else {
          return -1;
        }
      });
  };

  //endregion
}
