use anyhow::{Context, Result};
use image::{ImageFormat, Rgba, RgbaImage};
use rayon::prelude::*;
use std::fs::{self, File};
use std::io::{BufWriter, Write};
use std::path::PathBuf;
use std::sync::atomic::{AtomicU32, Ordering};
use std::sync::Arc;

/// Version number for the mock screen generation.
/// Increment this whenever the mock generation logic changes.
pub const CACHE_VERSION: u32 = 3;

/// Number of frames to pregenerate per client
pub const FRAMES_PER_CLIENT: u32 = 40;

/// Get the cache directory for a specific client
fn get_cache_dir(client_id: u32) -> PathBuf {
    PathBuf::from(format!("cache/v{}/client_{}", CACHE_VERSION, client_id))
}

/// Check if cache exists and is valid for a client
pub fn cache_exists(client_id: u32) -> bool {
    let cache_dir = get_cache_dir(client_id);
    if !cache_dir.exists() {
        return false;
    }

    // Check if we have all expected frames
    for i in 0..FRAMES_PER_CLIENT {
        let alpha_path = cache_dir.join(format!("frame_{:04}_alpha.png", i));
        let beta_path = cache_dir.join(format!("frame_{:04}_beta.png", i));
        if !alpha_path.exists() || !beta_path.exists() {
            return false;
        }
    }

    true
}

/// Pre-computed frame with PNG-encoded alpha and beta versions ready to send
#[derive(Clone)]
pub struct PrecomputedFrame {
    /// PNG bytes for alpha frame (full image)
    pub alpha_png: Arc<Vec<u8>>,
    /// PNG bytes for beta frame (diff from previous frame)
    pub beta_png: Arc<Vec<u8>>,
    /// Whether beta is actually different from alpha (if same, beta had >50% change)
    pub beta_is_alpha: bool,
}

/// Generate and save all frames for a client, including pre-encoded PNG alpha/beta
/// Reports progress via the provided callback after each frame is saved.
pub fn generate_and_save_frames_with_progress(client_id: u32, progress: &AtomicU32) -> Result<()> {
    use crate::mock_screen::MockScreenGenerator;

    let cache_dir = get_cache_dir(client_id);
    fs::create_dir_all(&cache_dir)
        .with_context(|| format!("Failed to create cache directory: {:?}", cache_dir))?;

    // Step 1: Generate all frame images in parallel
    let images: Vec<RgbaImage> = (0..FRAMES_PER_CLIENT)
        .into_par_iter()
        .map(|frame_idx| {
            let mut gen = MockScreenGenerator::new_at_frame(client_id, frame_idx);
            gen.generate_frame()
        })
        .collect();

    // Step 2: Compute diffs and encode PNGs, then save
    // Diffs must be computed sequentially (each depends on previous frame)
    // but PNG encoding can be parallelized per frame
    for frame_idx in 0..FRAMES_PER_CLIENT as usize {
        let img = &images[frame_idx];
        let prev_img = if frame_idx > 0 {
            Some(&images[frame_idx - 1])
        } else {
            None
        };

        // Compute what we need to encode
        let (diff_img, use_alpha_for_beta) = if let Some(prev) = prev_img {
            let (diff, is_alpha) = compute_diff(prev, img);
            (Some(diff), is_alpha)
        } else {
            // First frame has no previous, beta = alpha
            (None, true)
        };

        // Encode alpha and beta PNGs in parallel
        let (alpha_result, beta_result) = rayon::join(
            || encode_png(img),
            || {
                if use_alpha_for_beta {
                    // Will use alpha, encode it anyway (we'll copy the result)
                    encode_png(img)
                } else {
                    encode_png(diff_img.as_ref().unwrap())
                }
            },
        );

        let alpha_png = alpha_result?;
        let beta_png = if use_alpha_for_beta {
            alpha_png.clone()
        } else {
            beta_result?
        };

        // Save alpha PNG
        let alpha_path = cache_dir.join(format!("frame_{:04}_alpha.png", frame_idx));
        let mut file = BufWriter::new(File::create(&alpha_path)?);
        file.write_all(&alpha_png)?;

        // Save beta PNG
        let beta_path = cache_dir.join(format!("frame_{:04}_beta.png", frame_idx));
        let mut file = BufWriter::new(File::create(&beta_path)?);
        file.write_all(&beta_png)?;

        // Report progress after each frame is saved
        progress.fetch_add(1, Ordering::Relaxed);
    }

    Ok(())
}

