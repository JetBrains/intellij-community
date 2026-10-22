//! The recorded sessions of a run: the logs that [`VARIABLE`] names on the machine of the runner, and their copies in
//! the context of the lane IDE.
//!
//! Each entry of the variable is an `events.jsonl`, or a thread directory that holds one. The label of a log is the name
//! of the thread directory, or the file name without its extension, URL-decoded, with each character outside
//! `[A-Za-z0-9._-]` as `_`, as [`label_of`] states.
//!
//! The lane IDE of a guest cannot read a file of the host. So a runner packs each log as one zip of stored entries:
//! the events file as [`EVENTS_FILE`], and the files of the sibling directories [`SIBLING_DIRECTORIES`] when they exist.
//! The controller pushes the zip by digest, and the run request names it as a [`crate::daemon::SessionLog`]. The lane
//! daemon unpacks it into `<context>/`[`CONTEXT_DIRECTORY`]`/<label>/`, and answers the runner value [`RUNNER_VALUE`].

use std::collections::BTreeSet;
use std::fs;
use std::io::{self, Write};
use std::path::{Path, PathBuf};

use zip::write::SimpleFileOptions;
use zip::{CompressionMethod, DateTime, System, ZipArchive, ZipWriter};

#[cfg(test)]
mod tests;

/// The variable that names the recorded sessions, separated by [`SEPARATOR`].
pub const VARIABLE: &str = "AIR_UI_REAL_SESSION_LOGS";

/// The runner value of the recorded sessions: a JSON array of `{label, eventsPath}`, the `AirSessionLogFile` of the
/// bridge. A runner with no log answers no value.
pub const RUNNER_VALUE: &str = "realSessionLogs";

/// The directory of the copies below the context of the lane IDE.
pub const CONTEXT_DIRECTORY: &str = "air-real-session-logs";

/// The events file of a log, in a thread directory and in its zip.
pub const EVENTS_FILE: &str = "events.jsonl";

/// The directories beside the events file that a log takes with it when they exist.
pub const SIBLING_DIRECTORIES: [&str; 2] = ["attachments", "outputs"];

/// The separator of the entries of [`VARIABLE`], the path separator of the JVM: `;` on Windows, `:` elsewhere.
pub const SEPARATOR: char = if cfg!(windows) { ';' } else { ':' };

/// One log that [`VARIABLE`] names: its label and its events file.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct NamedLog {
    pub label: String,
    pub events: PathBuf,
}

/// The logs that the value of [`VARIABLE`] names, in its order. An entry that names no events file is refused, and so
/// are two logs of one label, because their copies would share a directory.
pub fn named_logs(value: &str) -> Result<Vec<NamedLog>, String> {
    let mut logs = Vec::new();
    let mut labels = BTreeSet::new();
    for entry in value.split(SEPARATOR).map(str::trim).filter(|entry| !entry.is_empty()) {
        let path = Path::new(entry);
        let events = if path.is_dir() {
            path.join(EVENTS_FILE)
        } else {
            path.to_path_buf()
        };
        if !events.is_file() {
            return Err(format!("{VARIABLE} names {entry}, and {} is not a file", events.display()));
        }
        let label = label_of(&events);
        if !labels.insert(label.clone()) {
            return Err(format!("{VARIABLE} names two logs of the label '{label}'"));
        }
        logs.push(NamedLog { label, events });
    }
    Ok(logs)
}

/// The label of the log of `events`: the name of its thread directory, or its file name without the extension.
pub fn label_of(events: &Path) -> String {
    let file_name = events
        .file_name()
        .map(|name| name.to_string_lossy().into_owned())
        .unwrap_or_default();
    let name = if file_name == EVENTS_FILE {
        events
            .parent()
            .and_then(Path::file_name)
            .map(|name| name.to_string_lossy().into_owned())
            .unwrap_or_default()
    } else {
        file_name.rsplit_once('.').map_or(file_name.clone(), |(stem, _)| stem.to_owned())
    };
    url_decoded(&name)
        .chars()
        .map(|character| {
            if character.is_ascii_alphanumeric() || matches!(character, '.' | '_' | '-') {
                character
            } else {
                '_'
            }
        })
        .collect()
}

