use std::process::Command;
use std::path::Path;
use std::path::PathBuf;
use std::fs;

fn build_autoconf_proj(project_root: &str, lib_prefix: &PathBuf, proj_name: &str) -> () {
    let mut result = Command::new("mkdir")
        .arg("-p")
        .arg("m4")
        .current_dir(Path::new(project_root).join("externals").join(proj_name))
        .output()
        .expect(&format!("could not run mkdir m4 of {}", proj_name));

    if !result.status.success() {
        panic!("mkdir m4 of {} failed: {} | {}", proj_name, String::from_utf8(result.stdout).unwrap(), String::from_utf8(result.stderr).unwrap());
    }

    result = Command::new("./autogen.sh")
        .current_dir(Path::new(project_root).join("externals").join(proj_name))
        .output()
        .expect(&format!("could not run autogen of {}", proj_name));

    if !result.status.success() {
        panic!("autogen of {} failed: {} | {}", proj_name, String::from_utf8(result.stdout).unwrap(), String::from_utf8(result.stderr).unwrap());
    }

    result = Command::new("./configure")
        .current_dir(Path::new(project_root).join("externals").join(proj_name))
        .arg("--disable-shared")
        .arg("--enable-static")
        //.arg("--host=x86_64-linux-musl")
        .arg(&format!("--prefix={}", lib_prefix.display()))
        .output()
        .expect(&format!("could not run configure of {}", proj_name));

    if !result.status.success() {
        panic!("configure of {} failed: {} | {}", proj_name, String::from_utf8(result.stdout).unwrap(), String::from_utf8(result.stderr).unwrap());
    }

    result = Command::new("make")
        .current_dir(Path::new(project_root).join("externals").join(proj_name))
        .output()
        .expect(&format!("could not make {}", proj_name));

    if !result.status.success() {
        panic!("make of {} failed: {} | {}", proj_name, String::from_utf8(result.stdout).unwrap(), String::from_utf8(result.stderr).unwrap());
    }

    result = Command::new("make")
        .arg("install")
        .current_dir(Path::new(project_root).join("externals").join(proj_name))
        .output()
        .expect(&format!("could not install {}", proj_name));

    if !result.status.success() {
        panic!("install of {} failed: {} | {}", proj_name, String::from_utf8(result.stdout).unwrap(), String::from_utf8(result.stderr).unwrap());
    }
}

fn build_meson_proj(project_root: &str, lib_prefix: &PathBuf, proj_name: &str) -> () {
    let mut result = Command::new("meson")
        .arg("setup")
        .arg("--reconfigure")
        .arg("build")
        .arg("--default-library=static")
        .current_dir(Path::new(project_root).join("externals").join(proj_name))
        .output()
        .expect(&format!("could not run meson setup of {}", proj_name));

    if !result.status.success() {
        panic!("meson setup of {} failed: {} | {}", proj_name, String::from_utf8(result.stdout).unwrap(), String::from_utf8(result.stderr).unwrap());
    }

    result = Command::new("meson")
        .arg("compile")
        .arg("-C")
        .arg("build")
        .current_dir(Path::new(project_root).join("externals").join(proj_name))
        .output()
        .expect(&format!("could not run meson compile of {}", proj_name));

    if !result.status.success() {
        panic!("meson compile of {} failed: {} | {}", proj_name, String::from_utf8(result.stdout).unwrap(), String::from_utf8(result.stderr).unwrap());
    }

    result = Command::new("meson")
        .arg("install")
        .arg("-C")
        .arg("build")
        .arg("--destdir")
        .arg(lib_prefix)
        .current_dir(Path::new(project_root).join("externals").join(proj_name))
        .output()
        .expect(&format!("could not run meson install of {}", proj_name));

    if !result.status.success() {
        panic!("meson install of {} failed: {} | {}", proj_name, String::from_utf8(result.stdout).unwrap(), String::from_utf8(result.stderr).unwrap());
    }
}

fn build_xorg_macros(project_root: &str, lib_prefix: &PathBuf) -> () {
    build_autoconf_proj(project_root, lib_prefix, "macros");
}

fn build_xcb_proto(project_root: &str, lib_prefix: &PathBuf) -> () {
    build_autoconf_proj(project_root, lib_prefix, "xcbproto");
}

fn build_xorg_proto(project_root: &str, lib_prefix: &PathBuf) -> () {
    build_meson_proj(project_root, lib_prefix, "xorgproto");
}

fn build_libxau(project_root: &str, lib_prefix: &PathBuf) -> () {
    //build_meson_proj(project_root, lib_prefix, "libxau");
    build_autoconf_proj(project_root, lib_prefix, "libxau");
}

fn build_libxdmcp(project_root: &str, lib_prefix: &PathBuf) -> () {
    build_autoconf_proj(project_root, lib_prefix, "libxdmcp");
}

fn build_libxcb(project_root: &str, lib_prefix: &PathBuf) -> () {
    build_autoconf_proj(project_root, lib_prefix, "libxcb");
}

fn main() {
    let profile = std::env::var("OUT_DIR")
        .expect("how did you unset your build path???")
        .split(std::path::MAIN_SEPARATOR)
        .nth_back(3)
        .unwrap_or_else(|| "unknown")
        .to_string();

    let project_root = std::env::var("CARGO_MANIFEST_DIR")
        .expect("CARGO_MANIFEST_DIR not set");

    let out_dir = std::env::var("OUT_DIR")
        .expect("no output dir?");

    if profile != "release-opt" {
        return;
    }

    let lib_prefix = Path::new(&out_dir).join("static-libs-openbox");

    std::env::set_var("PKG_CONFIG_PATH", format!("{p}/share/pkgconfig:{p}/usr/local/share/pkgconfig:{p}/usr/local/lib/pkgconfig:{p}/lib/pkgconfig", p = lib_prefix.display()));
    std::env::set_var("ACLOCAL", format!("aclocal -I {}/share/aclocal", lib_prefix.display()));
    std::env::set_var("CFLAGS", format!("-I {}/usr/local/include", lib_prefix.display()));

    fs::create_dir_all(format!("{}/usr/local/include", lib_prefix.display())).unwrap();


    build_xorg_macros(&project_root, &lib_prefix);
    build_xcb_proto(&project_root, &lib_prefix);
    build_xorg_proto(&project_root, &lib_prefix);
    build_libxau(&project_root, &lib_prefix);
    build_libxdmcp(&project_root, &lib_prefix);
    build_libxcb(&project_root, &lib_prefix);

    // link flags
    println!("cargo:rustc-link-arg=-lXau");
    println!("cargo:rustc-link-arg=-lXdmcp");
    //println!("cargo:rustc-link-arg=-l:libc.a");

    // add search paths for static libraries 
    println!("cargo:rustc-link-search=native={}/lib/", lib_prefix.display());
    println!("cargo:rustc-link-search=native={}/usr/local/lib/", lib_prefix.display());
    println!("cargo:rustc-link-search=native={}/usr/lib/", lib_prefix.display());

    // link libXau, libXdmcp, libxcb statically
    println!("cargo:rust-link-lib=static=libXau");
    println!("cargo:rust-link-lib=static=libXdmcp");
    println!("cargo:rust-link-lib=static=libxcb");
    //println!("cargo:rust-link-lib=static=libc");
}
