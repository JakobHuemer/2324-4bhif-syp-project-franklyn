use std::collections::{HashSet, VecDeque};
use std::fs;
use std::io::{stdout, Cursor, Write};
use std::sync::{
    atomic::{AtomicBool, AtomicUsize, Ordering},
    Arc,
};
use std::time::{Duration, Instant};

use anyhow::{anyhow, Result};
use clap::Parser;
use fastwebsockets::{Frame, OpCode};
use image::{ImageFormat, Rgba, RgbaImage};
use rand::rngs::{OsRng, StdRng};
use rand::{Rng, SeedableRng};
use rayon::prelude::*;
use reqwest::multipart;

use crossterm::{
    event::{self, DisableMouseCapture, EnableMouseCapture, Event, KeyCode},
    execute,
    terminal::{disable_raw_mode, enable_raw_mode, EnterAlternateScreen, LeaveAlternateScreen},
};
use ratatui::{
    backend::CrosstermBackend,
    layout::{Constraint, Direction, Layout},
    style::{Color, Modifier, Style},
    symbols,
    text::{Line, Span},
    widgets::{Axis, Block, Borders, Chart, Dataset, Gauge, GraphType, Paragraph},
    Terminal,
};

const _PROD_URL: &str = "http://franklyn3.htl-leonding.ac.at:8080";
const _DEV_URL: &str = "http://localhost:8080";

use ddos_tester::connect_ws;
const SUFFIX_LEN: usize = 8;
const MAX_CLIENTS: usize = 200_000;

mod bitmap_font {
    use super::*;

    // A simple 5x7 font data (packed) for '0'..'9' and ':'
    // 0: [0x3E, 0x51, 0x49, 0x45, 0x3E]
    const FONT_DATA: &[[u8; 5]] = &[
        [0x3E, 0x51, 0x49, 0x45, 0x3E], // 0
        [0x00, 0x42, 0x7F, 0x40, 0x00], // 1
        [0x42, 0x61, 0x51, 0x49, 0x46], // 2
        [0x21, 0x41, 0x45, 0x4B, 0x31], // 3
        [0x18, 0x14, 0x12, 0x7F, 0x10], // 4
        [0x27, 0x45, 0x45, 0x45, 0x39], // 5
        [0x3C, 0x4A, 0x49, 0x49, 0x30], // 6
        [0x01, 0x71, 0x09, 0x05, 0x03], // 7
        [0x36, 0x49, 0x49, 0x49, 0x36], // 8
        [0x06, 0x49, 0x49, 0x29, 0x1E], // 9
        [0x00, 0x36, 0x36, 0x00, 0x00], // : (10)
    ];

    pub fn draw_text_scaled(
        img: &mut RgbaImage,
        text: &str,
        x: u32,
        y: u32,
        scale: u32,
        color: Rgba<u8>,
    ) {
        let mut curr_x = x;
        let spacing = 1 * scale;

        for c in text.chars() {
            let glyph_idx = match c {
                '0'..='9' => (c as usize) - ('0' as usize),
                ':' => 10,
                _ => continue,
            };

            let glyph = FONT_DATA[glyph_idx];
            for (col_idx, &col_byte) in glyph.iter().enumerate() {
                for row_idx in 0..8 {
                    if (col_byte >> row_idx) & 1 == 1 {
                        // Draw scaled pixel
                        for sx in 0..scale {
                            for sy in 0..scale {
                                let px = curr_x + (col_idx as u32 * scale) + sx;
                                let py = y + (row_idx as u32 * scale) + sy;
                                if px < img.width() && py < img.height() {
                                    img.put_pixel(px, py, color);
                                }
                            }
                        }
                    }
                }
            }
            curr_x += (5 * scale) + spacing;
        }
    }
}

/// Small ddos-tester CLI for load testing the screenshot endpoint.
#[derive(Parser, Debug, Clone)]
#[command(author, version, about, long_about = None)]
struct Args {
    /// Number of concurrent clients (connections) to simulate
    #[arg(short, long)]
    clients: usize,

    /// Prefix for client names (A-Z and a-z only)
    #[arg(short, long)]
    prefix: String,

    /// Interval between sends (seconds, can be fractional)
    #[arg(short = 't', long, default_value_t = 5.0)]
    interval: f64,

    /// Do not actually send HTTP requests; show planned requests only
    #[arg(long)]
    dry_run: bool,

    /// Add random noise to the background (0.0 to 1.0) with +/- 5% variation
    #[arg(long)]
    noise: Option<f64>,

    /// Spread percentage (0.0-1.0) for staggering client start times.
    /// 0.0 = all start together (burst), 1.0 = evenly distributed over `interval` (smooth load).
    #[arg(long, default_value_t = 0.0)]
    spread: f64,

    /// Target URL (e.g. http://localhost:8080)
    #[arg(long, default_value = "http://localhost:8080")]
    url: String,

    /// Use the production URL (http://franklyn3.htl-leonding.ac.at:8080)
    #[arg(long)]
    prod: bool,

    /// Use the dev URL (http://localhost:8080)
    #[arg(long)]
    dev: bool,

    /// Run in headless mode (no TUI), printing stats to stdout
    #[arg(long)]
    headless: bool,
}

