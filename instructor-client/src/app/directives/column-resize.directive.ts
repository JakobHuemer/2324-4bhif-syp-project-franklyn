import {Directive, ElementRef, OnDestroy, OnInit, Renderer2} from '@angular/core';
import {distinctUntilChanged, fromEvent, Subject, takeUntil} from "rxjs";

@Directive({
  selector: '[appColumnResize]'
})
export class ColumnResizeDirective implements OnInit, OnDestroy {

  private startX!: number;
  private isResizing = false;
  private initialWidth!: number;
  private columnIndex!: number;
  private table: HTMLElement | null = null;
  private tableWidth: number | null = null;

  private onDestroy$ = new Subject<void>();

  constructor(
    private elementRef: ElementRef,
    private renderer: Renderer2
  ) {}

  ngOnInit(): void {
    const nativeElement = this.elementRef?.nativeElement as HTMLElement;

    // listen to the mousedown event
    fromEvent<MouseEvent>(nativeElement, 'mousedown')
      .pipe(
      takeUntil(this.onDestroy$),
      distinctUntilChanged())
      .subscribe(event => this.onMouseDown(event));

    // prevent click event
    fromEvent<MouseEvent>(nativeElement, 'click')
      .pipe(
        takeUntil(this.onDestroy$),
        distinctUntilChanged())
      .subscribe(event => event.stopPropagation());
  }

  private onMouseDown(event: MouseEvent) {
    event.preventDefault();
    this.startX = event?.pageX;
    this.isResizing = true;
    this.initialWidth = this.elementRef?.nativeElement?.offsetWidth;

    // Find the index of the current column
    const row = this.elementRef?.nativeElement?.parentElement;
    const cells = Array.from(row?.children);
    this.columnIndex = cells.indexOf(this.elementRef?.nativeElement);

    this.renderer.addClass(
      this.elementRef?.nativeElement,
      'resizing-col'
    );

    this.table = this.findParentTable(this.elementRef.nativeElement);

    if (this.table) {
      this.renderer.addClass(this.table, 'table-resizing');

      const columns = this.table
        .querySelectorAll('td');

      const tableWidth = this.table?.offsetWidth;

      const onMouseMove =
        (moveEvent: MouseEvent) => this.onMouseMove(
          moveEvent,
          tableWidth,
          columns
        );

      const onMouseUp = () => {
        this.isResizing = false;

        this.renderer.removeClass(
          this.elementRef?.nativeElement,
          'resizing-col'
        );

        if (this.table) {
          this.renderer.removeClass(
            this.table,
            'table-resizing'
          );
        }

        document.removeEventListener('mousemove', onMouseMove);
        document.removeEventListener('mouseup', onMouseUp);
      };

      document.addEventListener('mousemove', onMouseMove);
      document.addEventListener('mouseup', onMouseUp);
    }
  }

  private onMouseMove(
    moveEvent: MouseEvent,
    tableWidth: number,
    columns: NodeListOf<HTMLTableCellElement>) {
    if (this.isResizing) {
      const deltaX = moveEvent?.pageX - this.startX;

      const newWidth = this.initialWidth + deltaX;

      this.tableWidth = !this.tableWidth && this.table?.offsetWidth ?
        this.table?.offsetWidth / 2 : this.tableWidth;

      // Restrict the column width to a minimum of 40 and a maximum 50% of table
      if (newWidth >= 40 && newWidth <= (this.tableWidth || 450)) {

        // Update the width of the current column
        this.renderer.setStyle(
          this.elementRef?.nativeElement,
          'width',
          `${newWidth}px`
        );

        // Update the width of the corresponding header and cell in each row
        columns[this.columnIndex].style.width = `${newWidth}px`;

        const rows = this.table
          ?.querySelectorAll('tr');

        rows?.forEach((row) => {
          const cells = row.querySelectorAll('td');

          if (cells[this.columnIndex]) {
            cells[this.columnIndex].style.width = `${newWidth}px`;
          }
        });

        // Adjust the width of the table if it has a fixed width
        if (tableWidth > 0) {
          this.renderer.setStyle(
            this.table,
            'width',
            tableWidth + deltaX + 'px'
          );
        }
      }
    }
  }

  private findParentTable(element: HTMLElement): HTMLElement | null {
    while (element) {
      if (element.tagName === 'TABLE') {
        return element;
      }
      if (element?.parentElement) element = element?.parentElement;
    }
    return null;
  }

  ngOnDestroy(): void {
    // we've destroyed the component, so update the subject
    this.onDestroy$.next();
    this.onDestroy$.complete();
  }

}