fn encode_png(img: &RgbaImage) -> Result<Vec<u8>> {
    let mut buf = Vec::new();
    img.write_to(&mut std::io::Cursor::new(&mut buf), ImageFormat::Png)?;
    Ok(buf)
}

fn compute_diff(prev: &RgbaImage, new: &RgbaImage) -> (RgbaImage, bool) {
    let (w, h) = (prev.width(), prev.height());

    if w != new.width() || h != new.height() {
        return (new.clone(), true);
    }

    let mut changed_count = 0u32;
    let mut out_img = RgbaImage::from_pixel(w, h, Rgba([0, 0, 0, 0]));

    for x in 0..w {
        for y in 0..h {
            let new_pixel = new.get_pixel(x, y);
            if prev.get_pixel(x, y) != new_pixel {
                changed_count += 1;
                out_img.put_pixel(x, y, *new_pixel);
            }
        }
    }

    // If more than 50% changed, return alpha
    if changed_count > w * h / 2 {
        return (new.clone(), true);
    }

    (out_img, false)
}

/// Load a precomputed frame from cache
fn load_frame(client_id: u32, frame_index: u32) -> Result<PrecomputedFrame> {
    let cache_dir = get_cache_dir(client_id);

    let alpha_path = cache_dir.join(format!("frame_{:04}_alpha.png", frame_index));
    let beta_path = cache_dir.join(format!("frame_{:04}_beta.png", frame_index));

    let alpha_png =
        fs::read(&alpha_path).with_context(|| format!("Failed to read alpha: {:?}", alpha_path))?;
    let beta_png =
        fs::read(&beta_path).with_context(|| format!("Failed to read beta: {:?}", beta_path))?;

    // Check if beta is same as alpha (they'll have same size if identical)
    let beta_is_alpha = alpha_png == beta_png;

    Ok(PrecomputedFrame {
        alpha_png: Arc::new(alpha_png),
        beta_png: Arc::new(beta_png),
        beta_is_alpha,
    })
}

/// Load all precomputed frames for a client
pub fn load_all_frames(client_id: u32) -> Result<Vec<PrecomputedFrame>> {
    let mut frames = Vec::with_capacity(FRAMES_PER_CLIENT as usize);

    for i in 0..FRAMES_PER_CLIENT {
        let frame = load_frame(client_id, i)?;
        frames.push(frame);
    }

    Ok(frames)
}

/// Clean up old cache versions
pub fn cleanup_old_caches() {
    let cache_base = PathBuf::from("cache");
    if !cache_base.exists() {
        return;
    }

    if let Ok(entries) = fs::read_dir(&cache_base) {
        for entry in entries.flatten() {
            let path = entry.path();
            if path.is_dir() {
                if let Some(name) = path.file_name().and_then(|n| n.to_str()) {
                    if name.starts_with('v') {
                        if let Ok(version) = name[1..].parse::<u32>() {
                            if version < CACHE_VERSION {
                                println!("Cleaning up old cache version: {}", name);
                                let _ = fs::remove_dir_all(&path);
                            }
                        }
                    }
                }
            }
        }
    }
}

/// Frame provider that cycles through preloaded frames
#[derive(Clone)]
pub struct CachedFrameProvider {
    frames: Arc<Vec<PrecomputedFrame>>,
    current_index: usize,
}

impl CachedFrameProvider {
    /// Create a new frame provider by loading all cached frames
    pub fn new(client_id: u32) -> Result<Self> {
        let frames = load_all_frames(client_id)?;
        Ok(Self {
            frames: Arc::new(frames),
            current_index: 0,
        })
    }

    /// Get the next frame, cycling through the preloaded frames
    pub fn next_frame(&mut self) -> &PrecomputedFrame {
        let frame = &self.frames[self.current_index];
        self.current_index = (self.current_index + 1) % self.frames.len();
        frame
    }
}
