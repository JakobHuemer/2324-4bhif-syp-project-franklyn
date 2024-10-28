import { Component } from '@angular/core';
import {environment} from "../../../../../env/environment";
import {NgClass} from "@angular/common";

@Component({
  selector: 'app-create-exam',
  standalone: true,
  imports: [
    NgClass
  ],
  templateUrl: './create-exam.component.html',
  styleUrl: './create-exam.component.css'
})
export class CreateExamComponent {
  protected pad(num: number, size: number) {
    let s = num+"";
    while (s.length < size) s = "0" + s;
    return s;
  }

  protected readonly environment = environment;

  protected btnToggled(id: number): string | string[] {
    var btnClass: string | string[] = "btn-outline-info";

    //TODO: work with ids instead of times to work around ts
    //TODO: store in store
    var time1ID = 6;
    var time2ID = 9;

    if (id == time1ID || id == time2ID)  {
      btnClass = ["btn-info", "text-black"];
    }

    return btnClass;
  }
}
