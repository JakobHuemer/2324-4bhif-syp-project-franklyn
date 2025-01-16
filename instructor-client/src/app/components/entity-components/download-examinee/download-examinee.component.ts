import {Component, inject, Input} from '@angular/core';
import {ExamineeService} from "../../../services/examinee.service";
import {StoreService} from "../../../services/store.service";
import {Examinee, set} from "../../../model";
import {environment} from "../../../../../env/environment";

@Component({
    selector: 'app-download-examinee',
    imports: [],
    templateUrl: './download-examinee.component.html',
    styleUrl: './download-examinee.component.css'
})
export class DownloadExamineeComponent {
  protected examineeRepo = inject(ExamineeService);

  @Input() examinee: Examinee | undefined;

  showVideoOfExaminee() {
    this.examineeRepo.newPatrolExaminee(this.examinee, true);

    set((model) => {
      model.patrolModeModel.cacheBuster.cachebustNum++;
    })
  }

  getDownloadUrl(): string {
    return `${environment.serverBaseUrl}/video/download/${this.examinee?.firstname}-${this.examinee?.lastname}`
  }
}
