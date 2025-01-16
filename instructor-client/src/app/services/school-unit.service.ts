import {inject, Injectable} from '@angular/core';
import {StoreService} from "./store.service";
import {set, SchoolUnit} from "../model";
import {environment} from "../../../env/environment";

@Injectable({
  providedIn: 'root'
})
export class SchoolUnitService {

  private readonly store = inject(StoreService).store;

  constructor() {
    this.mapEnvironmentToSchoolUnits();
    this.checkIfSelected();
  }

  public checkIfSelected(): void {
    let startTimeId = this.getSchoolUnitByTime(
      this.store.value.createTestModel.createExam.start
    );
    let endTimeId = this.getSchoolUnitByTime(
      this.store.value.createTestModel.createExam.end
    );

    let schoolUnits: SchoolUnit[] = this.getSchoolUnitsSelection(
      startTimeId,
      endTimeId,
      this.store.value.createTestModel.schoolUnits
    );

    let eveningSchoolUnits: SchoolUnit[] = this.getSchoolUnitsSelection(
      startTimeId,
      endTimeId,
      this.store.value.createTestModel.eveningSchoolUnits
    );

    set(model => {
      model.createTestModel.schoolUnits = schoolUnits;
      model.createTestModel.eveningSchoolUnits = eveningSchoolUnits;
    });
  }

  private getSchoolUnitsSelection(
    timeFromId: number,
    timeToId: number,
    array: SchoolUnit[]
  ): SchoolUnit[] {
    const schoolUnits: SchoolUnit[] = [];

    for (let i = 0; i < array.length; i++) {
      schoolUnits.push({
        id: array[i].id,
        start: array[i].start,
        end: array[i].end,
        isSelected: array[i].id === timeFromId ||
          array[i].id === timeToId,
      });
    }

    return schoolUnits;
  }

  private getSchoolUnitByTime(date: Date): number {
    const times: SchoolUnit[] = [
      ...this.store.value.createTestModel.schoolUnits,
      ...this.store.value.createTestModel.eveningSchoolUnits
    ];

    let compareDate: Date = new Date(date);
    compareDate.setFullYear(times[0].start.getFullYear());
    compareDate.setMonth(times[0].start.getMonth());
    compareDate.setDate(times[0].start.getDate());

    for (let i = 0; i < times.length - 1; i++) {
      let time = times[i + 1].start;
      // fixes error, when start and end time are the same
      time.setSeconds(1);

      if (compareDate.getTime() < time.getTime()) {
        return times[i].id;
      }
    }

    return times[times.length - 1].id;
  }

  private mapEnvironmentToSchoolUnits(): void {
    let schoolUnits = environment
      .schoolUnits
      .map(su => this.getUnitByItem(su))
      .sort((a, b) => a.id - b.id);

    let eveningSchoolUnits = environment
      .eveningSchoolUnits
      .map(su => this.getUnitByItem(su, true))
      .sort((a, b) => a.id - b.id);

    set(model => {
      model.createTestModel.schoolUnits = schoolUnits;
      model.createTestModel.eveningSchoolUnits = eveningSchoolUnits;
    });
  }

  private getUnitByItem(item: { id: number, time: string }, isEveningSchool: boolean = false): SchoolUnit {
    const itemDate = new Date(0, 0, 0, 0, 0, 0, 0);
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
      end: endItemDate,
      isSelected: false
    };
  }

  public getHoursAndMinutes(time: string): number[] {
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

}
