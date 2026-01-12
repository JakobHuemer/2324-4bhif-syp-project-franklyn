use clap::Parser;
use rand::Rng;
use rayon::prelude::*;
use std::sync::atomic::{AtomicU32, Ordering};
use std::sync::Arc;

mod cache;
mod mock_screen;
mod screen;
mod ws;

const _PROD_URL: &str = "franklyn3.htl-leonding.ac.at:8080";
const _STAGING_URL: &str = "franklyn3a.htl-leonding.ac.at:8080";
const _CI_URL: &str = "franklyn.ddns.net:8080";
const _DEV_URL: &str = "localhost:8080";

/// OpenBox stress test client - simulates multiple students in an exam
#[derive(Parser, Debug)]
#[command(version, about, long_about = None)]
struct Args {
    /// 3-digit exam PIN
    #[arg(short, long)]
    pin: String,

    /// Number of simulated clients
    #[arg(short, long, default_value = "1")]
    clients: u32,
}

fn get_server_address() -> &'static str {
    if cfg!(feature = "srv_prod") {
        _PROD_URL
    } else if cfg!(feature = "srv_staging") {
        _STAGING_URL
    } else if cfg!(feature = "srv_ci") {
        _CI_URL
    } else {
        _DEV_URL
    }
}

// Generate random student names for testing
fn generate_student_name(client_id: u32) -> (String, String) {
    let firstnames = [
        "Max", "Anna", "Lukas", "Sophie", "Felix", "Emma", "Paul", "Lena",
        "David", "Laura", "Michael", "Sarah", "Thomas", "Julia", "Daniel",
        "Lisa", "Sebastian", "Hannah", "Markus", "Marie", "Johannes", "Katharina",
        "Florian", "Eva", "Stefan", "Nina", "Andreas", "Sandra", "Patrick", "Melanie"
    ];
    
    let lastnames = [
        "Mueller", "Schmidt", "Schneider", "Fischer", "Weber", "Meyer", "Wagner",
        "Becker", "Schulz", "Hoffmann", "Koch", "Bauer", "Richter", "Klein",
        "Wolf", "Schroeder", "Neumann", "Schwarz", "Braun", "Hofmann", "Lange",
        "Werner", "Krause", "Lehmann", "Schmid", "Schulze", "Maier", "Koehler"
    ];
    
    let mut rng = rand::thread_rng();
    let firstname = firstnames[rng.gen_range(0..firstnames.len())];
    let lastname = lastnames[rng.gen_range(0..lastnames.len())];
    
    // Add client_id suffix to make names unique
    (
        format!("{}{}", firstname, client_id),
        lastname.to_string()
    )
}

async fn run_client(
    client_id: u32,
    pin: String,
    server: String,
    frame_provider: cache::CachedFrameProvider,
    registered_counter: Arc<AtomicU32>,
    total_clients: u32,
) {
    // Rate limit: stagger client connections at 5 clients/second (200ms apart)
    let delay_ms = client_id as u64 * 200;
    tokio::time::sleep(tokio::time::Duration::from_millis(delay_ms)).await;
    
    let (firstname, lastname) = generate_student_name(client_id);
    
    println!("[Client {}] Starting as {} {}...", client_id, firstname, lastname);
    
    // Update registration progress
    let registered = registered_counter.fetch_add(1, Ordering::Relaxed) + 1;
    let pct = (registered as f64 / total_clients as f64) * 100.0;
    print!("\r  Registered: {}/{} clients ({:.1}%)", registered, total_clients, pct);
    std::io::Write::flush(&mut std::io::stdout()).ok();
    if registered == total_clients {
        println!();
        println!("All {} clients registered.", total_clients);
        println!();
    }
    
    loop {
        match ws::run_client(&pin, &server, &firstname, &lastname, client_id, frame_provider.clone()).await {
            Ok(ws::ClientResult::Disconnected) => {
                println!("[Client {}] Disconnected by server", client_id);
                break;
            }
            Ok(ws::ClientResult::ServerError) => {
                println!("[Client {}] Server error (invalid pin?), retrying in 5s...", client_id);
                tokio::time::sleep(tokio::time::Duration::from_secs(5)).await;
            }
            Err(e) => {
                println!("[Client {}] Connection error: {}, reconnecting in 1s...", client_id, e);
                tokio::time::sleep(tokio::time::Duration::from_secs(1)).await;
            }
        }
    }
}

