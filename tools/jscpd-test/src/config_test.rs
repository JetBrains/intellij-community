// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use super::{Config, Listing, glob, normalize};
use std::fs;
use std::path::{Path, PathBuf};

#[test]
fn a_glob_matches_the_paths_that_jscpd_ignores() {
    let cases = [
        ("**/node_modules/**", "a/node_modules/", true),
        ("**/node_modules/**", "node_modules/x.ts", true),
        ("**/node_modules/**", "a/node_modulesx/", false),
        ("**/*Icons.java", "a/b/AirIcons.java", true),
        ("**/*Icons.java", "a/b/Icons.kt", false),
        ("junie/server/api/src/**/rpc/**", "junie/server/api/src/x/rpc/A.kt", true),
        ("junie/server/api/src/**/rpc/**", "other/junie/server/api/src/rpc/A.kt", false),
        ("docs/?.md", "docs/a.md", true),
    ];
    for (pattern, path, matches) in cases {
        assert_eq!(glob(pattern).expect("a glob").is_match(path), matches, "{pattern} against {path}");
    }
}

#[test]
fn normalize_applies_each_parent_step() {
    assert_eq!(
        normalize(Path::new("/r/_main/build/rust-tools/./../../tools/bt")),
        PathBuf::from("/r/_main/tools/bt")
    );
}

#[test]
fn the_absolute_copy_keeps_every_other_key() {
    let dir = tempfile::tempdir().expect("a directory");
    let file = dir.path().join("cfg/.jscpd.json");
    fs::create_dir_all(file.parent().expect("a parent")).expect("a directory");
    fs::write(&file, r#"{"path": ["../a", "."], "format": ["rust"], "minTokens": 50}"#).expect("a file");
    let config = Config::read(&file).expect("a configuration");
    let copy: serde_json::Value = serde_json::from_str(&config.with_absolute_paths(&file).expect("a copy")).expect("JSON");
    let root = dir.path().display().to_string();
    assert_eq!(copy["path"], serde_json::json!([format!("{root}/a"), format!("{root}/cfg")]));
    assert_eq!(copy["minTokens"], 50);
}

#[test]
fn the_files_have_a_scanned_extension_and_no_skipped_part() {
    let dir = tempfile::tempdir().expect("a directory");
    let root = dir.path();
    for file in ["src/a.rs", "src/b.kt", "target/c.rs", ".hidden/d.rs", "gen/e.rs", "src/gen/f.rs"] {
        let path = root.join(file);
        fs::create_dir_all(path.parent().expect("a parent")).expect("a directory");
        fs::write(path, "fn x() {}").expect("a file");
    }
    let file = root.join(".jscpd.json");
    fs::write(&file, r#"{"path": ["."], "format": ["rust"], "ignore": ["**/gen/**"]}"#).expect("a file");
    let config = Config::read(&file).expect("a configuration");
    let files = config.files(&file, Listing::Runfiles).expect("the files");
    assert_eq!(files, [root.join("src/a.rs")]);
}

#[test]
fn an_unknown_format_fails() {
    let dir = tempfile::tempdir().expect("a directory");
    let file = dir.path().join(".jscpd.json");
    fs::write(&file, r#"{"path": ["."], "format": ["cobol"]}"#).expect("a file");
    let config = Config::read(&file).expect("a configuration");
    let error = config.files(&file, Listing::Runfiles).expect_err("an unknown format");
    assert_eq!(error.to_string(), "the runner knows no extensions of the format cobol");
}

#[test]
fn the_git_listing_leaves_out_what_gitignore_names() {
    let dir = tempfile::tempdir().expect("a directory");
    let root = dir.path();
    let git = |args: &[&str]| {
        let status = std::process::Command::new("git")
            .arg("-C")
            .arg(root)
            .args(args)
            .status()
            .expect("git");
        assert!(status.success(), "git {args:?}");
    };
    git(&["init", "-q"]);
    for (file, text) in [
        ("src/a.rs", "fn x() {}"),
        ("generated/b.rs", "fn x() {}"),
        (".gitignore", "generated/\n"),
    ] {
        let path = root.join(file);
        fs::create_dir_all(path.parent().expect("a parent")).expect("a directory");
        fs::write(path, text).expect("a file");
    }
    let file = root.join(".jscpd.json");
    fs::write(&file, r#"{"path": ["."], "format": ["rust"]}"#).expect("a file");
    let config = Config::read(&file).expect("a configuration");
    assert_eq!(config.files(&file, Listing::Git).expect("the files"), [root.join("src/a.rs")]);
}
