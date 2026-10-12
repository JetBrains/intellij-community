//! Finds the trace bundles on this machine and says what each one is.
//!
//! A bundle is found by its `spans.jsonl`, in a directory tree or inside a zip, and named by its manifest; a
//! bundle without one is named by what its first and last log records say, and by its path. `air-trace serve`
//! lists what a [Scanner] finds. `air-trace plan` reads the bundles an input holds, and finds those of the
//! scenarios it plans, with the same readers, so both name a bundle the same way. The controller's trace sync
//! reads a pulled zip with [read_zip].
//!
//! The controller's runtime root, where its per-iteration and per-scenario zips live, is the caller's to resolve
//! and hand to [default_roots]: it is the controller's configuration, which this crate does not read.

mod roots;
mod scan;
mod zips;

use std::path::PathBuf;

use avl_trace::bundle::{BundleStatus, Capture, LOGS_FILE, MANIFEST_FILE, Manifest, SPANS_FILE, decode_manifest, format_manifest_time};
use avl_trace::otlp::{self, attr, decode_logs_line, decode_traces_line, event, lookup_str};
use avl_trace::protocol::VideoCodec;
use avl_trace::vocabulary;
use serde::Serialize;
use sha2::{Digest, Sha256};

pub use roots::{Root, RootKind, VM_REPORTS_GLOB, VM_RUN_TRACES_DIR, VM_RUNS_DIR, VM_RUNS_GLOB, default_roots, within};
pub use scan::{Options, RootState, Scanner, Snapshot};
pub use zips::{EntryMeta, ZipCache, ZipIndex, clean_bundle_path, read_zip};

// --- what a bundle says --------------------------------------------------------------------------------------

vocabulary! {
    /// How a bundle ended, as far as its files say: the manifest's [BundleStatus], or one of two states a
    /// manifest does not have.
    #[derive(Default)]
    pub enum Status {
        Passed = "passed",
        Failed = "failed",
        Aborted = "aborted",
        /// Stopped before `done`, as the manifest says; and without a manifest, a directory bundle that stopped
        /// changing, any zip, and a file read alone.
        #[default]
        Truncated = "truncated",
        /// A directory bundle with no manifest whose files changed within [Options::stale_after].
        Running = "running",
        /// A bundle whose manifest does not decode; [Summary::error] says why.
        Invalid = "invalid",
    }
}

impl From<BundleStatus> for Status {
    fn from(status: BundleStatus) -> Self {
        match status {
            BundleStatus::Passed => Self::Passed,
            BundleStatus::Failed => Self::Failed,
            BundleStatus::Aborted => Self::Aborted,
            BundleStatus::Truncated => Self::Truncated,
        }
    }
}

/// One bundle, as its files describe it. It is the listing's JSON for the viewer.
#[derive(Serialize, Clone, PartialEq, Eq, Debug, Default)]
#[serde(rename_all = "camelCase")]
pub struct Summary {
    /// The bundle's opaque id: the same bundle at the same place always has the same one, so a URL that names it
    /// survives a restart of whatever serves it. See [bundle_id].
    pub id: String,
    pub run_id: String,
    pub test_class: String,
    pub scenario: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub flow: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub lane: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub launcher: Option<String>,
    pub status: Status,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub running_span: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub started_at: Option<String>,
    pub duration_ms: u64,
    pub has_video: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub capture: Option<Capture>,
    /// The last snapshot's picture, a path inside the bundle.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub thumbnail: Option<String>,
    pub source: Source,
    /// Why the manifest could not be read, when the status is [Status::Invalid].
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error: Option<String>,
}

vocabulary! {
    /// What kind of place a bundle was found in.
    #[derive(Default)]
    pub enum SourceKind {
        #[default]
        Dir = "dir",
        Zip = "zip",
        /// One file of a bundle read alone, such as one dropped on the planner. The rest of its bundle is not
        /// there, so the bundle is named by what the file itself says.
        File = "file",
    }
}

/// Where a bundle was found.
#[derive(Serialize, Clone, PartialEq, Eq, Debug, Default)]
pub struct Source {
    pub kind: SourceKind,
    /// The bundle's directory or the zip that holds it, as a real path; for a file read alone, the name it was
    /// given as.
    pub path: String,
    /// The bundle's directory inside the zip, ending in `/`, and empty at the zip's top.
    #[serde(skip_serializing_if = "String::is_empty")]
    pub entry: String,
}

