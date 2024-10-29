import {AfterViewInit, Component, ElementRef, inject, model, ViewChild} from '@angular/core';
import {environment} from "../../../../../env/environment";
import {NgClass} from "@angular/common";
import {StoreService} from "../../../services/store.service";
import {FormsModule} from "@angular/forms";
import {set, store} from "../../../model";

@Component({
  selector: 'app-create-exam',
  standalone: true,
  imports: [
    NgClass,
    FormsModule
  ],
  templateUrl: './create-exam.component.html',
  styleUrl: './create-exam.component.css'
})
export class CreateExamComponent implements AfterViewInit{
  protected store = inject(StoreService).store;

  protected readonly environment = environment;

  protected isValidTitle: boolean = this.store
    .value
    .createExam
    .title !== "";
  protected isValidDate: boolean = true;
  protected isValidEndTime: boolean = this.store.value.createExam
    .end > this.store.value.createExam.start;

  @ViewChild("startTime") startTimeInput!: ElementRef;
  @ViewChild("endTime") endTimeInput!: ElementRef;

  ngAfterViewInit() {
    this.startTimeInput.nativeElement.value = this.dateToTimeString(
      this.store.value.createExam.start
    );

    this.endTimeInput.nativeElement.value = this.dateToTimeString(
      this.store.value.createExam.end
    );
  }

  protected pad(num: number, size: number) {
    let s = num+"";
    while (s.length < size) s = "0" + s;
    return s;
  }

