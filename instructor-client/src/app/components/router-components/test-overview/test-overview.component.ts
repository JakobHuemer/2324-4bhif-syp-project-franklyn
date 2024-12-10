import { Component } from '@angular/core';
import {store} from "../../../model";
import {ExamSelectionListComponent} from "../../entity-lists/exam-selection-list/exam-selection-list.component";
import {ExamDashboardComponent} from "../../entity-components/exam-dashboard/exam-dashboard.component";

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

  protected readonly store = store;
}
