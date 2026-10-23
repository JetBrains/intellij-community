// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use super::{MARKER_PREFIX, check};

/// A text whose `{start}` and `{end}` stand for the two markers. The markers are built here, so that jscpd does not
/// read this file as a marked region.
fn text(lines: &[&str]) -> String {
    lines
        .iter()
        .map(|line| {
            line.replace("{start}", &format!("{MARKER_PREFIX}start"))
                .replace("{end}", &format!("{MARKER_PREFIX}end"))
        })
        .collect::<Vec<_>>()
        .join("\n")
}

#[test]
fn a_marked_region_with_a_reason_is_valid() {
    let text = text(&[
        "fn a() {}",
        "  // {start} the wire DTO mirrors the model",
        "fn b() {}",
        "  // {end}",
    ]);
    assert_eq!(check(&text), Vec::<String>::new());
}

#[test]
fn each_fault_names_its_line() {
    let cases: [(&[&str], &[&str]); 5] = [
        (&["// {start} two words", "// {end}"], &["1: the start marker states no reason"]),
        (&["// {start} a good reason here"], &["1: the start marker has no end marker"]),
        (&["x", "// {end}"], &["2: the end marker has no start marker"]),
        (
            &["/* {start} a good reason here */"],
            &["1: write the clone marker as a line comment of its own"],
        ),
        (
            &["// {start} a good reason here", "// {start} another good reason", "// {end}"],
            &["1: the start marker has no end marker before line 2"],
        ),
    ];
    for (lines, expected) in cases {
        assert_eq!(check(&text(lines)), expected, "for the lines {lines:?}");
    }
}

#[test]
fn a_text_without_markers_has_no_violation() {
    assert_eq!(check("fn main() {}\n// jscpd is a tool\n"), Vec::<String>::new());
}
