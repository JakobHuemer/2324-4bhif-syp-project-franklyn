import {Component, inject, Input, OnInit} from '@angular/core';
import {ExamService} from "../../../services/exam.service";
import {Exam} from "../../../model/entity/Exam";
import {ExamState} from "../../../model/entity/Exam-State";
import {RouterLink, RouterLinkActive} from "@angular/router";
import {FormsModule, ReactiveFormsModule} from "@angular/forms";

@Component({
    selector: 'app-exam-dashboard',
    imports: [
        RouterLink,
        RouterLinkActive,
        FormsModule,
        ReactiveFormsModule
    ],
    templateUrl: './exam-dashboard.component.html',
    styleUrl: './exam-dashboard.component.css'
})
export class ExamDashboardComponent {
  protected examSvc = inject(ExamService);

  @Input() exam: Exam | undefined;

  getDateFromExam(): string {
    if (!this.exam)
      return "";

    return (this.exam.plannedStart.getMonth() + 1) + '.' +
      this.exam.plannedStart.getDate() + '.' +
      this.exam.plannedStart.getFullYear();
  }

  protected pad(num: number, size: number) {
    let s = num+"";
    while (s.length < size) s = "0" + s;
    return s;
  }

  getFormattedTimeFrom(date: Date): string {
    return this.pad(date.getHours(), 2) + ":" +
      this.pad(date.getMinutes(), 2);
  }

  protected setCurExam() {
    if (this.exam) {
      this.examSvc.setCurExam(this.exam);
    }
  }

  protected stopExam(){
    if (this.exam) {
      this.examSvc.stopExam(this.exam.id);
    }
  }

  protected examStateToString(state: ExamState): string {
    switch (state) {
      case ExamState.ONGOING:
        return "ONGOING";
      case ExamState.DONE:
        return "DONE";
      case ExamState.DELETED:
        return "DELETED";
      default:
        return "CREATED";
    }
  }

  protected readonly ExamState = ExamState;
}
