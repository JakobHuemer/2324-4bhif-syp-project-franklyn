import {Component, inject} from '@angular/core';
import {NgClass} from "@angular/common";
import {ToastDto} from "../../../model";
import {StoreService} from "../../../services/store.service";
import {ToastService} from "../../../services/toast.service";

@Component({
  selector: 'app-toast',
  imports: [
    NgClass
  ],
  templateUrl: './toast.component.html',
  styleUrl: './toast.component.css'
})
export class ToastComponent {
  protected readonly store = inject(StoreService).store;
  protected readonly toastSvc = inject(ToastService);

  closeToast(toastDto: ToastDto) {
    this.toastSvc.removeToast(toastDto);
  }
}
