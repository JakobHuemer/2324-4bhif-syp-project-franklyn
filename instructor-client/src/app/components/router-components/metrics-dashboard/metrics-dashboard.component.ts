import {Component, inject, OnDestroy, OnInit, QueryList, ViewChildren} from '@angular/core';
import {BaseChartDirective} from "ng2-charts";
import {StoreService} from "../../../services/store.service";
import {ChartConfiguration, ChartData, ChartType} from "chart.js";
import {distinctUntilChanged, map} from "rxjs";
import {ScheduleService} from "../../../services/schedule.service";
import {WebApiService} from "../../../services/web-api.service";
import {set, store, ProfilingMetrics, HistogramData} from "../../../model";
import {environment} from "../../../../../env/environment";
import {CommonModule} from "@angular/common";

// Historical data point with timestamp
interface HistoricalDataPoint {
  timestamp: Date;
  screenshotRequestLatency: number;
  imageDecode: number;
  imageEncode: number;
  imageDownload: number;
  queueSize: number;
  activeRequests: number;
  uploadsPerSecond: number;
}

// Rolling window duration in milliseconds (5 minutes)
const ROLLING_WINDOW_MS = 5 * 60 * 1000;

@Component({
  selector: 'app-metrics-dashboard',
  imports: [
    BaseChartDirective,
    CommonModule
  ],
  templateUrl: './metrics-dashboard.component.html',
  styleUrl: './metrics-dashboard.component.css'
})
export class MetricsDashboardComponent implements OnInit, OnDestroy {
  @ViewChildren(BaseChartDirective) charts: QueryList<BaseChartDirective> | undefined;

  protected store = inject(StoreService).store;
  protected scheduleSvc = inject(ScheduleService);
  protected webApi = inject(WebApiService);

  // Profiling metrics for display
  protected profilingMetrics: ProfilingMetrics | null = null;

  // Latency histogram table data
  protected latencyMetrics: {name: string; data: HistogramData}[] = [];

  // Historical data for charts (rolling 5-minute window)
  protected historicalData: HistoricalDataPoint[] = [];

  async ngOnInit(): Promise<void> {
    // subscribe to server-metrics to update when
    // there are changes
    this.store.pipe(
      map(store => store.metricsDashboardModel.serverMetrics),
      distinctUntilChanged()
    ).subscribe(next => {
      this.updateDatasets();
    });

    // subscribe to profiling-metrics changes
    this.store.pipe(
      map(store => store.metricsDashboardModel.profilingMetrics),
      distinctUntilChanged()
    ).subscribe(next => {
      this.profilingMetrics = next;
      this.updateLatencyTable();
      this.addHistoricalDataPoint();
      this.updateTimelineChart();
      this.updateProcessingBreakdownChart();
    });

    await this.webApi.getServerMetrics();
    await this.webApi.getProfilingMetrics();
    this.updateDatasets();

    this.scheduleSvc.startGettingServerMetrics();
  }

  ngOnDestroy() {
    this.scheduleSvc.stopGettingServerMetrics();
  }

  // Add current profiling metrics to historical data
  addHistoricalDataPoint() {
    if (!this.profilingMetrics) return;

    const now = new Date();
    const cutoff = new Date(now.getTime() - ROLLING_WINDOW_MS);

    // Remove old data points
    this.historicalData = this.historicalData.filter(dp => dp.timestamp > cutoff);

    // Add new data point
    this.historicalData.push({
      timestamp: now,
      screenshotRequestLatency: this.profilingMetrics.screenshotRequestLatency.p50Ms,
      imageDecode: this.profilingMetrics.imageDecode.p50Ms,
      imageEncode: this.profilingMetrics.imageEncode.p50Ms,
      imageDownload: this.profilingMetrics.imageDownload.p50Ms,
      queueSize: this.profilingMetrics.queueSize,
      activeRequests: this.profilingMetrics.activeRequests,
      uploadsPerSecond: this.profilingMetrics.uploadsPerSecond,
    });
  }

  // Update the timeline line chart with historical data
  updateTimelineChart() {
    const labels = this.historicalData.map(dp => {
      const mins = dp.timestamp.getMinutes().toString().padStart(2, '0');
      const secs = dp.timestamp.getSeconds().toString().padStart(2, '0');
      return `${mins}:${secs}`;
    });

    this.timelineChartData.labels = labels;
    this.timelineChartData.datasets[0].data = this.historicalData.map(dp => dp.screenshotRequestLatency);
    this.timelineChartData.datasets[1].data = this.historicalData.map(dp => dp.imageDecode);
    this.timelineChartData.datasets[2].data = this.historicalData.map(dp => dp.imageEncode);
    this.timelineChartData.datasets[3].data = this.historicalData.map(dp => dp.imageDownload);

    this.charts?.forEach(c => c.update());
  }