#[tokio::main]
async fn main() -> Result<()> {
    let args = Args::parse();

    validate_args(&args)?;

    let base_url = if args.prod {
        _PROD_URL.to_string()
    } else if args.dev {
        _DEV_URL.to_string()
    } else {
        args.url.clone()
    };

    println!("ddos-tester: clients={} prefix={} interval={}s dry_run={} noise={:?} spread={} url={} headless={}", 
        args.clients, args.prefix, args.interval, args.dry_run, args.noise, args.spread, base_url, args.headless);

    let start = Instant::now();

    let client_names = generate_unique_names(&args.prefix, args.clients)?;

    let client = Arc::new(reqwest::Client::new());

    let attempted = Arc::new(AtomicUsize::new(0));
    let succeeded = Arc::new(AtomicUsize::new(0));
    let failed = Arc::new(AtomicUsize::new(0));
    let bytes_sent = Arc::new(AtomicUsize::new(0));
    let connected_clients = Arc::new(AtomicUsize::new(0));

    let interval_duration = Duration::from_secs_f64(args.interval);

    // Calculate start delay per client based on spread percentage.
    let spread_factor = args.spread.clamp(0.0, 1.0);
    let delay_per_client = if args.clients > 1 {
        (args.interval * spread_factor) / (args.clients as f64)
    } else {
        0.0
    };

    // Pre-compute frames to save CPU during the test
    let pool_size = (300.0 / args.interval).ceil() as usize; // 5 minute loop
    let precomputed_frames = Arc::new(precompute_frames(pool_size, args.interval, args.noise)?);

    // Capture global start time for synchronization
    let global_start_tokio = tokio::time::Instant::now();

    // Spawn client tasks
    for (idx, name) in client_names.into_iter().enumerate() {
        let name = name.clone();
        let client = Arc::clone(&client);
        let attempted = Arc::clone(&attempted);
        let succeeded = Arc::clone(&succeeded);
        let failed = Arc::clone(&failed);
        let bytes_sent = Arc::clone(&bytes_sent);
        let connected_clients = Arc::clone(&connected_clients);
        let dry_run = args.dry_run;
        let interval = interval_duration;
        let initial_delay_secs = delay_per_client * idx as f64;
        let precomputed_frames = Arc::clone(&precomputed_frames);
        let base_url = base_url.clone();

        // Generate a random loop offset for this client (0s to 300s)
        // This ensures not everyone is in the "coding" or "browsing" phase at the same time
        let loop_offset = rand::thread_rng().gen_range(0.0..300.0);

        tokio::spawn(async move {
            let target_start = global_start_tokio + Duration::from_secs_f64(initial_delay_secs);
            tokio::time::sleep_until(target_start).await;

            loop {
                // WebSocket logic
                let mut ws = loop {
                    // Use local connect_ws with dynamic URL
                    match connect_ws(&base_url, &name).await {
                        Ok(ws) => {
                            connected_clients.fetch_add(1, Ordering::Relaxed);
                            break ws;
                        }
                        Err(_) => tokio::time::sleep(Duration::from_secs(1)).await,
                    }
                };

                let ws_logic = async {
                    loop {
                        match ws.read_frame().await {
                            Ok(frame) => match frame.opcode {
                                OpCode::Ping => {
                                    if ws.write_frame(Frame::pong(frame.payload)).await.is_err() {
                                        return;
                                    }
                                }
                                OpCode::Close => return,
                                _ => {}
                            },
                            Err(_) => return,
                        }
                    }
                };

                // HTTP logic
                let http_logic = async {
                    let mut _frame_count = 0;
                    let mut next_deadline = tokio::time::Instant::now();

                    loop {
                        next_deadline += Duration::from_secs_f64(interval.as_secs_f64());
                        attempted.fetch_add(1, Ordering::Relaxed);

                        let elapsed = global_start_tokio.elapsed().as_secs_f64();
                        // Add random offset to loop time so clients are desynchronized in their "task"
                        let loop_time = (elapsed + loop_offset) % 300.0;

                        // Calculate frame index based on time
                        let frame_idx = (loop_time / interval.as_secs_f64()) as usize
                            % precomputed_frames.len();

                        let image_bytes = &precomputed_frames[frame_idx];
                        let payload_size = image_bytes.len();
                        // frame_count not used for content anymore
                        _frame_count += 1;

                        if !dry_run {
                            let body_bytes = image_bytes.clone();
                            let part = multipart::Part::bytes(body_bytes)
                                .file_name("image.png")
                                .mime_str("image/png")
                                .unwrap();

                            let form = multipart::Form::new().part("image", part);
                            let url = format!("{}/screenshot/{}/alpha", base_url, name);

                            match client.post(&url).multipart(form).send().await {
                                Ok(resp) => {
                                    if resp.status().is_success() {
                                        succeeded.fetch_add(1, Ordering::Relaxed);
                                        bytes_sent.fetch_add(payload_size, Ordering::Relaxed);
                                    } else {
                                        failed.fetch_add(1, Ordering::Relaxed);
                                    }
                                }
                                Err(_) => {
                                    failed.fetch_add(1, Ordering::Relaxed);
                                }
                            }
                        }

                        tokio::time::sleep_until(next_deadline).await;
                        if next_deadline.elapsed() > interval {
                            next_deadline = tokio::time::Instant::now();
                        }
                    }
                };

                tokio::select! {
                    _ = ws_logic => {},
                    _ = http_logic => {}
                }
                connected_clients.fetch_sub(1, Ordering::Relaxed);
                tokio::time::sleep(Duration::from_secs(1)).await;
            }
        });
    }

    if args.headless {
        println!("Running in headless mode. Press Ctrl-C to stop.");
        run_headless(
            &args,
            &attempted,
            &succeeded,
            &failed,
            &bytes_sent,
            &connected_clients,
        )
        .await?;
    } else {
        // Run TUI
        println!("Starting TUI... Press 'q' or Ctrl-C to exit.");
        // Give a moment for user to read
        tokio::time::sleep(Duration::from_secs(1)).await;

        let global_start = Instant::now();
        run_tui(
            &args,
            &attempted,
            &succeeded,
            &failed,
            &bytes_sent,
            &connected_clients,
            global_start,
        )
        .await?;
    }

    let elapsed = start.elapsed();
    println!("\nShutting down...");
    println!(
        "Summary: attempted={} succeeded={} failed={} elapsed={:?}",
        attempted.load(Ordering::Relaxed),
        succeeded.load(Ordering::Relaxed),
        failed.load(Ordering::Relaxed),
        elapsed
    );

    Ok(())
}

