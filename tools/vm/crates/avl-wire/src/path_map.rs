//! The table that turns a host path into the guest path of the same file.
//!
//! A Linux guest sees the host's files through shares: each share mounts one host directory at one guest directory.
//! A host path under a share's host directory is the same file under its guest directory. [`PathMap`] is that
//! table, one [`PathPrefix`] per share, and [`PathMap::map`] is the one reading of it that the host and the guest
//! share.
//!
//! A Windows host spells a path as `C:\a\b` or `C:/a/b`, and a Windows path has no case. The table accepts both
//! spellings on both sides of a comparison, and it compares a Windows prefix with ASCII case folding. Bazel on
//! Windows writes its output root in lower case, `C:/programdata/_bazel/...`, while the controller knows it as
//! `C:/ProgramData/_bazel`. The table always answers a guest path joined with `/`. A Unix path has case, and a Unix
//! host maps each share to the same path in the guest, so there the table is the identity for every prefix it
//! holds.

use serde::{Deserialize, Serialize};

#[cfg(test)]
mod tests;

/// One share: a host directory and the guest directory that holds the same files.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct PathPrefix {
    /// An absolute host path: `/Users/a/repo` on Unix, `C:\a\repo` or `C:/a/repo` on Windows.
    pub host: String,
    /// An absolute guest path, joined with `/`.
    pub guest: String,
}

impl PathPrefix {
    pub fn new(host: impl Into<String>, guest: impl Into<String>) -> Self {
        Self {
            host: host.into(),
            guest: guest.into(),
        }
    }

    /// The prefix of a share that the guest mounts at the host's own path, as every Unix host does.
    pub fn identity(path: impl Into<String>) -> Self {
        let path = path.into();
        Self {
            host: path.clone(),
            guest: path,
        }
    }

    /// Whether both halves are absolute. A prefix that is not matches no path.
    pub fn is_valid(&self) -> bool {
        is_absolute_host_path(&self.host) && self.guest.starts_with('/')
    }
}

/// The host-to-guest table of one pool, as JSON: `{"prefixes":[{"host":"C:\\a\\repo","guest":"/mnt/repo"}]}`.
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct PathMap {
    pub prefixes: Vec<PathPrefix>,
}

impl PathMap {
    pub const fn new(prefixes: Vec<PathPrefix>) -> Self {
        Self { prefixes }
    }

    /// The first prefix that is not absolute on both sides. A reader that acts on the table refuses it, because
    /// such a prefix matches no path and would make every path under it read as outside the table.
    pub fn invalid_prefix(&self) -> Option<&PathPrefix> {
        self.prefixes.iter().find(|prefix| !prefix.is_valid())
    }

    /// The guest path of the host path `host`, or `None` when no prefix holds it.
    ///
    /// The longest prefix wins, and a prefix matches only on a whole path component: `C:\a\repo` holds
    /// `C:\a\repo\x` and not `C:\a\repository`. A path that climbs out of its prefix with a `..` component is
    /// outside the table, because the guest would resolve it outside the share.
    ///
    /// A Windows prefix matches without ASCII case, and a Unix prefix matches with case. The part below the prefix
    /// keeps the case of `host`.
    ///
    /// Of two prefixes of the same length, the first one in the table wins.
    pub fn map(&self, host: &str) -> Option<String> {
        let path = HostPath::parse(host)?;
        let mut best: Option<(usize, &PathPrefix, Vec<&str>)> = None;
        for prefix in self.prefixes.iter().filter(|prefix| prefix.is_valid()) {
            let Some(parsed) = HostPath::parse(&prefix.host) else {
                continue;
            };
            let Some(rest) = path.strip(&parsed) else {
                continue;
            };
            if best.as_ref().is_none_or(|(length, _, _)| parsed.len() > *length) {
                best = Some((parsed.len(), prefix, rest));
            }
        }
        best.map(|(_, prefix, rest)| join_guest(&prefix.guest, &rest))
    }
}

/// Whether `path` is an absolute host path: `/…` on Unix, or a drive letter, a colon and a separator on Windows.
pub fn is_absolute_host_path(path: &str) -> bool {
    HostPath::parse(path).is_some()
}

/// An absolute host path, split into its components. The drive of a Windows path is lower case, and an empty or
/// `.` component is dropped, so two spellings of one path compare equal. Only a Windows path splits at `\`: on
/// Unix a backslash is a character of a file name.
#[derive(Debug, PartialEq, Eq)]
struct HostPath<'a> {
    /// `None` for a Unix path, the drive letter in lower case for a Windows path.
    drive: Option<char>,
    components: Vec<&'a str>,
}

impl<'a> HostPath<'a> {
    fn parse(path: &'a str) -> Option<Self> {
        let (drive, rest) = match path.as_bytes() {
            [b'/', ..] => (None, path),
            [letter, b':', b'/' | b'\\', ..] if letter.is_ascii_alphabetic() => (Some(char::from(letter.to_ascii_lowercase())), &path[2..]),
            _ => return None,
        };
        let separators: &[char] = if drive.is_some() { &['/', '\\'] } else { &['/'] };
        let components = rest
            .split(separators)
            .filter(|component| !component.is_empty() && *component != ".")
            .collect();
        Some(Self { drive, components })
    }

    const fn len(&self) -> usize {
        self.components.len()
    }

    /// The components below `prefix`, or `None` when `prefix` does not hold this path or the rest climbs out. A
    /// Windows component compares without ASCII case, and a Unix component compares as written.
    fn strip(&self, prefix: &Self) -> Option<Vec<&'a str>> {
        if self.drive != prefix.drive || self.components.len() < prefix.components.len() {
            return None;
        }
        let (head, rest) = self.components.split_at(prefix.components.len());
        let windows = self.drive.is_some();
        let same = head.iter().zip(&prefix.components).all(|(ours, theirs)| {
            if windows {
                ours.eq_ignore_ascii_case(theirs)
            } else {
                ours == theirs
            }
        });
        if !same {
            return None;
        }
        if rest.contains(&"..") {
            return None;
        }
        Some(rest.to_vec())
    }
}

fn join_guest(guest: &str, rest: &[&str]) -> String {
    let base = guest.trim_end_matches('/');
    if rest.is_empty() {
        return if base.is_empty() { "/".to_owned() } else { base.to_owned() };
    }
    format!("{base}/{}", rest.join("/"))
}
