import {Component, inject} from '@angular/core';
import {CommonModule, Location} from '@angular/common';
import {RouterLink, RouterLinkActive, RouterOutlet} from '@angular/router';
import {environment} from "../../env/environment";
import {StoreService} from "./services/store.service";
import {ExamineeService} from "./services/examinee.service";
import {set} from "./model";
import {WebApiService} from "./services/web-api.service";
import {ScheduleService} from "./services/schedule.service";
import {FormsModule} from "@angular/forms";

@Component({
    selector: 'app-root',
    imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive, FormsModule],
    templateUrl: './app.component.html',
    styleUrl: './app.component.css'
})
export class AppComponent {
  protected store = inject(StoreService).store;
  protected examineeSvc = inject(ExamineeService);
  protected location = inject(Location);
  protected scheduleSvc = inject(ScheduleService);

  protected readonly environment = environment;
  protected readonly Number = Number;

  public changeRoute() {
    if (this.location.path() === "patrol-mode") {
      this.scheduleSvc.startUpdateDataScheduleInterval();
      this.scheduleSvc.startPatrolInterval();
    } else {
      this.scheduleSvc.stopUpdateDataScheduleInterval();
      this.scheduleSvc.stopPatrolInterval();
    }

    set((model) => {
      //safety measure to prevent any possible bugs
      model.patrolModeModel.patrol.patrolExaminee = undefined;
      model.patrolModeModel.patrol.isPatrolModeOn = false;
      model.createTestModel.createdExam = false;
    })
  }
}