async fn run_headless(
    args: &Args,
    attempted: &Arc<AtomicUsize>,
    succeeded: &Arc<AtomicUsize>,
    failed: &Arc<AtomicUsize>,
    bytes_sent: &Arc<AtomicUsize>,
    connected_clients: &Arc<AtomicUsize>,
) -> Result<()> {
    let mut last_bytes = 0;
    let mut last_reqs = 0;
    let mut last_check = Instant::now();

    loop {
        tokio::time::sleep(Duration::from_secs(1)).await;

        let now = Instant::now();
        let duration = now.duration_since(last_check).as_secs_f64();

        let current_bytes = bytes_sent.load(Ordering::Relaxed);
        let att = attempted.load(Ordering::Relaxed);
        let succ = succeeded.load(Ordering::Relaxed);
        let fail = failed.load(Ordering::Relaxed);
        let conn = connected_clients.load(Ordering::Relaxed);

        let bytes_diff = current_bytes - last_bytes;
        let mb_per_sec = (bytes_diff as f64 / duration) * 8.0 / 1_000_000.0;

        let current_reqs = succ + fail;
        let reqs_diff = current_reqs - last_reqs;
        let rps = reqs_diff as f64 / duration;

        println!(
            "Clients: {}/{} | RPS: {:.1} | Requests: OK={} Fail={} Pending={} | Speed: {:.2} Mb/s",
            conn,
            args.clients,
            rps,
            succ,
            fail,
            att.saturating_sub(succ + fail),
            mb_per_sec
        );

        last_bytes = current_bytes;
        last_reqs = current_reqs;
        last_check = now;
    }
}

