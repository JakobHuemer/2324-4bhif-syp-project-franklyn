import {Component, inject, Input} from '@angular/core';
import {ExamService} from "../../../services/exam.service";
import {Exam} from "../../../model/entity/Exam";
import {ExamState} from "../../../model/entity/Exam-State";
import {RouterLink, RouterLinkActive} from "@angular/router";
import {set} from "../../../model";
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

  protected readonly ExamState = ExamState;

  protected setCurExam() {
    if (this.exam) {
      set(model => {
        model.curExam = this.exam;
      });
    }
  }
}
