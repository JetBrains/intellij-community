//! A Bazel runfiles MANIFEST, and the request and reply of the guest verb `runfiles-tree` that builds a tree from one.
//!
//! On Windows, Bazel writes the MANIFEST of a test but no runfiles tree. A Linux guest reads the runfiles through the
//! shares, so there the guest builds the tree itself: one symbolic link per MANIFEST line, to the guest path of its
//! host target ([`crate::path_map::PathMap`]).
//!
//! # The MANIFEST format
//!
//! One line per runfile: the logical path, one space, and the target, an absolute host path. An empty target is an
//! empty file. A line that starts with a space is escaped, because its path or its target holds a space, a newline or
//! a backslash. In the logical path of such a line `\s` is a space, `\n` a newline and `\b` a backslash. In its
//! target `\n` is a newline and `\b` a backslash, and a space stays as it is. The runfiles libraries of Bazel read
//! the same format.

use std::fmt;

use serde::{Deserialize, Serialize};
use sha2::{Digest as _, Sha256};

use crate::path_map::PathMap;

#[cfg(test)]
mod tests;

/// One line of a MANIFEST.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ManifestEntry {
    /// The path under the runfiles root, e.g. `_main/pkg/data.txt`.
    pub path: String,
    /// The host file the runfile is, or an empty text for an empty file.
    pub target: String,
}

/// A MANIFEST line that is not in the format.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ManifestError {
    /// The line, counted from 1.
    pub line: usize,
    pub reason: &'static str,
}

impl fmt::Display for ManifestError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "runfiles MANIFEST line {}: {}", self.line, self.reason)
    }
}

impl std::error::Error for ManifestError {}

/// A whole MANIFEST, in the order of its lines.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct RunfilesManifest {
    pub entries: Vec<ManifestEntry>,
}

impl RunfilesManifest {
    /// Reads a MANIFEST. An empty line is skipped. A `\r` at the end of a line is dropped, so a file that a Windows
    /// tool wrote with CRLF line ends does not give every target a stray `\r`.
    pub fn parse(text: &str) -> Result<Self, ManifestError> {
        let mut entries = Vec::new();
        for (index, line) in text.split('\n').enumerate() {
            let line = line.strip_suffix('\r').unwrap_or(line);
            if line.is_empty() {
                continue;
            }
            let error = |reason| ManifestError { line: index + 1, reason };
            let entry = if let Some(escaped) = line.strip_prefix(' ') {
                let (path, target) = escaped.split_once(' ').unwrap_or((escaped, ""));
                ManifestEntry {
                    path: unescape(path, true).ok_or_else(|| error("an unknown escape in the path"))?,
                    target: unescape(target, false).ok_or_else(|| error("an unknown escape in the target"))?,
                }
            } else {
                let (path, target) = line.split_once(' ').unwrap_or((line, ""));
                ManifestEntry {
                    path: path.to_owned(),
                    target: target.to_owned(),
                }
            };
            if entry.path.is_empty() {
                return Err(error("an empty runfile path"));
            }
            entries.push(entry);
        }
        Ok(Self { entries })
    }

    /// The target of the runfile at `path`, or `None` when no line names it. An empty target is an empty file.
    pub fn resolve(&self, path: &str) -> Option<&str> {
        self.entries
            .iter()
            .find(|entry| entry.path == path)
            .map(|entry| entry.target.as_str())
    }
}

/// Decodes one escaped half of a line in one pass, so `\bs` is a backslash and an `s`. A path knows `\s`, and a
/// target does not. Any other escape is `None`.
fn unescape(text: &str, path: bool) -> Option<String> {
    let mut decoded = String::with_capacity(text.len());
    let mut characters = text.chars();
    while let Some(character) = characters.next() {
        if character != '\\' {
            decoded.push(character);
            continue;
        }
        match characters.next()? {
            's' if path => decoded.push(' '),
            'n' => decoded.push('\n'),
            'b' => decoded.push('\\'),
            _ => return None,
        }
    }
    Some(decoded)
}

// --- the guest verb ------------------------------------------------------------------------------------------

/// The version of [`RunfilesTreeRequest`] and of the tree it builds.
pub const SCHEMA_VERSION: u32 = 1;

/// What `runfiles-tree` reads on stdin.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RunfilesTreeRequest {
    pub schema_version: u32,
    /// The MANIFEST, as a guest path: the host's file seen through a share.
    pub manifest: String,
    /// Turns each absolute host target into its guest path.
    pub path_map: PathMap,
    /// An absolute guest directory under the worker data directory. The tree is `<destination>/<digest>`.
    pub destination: String,
}

/// The name of the tree that `manifest` and `path_map` give: the sha256 of both, as lowercase hex.
///
/// The host and the guest compute the same name, so the controller knows the guest root `<destination>/<digest>`
/// before it asks the guest anything. The table is part of the name, because the same MANIFEST under another table
/// links to other targets. The length of the MANIFEST comes before its bytes, so no MANIFEST can end with the text of
/// a table. The table is hashed as its JSON, so the host must hash the value it sends, with its prefixes in the same
/// order.
pub fn tree_digest(manifest: &[u8], path_map: &PathMap) -> String {
    let table = serde_json::to_vec(path_map).expect("a table of strings always encodes as JSON");
    let mut hasher = Sha256::new();
    hasher.update(format!("runfiles-tree/{SCHEMA_VERSION}\n").as_bytes());
    hasher.update(u64::try_from(manifest.len()).unwrap_or(u64::MAX).to_le_bytes());
    hasher.update(manifest);
    hasher.update(&table);
    hex::encode(hasher.finalize())
}

/// What `runfiles-tree` answers in its envelope.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RunfilesTreeResult {
    /// The runfiles root: `<destination>/<digest>`.
    pub root: String,
    /// The sha256 of the MANIFEST bytes and the path table, as lowercase hex.
    pub digest: String,
    /// How many runfiles the tree holds.
    pub entries: u32,
    /// Whether a tree of this digest was already there, so nothing was built.
    pub reused: bool,
    /// The entries of the destination that the verb removed, as absolute guest paths, in sorted order. The verb
    /// keeps the tree it answers and the newest other tree, and removes everything else. A removal that failed is
    /// not listed.
    #[serde(default)]
    pub removed: Vec<String>,
}