#[tokio::main]
async fn main() {
    let args = Args::parse();
    
    // Validate PIN
    if args.pin.len() != 3 || args.pin.parse::<u16>().is_err() {
        eprintln!("Error: PIN must be exactly 3 digits");
        std::process::exit(1);
    }
    
    let server = get_server_address().to_string();
    
    println!("OpenBox Stress Test Client");
    println!("==========================");
    println!("Server: {}", server);
    println!("PIN: {}", args.pin);
    println!("Clients: {}", args.clients);
    println!("Cache version: v{}", cache::CACHE_VERSION);
    println!();

    // Clean up old cache versions
    cache::cleanup_old_caches();

    // Pregenerate frames for all clients that don't have a valid cache
    println!("Checking/generating frame cache...");
    let mut clients_to_generate: Vec<u32> = Vec::new();
    
    for client_id in 0..args.clients {
        if !cache::cache_exists(client_id) {
            clients_to_generate.push(client_id);
        }
    }

    if clients_to_generate.is_empty() {
        println!("All clients have valid cached frames.");
    } else {
        let total_clients = clients_to_generate.len();
        let total_frames = total_clients as u32 * cache::FRAMES_PER_CLIENT;
        println!(
            "Generating frames for {} client(s) ({} frames each, {} total) using {} threads...",
            total_clients,
            cache::FRAMES_PER_CLIENT,
            total_frames,
            rayon::current_num_threads()
        );

        let completed_frames = Arc::new(AtomicU32::new(0));
        let error_flag = Arc::new(std::sync::atomic::AtomicBool::new(false));

        // Spawn a thread to print progress
        let completed_clone = Arc::clone(&completed_frames);
        let error_clone = Arc::clone(&error_flag);
        let progress_handle = std::thread::spawn(move || {
            loop {
                let done = completed_clone.load(Ordering::Relaxed);
                let pct = (done as f64 / total_frames as f64) * 100.0;
                let clients_done = done / cache::FRAMES_PER_CLIENT;
                let frames_in_current = done % cache::FRAMES_PER_CLIENT;
                print!(
                    "\r  Progress: {}/{} frames ({:.1}%) - {} clients complete, {} frames in progress",
                    done, total_frames, pct, clients_done, frames_in_current
                );
                std::io::Write::flush(&mut std::io::stdout()).ok();

                if done >= total_frames || error_clone.load(Ordering::Relaxed) {
                    println!();
                    break;
                }
                std::thread::sleep(std::time::Duration::from_millis(100));
            }
        });

        // Generate all frames for each client in parallel, with per-frame progress
        let completed_for_gen = Arc::clone(&completed_frames);
        let result: Result<(), anyhow::Error> = clients_to_generate
            .par_iter()
            .try_for_each(|&client_id| {
                cache::generate_and_save_frames_with_progress(client_id, &completed_for_gen)?;
                Ok(())
            });

        if let Err(e) = result {
            error_flag.store(true, Ordering::Relaxed);
            let _ = progress_handle.join();
            eprintln!("ERROR: {}", e);
            std::process::exit(1);
        }

        let _ = progress_handle.join();
        println!("Frame generation complete.");
    }
    println!();

    // Load all cached frames into memory (in parallel with progress)
    println!("Loading cached frames into memory...");
    let num_clients = args.clients;
    let loaded = Arc::new(AtomicU32::new(0));
    let load_error = Arc::new(std::sync::atomic::AtomicBool::new(false));

    // Progress thread for loading
    let loaded_clone = Arc::clone(&loaded);
    let load_error_clone = Arc::clone(&load_error);
    let load_progress_handle = std::thread::spawn(move || {
        loop {
            let done = loaded_clone.load(Ordering::Relaxed);
            let pct = (done as f64 / num_clients as f64) * 100.0;
            print!("\r  Progress: {}/{} clients ({:.1}%)", done, num_clients, pct);
            std::io::Write::flush(&mut std::io::stdout()).ok();

            if done >= num_clients || load_error_clone.load(Ordering::Relaxed) {
                println!();
                break;
            }
            std::thread::sleep(std::time::Duration::from_millis(50));
        }
    });

    // Load frames in parallel
    let client_ids: Vec<u32> = (0..args.clients).collect();
    let loaded_for_par = Arc::clone(&loaded);
    let frame_providers: Result<Vec<_>, _> = client_ids
        .par_iter()
        .map(|&client_id| {
            let provider = cache::CachedFrameProvider::new(client_id)?;
            loaded_for_par.fetch_add(1, Ordering::Relaxed);
            Ok::<_, anyhow::Error>((client_id, provider))
        })
        .collect();

    let frame_providers = match frame_providers {
        Ok(providers) => providers,
        Err(e) => {
            load_error.store(true, Ordering::Relaxed);
            let _ = load_progress_handle.join();
            eprintln!("ERROR loading frames: {}", e);
            std::process::exit(1);
        }
    };

    let _ = load_progress_handle.join();
    println!("All frames loaded into memory.");
    println!();
    
    println!("Starting {} clients (rate limited to 5 clients/second)...", args.clients);
    
    let mut handles = Vec::new();
    let total_clients = frame_providers.len() as u32;
    let registered_counter = Arc::new(AtomicU32::new(0));
    
    for (client_id, frame_provider) in frame_providers {
        let pin = args.pin.clone();
        let server = server.clone();
        let counter = Arc::clone(&registered_counter);
        
        let handle = tokio::spawn(async move {
            run_client(client_id, pin, server, frame_provider, counter, total_clients).await;
        });
        
        handles.push(handle);
    }
    
    // Wait for all clients to complete
    for handle in handles {
        let _ = handle.await;
    }
    
    println!("All clients finished.");
}