/// `text` as `java.net.URLDecoder` reads it: `+` is a space and `%XY` is a byte. An escape that is no hex pair stays.
fn url_decoded(text: &str) -> String {
    let bytes = text.as_bytes();
    let mut decoded = Vec::with_capacity(bytes.len());
    let mut index = 0;
    while index < bytes.len() {
        let byte = bytes[index];
        let pair = bytes.get(index + 1..index + 3).and_then(|pair| std::str::from_utf8(pair).ok());
        match (byte, pair.and_then(|pair| u8::from_str_radix(pair, 16).ok())) {
            (b'%', Some(value)) => {
                decoded.push(value);
                index += 3;
            }
            (b'+', _) => {
                decoded.push(b' ');
                index += 1;
            }
            _ => {
                decoded.push(byte);
                index += 1;
            }
        }
    }
    String::from_utf8_lossy(&decoded).into_owned()
}

/// Writes the zip of `log` to `destination`: [`EVENTS_FILE`], then each file of the [`SIBLING_DIRECTORIES`] in name
/// order. Every entry is stored, with one time and one mode, so one log always packs to the same bytes.
pub fn pack(log: &NamedLog, destination: &Path) -> Result<(), String> {
    let failed = |error: &dyn std::fmt::Display| format!("cannot pack the session log {}: {error}", log.events.display());
    let file = fs::File::create(destination).map_err(|error| failed(&error))?;
    let mut writer = ZipWriter::new(io::BufWriter::new(file));
    let mut entries = vec![(EVENTS_FILE.to_owned(), log.events.clone())];
    let parent = log.events.parent().unwrap_or_else(|| Path::new("."));
    for sibling in SIBLING_DIRECTORIES {
        let directory = parent.join(sibling);
        if directory.is_dir() {
            files_below(&directory, sibling, &mut entries).map_err(|error| failed(&error))?;
        }
    }
    for (name, path) in entries {
        let content = fs::read(&path).map_err(|error| failed(&format!("{}: {error}", path.display())))?;
        let options = SimpleFileOptions::default()
            .compression_method(CompressionMethod::Stored)
            .last_modified_time(DateTime::default())
            .unix_permissions(0o600)
            .system(System::Unix)
            .large_file(u64::try_from(content.len()).unwrap_or(u64::MAX) >= u64::from(u32::MAX));
        writer.start_file(name, options).map_err(|error| failed(&error))?;
        writer.write_all(&content).map_err(|error| failed(&error))?;
    }
    writer
        .finish()
        .and_then(|mut out| out.flush().map_err(zip::result::ZipError::Io))
        .map_err(|error| failed(&error))
}

/// Adds each file below `directory` to `entries` as `<prefix>/<relative path>`, in name order.
fn files_below(directory: &Path, prefix: &str, entries: &mut Vec<(String, PathBuf)>) -> io::Result<()> {
    let mut children: Vec<_> = fs::read_dir(directory)?.collect::<Result<_, _>>()?;
    children.sort_by_key(fs::DirEntry::file_name);
    for child in children {
        let name = format!("{prefix}/{}", child.file_name().to_string_lossy());
        let path = child.path();
        if fs::metadata(&path)?.is_dir() {
            files_below(&path, &name, entries)?;
        } else {
            entries.push((name, path));
        }
    }
    Ok(())
}

/// Unpacks the zip `archive` of one log into `directory`, and answers the path of its events file there. An entry
/// whose name leaves the directory is refused, and so is a zip with no [`EVENTS_FILE`].
pub fn unpack(archive: &Path, directory: &Path) -> Result<PathBuf, String> {
    let shown = archive.display();
    let file = fs::File::open(archive).map_err(|error| format!("cannot open the session log {shown}: {error}"))?;
    let mut zip = ZipArchive::new(file).map_err(|error| format!("the session log {shown} is no zip: {error}"))?;
    for index in 0..zip.len() {
        let mut entry = zip
            .by_index(index)
            .map_err(|error| format!("cannot read entry {index} of the session log {shown}: {error}"))?;
        let Some(relative) = entry.enclosed_name() else {
            return Err(format!(
                "the session log {shown} has the entry {}, which leaves its directory",
                entry.name()
            ));
        };
        let target = directory.join(relative);
        if entry.is_dir() {
            fs::create_dir_all(&target).map_err(|error| format!("cannot create {}: {error}", target.display()))?;
            continue;
        }
        if let Some(parent) = target.parent() {
            fs::create_dir_all(parent).map_err(|error| format!("cannot create {}: {error}", parent.display()))?;
        }
        let mut out = fs::File::create(&target).map_err(|error| format!("cannot create {}: {error}", target.display()))?;
        io::copy(&mut entry, &mut out).map_err(|error| format!("cannot write {}: {error}", target.display()))?;
    }
    let events = directory.join(EVENTS_FILE);
    if !events.is_file() {
        return Err(format!("the session log {shown} holds no {EVENTS_FILE}"));
    }
    Ok(events)
}
