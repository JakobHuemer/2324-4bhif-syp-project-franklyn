import {AfterViewInit, ChangeDetectorRef, Component, ElementRef, inject, ViewChild} from '@angular/core';
import {environment} from "../../../../../env/environment";
import {NgClass} from "@angular/common";
import {StoreService} from "../../../services/store.service";
import {FormsModule} from "@angular/forms";
import {CreateExam, SchoolUnit, set} from "../../../model";
import {ExamService} from "../../../services/exam.service";
import {distinctUntilChanged, map} from "rxjs";
import {SchoolUnitService} from "../../../services/school-unit.service";
import {ToastService} from "../../../services/toast.service";

@Component({
    selector: 'app-create-exam',
    imports: [
        NgClass,
        FormsModule
    ],
    templateUrl: './create-exam.component.html',
    styleUrl: './create-exam.component.css'
})
export class CreateExamComponent implements AfterViewInit{
  protected store = inject(StoreService).store;
  private examSvc = inject(ExamService);
  private schoolUnitSvc = inject(SchoolUnitService);
  private readonly cdRef = inject(ChangeDetectorRef);
  private readonly toastSvc = inject(ToastService);

  protected readonly environment = environment;

  protected isValidTitle: boolean = this.store
    .value
    .createTestModel
    .createExam
    .title !== "";
  protected isValidDate: boolean = true;
  protected isEndTimeAfterStartTime: boolean = this.store.value
    .createTestModel
    .createExam
    .end > this.store.value.createTestModel.createExam.start;
  protected isValidStartTime: boolean = this.store.value
    .createTestModel
    .createExam
    .start > new Date(Date.now());
  protected isValidEndTime: boolean = this.store.value
    .createTestModel
    .createExam
    .end > new Date(Date.now());
  protected dateNotInPast: boolean = true;

  protected createdExamInfo: string = "";
  protected createdExamValue: string = "";
  protected textColourForCreateExamResponse: string = "";

  @ViewChild("startTime") startTimeInput!: ElementRef | undefined;
  @ViewChild("endTime") endTimeInput!: ElementRef | undefined;
  @ViewChild("date") dateInput!: ElementRef | undefined;

  ngAfterViewInit() {
    this.initialiseTimeInputs();

    this.store.pipe(
      map(model => model.createTestModel.createdExam),
      distinctUntilChanged()
    ).subscribe(() => {
      this.cdRef.detectChanges();
      this.initialiseTimeInputs();
      this.checkAllFieldsIfValid();
    });
  }

  private initialiseTimeInputs() {

    if (this.startTimeInput) {
      this.startTimeInput.nativeElement.value = this.dateToTimeString(
        this.store.value.createTestModel.createExam.start
      );
    }

    if (this.endTimeInput) {
      this.endTimeInput.nativeElement.value = this.dateToTimeString(
        this.store.value.createTestModel.createExam.end
      );
    }

    if (this.dateInput) {
      this.dateInput.nativeElement.value = this.store.value
        .createTestModel
        .createExam
        .start
        .toISOString()
        .split('T')[0];
    }
  }

  protected pad(num: number, size: number) {
    let s = num+"";
    while (s.length < size) s = "0" + s;
    return s;
  }

  protected btnToggled(schoolUnit: SchoolUnit): string | string[] {
    let btnClass: string | string[] = "btn-outline-info";

    if (schoolUnit.isSelected)  {
      btnClass = ["btn-info", "text-black"];
    }

    return btnClass;
  }

  protected dateToTimeString(date: Date): string {
    let timeString = `${date.getHours()}:`;

    if (date.getHours() < 10) {
      timeString = "0" + timeString;
    }

    if (date.getMinutes() < 10) {
      timeString += "0";
    }

    timeString += date.getMinutes();

    return timeString;
  }

  protected setIsValidTitle(title: string): void {
    this.checkAllFieldsIfValid();

    if (this.store.value.createTestModel.createExam.title !== title) {
      set(model => {
        model.createTestModel.createExam.title = title
      });
    }
  }

