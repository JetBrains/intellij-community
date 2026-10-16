//! A bundle is one scenario's trace: one directory, or the same tree inside a zip.
//!
//! The recorder writes it and nothing else changes it afterwards. What the viewer and the server may assume about
//! it is below: the file names, the manifest, and the frame index. What the files contain beyond the manifest is
//! OTLP ([crate::otlp]) and the bridge's tree ([crate::bridge]).
//!
//! The lane's system properties are read by Kotlin, and no Rust code sets them; for the record they are
//! `air.flow.trace.dir` (the root, set by the daemon for each iteration, which wins over the other two roots),
//! `air.flow.trace.run.id` (the run id the daemon sets beside it: the iteration id), `air.flow.trace` (`off`
//! disables tracing) and `air.trace.recorder` (the recorder binary, a runfiles path the lane resolves).

use std::borrow::Cow;
use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};

use crate::Error;
use crate::otlp::is_valid_span_id;
use crate::protocol::{CaptureSource, Label, Launcher, Status, VideoCodec};

// The files of a bundle, relative to its directory. The separator is always `/`, because the same paths name zip
// entries, URLs and log-record attributes.

/// Written last. A bundle without it is still being written, or its recorder was killed outright; a bundle whose
/// lane went away first has one, with [BundleStatus::Truncated].
pub const MANIFEST_FILE: &str = "bundle.json";
/// One OTLP `TracesData` per line, one line per ended span. A bundle is found by this file, whether it sits in a
/// directory, in a controller's iteration zip or in Bazel's `outputs.zip`.
pub const SPANS_FILE: &str = "spans.jsonl";
/// One OTLP `LogsData` per line, one line per log record.
pub const LOGS_FILE: &str = "logs.jsonl";
/// H.264 in fragmented MP4.
pub const VIDEO_FILE: &str = "video.mp4";
/// A [VideoIndex].
pub const VIDEO_INDEX_FILE: &str = "video.index.json";
/// The snapshots, named by [snap_image_path] and [snap_tree_path].
pub const SNAP_DIR: &str = "snap";
/// The files the lane attached, named by [attach_path].
pub const ATTACH_DIR: &str = "attach";
/// The slice of the IDE's `idea.log` written while the scenario ran.
pub const IDEA_LOG_FILE: &str = "idea.log";

/// Marks a temporary file of the recorder. A still, a tree, an index or a manifest is written under its name with
/// this suffix and renamed into place, so a file with it is only ever half a file, and a pack leaves it out.
pub const PARTIAL_SUFFIX: &str = ".partial";

/// The file of a bundle path, such as [snap_image_path], under the bundle's directory `dir`. It joins one segment
/// at a time, so the path is native on Windows too.
pub fn bundle_file(dir: &Path, name: &str) -> PathBuf {
    name.split('/').fold(dir.to_owned(), |path, segment| path.join(segment))
}

/// Where the snapshot with this ordinal keeps its lossless WebP picture. Ordinals count from 1 in the order the
/// snapshots were taken.
///
/// The file exists only when the screen changed since the previous picture. A snapshot of an unchanged screen
/// writes no file, and its [crate::otlp::attr::SNAPSHOT_IMAGE] names the previous picture's file. So a reader
/// takes a snapshot's picture from that attribute, never from its ordinal. The files follow the screen's changes in
/// order, and the last one by name is the last snapshot's picture.
pub fn snap_image_path(ordinal: u32) -> String {
    format!("{SNAP_DIR}/{ordinal:04}.webp")
}

/// Where the snapshot with this ordinal keeps its Swing tree, a [crate::bridge::Tree].
pub fn snap_tree_path(ordinal: u32) -> String {
    format!("{SNAP_DIR}/{ordinal:04}.tree.json")
}

/// Where the attachment with this ordinal is kept. Ordinals count from 1 in the order the lane attached the files.
/// The extension is the lane file's own, so a reader knows the type without the record.
pub fn attach_path(ordinal: u32, extension: &str) -> String {
    format!("{ATTACH_DIR}/{ordinal:04}.{extension}")
}

// --- where bundles live --------------------------------------------------------------------------------------

