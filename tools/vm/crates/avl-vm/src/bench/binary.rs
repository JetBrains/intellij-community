//! The age of the controller binary that `AIR_VM_BIN` names, against the sources of the checkout. A session that
//! starts the IDE prints the age as a note, and a source that is newer than the binary is a warning of the session.

use std::path::{Path, PathBuf};
use std::time::SystemTime;

use avl_base::Environment;
use jiff::tz::TimeZone;

use super::digest::minute;

/// The variable that names a controller binary, which `vm.cmd` runs without a build.
pub(crate) const BINARY_VARIABLE: &str = "AIR_VM_BIN";

/// The workspace of the controller, below the checkout root. Its [`SOURCES_DIR`] holds every source of the binary.
pub(crate) use avl_base::config::WORKSPACE_DIR;

/// The directory of the sources, below [`WORKSPACE_DIR`].
pub(crate) const SOURCES_DIR: &str = "crates";

/// The binary that `AIR_VM_BIN` names, and the newest source of the checkout.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct BinaryAge {
    /// The file that `AIR_VM_BIN` names, as an absolute path.
    pub(crate) binary: PathBuf,
    /// The mtime of the binary.
    pub(crate) built: SystemTime,
    /// The newest file below [`SOURCES_DIR`]. A checkout without the directory has none.
    pub(crate) newest_source: Option<Source>,
}

/// One source file: its path relative to [`WORKSPACE_DIR`], and its mtime.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Source {
    pub(crate) path: PathBuf,
    pub(crate) modified: SystemTime,
}

/// The age of the binary that `AIR_VM_BIN` names, against the sources of the checkout `repo`. `None` when the
/// variable is unset or empty, or when the binary has no mtime. A relative path is relative to the working directory.
pub(crate) fn age(environment: &Environment, repo: &Path) -> Option<BinaryAge> {
    let value = environment.get(BINARY_VARIABLE)?;
    let binary = std::path::absolute(value).unwrap_or_else(|_| PathBuf::from(value));
    let built = std::fs::metadata(&binary).and_then(|metadata| metadata.modified()).ok()?;
    let workspace = repo.join(WORKSPACE_DIR);
    let newest_source = newest(&workspace.join(SOURCES_DIR)).map(|(path, modified)| Source {
        path: path.strip_prefix(&workspace).map_or_else(|_| path.clone(), Path::to_path_buf),
        modified,
    });
    Some(BinaryAge {
        binary,
        built,
        newest_source,
    })
}

impl BinaryAge {
    /// The note of the session, in the local time zone, such as `AIR_VM_BIN /x/vm built 2026-10-01 18:02, newest
    /// source 2026-10-01 19:40 crates/avl-vm/src/bench/launch.rs`.
    pub(crate) fn note(&self) -> String {
        self.note_in(&TimeZone::system())
    }

    /// The note of [`BinaryAge::note`] in `zone`.
    pub(crate) fn note_in(&self, zone: &TimeZone) -> String {
        let source = self.newest_source.as_ref().map_or_else(
            || format!("no source under {WORKSPACE_DIR}/{SOURCES_DIR}"),
            |source| format!("newest source {} {}", minute(source.modified, zone), source.path.display()),
        );
        format!(
            "{BINARY_VARIABLE} {} built {}, {source}",
            self.binary.display(),
            minute(self.built, zone)
        )
    }

    /// The warning of the session when a source is newer than the binary.
    pub(crate) fn warning(&self) -> Option<String> {
        self.newest_source
            .as_ref()
            .filter(|source| source.modified > self.built)
            .map(|_| format!("the controller binary is older than its sources; rebuild it or unset {BINARY_VARIABLE}"))
    }
}

/// The test sources, which the walk of [`newest`] leaves out: a directory with one of these names.
const TEST_DIRS: [&str; 2] = ["testdata", "tests"];

/// The test module of a source file, which the walk of [`newest`] leaves out too.
const TEST_FILE: &str = "tests.rs";

/// The newest file below `dir` by its mtime, and its path. Of two files with one mtime, the first path wins. The walk
/// leaves out the test sources, see [`TEST_DIRS`] and [`TEST_FILE`], and a directory or a file that cannot be read.
fn newest(dir: &Path) -> Option<(PathBuf, SystemTime)> {
    let mut newest: Option<(PathBuf, SystemTime)> = None;
    let mut pending = vec![dir.to_path_buf()];
    while let Some(dir) = pending.pop() {
        let Ok(entries) = std::fs::read_dir(&dir) else {
            continue;
        };
        for entry in entries.filter_map(Result::ok) {
            let path = entry.path();
            let name = entry.file_name();
            if entry.file_type().is_ok_and(|file_type| file_type.is_dir()) {
                if !TEST_DIRS.iter().any(|test| name == *test) {
                    pending.push(path);
                }
                continue;
            }
            if name == TEST_FILE {
                continue;
            }
            let Ok(modified) = entry.metadata().and_then(|metadata| metadata.modified()) else {
                continue;
            };
            let newer = newest
                .as_ref()
                .is_none_or(|(best, time)| modified > *time || (modified == *time && path < *best));
            if newer {
                newest = Some((path, modified));
            }
        }
    }
    newest
}

#[cfg(test)]
mod tests;
