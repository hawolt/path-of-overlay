use std::process::Command;
use std::path::Path;

fn main() {
    if std::env::var("CARGO_CFG_TARGET_OS").unwrap_or_default() != "windows" {
        return;
    }

    let manifest_dir = std::env::var("CARGO_MANIFEST_DIR").unwrap();
    let out_dir = std::env::var("OUT_DIR").unwrap();

    let rc_path = Path::new(&manifest_dir).join("resources.rc");
    let res_path = format!("{}/resources.res", out_dir);

    let rc_exe = find_rc_exe().expect("Could not find rc.exe");
    println!("cargo:warning=Using rc.exe at: {}", rc_exe);

    let status = Command::new(&rc_exe)
        .args(["/fo", &res_path, rc_path.to_str().unwrap()])
        .current_dir(&manifest_dir)
        .status()
        .expect("Failed to run rc.exe");

    if !status.success() {
        panic!("rc.exe failed with status: {}", status);
    }

    println!("cargo:rustc-link-arg={}", res_path);
    println!("cargo:rerun-if-changed=resources.rc");
    println!("cargo:rerun-if-changed=logo.ico");
}

fn find_rc_exe() -> Option<String> {
    let roots = [
        "C:\\Program Files (x86)\\Windows Kits\\10\\bin",
        "C:\\Program Files\\Windows Kits\\10\\bin",
    ];
    for root in &roots {
        if let Ok(entries) = std::fs::read_dir(root) {
            for entry in entries.flatten() {
                let candidate = entry.path().join("x64").join("rc.exe");
                if candidate.exists() {
                    return Some(candidate.to_string_lossy().into_owned());
                }
            }
        }
    }
    None
}