/// The name of every root's last segment: the daemon's `<iterationDir>/air-traces`, Bazel's
/// `$TEST_UNDECLARED_OUTPUTS_DIR/air-traces`, and the IDE's `<home>/out/air-traces`.
pub const ROOT_DIR_NAME: &str = "air-traces";

/// How many runs the recorder keeps under the IDE's root, newest first. The other two roots belong to a daemon
/// iteration and to a Bazel test, whose owners already clean them up.
pub const IDE_ROOT_RETAINED_RUNS: usize = 20;

/// The directory of one scenario's bundle under a root: `<root>/<runId>/<TestClass>/<scenario>`, each segment
/// passed through [sanitize_name].
///
/// It is the first attempt's directory. When the same scenario runs again in one run, a retry or a rerun of its
/// class, the recorder writes the next attempt to `<scenario>.2`, then `.3`, rather than overwrite the attempt most
/// worth reading. So a reader finds bundles by their [SPANS_FILE] and names them by their manifest, never by this
/// path. Every attempt has the same [crate::otlp::trace_id], which is harmless because a reader joins records to
/// spans inside one bundle.
pub fn bundle_dir(root: &Path, run_id: &str, test_class: &str, scenario: &str) -> PathBuf {
    root.join(&*sanitize_name(run_id))
        .join(&*sanitize_name(test_class))
        .join(&*sanitize_name(scenario))
}

/// [bundle_dir] relative to its root and always with `/`, the form a zip entry and a URL use.
pub fn bundle_path(run_id: &str, test_class: &str, scenario: &str) -> String {
    format!(
        "{}/{}/{}",
        sanitize_name(run_id),
        sanitize_name(test_class),
        sanitize_name(scenario)
    )
}

/// Bounds one path segment, well under every file system's 255, so that a long journey title cannot make a bundle
/// unwritable.
const MAX_NAME_BYTES: usize = 120;

/// Turns a run id, a test class or a scenario name into one path segment that is the same on every file system.
///
/// ASCII letters, digits, `.`, `_` and `-` are kept, and every other character becomes `_`. Leading dots are
/// dropped, so a name is never hidden and never `..`. The result is cut to 120 bytes, and a name with nothing left
/// in it becomes `_`. Iteration ids, test classes and profile scenario names pass unchanged; a name with spaces or
/// slashes does not, and the manifest keeps it as it was.
pub fn sanitize_name(name: &str) -> Cow<'_, str> {
    let portable = |char: char| char.is_ascii_alphanumeric() || matches!(char, '.' | '_' | '-');
    if !name.is_empty() && name.len() <= MAX_NAME_BYTES && !name.starts_with('.') && name.chars().all(portable) {
        return Cow::Borrowed(name);
    }
    let mapped: String = name.chars().map(|char| if portable(char) { char } else { '_' }).collect();
    // Every character is ASCII now, so any byte index is a character boundary.
    let mut sanitized = mapped.trim_start_matches('.').to_owned();
    sanitized.truncate(MAX_NAME_BYTES);
    if sanitized.is_empty() {
        sanitized.push('_');
    }
    Cow::Owned(sanitized)
}

// --- the manifest --------------------------------------------------------------------------------------------

/// The manifest's schema. A reader refuses any other value.
pub const MANIFEST_SCHEMA: &str = "air-trace/1";

vocabulary! {
    /// How a bundle ended: the lane's [Status], or [BundleStatus::Truncated].
    pub enum BundleStatus {
        Passed = "passed",
        Failed = "failed",
        Aborted = "aborted",
        /// The lane stopped talking before `done`: its standard input reached EOF. That is a watchdog's
        /// `exitProcess`, a killed JVM, or a lane that never sent `done`. The recorder still closes the video and
        /// writes the manifest, naming the span that was running. It also writes every span still open to
        /// [SPANS_FILE] as aborted, ending at the EOF, the root included, so the root's program reaches the viewer. A
        /// reader treats those spans as cut off, not as having ended.
        Truncated = "truncated",
    }
}

impl From<Status> for BundleStatus {
    fn from(status: Status) -> Self {
        match status {
            Status::Passed => Self::Passed,
            Status::Failed => Self::Failed,
            Status::Aborted => Self::Aborted,
        }
    }
}