  // Update processing breakdown stacked bar chart
  updateProcessingBreakdownChart() {
    if (!this.profilingMetrics) return;

    this.breakdownChartData.datasets[0].data = [this.profilingMetrics.imageDecode.p50Ms];
    this.breakdownChartData.datasets[1].data = [this.profilingMetrics.betaFrameMerge.p50Ms];
    this.breakdownChartData.datasets[2].data = [this.profilingMetrics.imageEncode.p50Ms];
    this.breakdownChartData.datasets[3].data = [this.profilingMetrics.imageFileRead.p50Ms];

    this.charts?.forEach(c => c.update());
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

  // === Request Pipeline Timeline Chart (Line Chart) ===
  protected timelineChartType = "line" as const;
  protected timelineChartData: ChartData<'line', number[], string> = {
    labels: [],
    datasets: [
      {
        label: 'Screenshot Request (p50)',
        data: [],
        borderColor: 'rgb(255, 99, 132)',
        backgroundColor: 'rgba(255, 99, 132, 0.2)',
        tension: 0.3,
        fill: false,
      },
      {
        label: 'Image Decode (p50)',
        data: [],
        borderColor: 'rgb(54, 162, 235)',
        backgroundColor: 'rgba(54, 162, 235, 0.2)',
        tension: 0.3,
        fill: false,
      },
      {
        label: 'Image Encode (p50)',
        data: [],
        borderColor: 'rgb(255, 205, 86)',
        backgroundColor: 'rgba(255, 205, 86, 0.2)',
        tension: 0.3,
        fill: false,
      },
      {
        label: 'Image Download (p50)',
        data: [],
        borderColor: 'rgb(75, 192, 192)',
        backgroundColor: 'rgba(75, 192, 192, 0.2)',
        tension: 0.3,
        fill: false,
      }
    ]
  };

  protected timelineChartOptions: ChartConfiguration<'line'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    scales: {
      x: {
        display: true,
        title: {
          display: true,
          text: 'Time (mm:ss)'
        }
      },
      y: {
        display: true,
        title: {
          display: true,
          text: 'Latency (ms)'
        },
        beginAtZero: true
      }
    },
    plugins: {
      legend: {
        display: true,
        position: 'bottom'
      },
      tooltip: {
        callbacks: {
          label: (ttItem) => `${ttItem.dataset.label}: ${ttItem.parsed.y.toFixed(1)} ms`
        }
      }
    }
  };

  // === Processing Time Breakdown Chart (Stacked Bar) ===
  protected breakdownChartType = "bar" as const;
  protected breakdownChartData: ChartData<'bar', number[], string> = {
    labels: ['Processing Time per Screenshot'],
    datasets: [
      {
        label: 'Decode',
        data: [0],
        backgroundColor: 'rgb(54, 162, 235)',
      },
      {
        label: 'Merge',
        data: [0],
        backgroundColor: 'rgb(255, 205, 86)',
      },
      {
        label: 'Encode',
        data: [0],
        backgroundColor: 'rgb(255, 99, 132)',
      },
      {
        label: 'File Read',
        data: [0],
        backgroundColor: 'rgb(75, 192, 192)',
      }
    ]
  };

  protected breakdownChartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    indexAxis: 'y',
    scales: {
      x: {
        stacked: true,
        title: {
          display: true,
          text: 'Time (ms)'
        }
      },
      y: {
        stacked: true,
        display: false
      }
    },
    plugins: {
      legend: {
        display: true,
        position: 'bottom'
      },
      tooltip: {
        callbacks: {
          label: (ttItem) => `${ttItem.dataset.label}: ${ttItem.parsed.x.toFixed(1)} ms`
        }
      }
    }
  };

  // === Profiling Metrics Methods ===

  updateLatencyTable() {
    if (!this.profilingMetrics) {
      this.latencyMetrics = [];
      return;
    }

    this.latencyMetrics = [
      { name: 'Screenshot Request (end-to-end)', data: this.profilingMetrics.screenshotRequestLatency },
      { name: 'Image Decode (ImageIO.read)', data: this.profilingMetrics.imageDecode },
      { name: 'Image Encode (ImageIO.write)', data: this.profilingMetrics.imageEncode },
      { name: 'Beta Frame Merge', data: this.profilingMetrics.betaFrameMerge },
      { name: 'Image File Read (disk)', data: this.profilingMetrics.imageFileRead },
      { name: 'Image Save Total', data: this.profilingMetrics.imageSaveTotal },
      { name: 'WebSocket Message Send', data: this.profilingMetrics.websocketMessageSend },
      { name: 'WebSocket Ping Latency', data: this.profilingMetrics.websocketPingLatency },
      { name: 'Image Download', data: this.profilingMetrics.imageDownload },
      { name: 'Video FFmpeg Encoding', data: this.profilingMetrics.videoFfmpeg },
      { name: 'Video Zip Creation', data: this.profilingMetrics.videoZip },
    ];
  }

  formatMs(value: number): string {
    if (value < 1) {
      return value.toFixed(2);
    } else if (value < 1000) {
      return value.toFixed(1);
    } else {
      return (value / 1000).toFixed(2) + 's';
    }
  }

  formatBytes(bytes: number): string {
    if (bytes < 1024) {
      return bytes.toFixed(0) + ' B';
    } else if (bytes < 1024 * 1024) {
      return (bytes / 1024).toFixed(1) + ' KB';
    } else {
      return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
    }
  }

  getQueueHealthColor(): string {
    if (!this.profilingMetrics) return environment.metricsDashboardValueOkay;
    const ratio = this.profilingMetrics.queueSize / Math.max(1, this.profilingMetrics.connectedClients);
    if (ratio < 0.5) return environment.metricsDashboardValueOkay;
    if (ratio < 1.0) return environment.metricsDashboardValueBarelyOkay;
    return environment.metricsDashboardValueNotOkay;
  }

  getPoolHealthColor(ratio: number): string {
    if (ratio < 0.5) return environment.metricsDashboardValueOkay;
    if (ratio < 0.8) return environment.metricsDashboardValueBarelyOkay;
    return environment.metricsDashboardValueNotOkay;
  }
}