/// Where a found bundle's files are.
#[derive(Clone, PartialEq, Eq, Debug)]
pub enum Location {
    /// A directory bundle's real directory.
    Dir(PathBuf),
    /// A zip bundle's real zip, and its directory inside it: ending in `/`, or empty at the zip's top.
    Zip { path: PathBuf, prefix: String },
    /// A file read alone, whose bundle is nowhere.
    File,
}

/// One bundle found: what it says, and where its files are.
#[derive(Clone, PartialEq, Eq, Debug)]
pub struct Bundle {
    pub summary: Summary,
    pub location: Location,
}

impl std::ops::Deref for Bundle {
    type Target = Summary;

    fn deref(&self) -> &Summary {
        &self.summary
    }
}

/// The opaque id of the bundle at one place: the first 10 bytes of the SHA-256 of the kind, the container and the
/// prefix, as hex. The same place always answers the same id.
///
/// The ids are durable: reports, journals and the URLs the controller printed keep them, so the spelling of the
/// three parts must not change.
pub fn bundle_id(kind: SourceKind, container: &str, prefix: &str) -> String {
    let digest = Sha256::new()
        .chain_update(kind.as_str())
        .chain_update([0])
        .chain_update(container)
        .chain_update([0])
        .chain_update(prefix)
        .finalize();
    hex::encode(&digest[..10])
}

// --- reading one file alone ----------------------------------------------------------------------------------

/// What one file of a bundle says alone: its manifest, its spans, or its log records. `given` names where the file
/// came from, in the source and the id.
///
/// Without a manifest the bundle is truncated, as a zip without one is: nothing is still writing it here.
pub fn read_file(name: &str, content: &[u8], given: &str) -> Summary {
    let mut summary = Summary {
        id: bundle_id(SourceKind::File, given, ""),
        source: Source {
            kind: SourceKind::File,
            path: given.to_owned(),
            entry: String::new(),
        },
        ..Summary::default()
    };
    match name {
        MANIFEST_FILE => {
            match decode_manifest(content) {
                Ok(manifest) => apply_manifest(&mut summary, &manifest, false),
                Err(error) => {
                    summary.status = Status::Invalid;
                    summary.error = Some(error.to_string());
                }
            }
            return summary;
        }
        SPANS_FILE => infer_from_spans(&mut summary, content),
        LOGS_FILE => {
            let first = content.iter().position(|byte| *byte == b'\n').map(|end| &content[..end]);
            apply_inference(&mut summary, &infer_from_logs(first, last_complete_line(content)), &[""; 3]);
        }
        _ => {}
    }
    summary.status = Status::Truncated;
    summary
}

/// Names a bundle from its `spans.jsonl`: the resource carries the run id, and the root span, the one ended last,
/// carries the class, the scenario and the flow. A truncated bundle has no root span, and keeps the run id alone.
fn infer_from_spans(summary: &mut Summary, content: &[u8]) {
    for line in content.split(|byte| *byte == b'\n') {
        if line.trim_ascii().is_empty() {
            continue;
        }
        let Ok(data) = decode_traces_line(line) else {
            continue;
        };
        for resource in &data.resource_spans {
            if let Some(run_id) = non_empty(lookup_str(&resource.resource.attributes, attr::RUN_ID)) {
                summary.run_id = run_id;
            }
            for span in resource.scope_spans.iter().flat_map(|scope| &scope.spans) {
                if let Some(scenario) = non_empty(lookup_str(&span.attributes, attr::SCENARIO_NAME)) {
                    summary.scenario = scenario;
                    summary.test_class = lookup_str(&span.attributes, attr::TEST_CLASS).unwrap_or_default().to_owned();
                    summary.flow = non_empty(lookup_str(&span.attributes, attr::FLOW_ID));
                }
            }
        }
    }
}

// --- what a manifest or its absence says ---------------------------------------------------------------------