/// Where the bundle's pixels came from.
#[derive(Serialize, Deserialize, Clone, PartialEq, Eq, Debug)]
#[serde(deny_unknown_fields)]
pub struct Capture {
    pub source: CaptureSource,
    /// Why a better source was not used; required when the source is none.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub reason: Option<String>,
}

/// The bundle's video, or why there is none.
///
/// On the wire it is `{"codec":"h264","file":"video.mp4","index":"video.index.json"}` or
/// `{"codec":"none","reason":"..."}`; any other combination is refused when it is read.
#[derive(Serialize, Deserialize, Clone, PartialEq, Eq, Debug)]
#[serde(try_from = "VideoFields", into = "VideoFields")]
pub enum Video {
    /// [VIDEO_FILE], indexed by [VIDEO_INDEX_FILE].
    H264,
    None {
        reason: String,
    },
}

#[derive(Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct VideoFields {
    codec: VideoCodec,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    reason: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    file: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    index: Option<String>,
}

impl TryFrom<VideoFields> for Video {
    type Error = Error;

    fn try_from(fields: VideoFields) -> Result<Self, Error> {
        match fields.codec {
            VideoCodec::H264 => {
                if fields.file.as_deref() != Some(VIDEO_FILE) || fields.index.as_deref() != Some(VIDEO_INDEX_FILE) {
                    refuse!(
                        "{MANIFEST_FILE} has a video at {:?} indexed by {:?}, not at {VIDEO_FILE:?} and {VIDEO_INDEX_FILE:?}",
                        fields.file.unwrap_or_default(),
                        fields.index.unwrap_or_default()
                    );
                }
                Ok(Self::H264)
            }
            VideoCodec::None => {
                let Some(reason) = fields.reason.filter(|reason| !reason.is_empty()) else {
                    refuse!("{MANIFEST_FILE} has no video and does not say why");
                };
                if fields.file.is_some() || fields.index.is_some() {
                    refuse!("{MANIFEST_FILE} has no video and still names a video file");
                }
                Ok(Self::None { reason })
            }
        }
    }
}

impl From<Video> for VideoFields {
    fn from(video: Video) -> Self {
        match video {
            Video::H264 => Self {
                codec: VideoCodec::H264,
                reason: None,
                file: Some(VIDEO_FILE.to_owned()),
                index: Some(VIDEO_INDEX_FILE.to_owned()),
            },
            Video::None { reason } => Self {
                codec: VideoCodec::None,
                reason: Some(reason),
                file: None,
                index: None,
            },
        }
    }
}

impl Video {
    pub const fn codec(&self) -> VideoCodec {
        match self {
            Self::H264 => VideoCodec::H264,
            Self::None { .. } => VideoCodec::None,
        }
    }
}

/// `bundle.json`: what the bundle is, how it ended, and what it could capture.
#[derive(Serialize, Deserialize, Clone, PartialEq, Eq, Debug)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct Manifest {
    pub schema: String,
    pub run_id: String,
    pub test_class: String,
    pub scenario: String,
    /// Absent for a hand-authored journey.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub flow: Option<String>,
    pub lane: String,
    pub launcher: Launcher,
    pub status: BundleStatus,
    /// The OTLP span id of the innermost span still open when the lane went away, present exactly when the status
    /// is [BundleStatus::Truncated]. Its `air.span.started` log record names it.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub running_span: Option<String>,
    /// When the scenario command arrived, in RFC 3339 UTC with milliseconds; see [format_manifest_time].
    pub started_at: String,
    pub duration_ms: u64,
    pub capture: Capture,
    pub video: Video,
}

/// Writes an instant the way the manifest holds it: RFC 3339 in UTC, with milliseconds.
pub fn format_manifest_time(instant: jiff::Timestamp) -> String {
    instant.strftime("%Y-%m-%dT%H:%M:%S%.3fZ").to_string()
}

/// Writes `bundle.json`: the manifest, validated, indented and ended with a newline. It is indented, unlike every
/// other document of the bundle, because it is the one a person opens by hand.
pub fn encode_manifest(manifest: &Manifest) -> Result<Vec<u8>, Error> {
    manifest.validate()?;
    let mut document =
        serde_json::to_vec_pretty(manifest).map_err(|error| Error::new(format!("{MANIFEST_FILE} cannot be encoded: {error}")))?;
    document.push(b'\n');
    Ok(document)
}

