import { Component } from '@angular/core';
import {CreateExamComponent} from "../../entity-components/create-exam/create-exam.component";

@Component({
  selector: 'app-create-test',
  standalone: true,
  imports: [
    CreateExamComponent
  ],
  templateUrl: './create-test.component.html',
  styleUrl: './create-test.component.css'
})
export class CreateTestComponent {

}
