//! One attachment: a file the lane wrote under the trace root, moved into the bundle.
//!
//! # A PNG
//!
//! The IDE attaches the paint of its windows after each step as a PNG. In a scenario with a video, the stills are
//! lossy, and a PNG becomes a lossy WebP picture through a writer of its own, which is about half the size. In a
//! scenario without a video, the stills are lossless, and the PNG stays a PNG: a lossless WebP of the encoders that
//! the recorder links is larger than the PNG of the IDE. A PNG with the same bytes as the previous PNG attachment gets
//! no file of its own, and its record names the file of the previous one.

use std::ffi::OsStr;
use std::fs;
use std::io;
use std::path::{Component, Path};
use std::time::{Duration, Instant, SystemTime};

use anyhow::Context;
use avl_trace::bundle::{ATTACH_DIR, attach_path, bundle_file};
use avl_trace::otlp::{KeyValue, attr, event};
use avl_trace::protocol::AttachCommand;

use crate::bundle::copy_into_place;
use crate::capture::Frame;
use crate::scenario::Scenario;
use crate::stills::{MIME_WEBP, WEBP_EXTENSION};
use crate::{lock, unix_ms};

/// The source of an `air.trace.error` about an attachment the recorder could not keep.
const ERROR_SOURCE_ATTACH: &str = "attach";

/// The extension of an attachment whose lane file has none the bundle can keep.
const UNKNOWN_EXTENSION: &str = "bin";

/// Bounds the extension the bundle keeps, so a lane file name cannot make the bundle path long.
const MAX_EXTENSION_CHARS: usize = 16;

/// The media type of a PNG attachment, which a scenario with a video keeps as a WebP picture.
const MIME_PNG: &str = "image/png";

impl Scenario {
    /// Moves the lane's file into the bundle as [attach_path] and records an [event::ATTACHMENT] on its span.
    ///
    /// In a scenario with a video, a PNG becomes a WebP picture instead, see [Scenario::attach_picture]. The record then
    /// names `image/webp` and the picture's file. A PNG with the same bytes as the previous PNG attachment that the
    /// bundle keeps as a PNG is not moved, and the record names the file of the previous one.
    ///
    /// A span this scenario never opened, or a file outside `root`, is a lane mistake, and the answer says why. A file
    /// the recorder cannot move is evidence loss: an `air.trace.error` on the span says so, and the bundle has no
    /// record of the attachment.
    pub(crate) fn attach(&mut self, command: &AttachCommand, root: &Path, at: SystemTime, budget: Duration) -> Result<(), String> {
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
        let png = is_png(&command.mime);
        if png && let Some(picture) = self.attach_picture(command, file, &name, Instant::now() + budget, at) {
            self.previous_png = None;
            self.record_attachment(command, MIME_WEBP, &picture, at_ms);
            return Ok(());
        }
        let document = if png { read_regular_file(file).ok() } else { None };
        if let (Some(document), Some((previous_document, previous))) = (&document, &self.previous_png)
            && document == previous_document
        {
            let previous = previous.clone();
            if let Err(error) = fs::remove_file(file) {
                let message = format!("{previous}: the bundle holds the same PNG, and {} stays: {error}", command.file);
                self.writer.trace_error_on(command.span, ERROR_SOURCE_ATTACH, &message, at_ms);
            }
            self.record_attachment(command, &command.mime, &previous, at_ms);
            return Ok(());
        }
        match move_file(file, &bundle_file(&self.dir, &name)) {
            Err(error) => {
                let message = format!("{name}: cannot move {} into the bundle: {error}", command.file);
                self.writer.trace_error_on(command.span, ERROR_SOURCE_ATTACH, &message, at_ms);
                if png {
                    self.previous_png = None;
                }
                return Ok(());
            }
            Ok(Some(error)) => {
                let message = format!("{name}: the copy is in the bundle, and {} stays: {error}", command.file);
                self.writer.trace_error_on(command.span, ERROR_SOURCE_ATTACH, &message, at_ms);
            }
            Ok(None) => {}
        }
        if png {
            self.previous_png = document.map(|document| (document, name.clone()));
        }
        self.record_attachment(command, &command.mime, &name, at_ms);
        Ok(())
    }

    /// Queues the PNG `file` for the attachment writer as the lossy WebP picture `attach/NNNN.webp`, and answers the
    /// file that holds the picture. The writer follows the rules of a snapshot's still: off the lane's path, the
    /// lossless fallback, and the previous picture's file for the same picture.
    ///
    /// `None` keeps the lane file as the PNG `kept`. A scenario without a video has no attachment writer. A file that
    /// cannot be read gets no other record here, because the move then fails and says why. A PNG that does not decode,
    /// or a picture that the writer cannot queue before `deadline`, gets an `air.trace.error` on the span.
    fn attach_picture(&self, command: &AttachCommand, file: &Path, kept: &str, deadline: Instant, at: SystemTime) -> Option<String> {
        let pictures = self.attachment_pictures.as_ref()?;
        let document = read_regular_file(file).ok()?;
        let name = attach_path(self.attachments, WEBP_EXTENSION);
        let mut frame = Frame::default();
        let directory = bundle_file(&self.dir, ATTACH_DIR);
        let queued = frame
            .set_png(&document, at)
            .context("the PNG does not decode")
            .and_then(|()| fs::create_dir_all(&directory).with_context(|| format!("cannot create {}", directory.display())))
            .and_then(|()| {
                lock(&self.still_spans).insert(name.clone(), command.span);
                pictures.submit(deadline, name.clone(), frame)
            });
        let at_ms = unix_ms(at);
        match queued {
            Ok(picture) => {
                if let Err(error) = fs::remove_file(file) {
                    let message = format!("{picture}: the picture is queued, and {} stays: {error}", command.file);
                    self.writer.trace_error_on(command.span, ERROR_SOURCE_ATTACH, &message, at_ms);
                }
                Some(picture)
            }
            Err(error) => {
                lock(&self.still_spans).remove(&name);
                let message = format!("{kept}: the attachment stays a PNG and is no WebP picture: {error:#}");
                self.writer.trace_error_on(command.span, ERROR_SOURCE_ATTACH, &message, at_ms);
                None
            }
        }
    }

    fn record_attachment(&self, command: &AttachCommand, mime: &str, file: &str, at_ms: i64) {
        self.emit(
            event::ATTACHMENT,
            command.span,
            at_ms,
            vec![
                KeyValue::string(attr::ATTACHMENT_NAME, &command.name),
                KeyValue::string(attr::ATTACHMENT_MIME, mime),
                KeyValue::string(attr::ATTACHMENT_FILE, file),
            ],
        );
    }
}

/// Whether the media type is `image/png`, without its parameters and in any case.
fn is_png(mime: &str) -> bool {
    mime.split(';')
        .next()
        .is_some_and(|essence| essence.trim().eq_ignore_ascii_case(MIME_PNG))
}

/// The bytes of a regular file. A symbolic link or a directory is refused, as [move_file] refuses it.
fn read_regular_file(file: &Path) -> io::Result<Vec<u8>> {
    if !fs::symlink_metadata(file)?.is_file() {
        return Err(io::Error::other("it is not a regular file"));
    }
    fs::read(file)
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
