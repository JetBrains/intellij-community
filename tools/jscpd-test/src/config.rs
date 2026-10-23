// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The keys of a jscpd configuration that the runner reads: `path`, `format` and `ignore`.

use regex::Regex;
use serde::Deserialize;
use serde_json::Value;
use std::error::Error;
use std::fs;
use std::io;
use std::path::{Component, Path, PathBuf};
use std::process::{Command, Stdio};
use walkdir::WalkDir;

/// The file extensions of each jscpd format that a configuration of the repository names. A format that is not here
/// fails the marker case, so that a new format gets its extensions first.
const EXTENSIONS: &[(&str, &[&str])] = &[
    ("rust", &["rs"]),
    ("kotlin", &["kt", "kts"]),
    ("java", &["java"]),
    ("typescript", &["ts", "mts", "cts"]),
    ("tsx", &["tsx"]),
    ("javascript", &["js", "mjs", "cjs"]),
    ("jsx", &["jsx"]),
    ("go", &["go"]),
];

/// The directories that no walk of the marker case enters, as jscpd skips them through `.gitignore`.
const SKIPPED_DIRS: &[&str] = &["node_modules", "target"];

#[derive(Debug, Deserialize)]
pub(crate) struct Config {
    #[serde(default)]
    path: Vec<String>,
    #[serde(default)]
    format: Vec<String>,
    #[serde(default)]
    ignore: Vec<String>,
}

impl Config {
    pub(crate) fn read(file: &Path) -> Result<Self, Box<dyn Error>> {
        let config: Self =
            serde_json::from_str(&fs::read_to_string(file)?).map_err(|error| io::Error::other(format!("{}: {error}", file.display())))?;
        if config.path.is_empty() {
            return Err(io::Error::other(format!("{} names no `path`", file.display())).into());
        }
        Ok(config)
    }

    /// The configuration file `file` with each `path` entry absolute, and every other key as it is.
    pub(crate) fn with_absolute_paths(&self, file: &Path) -> Result<String, Box<dyn Error>> {
        let mut json: Value = serde_json::from_str(&fs::read_to_string(file)?)?;
        let base = file.parent().unwrap_or(Path::new("."));
        let paths = self
            .path
            .iter()
            .map(|entry| Value::from(normalize(&base.join(entry)).display().to_string()));
        json["path"] = Value::Array(paths.collect());
        Ok(serde_json::to_string_pretty(&json)?)
    }

    /// The files under the `path` entries that have an extension of a `format` and that no `ignore` pattern matches.
    /// A hidden directory and the directories of [`SKIPPED_DIRS`] are skipped.
    ///
    /// [`Listing::Runfiles`] walks the runfiles and follows their links. [`Listing::Git`] asks git for the files that
    /// `.gitignore` keeps, as jscpd reads a checkout. A walk of a checkout would also read the ignored outputs, such as
    /// the generated documents of Air, which take longer than the scan itself.
    pub(crate) fn files(&self, file: &Path, listing: Listing) -> Result<Vec<PathBuf>, Box<dyn Error>> {
        let filter = Filter {
            extensions: self.extensions()?,
            ignored: self.ignore.iter().map(|pattern| glob(pattern)).collect::<Result<Vec<_>, _>>()?,
        };
        let base = file.parent().unwrap_or(Path::new("."));
        let mut files = Vec::new();
        for entry in &self.path {
            let scan = normalize(&base.join(entry));
            match listing {
                Listing::Runfiles => walk(&scan, &filter, &mut files)?,
                Listing::Git => {
                    for relative in git_files(&scan)? {
                        if filter.keeps_file(&relative) {
                            files.push(scan.join(relative));
                        }
                    }
                }
            }
        }
        Ok(files)
    }

    fn extensions(&self) -> io::Result<Vec<&'static str>> {
        let mut extensions = Vec::new();
        for format in &self.format {
            let (_, known) = EXTENSIONS
                .iter()
                .find(|(name, _)| name == format)
                .ok_or_else(|| io::Error::other(format!("the runner knows no extensions of the format {format}")))?;
            extensions.extend_from_slice(known);
        }
        Ok(extensions)
    }
}