  protected setIsValidDate(dateString: string): void {
    this.isValidDate = dateString !== "";

    if (this.isValidDate) {
      let date = new Date(dateString);

      let startDate = new Date(
        this.store.value.createTestModel.createExam.start.getTime()
      );

      startDate.setFullYear(date.getFullYear());
      startDate.setMonth(date.getMonth());
      startDate.setDate(date.getDate());

      let endDate = new Date(
        this.store.value.createTestModel.createExam.end.getTime()
      );

      endDate.setFullYear(date.getFullYear());
      endDate.setMonth(date.getMonth());
      endDate.setDate(date.getDate());

      set(model => {
        model.createTestModel.createExam.start = startDate;
        model.createTestModel.createExam.end = endDate;
      });
      this.checkAllFieldsIfValid();
    }
  }

  protected setScreencaptureInterval(val: string): void {
    let screencaptureInterval = Number(val);
    set((model) => {
      model.createTestModel
        .createExam
        .screencapture_interval_seconds = screencaptureInterval;
    });
  }

  protected setStartTime(startTimeString: string): void {
    let startTime: number[] = this.schoolUnitSvc
      .getHoursAndMinutes(startTimeString);
    let storeStartDate = this.store.value
      .createTestModel
      .createExam
      .start;

    if (startTime[0] !== storeStartDate.getHours() ||
      startTime[1] !== storeStartDate.getMinutes()) {
      let startDate = this.store.value
        .createTestModel
        .createExam
        .start;
      startDate.setHours(startTime[0]);
      startDate.setMinutes(startTime[1]);
      startDate.setSeconds(0);
      startDate.setMilliseconds(0);

      set(model => {
        model.createTestModel.createExam.start = startDate;
      });
    }

    this.checkAllFieldsIfValid();
    this.schoolUnitSvc.checkIfSelected();
  }

  protected setEndTime(endTimeString: string): void {
    let endTime: number[] = this.schoolUnitSvc
      .getHoursAndMinutes(endTimeString);
    let storeEndDate = this.store.value
      .createTestModel
      .createExam
      .end;

    if (endTime[0] !== storeEndDate.getHours() ||
      endTime[1] !== storeEndDate.getMinutes()) {
      let endDate = this.store.value
        .createTestModel
        .createExam
        .end;
      endDate.setHours(endTime[0]);
      endDate.setMinutes(endTime[1]);
      endDate.setSeconds(0);
      endDate.setMilliseconds(0);

      set(model => {
        model
          .createTestModel
          .createExam
          .end = endDate;
      });
    }

    this.checkAllFieldsIfValid();
    this.schoolUnitSvc.checkIfSelected();
  }

  private setStartDate(newDate: Date) {
    let storeDate = this.store.value
      .createTestModel
      .createExam
      .start;
    let date: Date = this.setBaseDateForDate(newDate, storeDate);

    set(model => {
      model.createTestModel.createExam.start = date;
    });

    if (this.startTimeInput) {
      this.startTimeInput.nativeElement.value = this.dateToTimeString(
        this.store.value.createTestModel.createExam.start
      );
    }
    this.schoolUnitSvc.checkIfSelected();
    this.checkAllFieldsIfValid();
  }

  private setEndDate(newDate: Date) {
    let storeDate = this.store.value
      .createTestModel
      .createExam
      .end;
    let date: Date = this.setBaseDateForDate(newDate, storeDate);

    set(model => {
      model
        .createTestModel
        .createExam
        .end = date;
    });

    if (this.endTimeInput) {
      this.endTimeInput.nativeElement.value = this.dateToTimeString(
        this.store.value
          .createTestModel
          .createExam
          .end
      );
    }
    this.schoolUnitSvc.checkIfSelected();
    this.checkAllFieldsIfValid();
  }

  private setStartAndEndDate(newStartDate: Date, newEndDate: Date) {
    let storeDate = this.store.value
      .createTestModel
      .createExam
      .start;
    let startDate: Date = this.setBaseDateForDate(
      newStartDate,
      storeDate
    );
    let endDate: Date = this.setBaseDateForDate(
      newEndDate,
      storeDate
    );

    set(model => {
      model
        .createTestModel
        .createExam
        .start = startDate;
      model
        .createTestModel
        .createExam
        .end = endDate;
    });

    if (this.startTimeInput) {
      this.startTimeInput.nativeElement.value = this.dateToTimeString(
        this.store.value
          .createTestModel
          .createExam
          .start
      );
    }

    if (this.endTimeInput) {
      this.endTimeInput.nativeElement.value = this.dateToTimeString(
        this.store.value
          .createTestModel
          .createExam
          .end
      );
    }

    this.schoolUnitSvc.checkIfSelected();
    this.checkAllFieldsIfValid();
  }

