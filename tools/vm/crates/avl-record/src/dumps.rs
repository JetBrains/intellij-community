//! The thread dumps that the IDE wrote into its log directory while a failed scenario ran.

use std::ffi::OsStr;
use std::fs;
use std::path::{Path, PathBuf};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use avl_trace::bundle::{attach_path, bundle_file};
use avl_trace::otlp::{KeyValue, attr, event};

use crate::bundle::copy_into_place;
use crate::scenario::Scenario;

#[cfg(test)]
mod tests;

/// The part of the file name of a thread dump. A wait of the IDE that times out writes `<title>-thread-dump-at_<time>.log`.
const THREAD_DUMP_MARKER: &str = "thread-dump";

/// Bounds the dumps of one bundle. A failed wait writes one dump, so the first few name the holder.
const MAX_THREAD_DUMPS: usize = 4;

/// The source of an `air.trace.error` about a dump that the recorder could not list or copy.
const ERROR_SOURCE_THREAD_DUMP: &str = "thread-dump";

const THREAD_DUMP_EXTENSION: &str = "log";
const THREAD_DUMP_MIME: &str = "text/plain";

impl Scenario {
    /// Copies each thread dump that the IDE wrote into one of `directories` since the scenario opened into the bundle, as
    /// an attachment of the root span. The IDE keeps its file. A dump that the recorder cannot copy is an
    /// `air.trace.error`, and the bundle has no record of it.
    pub(crate) fn attach_thread_dumps(&mut self, directories: impl IntoIterator<Item = PathBuf>, at_ms: i64) {
        let since = UNIX_EPOCH + Duration::from_millis(u64::try_from(self.started_ms).unwrap_or(0));
        let mut dumps = Vec::new();
        for directory in directories {
            match fs::read_dir(&directory) {
                Ok(entries) => dumps.extend(
                    entries
                        .flatten()
                        .map(|entry| entry.path())
                        .filter(|path| is_thread_dump_since(path, since)),
                ),
                Err(error) => self.trace_error(
                    ERROR_SOURCE_THREAD_DUMP,
                    &format!("cannot list {}: {error}", directory.display()),
                    at_ms,
                ),
            }
        }
        dumps.sort();
        dumps.dedup();
        for dump in dumps.into_iter().take(MAX_THREAD_DUMPS) {
            self.attachments += 1;
            let name = attach_path(self.attachments, THREAD_DUMP_EXTENSION);
            match copy_dump(&dump, &bundle_file(&self.dir, &name)) {
                Ok(()) => self.emit(
                    event::ATTACHMENT,
                    0,
                    at_ms,
                    vec![
                        KeyValue::string(attr::ATTACHMENT_NAME, file_name(&dump)),
                        KeyValue::string(attr::ATTACHMENT_MIME, THREAD_DUMP_MIME),
                        KeyValue::string(attr::ATTACHMENT_FILE, &name),
                    ],
                ),
                Err(error) => self.trace_error(
                    ERROR_SOURCE_THREAD_DUMP,
                    &format!("{name}: cannot copy {}: {error}", dump.display()),
                    at_ms,
                ),
            }
        }
    }
}

/// Whether `path` is a file with [THREAD_DUMP_MARKER] in its name that changed at `since` or later.
fn is_thread_dump_since(path: &Path, since: SystemTime) -> bool {
    file_name(path).contains(THREAD_DUMP_MARKER)
        && fs::metadata(path).is_ok_and(|metadata| metadata.is_file() && metadata.modified().is_ok_and(|modified| modified >= since))
}

fn file_name(path: &Path) -> String {
    path.file_name().and_then(OsStr::to_str).unwrap_or_default().to_owned()
}

fn copy_dump(from: &Path, to: &Path) -> std::io::Result<()> {
    if let Some(parent) = to.parent() {
        fs::create_dir_all(parent)?;
    }
    copy_into_place(from, to)
}
