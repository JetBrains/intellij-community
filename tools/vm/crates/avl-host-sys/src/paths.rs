//! The guest path of a host file: the one mapping every host path goes through before it crosses into a guest.
//!
//! A worker sees two host directories through its shares: the checkout and the Bazel output root. The guest keeps
//! each one at a guest root, and [`GuestPaths`] maps a host path under a share to the same file under its guest root.
//!
//! On a Unix host each guest root is the host path itself. The parity layout builds it in the guest, so a host path
//! and its guest path are the same text, and the absolute links of the host runfiles tree resolve in the guest.
//!
//! A Windows host path such as `C:\Users\air\idea` cannot be a guest path. Its guest root is `/c/Users/air/idea`:
//! the drive letter in lower case, then the rest of the path joined with `/`. The comparison of a Windows host path
//! has no case, because Bazel writes its output root in lower case while the controller knows its true case.

use std::path::Path;

use avl_base::{Config, Exit, Refusal};
use avl_wire::path_map::{PathMap, PathPrefix};

#[cfg(test)]
mod tests;

/// The guest roots of the two shares, and the table that maps a host path under one of them.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct GuestPaths {
    repo: String,
    bazel_user_root: String,
    map: PathMap,
}

impl GuestPaths {
    /// The mapping for the host paths that `ensure_host_paths` resolved, and refused before they are resolved.
    pub fn of(settings: &Config) -> Result<Self, Refusal> {
        Self::for_roots(&text(settings.host_repo()?), &text(settings.host_bazel_user_root()?))
    }

    /// The mapping for two host roots, given as text.
    pub fn for_roots(host_repo: &str, host_bazel_user_root: &str) -> Result<Self, Refusal> {
        let repo = guest_root(host_repo)?;
        let bazel_user_root = guest_root(host_bazel_user_root)?;
        let map = PathMap::new(vec![prefix(host_repo, &repo), prefix(host_bazel_user_root, &bazel_user_root)]);
        Ok(Self {
            repo,
            bazel_user_root,
            map,
        })
    }

    /// The guest root of the checkout.
    pub fn repo(&self) -> &str {
        &self.repo
    }

    /// The guest root of the Bazel output root.
    pub fn bazel_user_root(&self) -> &str {
        &self.bazel_user_root
    }

    /// The table itself, for a guest that maps the paths of a host document on its own.
    pub const fn map(&self) -> &PathMap {
        &self.map
    }

    /// The guest path of `host`, or `guest_path_unmapped` when no share holds it.
    pub fn to_guest(&self, host: &Path) -> Result<String, Refusal> {
        self.to_guest_text(&text(host))
    }

    /// The guest path of the host path `host`, given as text.
    pub fn to_guest_text(&self, host: &str) -> Result<String, Refusal> {
        self.map.map(host).ok_or_else(|| {
            let [repo, bazel] = [&self.map.prefixes[0].host, &self.map.prefixes[1].host];
            Refusal::new(
                "guest_path_unmapped",
                Exit::DATA_ERR,
                format!(
                    "{host} lies outside the checkout {repo} and the Bazel output root {bazel}, so no worker can \
                     see it"
                ),
            )
        })
    }
}

/// The guest root of a share whose host directory is `host`.
///
/// A Unix path is its own guest root. A Windows path `C:\a\b` or `C:/a/b` is `/c/a/b`. Anything else is not an
/// absolute host path, and it is refused.
pub fn guest_root(host: &str) -> Result<String, Refusal> {
    if host.starts_with('/') {
        return Ok(host.to_owned());
    }
    match host.as_bytes() {
        [letter, b':', b'/' | b'\\', ..] if letter.is_ascii_alphabetic() => {
            let drive = char::from(letter.to_ascii_lowercase());
            let rest: Vec<&str> = host[3..]
                .split(['/', '\\'])
                .filter(|component| !component.is_empty() && *component != ".")
                .collect();
            Ok(if rest.is_empty() {
                format!("/{drive}")
            } else {
                format!("/{drive}/{}", rest.join("/"))
            })
        }
        _ => Err(Refusal::new(
            "guest_path_unmapped",
            Exit::DATA_ERR,
            format!("{host} is not an absolute host path, so no guest root can hold it"),
        )),
    }
}

/// Whether `path` lies strictly below the host directory `root`.
///
/// The same comparison as [`GuestPaths`]: on a whole path component, and without case for a Windows path. A plain
/// [`Path::starts_with`] compares with case, and it refuses Bazel's lower-case spelling of its own output root.
pub fn lies_below(root: &Path, path: &Path) -> bool {
    let root = text(root);
    let Ok(guest) = guest_root(&root) else {
        return false;
    };
    PathMap::new(vec![prefix(&root, &guest)])
        .map(&text(path))
        .is_some_and(|mapped| mapped != guest)
}

/// The table row of one share: [`PathPrefix::identity`] for a Unix root, and the drive form for a Windows root.
fn prefix(host: &str, guest: &str) -> PathPrefix {
    if host == guest {
        PathPrefix::identity(host)
    } else {
        PathPrefix::new(host, guest)
    }
}

fn text(path: &Path) -> String {
    path.to_string_lossy().into_owned()
}