async fn run_tui(
    args: &Args,
    attempted: &Arc<AtomicUsize>,
    succeeded: &Arc<AtomicUsize>,
    failed: &Arc<AtomicUsize>,
    bytes_sent: &Arc<AtomicUsize>,
    connected_clients: &Arc<AtomicUsize>,
    global_start: Instant,
) -> Result<()> {
    enable_raw_mode()?;
    let mut stdout = stdout();
    execute!(stdout, EnterAlternateScreen, EnableMouseCapture)?;
    let backend = CrosstermBackend::new(stdout);
    let mut terminal = Terminal::new(backend)?;

    let mut last_bytes = 0;
    let mut last_reqs = 0;
    let mut last_check = Instant::now();

    let mut rps_history: VecDeque<f64> = VecDeque::with_capacity(60);
    let mut raw_rps_buffer: VecDeque<f64> = VecDeque::with_capacity(20);
    let mut speed_history: VecDeque<f64> = VecDeque::with_capacity(60);
    let mut avg_speed_history: VecDeque<f64> = VecDeque::with_capacity(60);
    let mut raw_speed_buffer: VecDeque<f64> = VecDeque::with_capacity(20);
    let mut diff_history: VecDeque<f64> = VecDeque::with_capacity(60);

    for _ in 0..60 {
        rps_history.push_back(0.0);
        speed_history.push_back(0.0);
        avg_speed_history.push_back(0.0);
        diff_history.push_back(0.0);
    }

    loop {
        // Calculate stats
        let now = Instant::now();
        if now.duration_since(last_check).as_secs_f64() >= 1.0 {
            let current_bytes = bytes_sent.load(Ordering::Relaxed);
            let attempted_val = attempted.load(Ordering::Relaxed);
            let succeeded_val = succeeded.load(Ordering::Relaxed);
            let failed_val = failed.load(Ordering::Relaxed);
            
            let duration = now.duration_since(last_check).as_secs_f64();

            let bytes_diff = current_bytes - last_bytes;
            let bytes_per_sec = bytes_diff as f64 / duration;

            let current_reqs = succeeded_val + failed_val;
            let reqs_diff = current_reqs - last_reqs;
            let actual_rps = reqs_diff as f64 / duration;

            // Diff (Backlog/Failures)
            let current_diff = if attempted_val >= succeeded_val {
                (attempted_val - succeeded_val) as f64
            } else {
                0.0
            };
            diff_history.pop_front();
            diff_history.push_back(current_diff);

            // Sliding window average for smoothness (last 20 samples)
            if raw_rps_buffer.len() >= 20 {
                raw_rps_buffer.pop_front();
            }
            raw_rps_buffer.push_back(actual_rps);
            let avg_rps = raw_rps_buffer.iter().sum::<f64>() / raw_rps_buffer.len() as f64;

            rps_history.pop_front();
            rps_history.push_back(avg_rps);

            // Speed average
            if raw_speed_buffer.len() >= 20 {
                raw_speed_buffer.pop_front();
            }
            raw_speed_buffer.push_back(bytes_per_sec);
            let avg_speed = raw_speed_buffer.iter().sum::<f64>() / raw_speed_buffer.len() as f64;

            speed_history.pop_front();
            speed_history.push_back(bytes_per_sec);

            avg_speed_history.pop_front();
            avg_speed_history.push_back(avg_speed);

            last_bytes = current_bytes;
            last_reqs = current_reqs;
            last_check = now;
        }

        // Draw
        terminal.draw(|f| {
            let chunks = Layout::default()
                .direction(Direction::Vertical)
                .margin(1)
                .constraints(vec![
                    Constraint::Length(3), // Header
                    Constraint::Length(3), // Gauge
                    Constraint::Length(3), // Stats Row
                    Constraint::Min(0),    // Charts
                ])
                .split(f.area());

            let current_rps = *rps_history.back().unwrap_or(&0.0);
            let current_speed_mb = *speed_history.back().unwrap_or(&0.0) * 8.0 / 1_000_000.0;
            let avg_speed_mb = *avg_speed_history.back().unwrap_or(&0.0) * 8.0 / 1_000_000.0;
            let expected_rps = if args.clients > 0 {
                args.clients as f64 / args.interval
            } else {
                0.0
            };
            let connected_val = connected_clients.load(Ordering::Relaxed);

            let elapsed = global_start.elapsed().as_secs_f64();
            let loop_time_secs = elapsed % 300.0;
            let loop_mins = (loop_time_secs / 60.0) as u64;
            let loop_secs = (loop_time_secs % 60.0) as u64;
            let sync_time_str = format!("{:02}:{:02}", loop_mins, loop_secs);

            // 1. Header
            let header_block = Block::default().borders(Borders::ALL).title(Span::styled(
                " DDoS Tester ",
                Style::default()
                    .fg(Color::Cyan)
                    .add_modifier(Modifier::BOLD),
            ));

            let header_text = Line::from(vec![
                Span::styled(" Clients: ", Style::default().fg(Color::Gray)),
                Span::styled(
                    format!("{}/{} ", connected_val, args.clients),
                    Style::default()
                        .fg(if connected_val == args.clients {
                            Color::Green
                        } else {
                            Color::Yellow
                        })
                        .add_modifier(Modifier::BOLD),
                ),
                Span::raw("|"),
                Span::styled(" Interval: ", Style::default().fg(Color::Gray)),
                Span::styled(
                    format!("{}s ", args.interval),
                    Style::default()
                        .fg(Color::White)
                        .add_modifier(Modifier::BOLD),
                ),
                Span::raw("|"),
                Span::styled(" Sync Time: ", Style::default().fg(Color::Gray)),
                Span::styled(
                    format!("{} ", sync_time_str),
                    Style::default()
                        .fg(Color::Yellow)
                        .add_modifier(Modifier::BOLD),
                ),
                Span::raw("|"),
                Span::styled(" Target RPS: ", Style::default().fg(Color::Gray)),
                Span::styled(
                    format!("{:.1} ", expected_rps),
                    Style::default()
                        .fg(Color::Yellow)
                        .add_modifier(Modifier::BOLD),
                ),
            ]);

            let p_header = Paragraph::new(header_text)
                .block(header_block)
                .alignment(ratatui::layout::Alignment::Center);
            f.render_widget(p_header, chunks[0]);

            // Stats Values
            let attempted_val = attempted.load(Ordering::Relaxed);
            let succeeded_val = succeeded.load(Ordering::Relaxed);
            let failed_val = failed.load(Ordering::Relaxed);

            // Derived stats
            let avg_req_size_mb = if current_rps > 0.0 {
                current_speed_mb / current_rps
            } else {
                0.0
            };

            // Color Logic
            let rps_ratio = if expected_rps > 0.0 {
                current_rps / expected_rps
            } else {
                0.0
            };
            let health_color = if rps_ratio >= 0.95 {
                Color::Green
            } else if rps_ratio >= 0.80 {
                Color::Yellow
            } else {
                Color::Red
            };

            let fail_color = if failed_val > 0 {
                Color::Red
            } else {
                Color::Green
            };

            // 2. Gauge (RPS Health)
            let gauge = Gauge::default()
                .block(
                    Block::default()
                        .title(" RPS Stability ")
                        .borders(Borders::ALL),
                )
                .gauge_style(Style::default().fg(health_color))
                .percent((rps_ratio * 100.0).clamp(0.0, 100.0) as u16)
                .label(format!(
                    "{:.1} / {:.1} RPS ({:.0}%)",
                    current_rps,
                    expected_rps,
                    rps_ratio * 100.0
                ));
            f.render_widget(gauge, chunks[1]);

            // 3. Stats Row
            let stats_block = Block::default().borders(Borders::ALL);
            let stats_text = Line::from(vec![
                Span::styled(" Sent: ", Style::default().fg(Color::Gray)),
                Span::styled(
                    format!("{} ", attempted_val),
                    Style::default().fg(Color::White),
                ),
                Span::raw("   "),
                Span::styled(" OK: ", Style::default().fg(Color::Gray)),
                Span::styled(
                    format!("{} ", succeeded_val),
                    Style::default()
                        .fg(Color::Green)
                        .add_modifier(Modifier::BOLD),
                ),
                Span::raw("   "),
                Span::styled(" Fail: ", Style::default().fg(Color::Gray)),
                Span::styled(
                    format!("{} ", failed_val),
                    Style::default().fg(fail_color).add_modifier(Modifier::BOLD),
                ),
                Span::raw("   "),
                Span::styled(" Bandwidth: ", Style::default().fg(Color::Gray)),
                Span::styled(
                    format!(
                        "{:.2} Mb/s (Avg: {:.2} Mb/s | {:.3} Mb/req) ",
                        current_speed_mb, avg_speed_mb, avg_req_size_mb
                    ),
                    Style::default()
                        .fg(Color::Cyan)
                        .add_modifier(Modifier::BOLD),
                ),
            ]);
            let p_stats = Paragraph::new(stats_text)
                .block(stats_block)
                .alignment(ratatui::layout::Alignment::Center);
            f.render_widget(p_stats, chunks[2]);

            // 4. Charts
            // Split into 3 vertical columns
            let chart_chunks = Layout::default()
                .direction(Direction::Horizontal)
                .constraints([
                    Constraint::Percentage(33),
                    Constraint::Percentage(33),
                    Constraint::Percentage(33),
                ])
                .split(chunks[3]);

            // Chart 0: Diff (Sent - OK)
            let diff_data: Vec<(f64, f64)> = diff_history
                .iter()
                .enumerate()
                .map(|(i, &v)| (i as f64, v))
                .collect();
            let max_diff = diff_data
                .iter()
                .map(|(_, v)| *v)
                .fold(0.0f64, f64::max)
                .max(10.0);

            let diff_dataset = vec![Dataset::default()
                .name("Pending/Failed")
                .marker(symbols::Marker::Braille)
                .graph_type(GraphType::Line)
                .style(Style::default().fg(Color::Magenta))
                .data(&diff_data)];
            let chart_diff = Chart::new(diff_dataset)
                .block(
                    Block::default()
                        .title(" Pending/Dropped (Sent - OK) ")
                        .borders(Borders::ALL),
                )
                .x_axis(
                    Axis::default()
                        .title("Time")
                        .bounds([0.0, 60.0])
                        .labels(vec![Span::raw("0s"), Span::raw("60s")]),
                )
                .y_axis(
                    Axis::default()
                        .title("Reqs")
                        .bounds([0.0, max_diff * 1.2])
                        .labels(vec![
                            Span::raw("0"),
                            Span::raw(format!("{:.0}", max_diff * 1.2)),
                        ]),
                );
            f.render_widget(chart_diff, chart_chunks[0]);

            // Chart 1: RPS
            let rps_data: Vec<(f64, f64)> = rps_history
                .iter()
                .enumerate()
                .map(|(i, &v)| (i as f64, v))
                .collect();
            let rps_dataset = vec![Dataset::default()
                .name("RPS")
                .marker(symbols::Marker::Braille)
                .graph_type(GraphType::Line)
                .style(Style::default().fg(health_color))
                .data(&rps_data)];
            let chart_rps = Chart::new(rps_dataset)
                .block(
                    Block::default()
                        .title(" Requests Per Second ")
                        .borders(Borders::ALL),
                )
                .x_axis(
                    Axis::default()
                        .title("Time")
                        .bounds([0.0, 60.0])
                        .labels(vec![Span::raw("0s"), Span::raw("60s")]),
                )
                .y_axis(
                    Axis::default()
                        .title("Reqs")
                        .bounds([0.0, expected_rps * 1.2])
                        .labels(vec![
                            Span::raw("0"),
                            Span::raw(format!("{:.0}", expected_rps * 1.2)),
                        ]),
                );
            f.render_widget(chart_rps, chart_chunks[1]);

            // Chart 2: Speed
            let speed_data: Vec<(f64, f64)> = speed_history
                .iter()
                .enumerate()
                .map(|(i, &v)| (i as f64, v * 8.0 / 1_000_000.0))
                .collect();
            let avg_speed_data: Vec<(f64, f64)> = avg_speed_history
                .iter()
                .enumerate()
                .map(|(i, &v)| (i as f64, v * 8.0 / 1_000_000.0))
                .collect();

            let max_speed = speed_data
                .iter()
                .map(|(_, v)| *v)
                .fold(0.0f64, f64::max)
                .max(1.0); // avoid 0 bound

            let speed_dataset = vec![
                Dataset::default()
                    .name("Instant")
                    .marker(symbols::Marker::Braille)
                    .graph_type(GraphType::Line)
                    .style(Style::default().fg(Color::DarkGray))
                    .data(&speed_data),
                Dataset::default()
                    .name("Average")
                    .marker(symbols::Marker::Braille)
                    .graph_type(GraphType::Line)
                    .style(
                        Style::default()
                            .fg(Color::Cyan)
                            .add_modifier(Modifier::BOLD),
                    )
                    .data(&avg_speed_data),
            ];
            let chart_speed = Chart::new(speed_dataset)
                .block(
                    Block::default()
                        .title(" Network Speed (Mb/s) ")
                        .borders(Borders::ALL),
                )
                .x_axis(
                    Axis::default()
                        .title("Time")
                        .bounds([0.0, 60.0])
                        .labels(vec![Span::raw("0s"), Span::raw("60s")]),
                )
                .y_axis(
                    Axis::default()
                        .title("Mb/s")
                        .bounds([0.0, max_speed * 1.2])
                        .labels(vec![
                            Span::raw("0"),
                            Span::raw(format!("{:.1}", max_speed * 1.2)),
                        ]),
                );
            f.render_widget(chart_speed, chart_chunks[2]);
        })?;

        // Input handling
        if crossterm::event::poll(Duration::from_millis(100))? {
            if let Event::Key(key) = event::read()? {
                if let KeyCode::Char('q') = key.code {
                    break;
                }
                if let KeyCode::Char('c') = key.code {
                    if key
                        .modifiers
                        .contains(crossterm::event::KeyModifiers::CONTROL)
                    {
                        break;
                    }
                }
            }
        }
    }

    // Restore terminal
    disable_raw_mode()?;
    execute!(
        terminal.backend_mut(),
        LeaveAlternateScreen,
        DisableMouseCapture
    )?;
    terminal.show_cursor()?;

    Ok(())
}

