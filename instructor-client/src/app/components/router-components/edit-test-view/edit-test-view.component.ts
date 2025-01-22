import { Component } from '@angular/core';
import {RouterLink, RouterLinkActive} from "@angular/router";

@Component({
    selector: 'app-edit-test-view',
    imports: [
        RouterLink,
        RouterLinkActive
    ],
    templateUrl: './edit-test-view.component.html',
    styleUrl: './edit-test-view.component.css'
})
export class EditTestViewComponent {

}
