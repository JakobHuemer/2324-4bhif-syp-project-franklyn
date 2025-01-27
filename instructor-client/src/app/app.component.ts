import {Component, inject} from '@angular/core';
import {CommonModule, Location} from '@angular/common';
import {RouterLink, RouterLinkActive, RouterOutlet} from '@angular/router';
import {environment} from "../../env/environment";
import {StoreService} from "./services/store.service";
import {set} from "./model";
import {ScheduleService} from "./services/schedule.service";
import {FormsModule} from "@angular/forms";
import {ToastComponent} from "./components/entity-components/toast/toast.component";

@Component({
    selector: 'app-root',
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive, FormsModule, ToastComponent],
    templateUrl: './app.component.html',
    styleUrl: './app.component.css'
})
export class AppComponent {
  protected store = inject(StoreService).store;
  protected location = inject(Location);
  protected scheduleSvc = inject(ScheduleService);

  protected readonly environment = environment;
  protected readonly Number = Number;

  public changeRoute() {
    if (this.location.path() === "metrics-dashboard" ||
      this.location.path() === "create-test" ||
      this.location.path() === "test-overview/edit-test-view"){
      this.scheduleSvc.stopUpdateDataScheduleInterval();
    } else {
      this.scheduleSvc.startUpdateDataScheduleInterval();
    }

    set((model) => {
      //safety measure to prevent any possible bugs
      model.patrolModeModel.patrol.patrolExaminee = undefined;
      model.createTestModel.createdExam = false;
    })
  }
}