  private setBaseDateForDate(date: Date, baseDate: Date): Date {
    let newDate: Date = new Date(date);

    newDate.setFullYear(
      baseDate.getFullYear(),
      baseDate.getMonth(),
      baseDate.getDate()
    );

    return newDate;
  }

  private isStartDate(date: Date): boolean {
    let startDate: Date = this.store.value
      .createTestModel
      .createExam
      .start;
    let endDate: Date = this.store.value
      .createTestModel
      .createExam
      .end;
    let compareDate: Date = this.setBaseDateForDate(date, startDate);
    let result = false;

    // switch if end time is not valid since
    // we just assume that the first selected one is a start time
    this.isEndTimeAfterStartTime = this.store.value
      .createTestModel
      .createExam
      .end > this.store.value
      .createTestModel
      .createExam
      .start;

    if (!this.isEndTimeAfterStartTime) {
      const bucket = endDate;
      endDate = startDate;
      startDate = bucket;
    }

    if (compareDate.getTime() < endDate.getTime()) {
      if (compareDate.getTime() < startDate.getTime()) {
        result = true;
      } else if(
        Math.abs(startDate.getTime() - compareDate.getTime()) <
        Math.abs(endDate.getTime() - compareDate.getTime())
      ) {
        // startDate is closer
        result = true;
      }
    }

    return result;
  }

  protected selectUnit(date: SchoolUnit): void {
    if (date.isSelected) {
      this.setStartAndEndDate(date.start, date.end);
    } else {
      if (this.isStartDate(date.start)) {
        this.setStartDate(date.start);
      } else {
        this.setEndDate(date.end);
      }
    }
  }

  protected async createTestBtnClicked(): Promise<void> {
    // reset response text colour
    this.textColourForCreateExamResponse = "";

    // save exam
    let exam: CreateExam = this.store.value
      .createTestModel
      .createExam;

    this.toastSvc.addToast(
      "Started creating the exam",
      `Started creating the exam '${this.store.value.createTestModel.createExam.title}'.`,
      "info"
    );

    (await this.examSvc.createNewExam(exam)).subscribe({
      next: (exam) => {
        this.createdExamInfo = "Der Test wurde erfolgreich gespeichert:";
        this.createdExamValue = "Pin: " + exam.pin;

        set(model => {
          model.createTestModel.createdExam = true;
        })

        this.examSvc.reloadAllExams();

        // clear exam
        set(model => {
          model.createTestModel.createExam.title = "";
          model.createTestModel.createExam.start = new Date(Date.now());
          model.createTestModel.createExam.end = new Date(Date.now() + 3000000);
          model.createTestModel.createExam.screencapture_interval_seconds = environment
            .patrolSpeed;
        });
        this.schoolUnitSvc.checkIfSelected();

        this.examSvc.setCurDashboardExam(exam);
        this.examSvc.setCurVideoExam(exam);
      },
      error: error => {
        this.createdExamInfo = "Der Test wurde nicht erfolgreich gespeichert:";
        this.createdExamValue = (error.message) ? error.message :
          error.status ? `${error.status} - ${error.statusText}` :
            'Server error';
        this.textColourForCreateExamResponse = "text-danger";

        set(model => {
          model.createTestModel.createdExam = true;
        })
      }
    });
  }

  private checkAllFieldsIfValid() : void {
    this.isValidTitle = this.store
      .value
      .createTestModel
      .createExam
      .title !== "";
    this.isEndTimeAfterStartTime = this.store.value
      .createTestModel
      .createExam
      .end > this.store.value
      .createTestModel
      .createExam
      .start;

    let curDate: Date = new Date(Date.now());

    this.isValidStartTime = this.store.value
      .createTestModel
      .createExam
      .start > curDate;
    this.isValidEndTime = this.store.value
      .createTestModel
      .createExam
      .end > curDate;

    curDate.setHours(0, 0, 0, 0);
    let storeDate = new Date(
      this.store.value.createTestModel.createExam.start
    );
    storeDate.setHours(0, 0, 0, 0);

    this.dateNotInPast = storeDate.getTime() >= curDate.getTime();
  }
}