fn validate_args(args: &Args) -> Result<()> {
    if args.clients == 0 {
        return Err(anyhow!("clients must be > 0"));
    }

    if args.clients > MAX_CLIENTS {
        return Err(anyhow!("clients too large; max allowed is {}", MAX_CLIENTS));
    }

    if args.interval <= 0.0 {
        return Err(anyhow!("interval must be > 0"));
    }

    if args.spread < 0.0 || args.spread > 1.0 {
        return Err(anyhow!("spread must be between 0.0 and 1.0"));
    }

    if let Some(n) = args.noise {
        if n < 0.0 || n > 1.0 {
            return Err(anyhow!("noise must be between 0.0 and 1.0"));
        }
    }

    if !args.prefix.chars().all(|c| c.is_ascii_alphabetic()) {
        return Err(anyhow!("prefix must contain only A-Z or a-z characters"));
    }

    Ok(())
}

fn precompute_frames(
    count: usize,
    interval: f64,
    noise_level: Option<f64>,
) -> Result<Vec<Vec<u8>>> {
    let cache_dir = "frame_cache";
    fs::create_dir_all(cache_dir)?;

    let noise_str = match noise_level {
        Some(n) => format!("{:.4}", n),
        None => "none".to_string(),
    };
    // Update filename to reflect new version (v5) to force regeneration when visuals change
    let filename = format!(
        "{}/frames_v5_{}_int_{}_noise_{}.bin",
        cache_dir, count, interval, noise_str
    );
    let path = std::path::Path::new(&filename);

    if path.exists() {
        println!("Loading precomputed frames from cache: {}", filename);
        // Attempt to load
        let load_result = (|| -> Result<Vec<Vec<u8>>> {
            let file = fs::File::open(path)?;
            let mut reader = std::io::BufReader::new(file);
            use std::io::Read;

            let mut buf_u32 = [0u8; 4];
            reader.read_exact(&mut buf_u32)?;
            let stored_count = u32::from_le_bytes(buf_u32) as usize;

            if stored_count != count {
                return Err(anyhow!("Cache count mismatch"));
            }

            let mut frames = Vec::with_capacity(stored_count);
            for _ in 0..stored_count {
                reader.read_exact(&mut buf_u32)?;
                let size = u32::from_le_bytes(buf_u32) as usize;
                let mut frame = vec![0u8; size];
                reader.read_exact(&mut frame)?;
                frames.push(frame);
            }
            Ok(frames)
        })();

        match load_result {
            Ok(frames) => return Ok(frames),
            Err(e) => {
                eprintln!("Failed to load cache (will regenerate): {}", e);
                let _ = fs::remove_file(path);
            }
        }
    }

    println!("Precomputing {} frames (5min loop)...", count);
    let start_precomp = Instant::now();

    // Track progress
    let completed = Arc::new(AtomicUsize::new(0));
    let completed_monitor = completed.clone();
    let completed_worker = completed.clone();

    // Spawn a background thread to update the timer
    let done = Arc::new(AtomicBool::new(false));
    let done_clone = done.clone();

    std::thread::spawn(move || {
        while !done_clone.load(Ordering::Relaxed) {
            let n = completed_monitor.load(Ordering::Relaxed);
            let elapsed = start_precomp.elapsed().as_secs_f64();

            if n > 0 {
                let rate = n as f64 / elapsed; // frames per second
                let remaining = count - n;
                let eta_secs = if rate > 0.0 {
                    remaining as f64 / rate
                } else {
                    0.0
                };
                print!(
                    "\rPrecomputing... {:.1}% ({}/{}) ETA: {:.1}s   ",
                    (n as f64 / count as f64) * 100.0,
                    n,
                    count,
                    eta_secs
                );
            } else {
                print!("\rPrecomputing... 0% ({}/{}) ETA: ???   ", n, count);
            }

            let _ = std::io::stdout().flush();
            std::thread::sleep(Duration::from_millis(100));
        }
    });

    // Use Rayon to generate frames in parallel
    let frames: Result<Vec<Vec<u8>>> = (0..count)
        .into_par_iter()
        .map(move |i| {
            let res = generate_placeholder_png(i, interval, noise_level);
            completed_worker.fetch_add(1, Ordering::Relaxed);
            res
        })
        .collect();

    done.store(true, Ordering::Relaxed);
    let frames = frames?;

    println!(
        "\rPrecomputation complete in {:.2?}.          ",
        start_precomp.elapsed()
    );

    // Save to cache
    if let Err(e) = (|| -> Result<()> {
        let file = fs::File::create(path)?;
        let mut writer = std::io::BufWriter::new(file);

        writer.write_all(&(frames.len() as u32).to_le_bytes())?;
        for frame in &frames {
            writer.write_all(&(frame.len() as u32).to_le_bytes())?;
            writer.write_all(frame)?;
        }
        Ok(())
    })() {
        eprintln!("Failed to save cache: {}", e);
    } else {
        println!("Saved frames to cache: {}", filename);
    }

    Ok(frames)
}

