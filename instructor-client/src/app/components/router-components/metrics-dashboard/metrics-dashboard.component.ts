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
      map(store => store.metricsDashboardModel.serverMetrics),
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
      this.store.value.metricsDashboardModel.serverMetrics.savedVideosSizeInBytes / (1024 * 1024 * 1024),
      this.store.value.metricsDashboardModel.serverMetrics.savedScreenshotsSizeInBytes / (1024 * 1024 * 1024),
      (this.store.value.metricsDashboardModel.serverMetrics.totalDiskSpaceInBytes - this.store.value.metricsDashboardModel.serverMetrics.remainingDiskSpaceInBytes - this.store.value.metricsDashboardModel.serverMetrics.savedVideosSizeInBytes - this.store.value.metricsDashboardModel.serverMetrics.savedScreenshotsSizeInBytes) / (1024 * 1024 * 1024),
      this.store.value.metricsDashboardModel.serverMetrics.remainingDiskSpaceInBytes / (1024 * 1024 * 1024),
    ];
    this.diskDoughnutLabel.lblText = `Disk: ${((this.store.value.metricsDashboardModel.serverMetrics.totalDiskSpaceInBytes - this.store.value.metricsDashboardModel.serverMetrics.remainingDiskSpaceInBytes) / (1024 * 1024 * 1024)).toFixed(0)} / ${(this.store.value.metricsDashboardModel.serverMetrics.totalDiskSpaceInBytes / (1024 * 1024 * 1024)).toFixed(0)} GiB`;

    this.memChartData.datasets[0].data = [
      (this.store.value.metricsDashboardModel.serverMetrics.maxAvailableMemoryInBytes - this.store.value.metricsDashboardModel.serverMetrics.totalUsedMemoryInBytes) / (1024 * 1024 * 1024),
      this.store.value.metricsDashboardModel.serverMetrics.totalUsedMemoryInBytes / (1024 * 1024 * 1024),
    ];
    this.memoryDoughnutLabel.lblText = `Memory utilization: ${(this.store.value.metricsDashboardModel.serverMetrics.totalUsedMemoryInBytes / this.store.value.metricsDashboardModel.serverMetrics.maxAvailableMemoryInBytes * 100).toFixed(2)}%`;

    this.cpuChartData.datasets[0].data = [
      this.store.value.metricsDashboardModel.serverMetrics.cpuUsagePercent,
      100 - this.store.value.metricsDashboardModel.serverMetrics.cpuUsagePercent
    ]

    this.updateColorLabels();

    this.diskChartData.datasets[0].backgroundColor = [
      store.value.metricsDashboardModel.diskUsageVideoColor,
      store.value.metricsDashboardModel.diskUsageScreenshotColor,
      store.value.metricsDashboardModel.diskUsageOtherColor,
      store.value.metricsDashboardModel.diagramBackgroundColor,
    ];

    this.memChartData.datasets[0].backgroundColor = [
      store.value.metricsDashboardModel.diagramBackgroundColor,
      store.value.metricsDashboardModel.memoryUtilisationColor
    ];

    this.cpuChartData.datasets[0].backgroundColor = [
      store.value.metricsDashboardModel.cpuUtilisationColor,
      store.value.metricsDashboardModel.diagramBackgroundColor
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
    let cpuPercent: number = this.store.value.metricsDashboardModel.serverMetrics.cpuUsagePercent / 100;

    let memoryPercent: number = this.store.value.metricsDashboardModel.serverMetrics.totalUsedMemoryInBytes / this.store.value.metricsDashboardModel.serverMetrics.maxAvailableMemoryInBytes;

    set((model) => {
      model.metricsDashboardModel.cpuUtilisationColor = this.getColorPerPercentage(cpuPercent);
      model.metricsDashboardModel.memoryUtilisationColor = this.getColorPerPercentage(memoryPercent);
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
      ctx.fillStyle = store.value.metricsDashboardModel.diagramTextColor;
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
      ctx.fillStyle = store.value.metricsDashboardModel.diagramTextColor;
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
          this.store.value.metricsDashboardModel.serverMetrics.savedVideosSizeInBytes / (1024 * 1024 * 1024),
          this.store.value.metricsDashboardModel.serverMetrics.savedScreenshotsSizeInBytes / (1024 * 1024 * 1024),
          (this.store.value.metricsDashboardModel.serverMetrics.totalDiskSpaceInBytes - this.store.value.metricsDashboardModel.serverMetrics.remainingDiskSpaceInBytes - this.store.value.metricsDashboardModel.serverMetrics.savedVideosSizeInBytes - this.store.value.metricsDashboardModel.serverMetrics.savedScreenshotsSizeInBytes) / (1024 * 1024 * 1024),
          this.store.value.metricsDashboardModel.serverMetrics.remainingDiskSpaceInBytes / (1024 * 1024 * 1024),
        ],
        backgroundColor: [
          store.value.metricsDashboardModel.diskUsageVideoColor,
          store.value.metricsDashboardModel.diskUsageScreenshotColor,
          store.value.metricsDashboardModel.diskUsageOtherColor,
          store.value.metricsDashboardModel.diagramBackgroundColor,
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
          (this.store.value.metricsDashboardModel.serverMetrics.maxAvailableMemoryInBytes - this.store.value.metricsDashboardModel.serverMetrics.totalUsedMemoryInBytes) / (1024 * 1024 * 1024),
          this.store.value.metricsDashboardModel.serverMetrics.totalUsedMemoryInBytes / (1024 * 1024 * 1024),
        ],
        backgroundColor: [
          store.value.metricsDashboardModel.diagramBackgroundColor,
          store.value.metricsDashboardModel.memoryUtilisationColor
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
          100 - this.store.value.metricsDashboardModel.serverMetrics.cpuUsagePercent
        ],
        backgroundColor: [
          store.value.metricsDashboardModel.cpuUtilisationColor
        ]
      },
      {
        data: [
          100 - this.store.value.metricsDashboardModel.serverMetrics.cpuUsagePercent
        ],
        backgroundColor: [
          store.value.metricsDashboardModel.diagramBackgroundColor
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
