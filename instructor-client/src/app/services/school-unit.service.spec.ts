import { TestBed } from '@angular/core/testing';

import { SchoolUnitService } from './school-unit.service';

describe('SchoolUnitService', () => {
  let service: SchoolUnitService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(SchoolUnitService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