/// Generate a realistic simulation frame (IDE or Browser)
fn generate_placeholder_png(
    frame_count: usize,
    interval: f64,
    noise_level: Option<f64>,
) -> Result<Vec<u8>> {
    let width = 1920;
    let height = 1080;
    let mut img: RgbaImage = RgbaImage::new(width, height);

    let noise = noise_level.unwrap_or(0.0).clamp(0.0, 1.0);
    let mut rng = StdRng::seed_from_u64(
        (frame_count as u64).wrapping_mul(0x9E37_79B9_7F4A_7C15),
    );

    // Calculate simulation time within 90s cycle with noise-driven jitter
    let time_secs = frame_count as f64 * interval;
    let jitter = if noise > 0.0 {
        // up to +/-30s jitter scaled by noise
        (rng.gen::<f64>() - 0.5) * 30.0 * noise
    } else {
        0.0
    };
    let cycle_time = (time_secs + jitter).rem_euclid(90.0);

    // Determine base state:
    // 0-15s: Browser Reading (Light)
    // 15-50s: IDE Coding (Dark)
    // 50-60s: Browser Check (Light)
    // 60-90s: IDE Refactor (Dark)
    let mut state = if cycle_time < 15.0 {
        0
    } else if cycle_time < 50.0 {
        1
    } else if cycle_time < 60.0 {
        2
    } else {
        3
    };

    // Higher noise means more context switches between the states
    if noise > 0.0 && rng.gen::<f64>() < noise * 0.6 {
        state = rng.gen_range(0..4);
    }

    match state {
        0 => {
            let progress = if cycle_time < 15.0 {
                cycle_time / 15.0
            } else {
                rng.gen::<f64>()
            };
            draw_browser_frame(&mut img, progress, false);
        }
        1 => {
            let progress = if (15.0..50.0).contains(&cycle_time) {
                (cycle_time - 15.0) / 35.0
            } else {
                rng.gen::<f64>()
            };
            draw_ide_frame(&mut img, progress, false);
        }
        2 => {
            let progress = if (50.0..60.0).contains(&cycle_time) {
                (cycle_time - 50.0) / 10.0
            } else {
                rng.gen::<f64>()
            };
            draw_browser_frame(&mut img, progress, true);
        }
        _ => {
            let progress = if (60.0..90.0).contains(&cycle_time) {
                (cycle_time - 60.0) / 30.0
            } else {
                rng.gen::<f64>()
            };
            draw_ide_frame(&mut img, progress, true);
        }
    }

    // Add visual noise/complexity based on noise level
    if noise > 0.0 {
        let patch_count = (5.0 + noise * 120.0) as u32;
        for _ in 0..patch_count {
            let px = rng.gen_range(0..width);
            let py = rng.gen_range(0..height);
            let max_w = 10 + (noise * 150.0) as u32;
            let max_h = 10 + (noise * 100.0) as u32;
            let span_w = (width - px).max(3).min(max_w);
            let span_h = (height - py).max(3).min(max_h);
            let w = rng.gen_range(3..=span_w);
            let h = rng.gen_range(3..=span_h);
            let color = Rgba([
                rng.gen_range(0..=255),
                rng.gen_range(0..=255),
                rng.gen_range(0..=255),
                200,
            ]);
            draw_filled_rect(&mut img, px, py, w, h, color);
        }
    }

    // Burn timestamp for debugging
    let mins = (time_secs / 60.0) as u64;
    let secs = (time_secs % 60.0) as u64;
    let time_str = format!("{:02}:{:02}", mins, secs);

    // Draw shadow then text (bottom right)
    bitmap_font::draw_text_scaled(&mut img, &time_str, 1700, 1000, 5, Rgba([0, 0, 0, 255]));
    bitmap_font::draw_text_scaled(&mut img, &time_str, 1698, 998, 5, Rgba([255, 255, 0, 255]));

    let mut buf = Vec::new();
    // Default compression is fine here as image is simple
    image::DynamicImage::ImageRgba8(img).write_to(&mut Cursor::new(&mut buf), ImageFormat::Png)?;
    Ok(buf)
}

