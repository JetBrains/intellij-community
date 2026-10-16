//! A Bazel runfiles MANIFEST, and the request and reply of the guest verb `runfiles-tree` that builds a tree from one.
//!
//! The guest builds its own runfiles tree on every host. It reads the Bazel outputs through the Bazel share, so the
//! tree holds one symbolic link per MANIFEST line, to the guest path of its host target
//! ([`crate::path_map::PathMap`]). No share holds the checkout. So a runfile whose host file lies in the checkout is
//! a [`StagedRunfile`]: the host sends its bytes after the request, and the tree holds a copy. On a Windows host a
//! package directory of a `node_modules` store is a copy too, so Node's real-path resolution stays inside the tree.
//! The host sends the MANIFEST as text. On Windows it resolves every target through its junctions first, because
//! Bazel's external repositories there are junctions into the repository cache, and the guest's mount cannot read a
//! junction.
//!
//! # The MANIFEST format
//!
//! One line per runfile: the logical path, one space, and the target: an absolute host path, or the link's own text
//! for a symlink runfile, relative to the directory of its runfile. An empty target is an empty file. A line that
//! starts with a space is escaped, because its path or its target holds a space, a newline or
//! a backslash. In the logical path of such a line `\s` is a space, `\n` a newline and `\b` a backslash. In its
//! target `\n` is a newline and `\b` a backslash, and a space stays as it is. The runfiles libraries of Bazel read
//! the same format.

use std::fmt;

use serde::{Deserialize, Serialize};
use sha2::{Digest as _, Sha256};

use crate::path_map::{PathMap, is_absolute_host_path};

#[cfg(test)]
mod tests;

/// One line of a MANIFEST.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ManifestEntry {
    /// The path under the runfiles root, e.g. `_main/pkg/data.txt`.
    pub path: String,
    /// The host file the runfile is, the link text of a symlink runfile, or an empty text for an empty file.
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

    /// The host target of the runfile at `path`, through the symlink runfiles on the way. A target that is not an
    /// absolute host path is the text of a symlink, relative to the directory of its runfile, and the runfile it
    /// names has the next line. `None` when no line names the runfile, when a chain leaves the runfiles root, or
    /// when it is longer than [`SYMLINK_CHAIN_LIMIT`]. An empty target is an empty file.
    pub fn host_target(&self, path: &str) -> Option<&str> {
        let mut path = path.to_owned();
        for _ in 0..SYMLINK_CHAIN_LIMIT {
            let target = self.resolve(&path)?;
            if target.is_empty() || is_absolute_host_path(target) {
                return Some(target);
            }
            path = join_logical(&path, target)?;
        }
        None
    }

    /// The MANIFEST text of these entries, in the format [`RunfilesManifest::parse`] reads: a line whose path or
    /// target holds a space, a newline or a backslash is escaped.
    pub fn render(&self) -> String {
        let mut text = String::new();
        for entry in &self.entries {
            let escaped = [&entry.path, &entry.target].iter().any(|half| half.contains([' ', '\n', '\\']));
            if escaped {
                text.push(' ');
                text.push_str(&escape(&entry.path, true));
                text.push(' ');
                text.push_str(&escape(&entry.target, false));
            } else {
                text.push_str(&entry.path);
                text.push(' ');
                text.push_str(&entry.target);
            }
            text.push('\n');
        }
        text
    }
}

/// How many symlink runfiles one lookup follows: the limit Linux puts on a chain of symbolic links.
const SYMLINK_CHAIN_LIMIT: usize = 40;

