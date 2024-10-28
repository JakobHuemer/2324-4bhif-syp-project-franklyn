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
    var fromDate = new Date();
    var toDate = new Date(fromDate);
    toDate.setHours(fromDate.getHours() + 2);


    var time1ID = this.getSchoolUnitByTime(fromDate);
    var time2ID = this.getSchoolUnitByTime(toDate);

    if (id == time1ID || id == time2ID)  {
      btnClass = ["btn-info", "text-black"];
    }

    return btnClass;
  }

  private getSchoolUnitByTime(date: Date): number {
    const times: {
      id: number,
      start: Date,
      end: Date
    }[] = [
      ...environment
        .schoolUnits
        .map((item) =>
          this.getDateByItem(item, date)),
      ...environment
        .eveningSchoolUnits
        .map((item) =>
          this.getDateByItem(item, date, true))
    ];

    for (var time of times) {
      if (date.getTime() >= time.start.getTime() &&
        date.getTime() <= time.end.getTime()) {
        return time.id;
      }
    }

    return -1;
  }

  private getDateByItem(item: {id: number, time:string}, baseDate: Date, isEveningSchool: boolean = false):
    {
      id: number,
      start: Date,
      end: Date
    } {
    const itemDate = new Date(baseDate);
    const hoursAndMinutes = this.getHoursAndMinutes(item.time);

    itemDate.setHours(hoursAndMinutes[0]);
    itemDate.setMinutes(hoursAndMinutes[1]);
    itemDate.setSeconds(0);
    itemDate.setMilliseconds(0);

    const endItemDate = new Date(itemDate);

    if (isEveningSchool) {
      endItemDate.setMinutes(
        itemDate.getMinutes() + environment.eveningSchoolUnitMinutes
      );
    } else {
      endItemDate.setMinutes(
        itemDate.getMinutes() + environment.schoolUnitMinutes
      );
    }

    return {
      id: item.id,
      start: itemDate,
      end: endItemDate
    };
  }

  private getHoursAndMinutes(time: string): number[] {
    const hoursAndMinutes = [0, 0];

    const splitTime = time.split(":");
    hoursAndMinutes[0] = Number(splitTime[0]);

    const splitMinutes = splitTime[1].split(" ");
    hoursAndMinutes[1] = Number(splitMinutes[0]);

    if (hoursAndMinutes[0] < 12 && splitMinutes[1] === "PM") {
      hoursAndMinutes[0] += 12;
    }

    return hoursAndMinutes;
  }
}