fn draw_filled_rect(img: &mut RgbaImage, x: u32, y: u32, w: u32, h: u32, color: Rgba<u8>) {
    let w_img = img.width();
    let h_img = img.height();

    for iy in y..(y + h) {
        if iy >= h_img {
            break;
        }
        for ix in x..(x + w) {
            if ix >= w_img {
                break;
            }
            img.put_pixel(ix, iy, color);
        }
    }
}

// Draw "Browser" style (Light Theme, Reading)
fn draw_browser_frame(img: &mut RgbaImage, progress: f64, is_checking: bool) {
    let bg_color = Rgba([245, 245, 245, 255]);
    let text_color = Rgba([70, 70, 70, 255]);
    let header_color = Rgba([225, 225, 225, 255]);
    let url_bar_color = Rgba([255, 255, 255, 255]);
    let tab_inactive = Rgba([210, 210, 210, 255]);
    let tab_active = Rgba([255, 255, 255, 255]);
    let accent = Rgba([66, 133, 244, 255]);

    // Fill BG
    draw_filled_rect(img, 0, 0, 1920, 1080, bg_color);

    // Header with tabs and controls
    draw_filled_rect(img, 0, 0, 1920, 90, header_color);
    // Tabs
    for i in 0..6u32 {
        let tab_x = 40 + i * 170;
        let color = if i == ((progress * 6.0) as u32 % 6) { tab_active } else { tab_inactive };
        draw_filled_rect(img, tab_x, 10, 150, 35, color);
        draw_filled_rect(img, tab_x + 10, 20, 90, 6, accent);
    }
    // URL bar and search box
    draw_filled_rect(img, 220, 50, 1100, 30, url_bar_color);
    draw_filled_rect(img, 1340, 50, 200, 30, Rgba([240, 240, 240, 255]));

    // Content: sections + side nav
    let scroll_y = if is_checking { 500 } else { (progress * 1200.0) as u32 };
    let start_y = 120;

    // Left navigation
    draw_filled_rect(img, 60, 110, 220, 920, Rgba([255, 255, 255, 255]));
    for i in 0..8 {
        let item_y = start_y + (i * 90) as i32 - scroll_y as i32;
        if item_y < 110 || item_y > 1040 { continue; }
        let y = item_y as u32;
        draw_filled_rect(img, 80, y, 180, 28, Rgba([235, 235, 235, 255]));
        draw_filled_rect(img, 85, y + 35, 130, 12, text_color);
        draw_filled_rect(img, 85, y + 52, 90, 12, text_color);
    }

    // Main cards
    for i in 0..10 {
        let block_y = start_y + (i * 180) as i32 - scroll_y as i32;
        if block_y < 110 { continue; }
        if block_y > 1100 { break; }
        let by = block_y as u32;

        draw_filled_rect(img, 320, by, 1400, 150, Rgba([255, 255, 255, 255]));
        draw_filled_rect(img, 340, by + 15, 420, 26, Rgba([40, 40, 40, 255]));
        draw_filled_rect(img, 340, by + 50, 1050, 14, text_color);
        draw_filled_rect(img, 340, by + 70, 980, 14, text_color);
        draw_filled_rect(img, 340, by + 90, 900, 14, text_color);
        // Image/preview placeholder
        draw_filled_rect(img, 1400, by + 20, 280, 110, Rgba([240, 240, 240, 255]));
    }

    // Sticky footer bar to add color variation
    draw_filled_rect(img, 0, 1040, 1920, 40, Rgba([52, 152, 219, 255]));
}

