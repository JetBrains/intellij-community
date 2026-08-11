// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::process::Command;

fn main() {
    println!("cargo:rerun-if-env-changed=PKG_CONFIG_PATH");
    emit_pkg_config("webkit2gtk-4.1");
    emit_pkg_config("gtk+-x11-3.0");
    emit_pkg_config("x11");
}

fn emit_pkg_config(package: &str) {
    let output = Command::new("pkg-config")
        .args(["--libs", package])
        .output()
        .unwrap_or_else(|error| panic!("failed to execute pkg-config for {package}: {error}"));

    if !output.status.success() {
        let stderr = String::from_utf8_lossy(&output.stderr);
        panic!("pkg-config --libs {package} failed: {stderr}");
    }

    let stdout = String::from_utf8_lossy(&output.stdout);
    for token in stdout.split_whitespace() {
        if let Some(path) = token.strip_prefix("-L") {
            println!("cargo:rustc-link-search=native={path}");
        } else if let Some(library) = token.strip_prefix("-l") {
            println!("cargo:rustc-link-lib={library}");
        } else if token == "-pthread" || token.starts_with("-Wl,") {
            println!("cargo:rustc-link-arg={token}");
        }
    }
}
