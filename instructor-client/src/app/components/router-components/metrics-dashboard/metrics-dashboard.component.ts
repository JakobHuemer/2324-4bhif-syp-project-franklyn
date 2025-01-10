import {Component, inject, OnDestroy, OnInit, QueryList, ViewChildren} from '@angular/core';
import {BaseChartDirective} from "ng2-charts";
import {StoreService} from "../../../services/store.service";
import {ChartConfiguration, ChartData, ChartType} from "chart.js";
import {distinctUntilChanged, map} from "rxjs";
import {ScheduleService} from "../../../services/schedule.service";
import {WebApiService} from "../../../services/web-api.service";
import {set, store} from "../../../model";
import {environment} from "../../../../../env/environment";

@Component({
  selector: 'app-metrics-dashboard',
  imports: [
    BaseChartDirective
  ],
  templateUrl: './metrics-dashboard.component.html',
  styleUrl: './metrics-dashboard.component.css'
})
export class MetricsDashboardComponent implements OnInit, OnDestroy {
  @ViewChildren(BaseChartDirective) charts: QueryList<BaseChartDirective> | undefined;

  protected store = inject(StoreService).store;
  protected scheduleSvc = inject(ScheduleService);
  protected webApi = inject(WebApiService);

  async ngOnInit(): Promise<void> {
    // subscribe to server-metrics to update when
    // there are changes
    this.store.pipe(
      map(store => store.serverMetrics),
      distinctUntilChanged()
    ).subscribe(next => {
      this.updateDatasets();
    });

    await this.webApi.getServerMetrics();
    this.updateDatasets();

    this.scheduleSvc.startGettingServerMetrics();
  }

  ngOnDestroy() {
    this.scheduleSvc.stopGettingServerMetrics();
  }

  updateDatasets() {
    this.diskChartData.datasets[0].data = [
      this.store.value.serverMetrics.savedVideosSizeInBytes / (1024 * 1024 * 1024),
      this.store.value.serverMetrics.savedScreenshotsSizeInBytes / (1024 * 1024 * 1024),
      (this.store.value.serverMetrics.totalDiskSpaceInBytes - this.store.value.serverMetrics.remainingDiskSpaceInBytes - this.store.value.serverMetrics.savedVideosSizeInBytes - this.store.value.serverMetrics.savedScreenshotsSizeInBytes) / (1024 * 1024 * 1024),
      this.store.value.serverMetrics.remainingDiskSpaceInBytes / (1024 * 1024 * 1024),
    ];
    this.diskDoughnutLabel.lblText = `Disk: ${((this.store.value.serverMetrics.totalDiskSpaceInBytes - this.store.value.serverMetrics.remainingDiskSpaceInBytes) / (1024 * 1024 * 1024)).toFixed(0)} / ${(this.store.value.serverMetrics.totalDiskSpaceInBytes / (1024 * 1024 * 1024)).toFixed(0)} GiB`;

    this.memChartData.datasets[0].data = [
      (this.store.value.serverMetrics.maxAvailableMemoryInBytes - this.store.value.serverMetrics.totalUsedMemoryInBytes) / (1024 * 1024 * 1024),
      this.store.value.serverMetrics.totalUsedMemoryInBytes / (1024 * 1024 * 1024),
    ];
    this.memoryDoughnutLabel.lblText = `Memory utilization: ${(this.store.value.serverMetrics.totalUsedMemoryInBytes / this.store.value.serverMetrics.maxAvailableMemoryInBytes * 100).toFixed(2)}%`;

    this.cpuChartData.datasets[0].data = [
      this.store.value.serverMetrics.cpuUsagePercent,
      100 - this.store.value.serverMetrics.cpuUsagePercent
    ]

    this.updateColorLabels();

    this.diskChartData.datasets[0].backgroundColor = [
      store.value.serverMetrics.diskUsageVideoColor,
      store.value.serverMetrics.diskUsageScreenshotColor,
      store.value.serverMetrics.diskUsageOtherColor,
      store.value.serverMetrics.diagramBackgroundColor,
    ];

    this.memChartData.datasets[0].backgroundColor = [
      store.value.serverMetrics.diagramBackgroundColor,
      store.value.serverMetrics.memoryUtilisationColor
    ];

    this.cpuChartData.datasets[0].backgroundColor = [
      store.value.serverMetrics.cpuUtilisationColor,
      store.value.serverMetrics.diagramBackgroundColor
    ];

    this.charts?.forEach(c => c.update());
  }

  getColorPerPercentage(val: number): string {
    let color = environment.metricsDashboardValueNotOkay;

    if (val < 0.5) {
      color = environment.metricsDashboardValueOkay;
    } else if (val < 0.8) {
      color = environment.metricsDashboardValueBarelyOkay;
    }

    return color;
  }

  updateColorLabels() {
    let cpuPercent: number = this.store.value.serverMetrics.cpuUsagePercent / 100;

    let memoryPercent: number = this.store.value.serverMetrics.totalUsedMemoryInBytes / this.store.value.serverMetrics.maxAvailableMemoryInBytes;

    set((model) => {
      model.serverMetrics.cpuUtilisationColor = this.getColorPerPercentage(cpuPercent);
      model.serverMetrics.memoryUtilisationColor = this.getColorPerPercentage(memoryPercent);
    })
  }