/// The runfile that the symlink runfile at `path` names with the relative `target`: the target applied to the
/// directory of `path`, or `None` when it climbs above the runfiles root.
fn join_logical(path: &str, target: &str) -> Option<String> {
    let mut components: Vec<&str> = path.split('/').collect();
    components.pop();
    for component in target.split('/') {
        match component {
            "" | "." => {}
            ".." => {
                components.pop()?;
            }
            name => components.push(name),
        }
    }
    Some(components.join("/"))
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

/// Encodes one half of an escaped line: a newline is `\n`, a backslash `\b`, and in a path a space is `\s`.
fn escape(text: &str, path: bool) -> String {
    let mut encoded = String::with_capacity(text.len());
    for character in text.chars() {
        match character {
            ' ' if path => encoded.push_str("\\s"),
            '\n' => encoded.push_str("\\n"),
            '\\' => encoded.push_str("\\b"),
            other => encoded.push(other),
        }
    }
    encoded
}

// --- the guest verb ------------------------------------------------------------------------------------------

/// The version of [`RunfilesTreeRequest`] and of the tree it builds. It is part of the tree's digest, and the guest
/// reuses a tree by its digest, so a change in how the tree is built is a new version, or the guest keeps the tree
/// the old builder made.
pub const SCHEMA_VERSION: u32 = 4;

/// The refusal of a request that needs a new tree and carries no bytes of its staged runfiles. The host then sends
/// the request again with the bytes ([`RunfilesTreeRequest::with_bytes`]).
pub const STAGED_BYTES_MISSING_CODE: &str = "runfiles_staged_bytes_missing";

/// What `runfiles-tree` reads on stdin: one JSON line, then the bytes of [`Self::staged`] when [`Self::with_bytes`]
/// is set ([`request_stdin`]).
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RunfilesTreeRequest {
    pub schema_version: u32,
    /// The MANIFEST text, as the host read and resolved it, without the lines of [`Self::staged`].
    pub manifest_text: String,
    /// Turns each absolute host target into its guest path.
    pub path_map: PathMap,
    /// An absolute guest directory under the worker data directory. The tree is `<destination>/<digest>`.
    pub destination: String,
    /// The runfiles whose host files lie in the checkout, in the order of their bytes on stdin. The tree holds a copy
    /// of each.
    #[serde(default)]
    pub staged: Vec<StagedRunfile>,
    /// Whether the bytes of [`Self::staged`] follow the JSON line. Without them the verb reuses a tree of the digest,
    /// and refuses [`STAGED_BYTES_MISSING_CODE`] when it has none.
    #[serde(default)]
    pub with_bytes: bool,
    /// Whether a package directory of a `node_modules` store is a copy in the tree instead of a link. A Windows host
    /// sets it, because the dependency links beside a package there are junctions, which the guest's mount cannot read.
    #[serde(default)]
    pub copy_package_stores: bool,
}

/// One runfile that the tree holds as a copy: its path under the runfiles root, and the digest and the length of its
/// bytes.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct StagedRunfile {
    pub path: String,
    pub sha256: String,
    pub size: u64,
}

/// The stdin of `runfiles-tree`: the request as one compact JSON line, then each chunk of `bytes` in order. Compact
/// JSON holds no raw newline, so the first newline ends the request.
pub fn request_stdin(request: &RunfilesTreeRequest, bytes: &[Vec<u8>]) -> Vec<u8> {
    let mut stdin = serde_json::to_vec(request).expect("a request of strings, numbers and flags always encodes as JSON");
    stdin.push(b'\n');
    for chunk in bytes {
        stdin.extend_from_slice(chunk);
    }
    stdin
}

/// The JSON line of a `runfiles-tree` stdin, and the bytes after it.
pub fn split_request_stdin(raw: &[u8]) -> (&[u8], &[u8]) {
    match raw.iter().position(|byte| *byte == b'\n') {
        Some(end) => (&raw[..end], &raw[end + 1..]),
        None => (raw, &[]),
    }
}

/// The name of the tree that `request` gives: the sha256 of the MANIFEST, the path table, the staged runfiles and the
/// copy rule, as lowercase hex. [`RunfilesTreeRequest::with_bytes`] is not part of it.
///
/// The host and the guest compute the same name, so the controller knows the guest root `<destination>/<digest>`
/// before it asks the guest anything. The table is part of the name, because the same MANIFEST under another table
/// links to other targets. A length comes before the MANIFEST and before the table, so no part can end with the text
/// of the next. The table and the staged runfiles are hashed as their JSON, so the host must hash the values it
/// sends, in the same order.
pub fn tree_digest(request: &RunfilesTreeRequest) -> String {
    let manifest = request.manifest_text.as_bytes();
    let table = serde_json::to_vec(&request.path_map).expect("a table of strings always encodes as JSON");
    let staged = serde_json::to_vec(&request.staged).expect("a list of strings and numbers always encodes as JSON");
    let mut hasher = Sha256::new();
    hasher.update(format!("runfiles-tree/{SCHEMA_VERSION}\n").as_bytes());
    hasher.update(u64::try_from(manifest.len()).unwrap_or(u64::MAX).to_le_bytes());
    hasher.update(manifest);
    hasher.update(u64::try_from(table.len()).unwrap_or(u64::MAX).to_le_bytes());
    hasher.update(&table);
    hasher.update(&staged);
    hasher.update([u8::from(request.copy_package_stores)]);
    hex::encode(hasher.finalize())
}

/// What `runfiles-tree` answers in its envelope.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RunfilesTreeResult {
    /// The runfiles root: `<destination>/<digest>`.
    pub root: String,
    /// The name of the tree ([`tree_digest`]).
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