/// Reads `bundle.json` and refuses anything that is not an `air-trace/1` manifest.
pub fn decode_manifest(document: &[u8]) -> Result<Manifest, Error> {
    let manifest: Manifest =
        serde_json::from_slice(document).map_err(|error| Error::new(format!("{MANIFEST_FILE} does not fit the contract: {error}")))?;
    manifest.validate()?;
    Ok(manifest)
}

impl Manifest {
    /// Refuses a manifest this crate would not have written.
    pub fn validate(&self) -> Result<(), Error> {
        if self.schema != MANIFEST_SCHEMA {
            refuse!(
                "{MANIFEST_FILE} declares the schema {:?}, and this reader reads {MANIFEST_SCHEMA:?}",
                self.schema
            );
        }
        if self.run_id.is_empty() || self.test_class.is_empty() || self.scenario.is_empty() || self.lane.is_empty() {
            refuse!("{MANIFEST_FILE} needs a runId, a testClass, a scenario and a lane");
        }
        match (&self.status, &self.running_span) {
            (BundleStatus::Truncated, Some(span)) if !is_valid_span_id(span) => {
                refuse!("{MANIFEST_FILE} names the running span {span:?}, which is not a span id")
            }
            (BundleStatus::Truncated, Some(_)) => {}
            (BundleStatus::Truncated, None) | (_, Some(_)) => refuse!(
                "{MANIFEST_FILE} has the status {:?} and the running span {:?}; a running span is named exactly when the bundle is truncated",
                self.status.as_str(),
                self.running_span.as_deref().unwrap_or_default()
            ),
            (_, None) => {}
        }
        if self.started_at.parse::<jiff::Timestamp>().is_err() {
            refuse!("{MANIFEST_FILE} has the start {:?}, which is not RFC 3339", self.started_at);
        }
        if self.capture.source == CaptureSource::None && self.capture.reason.as_deref().unwrap_or_default().is_empty() {
            refuse!("{MANIFEST_FILE} captured nothing and does not say why");
        }
        Ok(())
    }
}

// --- the Allure results -------------------------------------------------------------------------------------

/// The directory of one run's Allure results, `<root>/<runId>/allure-results`, beside the run's bundles. It is one
/// flat directory, as Allure reads it: a `<uuid>-result.json` per bundle and the `<uuid>-attachment.<ext>` files that
/// the results name. The recorder writes a bundle's result before the bundle's [MANIFEST_FILE], so a finished bundle
/// has its result.
pub const ALLURE_RESULTS_DIR: &str = "allure-results";

/// The end of a result's file name. Allure reads every file with this end as one test result.
pub const ALLURE_RESULT_SUFFIX: &str = "-result.json";

/// The label that names the bundle of a result: the bundle's path under the run's directory, such as
/// `AirExampleUiTest/example.2`. A pack keeps a result with its bundle by this label.
pub const ALLURE_BUNDLE_LABEL: &str = "traceBundle";

/// The Allure results directory of the run `run_id` under a root, [ALLURE_RESULTS_DIR] beside the run's bundles.
pub fn allure_results_dir(root: &Path, run_id: &str) -> PathBuf {
    root.join(&*sanitize_name(run_id)).join(ALLURE_RESULTS_DIR)
}

/// The file name of the result with this uuid.
pub fn allure_result_file(uuid: &str) -> String {
    format!("{uuid}{ALLURE_RESULT_SUFFIX}")
}

/// The file name of the attachment with this uuid. The extension is the bundle file's own.
pub fn allure_attachment_file(uuid: &str, extension: &str) -> String {
    format!("{uuid}-attachment.{extension}")
}

