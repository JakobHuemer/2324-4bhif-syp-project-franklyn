import {Component, inject} from '@angular/core';
import {store} from "../../../model";
import {ExamSelectionListComponent} from "../../entity-lists/exam-selection-list/exam-selection-list.component";
import {ExamDashboardComponent} from "../../entity-components/exam-dashboard/exam-dashboard.component";
import {Exam} from "../../../model/entity/Exam";
import {ExamService} from "../../../services/exam.service";
import {StoreService} from "../../../services/store.service";

@Component({
    selector: 'app-test-overview',
    imports: [
        ExamSelectionListComponent,
        ExamDashboardComponent
    ],
    templateUrl: './test-overview.component.html',
    styleUrl: './test-overview.component.css'
})
export class TestOverviewComponent {

  protected readonly store = inject(StoreService).store;
  protected readonly examSvc = inject(ExamService);


  protected getCurExam(): Exam | undefined {
    return this.examSvc.get(
      (e) =>
        e.id === this.store.value.examDashboardData.curExamId)
      .at(0);
  }
}