/// How [`Config::files`] lists the files of a `path` entry.
#[derive(Clone, Copy, Debug)]
pub(crate) enum Listing {
    /// Walk the directory and follow its links.
    Runfiles,
    /// Ask git for the tracked files and the untracked files that `.gitignore` keeps.
    Git,
}

/// What a scanned file must have: an extension of a `format`, and no part that an `ignore` pattern or a skipped
/// directory names. Each path is relative to its `path` entry, with `/` separators.
struct Filter {
    extensions: Vec<&'static str>,
    ignored: Vec<Regex>,
}

impl Filter {
    fn keeps_dir(&self, relative: &str) -> bool {
        let name = relative.rsplit('/').next().unwrap_or(relative);
        !name.starts_with('.') && !SKIPPED_DIRS.contains(&name) && !self.is_ignored(&format!("{relative}/"))
    }

    fn keeps_file(&self, relative: &str) -> bool {
        let has_extension = Path::new(relative)
            .extension()
            .is_some_and(|extension| self.extensions.contains(&extension.to_string_lossy().as_ref()));
        let mut parents = relative.match_indices('/').map(|(end, _)| &relative[..end]);
        has_extension && !self.is_ignored(relative) && parents.all(|parent| self.keeps_dir(parent))
    }

    fn is_ignored(&self, relative: &str) -> bool {
        self.ignored.iter().any(|pattern| pattern.is_match(relative))
    }
}

fn walk(scan: &Path, filter: &Filter, files: &mut Vec<PathBuf>) -> Result<(), Box<dyn Error>> {
    let relative = |path: &Path| path.strip_prefix(scan).unwrap_or(path).to_string_lossy().replace('\\', "/");
    let entries = WalkDir::new(scan)
        .follow_links(true)
        .into_iter()
        .filter_entry(|entry| entry.depth() == 0 || !entry.file_type().is_dir() || filter.keeps_dir(&relative(entry.path())));
    for entry in entries {
        let entry = entry?;
        if entry.file_type().is_file() && filter.keeps_file(&relative(entry.path())) {
            files.push(entry.into_path());
        }
    }
    Ok(())
}

/// The files of the checkout under `scan` that git tracks or that `.gitignore` keeps, relative to `scan`.
fn git_files(scan: &Path) -> Result<Vec<String>, Box<dyn Error>> {
    let output = Command::new("git")
        .arg("-C")
        .arg(scan)
        .args(["ls-files", "-z", "--cached", "--others", "--exclude-standard"])
        .stdin(Stdio::null())
        .output()?;
    if !output.status.success() {
        return Err(io::Error::other(format!(
            "git ls-files failed in {}: {}",
            scan.display(),
            String::from_utf8_lossy(&output.stderr)
        ))
        .into());
    }
    let text = String::from_utf8(output.stdout)?;
    Ok(text.split('\0').filter(|path| !path.is_empty()).map(str::to_owned).collect())
}

/// A regular expression for a glob of jscpd's `ignore`: `**/` is any number of directories, `**` any text, `*` any
/// text without `/`, and `?` one character other than `/`. The glob matches the whole path relative to the scanned
/// `path` entry, and a directory path ends with `/`.
pub(crate) fn glob(pattern: &str) -> Result<Regex, regex::Error> {
    let mut expression = String::from("^");
    let mut rest = pattern;
    while let Some(character) = rest.chars().next() {
        if let Some(after) = rest.strip_prefix("**/") {
            expression.push_str("(?:.*/)?");
            rest = after;
            continue;
        }
        if let Some(after) = rest.strip_prefix("**") {
            expression.push_str(".*");
            rest = after;
            continue;
        }
        match character {
            '*' => expression.push_str("[^/]*"),
            '?' => expression.push_str("[^/]"),
            _ => expression.push_str(&regex::escape(&character.to_string())),
        }
        rest = &rest[character.len_utf8()..];
    }
    expression.push('$');
    Regex::new(&expression)
}

/// `path` without `.` and with each `..` applied to the part before it, as text.
pub(crate) fn normalize(path: &Path) -> PathBuf {
    let mut normal = PathBuf::new();
    for component in path.components() {
        match component {
            Component::CurDir => {}
            Component::ParentDir => {
                normal.pop();
            }
            other => normal.push(other),
        }
    }
    normal
}

#[cfg(test)]
#[path = "config_test.rs"]
mod test;
