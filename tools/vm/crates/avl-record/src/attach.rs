//! One attachment: a file the lane wrote under the trace root, moved into the bundle.

use std::ffi::OsStr;
use std::fs;
use std::io;
use std::path::{Component, Path};
use std::time::SystemTime;

use avl_trace::bundle::{attach_path, bundle_file};
use avl_trace::otlp::{KeyValue, attr, event};
use avl_trace::protocol::AttachCommand;

use crate::bundle::copy_into_place;
use crate::scenario::Scenario;
use crate::unix_ms;

/// The source of an `air.trace.error` about an attachment the recorder could not keep.
const ERROR_SOURCE_ATTACH: &str = "attach";

/// The extension of an attachment whose lane file has none the bundle can keep.
const UNKNOWN_EXTENSION: &str = "bin";

/// Bounds the extension the bundle keeps, so a lane file name cannot make the bundle path long.
const MAX_EXTENSION_CHARS: usize = 16;

impl Scenario {
    /// Moves the lane's file into the bundle as [attach_path] and records an [event::ATTACHMENT] on its span.
    ///
    /// A span this scenario never opened, or a file outside `root`, is a lane mistake, and the answer says why. A file
    /// the recorder cannot move is evidence loss: an `air.trace.error` on the span says so, and the bundle has no
    /// record of the attachment.
    pub(crate) fn attach(&mut self, command: &AttachCommand, root: &Path, at: SystemTime) -> Result<(), String> {
        if !self.opened.contains(&command.span) {
            return Err(format!(
                "the attachment {:?} names the span {}, which this scenario never opened",
                command.name, command.span
            ));
        }
        let file = Path::new(&command.file);
        let inside = file.is_absolute() && file.starts_with(root) && !file.components().any(|component| component == Component::ParentDir);
        if !inside {
            return Err(format!(
                "the attachment {:?} names {}, which is not under the trace root {}",
                command.name,
                command.file,
                root.display()
            ));
        }
        self.attachments += 1;
        let name = attach_path(self.attachments, &extension_of(file));
        let at_ms = unix_ms(at);
        match move_file(file, &bundle_file(&self.dir, &name)) {
            Err(error) => {
                let message = format!("{name}: cannot move {} into the bundle: {error}", command.file);
                self.writer.trace_error_on(command.span, ERROR_SOURCE_ATTACH, &message, at_ms);
                return Ok(());
            }
            Ok(Some(error)) => {
                let message = format!("{name}: the copy is in the bundle, and {} stays: {error}", command.file);
                self.writer.trace_error_on(command.span, ERROR_SOURCE_ATTACH, &message, at_ms);
            }
            Ok(None) => {}
        }
        self.emit(
            event::ATTACHMENT,
            command.span,
            at_ms,
            vec![
                KeyValue::string(attr::ATTACHMENT_NAME, &command.name),
                KeyValue::string(attr::ATTACHMENT_MIME, &command.mime),
                KeyValue::string(attr::ATTACHMENT_FILE, &name),
            ],
        );
        Ok(())
    }
}

/// The lane file's extension in lowercase, or [UNKNOWN_EXTENSION] when it has none of ASCII letters and digits.
fn extension_of(file: &Path) -> String {
    file.extension()
        .and_then(OsStr::to_str)
        .filter(|extension| {
            !extension.is_empty() && extension.len() <= MAX_EXTENSION_CHARS && extension.chars().all(|char| char.is_ascii_alphanumeric())
        })
        .map_or_else(|| UNKNOWN_EXTENSION.to_owned(), str::to_ascii_lowercase)
}

/// Moves a regular file to `to`, and answers the error of a lane file that stays behind.
///
/// A rename is the move on one file system. Across two, the rename fails, so [copy_into_place] copies the file, and
/// the lane file is removed. The copy is then the attachment even when the removal fails, which is the error answered.
fn move_file(from: &Path, to: &Path) -> io::Result<Option<io::Error>> {
    if !fs::symlink_metadata(from)?.is_file() {
        return Err(io::Error::other("it is not a regular file"));
    }
    if let Some(parent) = to.parent() {
        fs::create_dir_all(parent)?;
    }
    if fs::rename(from, to).is_ok() {
        return Ok(None);
    }
    copy_into_place(from, to)?;
    Ok(fs::remove_file(from).err())
}

#[cfg(test)]
mod tests;
