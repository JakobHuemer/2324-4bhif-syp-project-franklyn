import {Component, inject} from '@angular/core';
import {StoreService} from "../../../services/store.service";
import {distinctUntilChanged, map} from "rxjs";
import {AsyncPipe} from "@angular/common";
import {ExamComponent} from "../../entity-components/exam/exam.component";
import {set, store} from "../../../model";

@Component({
    selector: 'app-exam-selection-list',
    imports: [
        AsyncPipe,
        ExamComponent
    ],
    templateUrl: './exam-selection-list.component.html',
    styleUrl: './exam-selection-list.component.css'
})
export class ExamSelectionListComponent {
  protected exams = inject(StoreService)
    .store
    .pipe(
      map(model =>
        model.examDashboardModel.exams
          .filter(e => e.title
            .toLowerCase()
            .includes(model.examDashboardModel.examSearch.toLowerCase()))),
      distinctUntilChanged()
    );
  protected readonly store = store;

  setExamSearch(val: string) {
    set(model => {
      model.examDashboardModel.examSearch = val;
    });
  }
}
