//! File helpers that add the path to each error.

use std::fs;
use std::io;
use std::path::Path;

use anyhow::Context;
use serde::Serialize;

/// The text of a file.
pub(crate) fn read_text(path: &Path) -> anyhow::Result<String> {
    fs::read_to_string(path).with_context(|| format!("cannot read {}", path.display()))
}

/// The text of a file, or `None` when the file does not exist.
pub(crate) fn read_optional(path: &Path) -> anyhow::Result<Option<String>> {
    match fs::read(path) {
        Ok(bytes) => Ok(Some(String::from_utf8_lossy(&bytes).into_owned())),
        Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(None),
        Err(error) => Err(error).with_context(|| format!("cannot read {}", path.display())),
    }
}

/// Writes a file and creates its parent directory.
pub(crate) fn write_text(path: &Path, text: &str) -> anyhow::Result<()> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).with_context(|| format!("cannot create {}", parent.display()))?;
    }
    fs::write(path, text).with_context(|| format!("cannot write {}", path.display()))
}

/// Writes a value as pretty JSON with a final newline.
pub(crate) fn write_json(path: &Path, value: &impl Serialize) -> anyhow::Result<()> {
    let mut text = serde_json::to_string_pretty(value).context("cannot encode JSON")?;
    text.push('\n');
    write_text(path, &text)
}

/// Copies a directory tree. A symbolic link is copied as a link. `fs::copy` clones a file on APFS, so a copy of a
/// primed sandbox is fast.
pub(crate) fn copy_dir(from: &Path, to: &Path) -> anyhow::Result<()> {
    fs::create_dir_all(to).with_context(|| format!("cannot create {}", to.display()))?;
    for entry in fs::read_dir(from).with_context(|| format!("cannot list {}", from.display()))? {
        let entry = entry.with_context(|| format!("cannot list {}", from.display()))?;
        let source = entry.path();
        let target = to.join(entry.file_name());
        let kind = entry.file_type().with_context(|| format!("cannot stat {}", source.display()))?;
        if kind.is_dir() {
            copy_dir(&source, &target)?;
        } else if kind.is_symlink() {
            copy_link(&source, &target)?;
        } else {
            fs::copy(&source, &target).with_context(|| format!("cannot copy {} to {}", source.display(), target.display()))?;
        }
    }
    Ok(())
}

#[cfg(unix)]
fn copy_link(source: &Path, target: &Path) -> anyhow::Result<()> {
    let link = fs::read_link(source).with_context(|| format!("cannot read the link {}", source.display()))?;
    std::os::unix::fs::symlink(&link, target).with_context(|| format!("cannot create the link {}", target.display()))
}

#[cfg(not(unix))]
fn copy_link(source: &Path, target: &Path) -> anyhow::Result<()> {
    fs::copy(source, target)
        .map(|_| ())
        .with_context(|| format!("cannot copy {} to {}", source.display(), target.display()))
}

#[cfg(test)]
mod tests;
