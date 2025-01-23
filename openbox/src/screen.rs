use anyhow::{Context, Result};
use image::{ImageFormat, Rgba, RgbaImage};
use reqwest::multipart::Part;

pub fn take_screenshot(
    expect_alpha: bool,
    cur: Option<&RgbaImage>,
) -> (Result<Part>, RgbaImage, &'static str) {
    let image = xcap::Monitor::all()
        .expect("ERROR: could not access monitors")
        .first()
        .expect("ERROR: no monitors found")
        .capture_image()
        .expect("ERROR: unable to take screenshot");

    if expect_alpha {
        return (image_to_file_part(&image), image, "alpha");
    }

    let (image, option) = transform_screenshot(cur, image);
    (image_to_file_part(&image), image, option)
}

fn image_to_file_part(image: &RgbaImage) -> Result<Part> {
    let mut buf = Vec::<u8>::new();
    image.write_to(&mut std::io::Cursor::new(&mut buf), ImageFormat::Png)?;
    Part::bytes(buf)
        .file_name("image.png")
        .mime_str("image/png")
        .context("ERROR: failed to create file part")
}

fn transform_screenshot(cur_img: Option<&RgbaImage>, img: RgbaImage) -> (RgbaImage, &'static str) {
    let Some(cur_img) = cur_img else {
        return (img, "alpha");
    };

    let (w, h, mut c) = (cur_img.width(), cur_img.height(), 0);
    let mut out_img = RgbaImage::from_pixel(w, h, Rgba([0, 0, 0, 0]));

    for x in 0..w {
        for y in 0..h {
            let beta_rgb = img.get_pixel(x, y);

            if cur_img.get_pixel(x, y) != beta_rgb {
                c += 1;
                out_img.put_pixel(x, y, beta_rgb.clone());
            }
        }
    }

    if c > w * h / 2 {
        return (img, "alpha");
    }

    (out_img, "beta")
}