fn apply_manifest(summary: &mut Summary, manifest: &Manifest, video_present: bool) {
    summary.run_id.clone_from(&manifest.run_id);
    summary.test_class.clone_from(&manifest.test_class);
    summary.scenario.clone_from(&manifest.scenario);
    summary.flow.clone_from(&manifest.flow);
    summary.lane = Some(manifest.lane.clone());
    summary.launcher = Some(manifest.launcher.as_str().to_owned());
    summary.status = manifest.status.into();
    summary.running_span.clone_from(&manifest.running_span);
    summary.started_at = Some(manifest.started_at.clone());
    summary.duration_ms = manifest.duration_ms;
    summary.capture = Some(manifest.capture.clone());
    summary.has_video = manifest.video.codec() == VideoCodec::H264 && video_present;
}

/// What a bundle without a manifest says about itself: its first log record carries the resource, and the root
/// span's `air.span.started` carries the scenario's name and start.
#[derive(Default)]
struct Inferred {
    run_id: Option<String>,
    lane: Option<String>,
    launcher: Option<String>,
    scenario: Option<String>,
    start_ms: i64,
    last_ms: i64,
}

fn infer_from_logs(first: Option<&[u8]>, last: Option<&[u8]>) -> Inferred {
    let mut result = Inferred::default();
    let root = otlp::span_id(0);
    if let Some(data) = first.and_then(|line| decode_logs_line(line).ok()) {
        for resource in &data.resource_logs {
            let attributes = &resource.resource.attributes;
            result.run_id = non_empty(lookup_str(attributes, attr::RUN_ID));
            result.lane = non_empty(lookup_str(attributes, attr::LANE));
            result.launcher = non_empty(lookup_str(attributes, attr::LAUNCHER));
            for record in resource.scope_logs.iter().flat_map(|scope| &scope.log_records) {
                if result.start_ms == 0 {
                    result.start_ms = record.time_unix_nano.ms();
                }
                if record.event_name == event::SPAN_STARTED && record.span_id == root {
                    result.scenario = non_empty(lookup_str(&record.attributes, attr::SPAN_TITLE));
                    result.start_ms = record.time_unix_nano.ms();
                }
            }
        }
    }
    if let Some(data) = last.and_then(|line| decode_logs_line(line).ok()) {
        for record in data
            .resource_logs
            .iter()
            .flat_map(|resource| &resource.scope_logs)
            .flat_map(|scope| &scope.log_records)
        {
            result.last_ms = result.last_ms.max(record.time_unix_nano.ms());
        }
    }
    result
}

/// Names a bundle from what it inferred, and from the last three segments of its path as the names of last
/// resort: the run id, the test class and the scenario, in the layout [avl_trace::bundle::bundle_path] writes,
/// sanitized as they are.
fn apply_inference(summary: &mut Summary, facts: &Inferred, segments: &[&str; 3]) {
    summary.run_id = facts.run_id.clone().unwrap_or_else(|| segments[0].to_owned());
    summary.test_class = segments[1].to_owned();
    summary.scenario = facts.scenario.clone().unwrap_or_else(|| segments[2].to_owned());
    summary.lane.clone_from(&facts.lane);
    summary.launcher.clone_from(&facts.launcher);
    if facts.start_ms > 0
        && let Ok(start) = jiff::Timestamp::from_millisecond(facts.start_ms)
    {
        summary.started_at = Some(format_manifest_time(start));
        if facts.last_ms > facts.start_ms {
            summary.duration_ms = facts.last_ms.abs_diff(facts.start_ms);
        }
    }
}

/// The last three segments of a `/`-separated path, the missing ones empty.
fn trailing_segments(slashed: &str) -> [&str; 3] {
    let parts: Vec<&str> = slashed.trim_end_matches('/').split('/').collect();
    let mut segments = [""; 3];
    for (index, segment) in segments.iter_mut().enumerate() {
        if let Some(position) = (parts.len() + index).checked_sub(3) {
            *segment = parts[position];
        }
    }
    segments
}

/// The last newline-terminated line of a buffer, without its newline. A last line without its newline is still
/// being written, and is not answered.
fn last_complete_line(buffer: &[u8]) -> Option<&[u8]> {
    let end = buffer.iter().rposition(|byte| *byte == b'\n')?;
    let start = buffer[..end]
        .iter()
        .rposition(|byte| *byte == b'\n')
        .map_or(0, |newline| newline + 1);
    Some(&buffer[start..end])
}

fn non_empty(value: Option<&str>) -> Option<String> {
    value.filter(|value| !value.is_empty()).map(str::to_owned)
}

#[cfg(test)]
mod tests;
