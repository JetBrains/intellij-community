//! Builds libaom for a cargo build: the release that `avl.MODULE.bazel` pins, from the Space file mirror, with the
//! options of `community/tools/vm/aom/update.mjs`. Bazel builds the same release with `aom/aom.BUILD.bazel` and runs no
//! build script.
//!
//! The download is checked against its SHA-256 before it is used, and it is kept in `OUT_DIR`, so a rebuild reads no
//! network. The cmake of libaom needs nasm on an x86_64 host.

use std::env;
use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;

use sha2::{Digest, Sha256};

/// The release, its digest and its file on the mirror. `the_cargo_build_takes_the_libaom_of_bazel` in `src/tests.rs`
/// holds them equal to `avl_aom` of `avl.MODULE.bazel`.
const VERSION: &str = "3.15.2";
const SHA256: &str = "67bb54b245f33ed98600e08269e6139986e48114e14042474ddd8885801dfddc";
const URL: &str = "https://cache-redirector.jetbrains.com/files/p/ij/intellij-build-dependencies/libaom/3.15.2/libaom-3.15.2.tar.gz";

/// The options of the recorder's libaom, as in `update.mjs`: an 8-bit AV1 encoder without a decoder.
const OPTIONS: &[(&str, &str)] = &[
    ("CONFIG_AV1_DECODER", "0"),
    ("CONFIG_AV1_HIGHBITDEPTH", "0"),
    ("CONFIG_PIC", "1"),
    ("CONFIG_SVT_AV1", "0"),
    ("CONFIG_LIBYUV", "0"),
    ("CONFIG_WEBM_IO", "0"),
    ("ENABLE_DOCS", "0"),
    ("ENABLE_EXAMPLES", "0"),
    ("ENABLE_TESTDATA", "0"),
    ("ENABLE_TESTS", "0"),
    ("ENABLE_TOOLS", "0"),
    ("ENABLE_SVE", "0"),
    ("ENABLE_SVE2", "0"),
];

fn main() {
    println!("cargo::rerun-if-changed=build.rs");
    // The recorder links libaom on Unix only. The Windows clippy of the workspace still checks this crate.
    if env::var_os("CARGO_CFG_UNIX").is_none() {
        return;
    }
    let out = PathBuf::from(env::var_os("OUT_DIR").expect("cargo sets OUT_DIR"));
    let archive = out.join(format!("libaom-{VERSION}.tar.gz"));
    if !archive.is_file() || digest(&archive) != SHA256 {
        download(&archive);
    }
    let source = out.join(format!("libaom-{VERSION}"));
    if !source.join("CMakeLists.txt").is_file() {
        // The recorder builds no Highway, and its copy holds a Bazel `BUILD` file, which would make `OUT_DIR` a package
        // of `//tools/vm/...`.
        run(Command::new("tar")
            .arg("xzf")
            .arg(&archive)
            .arg("-C")
            .arg(&out)
            .arg(format!("--exclude=libaom-{VERSION}/third_party/highway")));
    }
    let mut config = cmake::Config::new(&source);
    // A debug libaom encodes a test picture ten times slower, so even a debug build of the recorder links a fast one.
    config.profile("Release").define("CMAKE_INSTALL_LIBDIR", "lib");
    for (name, value) in OPTIONS {
        config.define(name, value);
    }
    let installed = config.build();
    println!("cargo::rustc-link-search=native={}", installed.join("lib").display());
    println!("cargo::rustc-link-lib=static=aom");
}

/// Downloads the release into `archive` through a partial file, and fails the build on a wrong digest.
fn download(archive: &Path) {
    let partial = archive.with_extension("partial");
    run(Command::new("curl")
        .args(["--fail", "--silent", "--show-error", "--location", "--retry", "3", "--output"])
        .arg(&partial)
        .arg(URL));
    let found = digest(&partial);
    assert!(found == SHA256, "{URL} has the SHA-256 {found}, not {SHA256}");
    fs::rename(&partial, archive).unwrap_or_else(|error| panic!("cannot move {} into place: {error}", partial.display()));
}

fn digest(file: &Path) -> String {
    let content = fs::read(file).unwrap_or_else(|error| panic!("cannot read {}: {error}", file.display()));
    Sha256::digest(content).iter().map(|byte| format!("{byte:02x}")).collect()
}

fn run(command: &mut Command) {
    let status = command.status().unwrap_or_else(|error| panic!("cannot run {command:?}: {error}"));
    assert!(status.success(), "{command:?} failed: {status}");
}
