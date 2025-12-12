import {Component, inject, OnDestroy, OnInit, QueryList, ViewChildren} from '@angular/core';
import {BaseChartDirective} from "ng2-charts";
import {StoreService} from "../../../services/store.service";
import {ChartConfiguration, ChartData, ChartType} from "chart.js";
import {distinctUntilChanged, map} from "rxjs";
import {ScheduleService} from "../../../services/schedule.service";
import {WebApiService} from "../../../services/web-api.service";
import {set, store} from "../../../model";
import {environment} from "../../../../../env/environment";
import {CommonModule} from "@angular/common";

type MetricBar = {
  label: string;
  value: number;
  unit: string;
  width: number;
  color: string;
};

@Component({
  selector: 'app-metrics-dashboard',
  standalone: true,
  imports: [
    BaseChartDirective,
    CommonModule
  ],
  templateUrl: './metrics-dashboard.component.html',
  styleUrl: './metrics-dashboard.component.css'
})
export class MetricsDashboardComponent implements OnInit, OnDestroy{
  @ViewChildren(BaseChartDirective) charts: QueryList<BaseChartDirective> | undefined;

  protected store = inject(StoreService).store;
  protected scheduleSvc = inject(ScheduleService);
  protected webApi = inject(WebApiService);

  uploadMetricsBars: MetricBar[] = [];
  patrolMetricsBars: MetricBar[] = [];
  videoMetricsBars: MetricBar[] = [];
  storageMetricsBars: MetricBar[] = [];
  alphaAnalysisBars: MetricBar[] = [];

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
      this.store.value.serverMetrics.remainingDiskSpaceInBytes / (1024 * 1024 * 1024),
      this.store.value.serverMetrics.savedScreenshotsSizeInBytes / (1024 * 1024 * 1024),
    ];
    this.diskChartData.labels![this.memChartData.labels!.length - 1] = `Disk usage: ${this.diskChartData.datasets[0].data[1].toFixed(2)} GiB`;

    this.memChartData.datasets[0].data = [
      (this.store.value.serverMetrics.maxAvailableMemoryInBytes - this.store.value.serverMetrics.totalUsedMemoryInBytes)/ (1024 * 1024 * 1024),
      this.store.value.serverMetrics.totalUsedMemoryInBytes / (1024 * 1024 * 1024),
    ];
    this.memChartData.labels![this.memChartData.labels!.length - 1] = `Memory utilization: ${(this.store.value.serverMetrics.totalUsedMemoryInBytes / this.store.value.serverMetrics.maxAvailableMemoryInBytes * 100).toFixed(2)}%`;

    this.cpuChartData.datasets[0].data = [
      this.store.value.serverMetrics.cpuUsagePercent,
      100 - this.store.value.serverMetrics.cpuUsagePercent
    ]

    this.updateColorLabels();

    this.diskChartData.datasets[0].backgroundColor = [
      store.value.serverMetrics.diagramBackgroundColor,
      store.value.serverMetrics.diskUsageColor
    ]

    this.memChartData.datasets[0].backgroundColor = [
      store.value.serverMetrics.diagramBackgroundColor,
      store.value.serverMetrics.memoryUtilisationColor
    ];

    this.cpuChartData.datasets[0].backgroundColor = [
      store.value.serverMetrics.cpuUtilisationColor,
      store.value.serverMetrics.diagramBackgroundColor
    ]

    this.buildBarData();

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

  getColorPerValue(val: number, thresholds: {ok: number, warn: number}, invert = false): string {
    const value = invert ? -val : val;
    if (value <= thresholds.ok) {
      return environment.metricsDashboardValueOkay;
    }
    if (value <= thresholds.warn) {
      return environment.metricsDashboardValueBarelyOkay;
    }
    return environment.metricsDashboardValueNotOkay;
  }

  updateColorLabels() {
    let cpuPercent: number = this.store.value.serverMetrics.cpuUsagePercent/100;

    let diskUsagePercent: number = this.store.value.serverMetrics.totalUsedMemoryInBytes / this.store.value.serverMetrics.maxAvailableMemoryInBytes;

    let memoryPercent: number = this.store.value.serverMetrics.totalUsedMemoryInBytes / this.store.value.serverMetrics.maxAvailableMemoryInBytes;

    set((model) => {
      model.serverMetrics.cpuUtilisationColor = this.getColorPerPercentage(cpuPercent);
      model.serverMetrics.diskUsageColor = this.getColorPerPercentage(diskUsagePercent);
      model.serverMetrics.memoryUtilisationColor = this.getColorPerPercentage(memoryPercent);
    })
  }

  doughnutLabel = {
    id: 'doughnutLabel',
    beforeDatasetsDraw(chart:  any, args:  any, options: any): boolean | void {
      const { ctx, data } = chart;

      ctx.save();
      const x = chart.getDatasetMeta(0).data[0].x;
      const y = chart.getDatasetMeta(0).data[0].y;
      ctx.font = "bold 15px sans-serif";
      ctx.fillStyle = store.value.serverMetrics.diagramTextColor;
      ctx.textAlign = "center";
      ctx.textBaseline = "middle";
      ctx.fillText(data.labels[data.labels.length - 1], x, y);
    }
  };

  protected diskChartType: ChartType = "doughnut";
  protected diskChartData: ChartData<'doughnut', number[], string> = {
    labels: [ "Free", "Screenshots", "Disk usage"],
    datasets: [
      {
        data: [
          this.store.value.serverMetrics.remainingDiskSpaceInBytes / (1024 * 1024 * 1024),
          this.store.value.serverMetrics.savedScreenshotsSizeInBytes / (1024 * 1024 * 1024),
        ],
        backgroundColor: [
          store.value.serverMetrics.diagramBackgroundColor,
          store.value.serverMetrics.diskUsageColor
        ]
      }
    ]
  };

  protected diskChartOptions: ChartConfiguration['options'] = {
    responsive: true,
    plugins: {
      legend: {
        display: false,
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
    labels: [ "Free", "Used", "Memory utilization"],
    datasets: [
      {
        data: [
          (this.store.value.serverMetrics.maxAvailableMemoryInBytes - this.store.value.serverMetrics.totalUsedMemoryInBytes)/ (1024 * 1024 * 1024),
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
        display: false,
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
    labels: [ "CPU Utilization"],
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
            if(ttItem.datasetIndex == 0) {
              return(`${ttItem.parsed.x.toFixed(2)} %`)
            } else {
              return "";
            }
          }
        }
      }
    }
  };

  private buildBarData() {
    const metrics = this.store.value.serverMetrics;

    this.uploadMetricsBars = [
      {
        label: "Alpha uploads (ms avg)",
        value: metrics.alphaUploadAvgDurationMs,
        unit: "ms",
        width: this.normalize(metrics.alphaUploadAvgDurationMs, 0, 2000),
        color: this.getColorPerValue(metrics.alphaUploadAvgDurationMs, { ok: 300, warn: 800 })
      },
      {
        label: "Alpha bytes total", value: metrics.alphaUploadBytesTotal / (1024 * 1024), unit: "MiB",
        width: this.normalize(metrics.alphaUploadBytesTotal, 0, 1024 * 1024 * 500),
        color: this.getColorPerValue(metrics.alphaUploadBytesTotal, { ok: 1024 * 1024 * 200, warn: 1024 * 1024 * 400 })
      },
      {
        label: "Alpha errors", value: metrics.alphaUploadErrors, unit: "", width: this.normalize(metrics.alphaUploadErrors, 0, 50),
        color: this.getColorPerValue(metrics.alphaUploadErrors, { ok: 0, warn: 5 })
      },
      {
        label: "Beta uploads (ms avg)", value: metrics.betaUploadAvgDurationMs, unit: "ms",
        width: this.normalize(metrics.betaUploadAvgDurationMs, 0, 2000),
        color: this.getColorPerValue(metrics.betaUploadAvgDurationMs, { ok: 300, warn: 800 })
      },
      {
        label: "Beta bytes total", value: metrics.betaUploadBytesTotal / (1024 * 1024), unit: "MiB",
        width: this.normalize(metrics.betaUploadBytesTotal, 0, 1024 * 1024 * 500),
        color: this.getColorPerValue(metrics.betaUploadBytesTotal, { ok: 1024 * 1024 * 200, warn: 1024 * 1024 * 400 })
      },
      {
        label: "Beta changed pixels", value: metrics.betaChangedPixelsTotal, unit: "px",
        width: this.normalize(metrics.betaChangedPixelsTotal, 0, 5_000_000),
        color: this.getColorPerValue(metrics.betaChangedPixelsTotal, { ok: 500_000, warn: 2_000_000 })
      },
      {
        label: "Beta errors", value: metrics.betaUploadErrors, unit: "", width: this.normalize(metrics.betaUploadErrors, 0, 50),
        color: this.getColorPerValue(metrics.betaUploadErrors, { ok: 0, warn: 5 })
      },
      {
        label: "Requested alpha frames", value: metrics.requestedAlphaFrames, unit: "", width: this.normalize(metrics.requestedAlphaFrames, 0, 200),
        color: this.getColorPerValue(metrics.requestedAlphaFrames, { ok: 5, warn: 25 })
      }
    ];

    this.patrolMetricsBars = [
      {
        label: "Screenshot fetch (ms avg)", value: metrics.screenshotFetchAvgMs, unit: "ms",
        width: this.normalize(metrics.screenshotFetchAvgMs, 0, 2000),
        color: this.getColorPerValue(metrics.screenshotFetchAvgMs, { ok: 150, warn: 600 })
      },
      {
        label: "Scaled fetch (ms avg)", value: metrics.screenshotScaledFetchAvgMs, unit: "ms",
        width: this.normalize(metrics.screenshotScaledFetchAvgMs, 0, 2000),
        color: this.getColorPerValue(metrics.screenshotScaledFetchAvgMs, { ok: 150, warn: 600 })
      },
      {
        label: "Screenshot errors", value: metrics.screenshotErrors, unit: "", width: this.normalize(metrics.screenshotErrors, 0, 50),
        color: this.getColorPerValue(metrics.screenshotErrors, { ok: 0, warn: 5 })
      }
    ];

    this.videoMetricsBars = [
      {
        label: "Video download (ms avg)", value: metrics.videoDownloadAvgMs, unit: "ms",
        width: this.normalize(metrics.videoDownloadAvgMs, 0, 5000),
        color: this.getColorPerValue(metrics.videoDownloadAvgMs, { ok: 500, warn: 2000 })
      },
      {
        label: "Video download errors", value: metrics.videoDownloadErrors, unit: "", width: this.normalize(metrics.videoDownloadErrors, 0, 50),
        color: this.getColorPerValue(metrics.videoDownloadErrors, { ok: 0, warn: 5 })
      }
    ];

    this.storageMetricsBars = [
      {
        label: "Screenshots folder size", value: metrics.savedScreenshotsSizeInBytes / (1024 * 1024), unit: "MiB",
        width: this.normalize(metrics.savedScreenshotsSizeInBytes, 0, 1024 * 1024 * 1024),
        color: this.getColorPerValue(metrics.savedScreenshotsSizeInBytes, { ok: 1024 * 1024 * 200, warn: 1024 * 1024 * 800 })
      },
      {
        label: "Screenshots file count", value: metrics.screenshotsFileCount, unit: "files",
        width: this.normalize(metrics.screenshotsFileCount, 0, 20_000),
        color: this.getColorPerValue(metrics.screenshotsFileCount, { ok: 2_000, warn: 10_000 })
      }
    ];

    this.alphaAnalysisBars = [
      {
        label: "Alpha bytes total", value: metrics.alphaUploadBytesTotal / (1024 * 1024), unit: "MiB",
        width: this.normalize(metrics.alphaUploadBytesTotal, 0, 1024 * 1024 * 500),
        color: this.getColorPerValue(metrics.alphaUploadBytesTotal, { ok: 1024 * 1024 * 200, warn: 1024 * 1024 * 400 })
      },
      {
        label: "Alpha uploads", value: metrics.alphaUploadsCount, unit: "count",
        width: this.normalize(metrics.alphaUploadsCount, 0, 10_000),
        color: this.getColorPerValue(metrics.alphaUploadsCount, { ok: 1_000, warn: 5_000 })
      },
      {
        label: "Alpha duration avg", value: metrics.alphaUploadAvgDurationMs, unit: "ms",
        width: this.normalize(metrics.alphaUploadAvgDurationMs, 0, 2000),
        color: this.getColorPerValue(metrics.alphaUploadAvgDurationMs, { ok: 300, warn: 800 })
      }
    ];
  }

  private normalize(value: number, min: number, max: number): number {
    if (value <= min) return 0;
    if (value >= max) return 100;
    return ((value - min) / (max - min)) * 100;
  }
}