/// A UUID derived from `parts`: the first 16 bytes of their SHA-256, with the version and variant bits of an RFC 9562
/// version 8 UUID.
///
/// It is derived rather than random for the reason [crate::otlp::trace_id] is: one replay of a transcript writes the
/// same results twice. The recorder derives a result's uuid from the run id, the bundle's path and its start, so two
/// runs never share one.
pub fn derived_uuid(parts: &[&str]) -> String {
    let mut hasher = Sha256::new();
    for part in parts {
        hasher.update(part);
        hasher.update([0]);
    }
    let digest = hasher.finalize();
    let mut bytes = [0; 16];
    bytes.copy_from_slice(&digest[..16]);
    bytes[6] = (bytes[6] & 0x0f) | 0x80;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    let hex = hex::encode(bytes);
    format!("{}-{}-{}-{}-{}", &hex[..8], &hex[8..12], &hex[12..16], &hex[16..20], &hex[20..])
}

/// The `historyId` of a result: the SHA-256 of its `fullName` as lowercase hex. Every run of one scenario has the
/// same one, so Allure joins the runs into one history, and two shards of one build merge.
pub fn allure_history_id(full_name: &str) -> String {
    hex::encode(Sha256::digest(full_name))
}

vocabulary! {
    /// The status of an Allure result or step.
    pub enum AllureStatus {
        Passed = "passed",
        Failed = "failed",
        /// A test that reached no verdict: an aborted or a truncated bundle, or an aborted span.
        Broken = "broken",
        Skipped = "skipped",
    }
}

impl From<BundleStatus> for AllureStatus {
    fn from(status: BundleStatus) -> Self {
        match status {
            BundleStatus::Passed => Self::Passed,
            BundleStatus::Failed => Self::Failed,
            BundleStatus::Aborted | BundleStatus::Truncated => Self::Broken,
        }
    }
}

impl From<Status> for AllureStatus {
    fn from(status: Status) -> Self {
        BundleStatus::from(status).into()
    }
}

vocabulary! {
    /// The stage of an Allure result or step. The recorder writes a result when its bundle is complete, so every
    /// result and step is finished.
    pub enum AllureStage {
        Finished = "finished",
    }
}

/// Why a result or a step did not pass.
#[derive(Serialize, Deserialize, Clone, PartialEq, Eq, Debug, Default)]
#[serde(deny_unknown_fields)]
pub struct AllureStatusDetails {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub message: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub trace: Option<String>,
}

impl AllureStatusDetails {
    /// Whether the details say nothing. A result or a step without details has no `statusDetails`.
    pub const fn is_empty(&self) -> bool {
        self.message.is_none() && self.trace.is_none()
    }
}

/// One file that a result or a step names: its title, the file in the results directory and its media type.
#[derive(Serialize, Deserialize, Clone, PartialEq, Eq, Debug)]
#[serde(deny_unknown_fields)]
pub struct AllureAttachment {
    pub name: String,
    /// The file name in the results directory, an [allure_attachment_file].
    pub source: String,
    #[serde(rename = "type")]
    pub mime: String,
}

/// One step of a result: one lane span, with the steps of the spans inside it.
#[derive(Serialize, Deserialize, Clone, PartialEq, Eq, Debug)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct AllureStep {
    pub name: String,
    pub status: AllureStatus,
    #[serde(default, skip_serializing_if = "AllureStatusDetails::is_empty")]
    pub status_details: AllureStatusDetails,
    pub stage: AllureStage,
    /// Epoch milliseconds.
    pub start: i64,
    pub stop: i64,
    pub steps: Vec<Self>,
    pub attachments: Vec<AllureAttachment>,
}

/// `<uuid>-result.json`: one bundle as one Allure test result.
#[derive(Serialize, Deserialize, Clone, PartialEq, Eq, Debug)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct AllureResult {
    pub uuid: String,
    /// See [allure_history_id].
    pub history_id: String,
    /// `<testClass>.<scenario>`.
    pub full_name: String,
    pub name: String,
    pub status: AllureStatus,
    #[serde(default, skip_serializing_if = "AllureStatusDetails::is_empty")]
    pub status_details: AllureStatusDetails,
    pub stage: AllureStage,
    /// Epoch milliseconds.
    pub start: i64,
    pub stop: i64,
    pub labels: Vec<Label>,
    pub steps: Vec<AllureStep>,
    pub attachments: Vec<AllureAttachment>,
}