  diskDoughnutLabel = {
    id: 'doughnutLabel',
    lblText: "Disk usage",
    beforeDatasetsDraw(chart: any, args: any, options: any): boolean | void {
      const {ctx, data} = chart;

      ctx.save();
      const x = chart.getDatasetMeta(0).data[0].x;
      const y = chart.getDatasetMeta(0).data[0].y;
      ctx.font = "bold 15px sans-serif";
      ctx.fillStyle = store.value.serverMetrics.diagramTextColor;
      ctx.textAlign = "center";
      ctx.textBaseline = "middle";
      ctx.fillText(this.lblText, x, y);
    }
  };

  memoryDoughnutLabel = {
    id: 'doughnutLabel',
    lblText: "Memory utilization",
    beforeDatasetsDraw(chart: any, args: any, options: any): boolean | void {
      const {ctx, data} = chart;

      ctx.save();
      const x = chart.getDatasetMeta(0).data[0].x;
      const y = chart.getDatasetMeta(0).data[0].y;
      ctx.font = "bold 15px sans-serif";
      ctx.fillStyle = store.value.serverMetrics.diagramTextColor;
      ctx.textAlign = "center";
      ctx.textBaseline = "middle";
      ctx.fillText(this.lblText, x, y);
    }
  };

  protected diskChartType: ChartType = "doughnut";
  protected diskChartData: ChartData<'doughnut', number[], string> = {
    labels: ["Videos", "Screenshots", "Other", "Free"],
    datasets: [
      {
        data: [
          this.store.value.serverMetrics.savedVideosSizeInBytes / (1024 * 1024 * 1024),
          this.store.value.serverMetrics.savedScreenshotsSizeInBytes / (1024 * 1024 * 1024),
          (this.store.value.serverMetrics.totalDiskSpaceInBytes - this.store.value.serverMetrics.remainingDiskSpaceInBytes - this.store.value.serverMetrics.savedVideosSizeInBytes - this.store.value.serverMetrics.savedScreenshotsSizeInBytes) / (1024 * 1024 * 1024),
          this.store.value.serverMetrics.remainingDiskSpaceInBytes / (1024 * 1024 * 1024),
        ],
        backgroundColor: [
          store.value.serverMetrics.diskUsageVideoColor,
          store.value.serverMetrics.diskUsageScreenshotColor,
          store.value.serverMetrics.diskUsageOtherColor,
          store.value.serverMetrics.diagramBackgroundColor,
        ]
      }
    ]
  };

  protected diskChartOptions: ChartConfiguration['options'] = {
    responsive: true,
    plugins: {
      legend: {
        display: true,
      },
      tooltip: {
        callbacks: {
          label: (ttItem) => (`${ttItem.parsed.toFixed(2)} GiB`)
        }
      }
    }
  };

  protected memChartType: ChartType = "doughnut"
  protected memChartData: ChartData<'doughnut', number[], string> = {
    labels: ["Free", "Used"],
    datasets: [
      {
        data: [
          (this.store.value.serverMetrics.maxAvailableMemoryInBytes - this.store.value.serverMetrics.totalUsedMemoryInBytes) / (1024 * 1024 * 1024),
          this.store.value.serverMetrics.totalUsedMemoryInBytes / (1024 * 1024 * 1024),
        ],
        backgroundColor: [
          store.value.serverMetrics.diagramBackgroundColor,
          store.value.serverMetrics.memoryUtilisationColor
        ]
      }
    ]
  };

  protected memChartOptions: ChartConfiguration['options'] = {
    responsive: true,
    plugins: {
      legend: {
        display: true,
      },
      tooltip: {
        callbacks: {
          label: (ttItem) => (`${ttItem.parsed.toFixed(2)} GiB`)
        }
      }
    }
  };

  protected cpuChartType = "bar" as const;
  protected cpuChartData: ChartData<'bar', number[], string> = {
    labels: ["CPU Utilization"],
    datasets: [
      {
        data: [
          100 - this.store.value.serverMetrics.cpuUsagePercent
        ],
        backgroundColor: [
          store.value.serverMetrics.cpuUtilisationColor
        ]
      },
      {
        data: [
          100 - this.store.value.serverMetrics.cpuUsagePercent
        ],
        backgroundColor: [
          store.value.serverMetrics.diagramBackgroundColor
        ]
      }
    ]
  };

  protected cpuChartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    indexAxis: "y",
    scales: {
      x: {
        display: false,
        stacked: true,
        min: 0,
        max: 100
      },
      y: {
        display: false,
        stacked: true,
        min: 0,
        max: 100
      }
    },
    plugins: {
      legend: {
        display: false,
      },
      tooltip: {
        callbacks: {
          label: (ttItem) => {
            if (ttItem.datasetIndex == 0) {
              return (`${ttItem.parsed.x.toFixed(2)} %`)
            } else {
              return "";
            }
          }
        }
      }
    }
  };
}