  protected btnToggled(id: number): string | string[] {
    var btnClass: string | string[] = "btn-outline-info";

    var fromDate = this.store.value.createExam.start;
    var toDate = this.store.value.createExam.end;

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
          this.getUnitByItem(item, date)),
      ...environment
        .eveningSchoolUnits
        .map((item) =>
          this.getUnitByItem(item, date, true))
    ];

    for (var i = 0; i < times.length - 1; i++) {
      if (date.getTime() < times[i+1].start.getTime()) {
        return times[i].id;
      }
    }

    return times[times.length-1].id;
  }

  private getUnitByItem(item: {id: number, time:string}, baseDate: Date, isEveningSchool: boolean = false):
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

    if (splitTime.length === 2) {
      hoursAndMinutes[0] = Number(splitTime[0]);
      const splitMinutes = splitTime[1].split(" ");
      hoursAndMinutes[1] = Number(splitMinutes[0]);

      if (splitMinutes.length === 2) {
        if (hoursAndMinutes[0] < 12 && splitMinutes[1] === "PM") {
          hoursAndMinutes[0] += 12;
        }
      }
    }

    return hoursAndMinutes;
  }

  protected setIsValidTitle(title: string): void {
    this.isValidTitle = title !== "";

    if (this.store.value.createExam.title !== title) {
      set(model => {
        model.createExam.title = title
      });
    }
  }

  protected setIsValidDate(dateString: string): void {
    this.isValidDate = dateString !== "";

    if (this.isValidDate) {
      var date = new Date(dateString);

      var startDate = new Date(
        this.store.value.createExam.start.getTime()
      );

      startDate.setFullYear(date.getFullYear());
      startDate.setMonth(date.getMonth());
      startDate.setDate(date.getDate());

      var endDate = new Date(
        this.store.value.createExam.end.getTime()
      );

      endDate.setFullYear(date.getFullYear());
      endDate.setMonth(date.getMonth());
      endDate.setDate(date.getDate());

      set(model => {
        model.createExam.start = startDate;
        model.createExam.end = endDate;
      });
    }
  }

  protected setPatrolSpeed(val: string): void {
    var patrolSpeed = Number(val);
    set((model) => {
      model.timer.patrolSpeed = patrolSpeed;
    });
  }

  protected setStartTime(startTimeString: string, endTimeString: string): void {
    var startTime: number[] = this.getHoursAndMinutes(startTimeString);
    var storeStartDate = this.store.value.createExam.start;

    if (startTime[0] !== storeStartDate.getHours() ||
      startTime[1] !== storeStartDate.getMinutes()) {
      var startDate = this.store.value.createExam.start;
      startDate.setHours(startTime[0]);
      startDate.setMinutes(startTime[1]);
      startDate.setSeconds(0);
      startDate.setMilliseconds(0);

      set(model => {
        model.createExam.start = startDate;
      });
    }

    this.setIsValidEndTime(startTimeString, endTimeString);
  }

  protected setEndTime(startTimeString: string, endTimeString: string): void {
    var endTime: number[] = this.getHoursAndMinutes(endTimeString);
    var storeEndDate = this.store.value.createExam.end;

    if (endTime[0] !== storeEndDate.getHours() ||
      endTime[1] !== storeEndDate.getMinutes()) {
      var endDate = this.store.value.createExam.end;
      endDate.setHours(endTime[0]);
      endDate.setMinutes(endTime[1]);
      endDate.setSeconds(0);
      endDate.setMilliseconds(0);

      set(model => {
        model.createExam.end = endDate;
      });
    }

    this.setIsValidEndTime(startTimeString, endTimeString);
  }

  private setIsValidEndTime(startTimeString: string, endTimeString: string): void {
    var startTime: number[] = this.getHoursAndMinutes(startTimeString);
    var endTime: number[] = this.getHoursAndMinutes(endTimeString);

    this.isValidEndTime = !(endTime[0] < startTime[0] ||
      (
        endTime[0] === startTime[0] &&
        endTime[1] < startTime[1]
      ));
  }

  protected dateToTimeString(date: Date): string {
    var timeString = `${date.getHours()}:`;

    if (date.getHours() < 10) {
      timeString = "0" + timeString;
    }

    if (date.getMinutes() < 10) {
      timeString += "0";
    }

    timeString += date.getMinutes();

    return timeString;
  }

  protected selectedUnit(item: {
    id: number,
    time: string
  }): void {
    let classes = this.btnToggled(item.id);
    let date = this.getUnitByItem(
      item,
      this.store.value.createExam.start,
      false
    );
    const isStartDate = this.isStartDate(date.start);

    if (Array.isArray(classes)) {
      if (isStartDate) {
        let id = this.getSchoolUnitByTime(
          this.store.value.createExam.start
        );
        let result = this
          .getSchoolUnitById(id);

        if (result !== undefined) {
          this.setStartDate(result.start);
          this.setEndDate(result.end);
        }
      } else {
        let id = this.getSchoolUnitByTime(
          this.store.value.createExam.end
        );
        let result = this
          .getSchoolUnitById(id);

        if (result !== undefined) {
          this.setStartDate(result.start);
          this.setEndDate(result.end);
        }
      }
    } else {
      if (isStartDate) {
        this.setStartDate(date.start);
      } else {
        this.setEndDate(date.end);
      }
    }
  }

  private isStartDate(date: Date): boolean {
    let startDate: Date = this.store.value.createExam.start;
    let endDate: Date = this.store.value.createExam.end;

    let result = false;

    // switch if end time is not valid since
    // we just assume that the first selected one is a start time
    if (!this.isValidEndTime) {
      const bucket = endDate;
      endDate = startDate;
      startDate = bucket;
    }

    if (date.getTime() < endDate.getTime()) {
      if (date.getTime() < startDate.getTime()) {
        result = true;
      } else if(
        Math.abs(startDate.getTime() - date.getTime()) <
        Math.abs(endDate.getTime() - date.getTime())
      ) {
        // startDate is closer
        result = true;
      }
    }

    return result;
  }

  private getSchoolUnitById(id: number): {
    id: number,
    start: Date,
    end: Date
  } | undefined {
    let isEveningSchool = false;
    let results = environment.schoolUnits
      .filter(su => su.id === id);

    if (results.length === 0) {
      results = environment.eveningSchoolUnits
        .filter(su => su.id === id);
      isEveningSchool = true;
    }

    if (results.length === 1) {
      return this.getUnitByItem(
        results[0],
        this.store.value.createExam.start,
        isEveningSchool
      );
    } else {
      return undefined;
    }
  }

  private setStartDate(date: Date) {
    set(model => {
      model.createExam.start = date;
    });

    this.startTimeInput.nativeElement.value = this.dateToTimeString(
      this.store.value.createExam.start
    );
  }

  private setEndDate(date: Date) {
    set(model => {
      model.createExam.end = date;
    });

    this.endTimeInput.nativeElement.value = this.dateToTimeString(
      this.store.value.createExam.end
    );
  }

  protected createTestBtnClicked(): void {
    //TODO: notification that exam successfully created with the new pin

    set(model => {
      model.createExam.title = "";
      model.createExam.start = new Date(Date.now());
      model.createExam.end = new Date(Date.now() + 3000000);
      model.createExam.screencapture_interval_seconds = environment
        .patrolSpeed;
    });
    this.isValidTitle = this.store
      .value
      .createExam
      .title !== "";
    this.isValidDate = true;
    this.isValidEndTime = this.store.value.createExam
      .end > this.store.value.createExam.start;
  }

}
