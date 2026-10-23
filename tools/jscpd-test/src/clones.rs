// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The clones of the JSON report of jscpd.

use serde::Deserialize;
use std::fmt;
use std::path::Path;

#[derive(Deserialize)]
struct Report {
    duplicates: Vec<Duplicate>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct Duplicate {
    first_file: Side,
    second_file: Side,
}

#[derive(Deserialize)]
struct Side {
    name: String,
    start: u32,
    end: u32,
}

/// One clone: two places of one copy.
#[derive(Debug, PartialEq, Eq)]
pub(crate) struct Clone {
    pub(crate) first: String,
    pub(crate) second: String,
}

impl fmt::Display for Clone {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{} ~ {}", self.first, self.second)
    }
}

/// The clones of `json`, each place as `<path>:<start>-<end>`. A path under one of `roots` is relative to it.
pub(crate) fn read(json: &str, roots: &[impl AsRef<Path>]) -> serde_json::Result<Vec<Clone>> {
    let report: Report = serde_json::from_str(json)?;
    Ok(report
        .duplicates
        .into_iter()
        .map(|duplicate| Clone {
            first: place(&duplicate.first_file, roots),
            second: place(&duplicate.second_file, roots),
        })
        .collect())
}

fn place(side: &Side, roots: &[impl AsRef<Path>]) -> String {
    let path = Path::new(&side.name);
    let relative = roots.iter().find_map(|root| path.strip_prefix(root).ok()).unwrap_or(path);
    format!("{}:{}-{}", relative.display(), side.start, side.end)
}

#[cfg(test)]
#[path = "clones_test.rs"]
mod test;
