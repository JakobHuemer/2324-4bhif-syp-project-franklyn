import {Component, inject, Input} from '@angular/core';
import {StoreService} from "../../../services/store.service";
import {Examinee} from "../../../model";
import {environment} from "../../../../../env/environment";
import {RouterLink, RouterLinkActive} from "@angular/router";

@Component({
    selector: 'app-video-examinee',
    imports: [
        RouterLink,
        RouterLinkActive
    ],
    templateUrl: './video-examinee.component.html',
    styleUrl: './video-examinee.component.css'
})
export class VideoExamineeComponent {
  private store = inject(StoreService).store;

  @Input() examinee: Examinee | undefined;

  getVideoUrl(): string {
    return `${environment.serverBaseUrl}/video/${this.examinee?.firstname}-${this.examinee?.lastname}?cache=${this.store.value.patrolModeModel.cacheBuster.cachebustNum}`; //TODO: get new video url //examinee gets checked in the html
  }

  showVideo(): boolean {
    return this.examinee !== undefined &&
      this.store.value.patrolModeModel.patrol.patrolExaminee?.firstname === this.examinee.firstname &&
      this.store.value.patrolModeModel.patrol.patrolExaminee?.lastname === this.examinee.lastname;
  }
}
