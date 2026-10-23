// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use super::{Clone, read};
use std::path::Path;

#[test]
fn a_clone_names_both_places_relative_to_a_root() {
    let json = r#"{"duplicates": [{
        "firstFile": {"name": "/run/_main/tools/a.rs", "start": 3, "end": 9, "startLoc": {}},
        "secondFile": {"name": "/elsewhere/b.rs", "start": 10, "end": 16},
        "fragment": "fn a() {}"
    }], "statistics": {}}"#;
    let clones = read(json, &[Path::new("/run/_main")]).expect("a report");
    assert_eq!(
        clones,
        [Clone {
            first: "tools/a.rs:3-9".to_owned(),
            second: "/elsewhere/b.rs:10-16".to_owned(),
        }]
    );
    assert_eq!(clones[0].to_string(), "tools/a.rs:3-9 ~ /elsewhere/b.rs:10-16");
}

#[test]
fn a_report_without_duplicates_has_no_clone() {
    assert_eq!(read(r#"{"duplicates": []}"#, &[Path::new("/")]).expect("a report"), []);
}