impl AllureResult {
    /// The value of the [ALLURE_BUNDLE_LABEL] label.
    pub fn bundle(&self) -> Option<&str> {
        self.labels
            .iter()
            .find(|label| label.name == ALLURE_BUNDLE_LABEL)
            .map(|label| label.value.as_str())
    }

    /// The file of every attachment, the steps' included, in the order the result names them.
    pub fn sources(&self) -> Vec<&str> {
        fn collect<'a>(steps: &'a [AllureStep], sources: &mut Vec<&'a str>) {
            for step in steps {
                sources.extend(step.attachments.iter().map(|attachment| attachment.source.as_str()));
                collect(&step.steps, sources);
            }
        }
        let mut sources: Vec<&str> = self.attachments.iter().map(|attachment| attachment.source.as_str()).collect();
        collect(&self.steps, &mut sources);
        sources
    }

    /// Refuses a result the recorder would not have written: one without an id or a name, with a stop before its
    /// start, without its bundle, or with an attachment file that is not one plain name in the results directory.
    pub fn validate(&self) -> Result<(), Error> {
        if self.uuid.is_empty() || self.history_id.is_empty() || self.full_name.is_empty() || self.name.is_empty() {
            refuse!("an Allure result needs a uuid, a historyId, a fullName and a name");
        }
        if self.stop < self.start {
            refuse!(
                "the Allure result {} stops at {} before its start {}",
                self.uuid,
                self.stop,
                self.start
            );
        }
        if self.bundle().is_none_or(str::is_empty) {
            refuse!("the Allure result {} has no {ALLURE_BUNDLE_LABEL} label", self.uuid);
        }
        let plain = |name: &str| !name.is_empty() && !name.starts_with('.') && !name.contains(['/', '\\']);
        if let Some(source) = self.sources().into_iter().find(|source| !plain(source)) {
            refuse!(
                "the Allure result {} names the attachment {source:?}, which is not a file of its directory",
                self.uuid
            );
        }
        Ok(())
    }
}

/// Writes `<uuid>-result.json`: the result, validated, indented and ended with a newline, as [encode_manifest] does.
pub fn encode_allure_result(result: &AllureResult) -> Result<Vec<u8>, Error> {
    result.validate()?;
    let mut document = serde_json::to_vec_pretty(result)
        .map_err(|error| Error::new(format!("the Allure result {} cannot be encoded: {error}", result.uuid)))?;
    document.push(b'\n');
    Ok(document)
}

/// Reads `<uuid>-result.json` and refuses anything the recorder would not have written.
pub fn decode_allure_result(document: &[u8]) -> Result<AllureResult, Error> {
    let result: AllureResult =
        serde_json::from_slice(document).map_err(|error| Error::new(format!("an Allure result does not fit the contract: {error}")))?;
    result.validate()?;
    Ok(result)
}

// --- the frame index -----------------------------------------------------------------------------------------

/// `video.index.json`: when each frame of the video was captured.
///
/// The video is encoded at a constant frame rate, so frame i is presented at i/frameRate seconds. That is what
/// makes an exact seek possible: to show the moment of a log record, the viewer finds the last frame captured at or
/// before the record's time and seeks to that frame's presentation time. Nothing guesses at the offset between the
/// encoder's clock and the lane's. An unchanged screen is still sent as a frame, so the index has no gaps.
#[derive(Serialize, Deserialize, Clone, PartialEq, Eq, Debug)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct VideoIndex {
    pub frame_rate: u32,
    /// The epoch millisecond each frame was captured at, one entry per frame, in order.
    pub frames_ms: Vec<i64>,
}

/// Reads `video.index.json`.
pub fn decode_video_index(document: &[u8]) -> Result<VideoIndex, Error> {
    let index: VideoIndex =
        serde_json::from_slice(document).map_err(|error| Error::new(format!("{VIDEO_INDEX_FILE} does not fit the contract: {error}")))?;
    if index.frame_rate == 0 {
        refuse!("{VIDEO_INDEX_FILE} has the frame rate {}", index.frame_rate);
    }
    if !index.frames_ms.is_sorted() {
        refuse!("{VIDEO_INDEX_FILE} lists its frames out of order");
    }
    Ok(index)
}

#[cfg(test)]
mod tests;
