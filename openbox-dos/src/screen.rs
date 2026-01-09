use anyhow::{Context, Result};
use reqwest::multipart::Part;

use crate::cache::PrecomputedFrame;

/// Get the appropriate pre-encoded PNG bytes for upload
/// Returns (file_part, frame_type)
pub fn get_upload_part(
    expect_alpha: bool,
    frame: &PrecomputedFrame,
) -> (Result<Part>, &'static str) {
    if expect_alpha {
        // Server wants alpha, send the pre-encoded alpha PNG
        let part = bytes_to_file_part(&frame.alpha_png);
        (part, "alpha")
    } else {
        // Server wants beta, send the pre-encoded beta PNG
        // (which might actually be alpha if >50% pixels changed)
        let frame_type = if frame.beta_is_alpha { "alpha" } else { "beta" };
        let part = bytes_to_file_part(&frame.beta_png);
        (part, frame_type)
    }
}

fn bytes_to_file_part(bytes: &[u8]) -> Result<Part> {
    Part::bytes(bytes.to_vec())
        .file_name("image.png")
        .mime_str("image/png")
        .context("ERROR: failed to create file part")
}
