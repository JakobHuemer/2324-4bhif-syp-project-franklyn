import {Component, inject, Input} from '@angular/core';
import {ExamService} from "../../../services/exam.service";
import {NgClass} from "@angular/common";
import {Exam, set} from "../../../model";

@Component({
    selector: 'app-exam',
    imports: [
        NgClass
    ],
    templateUrl: './exam.component.html',
    styleUrl: './exam.component.css'
})
export class ExamComponent {
  private examSvc = inject(ExamService);
  protected isHovered = false;

  @Input() exam: Exam | undefined;

  protected setExamToCurExam() {
    if (this.exam !== undefined) {
      this.examSvc.setCurDashboardExam(this.exam);
      this.examSvc.setCurVideoExam(this.exam);
    }
  }

  protected isCurExam(): boolean {
    if (this.exam) {
      return this.examSvc.isCurDashboardExam(this.exam.id);
    } else {
      return false;
    }
  }

  getDateFromExam(): string {
    if (!this.exam)
      return "";

    return (this.exam.plannedStart.getMonth() + 1) + '.' +
      this.exam.plannedStart.getDate() + '.' +
      this.exam.plannedStart.getFullYear();
  }

  getTimeFromExam(): string {
    if (!this.exam)
      return "";

    return this.exam.plannedStart.getHours() + ":" + this.exam.plannedStart.getMinutes();
  }

  setExamClassHoverOrCur(): string[] {
    if (this.isHovered || this.isCurExam()) {
      return ['text-bg-dark', 'bg-secondary'];
    } else {
      return ['text-bg-light'];
    }
  }
}
