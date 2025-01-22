import {CreateExam} from "./create-exam";
import {SchoolUnit} from "./school-unit";

export interface CreateTestModel {
  readonly createExam: CreateExam,
  readonly createdExam: boolean,
  readonly schoolUnits: SchoolUnit[],
  readonly eveningSchoolUnits: SchoolUnit[],
}
