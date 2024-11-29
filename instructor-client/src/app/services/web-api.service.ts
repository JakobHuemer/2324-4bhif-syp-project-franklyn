import {inject, Injectable} from '@angular/core';
import { HttpClient, HttpHeaders } from "@angular/common/http";
import {Examinee, ServerMetrics} from "../model";
import {environment} from "../../../env/environment";
import {lastValueFrom, Observable} from "rxjs";
import {set} from "../model";
import {Exam} from "../model/entity/Exam";
import {ExamDto} from "../model/entity/dto/ExamDto";
import {CreateExam} from "../model/entity/CreateExam";

@Injectable({
  providedIn: 'root'
})
export class WebApiService {
  private httpClient = inject(HttpClient);
  private headers: HttpHeaders = new HttpHeaders().set('Accept', 'application/json');

  public async resetServer(): Promise<void> {
    this.httpClient.post(
      `${environment.serverBaseUrl}/state/reset`,
      {}
    ).subscribe({
      error: err => console.error(err),
    });
  }

  public async getServerMetrics(): Promise<void> {
    const serverMetrics: ServerMetrics = await lastValueFrom(
      this.httpClient
        .get<ServerMetrics>(
        `${environment.serverBaseUrl}/state/system-metrics`,
        {headers: this.headers}
      )
    );

    set((model) => {
      model.serverMetrics = serverMetrics;
    });
  }

  //region Examinee-WebApi calls

  public async getExamineesFromServer(examId: number): Promise<void> {
    this.httpClient.get<Examinee[]>(
      `${environment.serverBaseUrl}/exams/${examId}/examinees`,
      {headers: this.headers})
      .subscribe({
        "next": (examinees) => set((model) => {
          model.examineeData.examinees = examinees;
        }),
        "error": (err) => console.error(err),
      });
  }

  //endregion

  //region Exam-WebApi calls

  public async getExamsFromServer(): Promise<void> {
    this.httpClient.get<ExamDto[]>(
      `${environment.serverBaseUrl}/exams`,
      {headers: this.headers})
      .subscribe({
        "next": (exams) => {
          set((model) => {
            model.examData.exams = exams.map(
              eDto => {
                let exam: Exam = {
                  id: eDto.id,
                  title: eDto.title,
                  pin: eDto.pin,
                  state: eDto.state,
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

            model.examData.exams = this.sortExams(
              model.examData.exams
            );

            if (model.examData.exams.length >= 1) {
              model.examData.curExam = model.examData.exams[0];
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
      screencapture_interval_seconds: exam.screencapture_interval_seconds
    };

    return this.httpClient.post<Exam>(
      `${environment.serverBaseUrl}/exams`,
      newExam
    );
  }

  public async deleteExamByIdFromServer(id: number): Promise<void> {
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