// Draw "IDE" style (Dark Theme, Coding)
fn draw_ide_frame(img: &mut RgbaImage, progress: f64, is_refactoring: bool) {
    let bg_color = Rgba([26, 26, 30, 255]); // VSCode Dark
    let sidebar_color = Rgba([38, 38, 42, 255]);
    let panel_color = Rgba([24, 24, 28, 255]);

    // Fill BG
    draw_filled_rect(img, 0, 0, 1920, 1080, bg_color);

    // Sidebar with items
    draw_filled_rect(img, 0, 0, 300, 1080, sidebar_color);
    for i in 0..20u32 {
        let y = 20 + i * 50;
        if y + 30 > 1080 { break; }
        let active = i == ((progress * 10.0) as u32 % 12);
        let color = if active { Rgba([60, 60, 90, 255]) } else { Rgba([48, 48, 54, 255]) };
        draw_filled_rect(img, 20, y, 260, 32, color);
        draw_filled_rect(img, 30, y + 38, 140, 10, Rgba([170, 170, 170, 255]));
    }

    // Bottom panel / terminal
    draw_filled_rect(img, 300, 800, 1620, 280, panel_color);
    draw_filled_rect(img, 320, 820, 1580, 20, Rgba([55, 55, 65, 255]));
    for i in 0..8u32 {
        draw_filled_rect(img, 330, 850 + i * 24, 1480, 16, Rgba([120, 255, 120, 255]));
    }

    // Syntax colors
    let c_blue = Rgba([86, 156, 214, 255]);
    let c_green = Rgba([106, 153, 85, 255]);
    let c_orange = Rgba([206, 145, 120, 255]);
    let c_white = Rgba([220, 220, 220, 255]);
    let c_purple = Rgba([197, 134, 192, 255]);

    let colors = [c_blue, c_white, c_white, c_orange, c_green, c_purple];

    let lines_to_draw = if is_refactoring {
        50 // Full file
    } else {
        // Typing effect: lines appear over time
        15 + (progress * 40.0) as u32
    };

    let start_x = 350;
    let mut current_y = 40;

    for i in 0..lines_to_draw {
        if current_y > 760 { break; }
        let indent = (i % 4) * 30; // varied indentation

        // Draw line number gutter
        draw_filled_rect(img, 310, current_y, 28, 18, Rgba([90, 90, 100, 255]));

        // Draw tokens
        let num_tokens = 3 + (i % 6);
        let mut cx = start_x + indent;

        for t in 0..num_tokens {
            let col = colors[(i as usize + t as usize) % colors.len()];
            let w = 28 + ((i * (t + 1) * 11) % 120);
            draw_filled_rect(img, cx, current_y, w, 20, col);
            cx += w + 12;
        }

        current_y += 26;
    }

    // Refactor Selection Highlight
    if is_refactoring {
        let sel_color = Rgba([38, 79, 120, 180]);
        draw_filled_rect(img, 360, 220, 620, 320, sel_color);
    }
}

/// Generate `count` unique client names by appending an A-Z/a-z suffix to the prefix.
fn generate_unique_names(prefix: &str, count: usize) -> Result<Vec<String>> {
    let mut set = HashSet::new();
    let mut out = Vec::with_capacity(count);
    let mut rng = OsRng;

    while out.len() < count {
        let s = gen_suffix(SUFFIX_LEN, &mut rng);
        let name = format!("{}{}", prefix, s);
        if !set.contains(&name) {
            set.insert(name.clone());
            out.push(name);
        }
    }

    Ok(out)
}

/// Generate a random suffix consisting only of ASCII letters.
fn gen_suffix(len: usize, rng: &mut OsRng) -> String {
    const ALPH: &[u8] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    let mut v = Vec::with_capacity(len);
    for _ in 0..len {
        let idx = rng.gen_range(0..ALPH.len());
        v.push(ALPH[idx]);
    }
    // Safety: bytes are ASCII letters
    unsafe { String::from_utf8_unchecked(v) }
}
