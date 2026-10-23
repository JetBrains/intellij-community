// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The check of the clone markers.
//!
//! jscpd skips the code between a line comment with its start marker and a line comment with its end marker. So each
//! start marker states the reason why the copy stays, in at least [`MIN_REASON_WORDS`] words, and each start marker has
//! its end marker before the next start marker of the file. A marker in another form, such as a block comment, is not
//! valid, because the check cannot read its reason.

use regex::Regex;
use std::sync::LazyLock;

/// The literal marker text is split, so that jscpd does not read this file as a marked region.
const MARKER_PREFIX: &str = concat!("jscpd", ":ignore-");

/// A reason names the kept copy and why it stays, so one or two words are not a reason.
const MIN_REASON_WORDS: usize = 3;

static MARKER_LINE: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(&format!(r"^\s*//\s*{}(start|end)\b(.*)$", regex::escape(MARKER_PREFIX))).expect("a valid expression"));

/// The violations of the markers of one file, each as `<line>: <what is wrong>`.
pub(crate) fn check(text: &str) -> Vec<String> {
    let mut violations = Vec::new();
    let mut open_start = 0;
    for (index, line) in text.lines().enumerate() {
        if !line.contains(MARKER_PREFIX) {
            continue;
        }
        let number = index + 1;
        let Some(marker) = MARKER_LINE.captures(line) else {
            violations.push(format!("{number}: write the clone marker as a line comment of its own"));
            continue;
        };
        if &marker[1] == "start" {
            if open_start != 0 {
                violations.push(format!("{open_start}: the start marker has no end marker before line {number}"));
            }
            if marker[2].split_whitespace().count() < MIN_REASON_WORDS {
                violations.push(format!("{number}: the start marker states no reason"));
            }
            open_start = number;
        } else if open_start == 0 {
            violations.push(format!("{number}: the end marker has no start marker"));
        } else {
            open_start = 0;
        }
    }
    if open_start != 0 {
        violations.push(format!("{open_start}: the start marker has no end marker"));
    }
    violations
}

#[cfg(test)]
#[path = "markers_test.rs"]
mod test;
