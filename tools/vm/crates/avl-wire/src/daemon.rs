//! The daemon control channel's wire: the state file, the HTTP routes, and the NDJSON run stream.
//!
//! The far end is Kotlin (`plugins/air/tests/integration/uiDaemon/testSrc/AirUiDaemonProtocol.kt`); this is the
//! controller's half. Two guards make it worth its own module: the version refuses a daemon this controller
//! does not understand, and the decoder refuses a record it cannot read rather than yielding a silent zero.
//!
//! Two rules run through the whole file, and they are opposites on purpose:
//!
//! - An unknown *field* is kept. Adding one is how the daemon grows compatibly, and the host publishes a run's
//!   records verbatim ([`RunEvent::raw`]), so a rebuilt object would change bytes an agent reads.
//! - An unknown *record*, or a declared field of the wrong kind, is refused. Those are renames, and a
//!   controller that shrugged at them reported a run that merely looked odd. An explicit `null` on an optional
//!   field is a rename that landed halfway, and is refused too.

use std::borrow::Cow;
use std::fmt;
use std::time::Duration;

use serde::de::DeserializeOwned;
use serde::{Deserialize, Serialize};
use serde_json::value::RawValue;
use serde_json::{Map, Value};

#[cfg(test)]
mod tests;

/// Must match `AIR_UI_DAEMON_PROTOCOL_VERSION` on the Kotlin side. Bump both together, or a boot fails by name.
pub const PROTOCOL_VERSION: u32 = 3;

/// The Bazel target the runtime descriptor comes from.
pub const LABEL: &str = "//plugins/air/tests/integration/ui:ui_daemon";

/// The prefix of every daemon run id. Only a daemon start sends a supervisor `start` with it, so it is what lets a
/// controller that holds no record still recognise a daemon run in the slot.
pub const DAEMON_RUN_PREFIX: &str = "run-ui-daemon-";

/// Names a daemon run after the uuid that identifies it. The shape is the run supervisor's
/// ([`crate::supervisor::is_run_id`]), because the same id names the same run on both channels.
pub fn run_id(uuid: &str) -> String {
    format!("{DAEMON_RUN_PREFIX}{uuid}")
}

/// Whether a run id is a daemon run's.
pub fn is_daemon_run(run_id: &str) -> bool {
    run_id.starts_with(DAEMON_RUN_PREFIX)
}

// --- the routes -------------------------------------------------------------------------------------------

vocabulary! {
    /// Which route a request is for, including the two parameterized routes that have no fixed path.
    pub enum Route {
        Status = "status",
        MissingJars = "missingJars",
        Run = "run",
        MountQuiesce = "mountQuiesce",
        MountResume = "mountResume",
        IdeStop = "ideStop",
        Shutdown = "shutdown",
        UploadJar = "uploadJar",
        Results = "results",
    }
}

vocabulary! {
    pub enum Method {
        Get = "GET",
        Post = "POST",
        Put = "PUT",
    }
}

/// One request of the control channel, with the budget that route's work actually needs.
///
/// `timeout` is `None` for exactly one route - `/run`, whose deadline comes from the daemon's own streamed
/// watchdog. A second number here could only disagree with it.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Endpoint {
    pub route: Route,
    pub method: Method,
    pub path: Cow<'static, str>,
    pub timeout: Option<Duration>,
}

const fn fixed(route: Route, method: Method, path: &'static str, timeout: Option<Duration>) -> Endpoint {
    Endpoint {
        route,
        method,
        path: Cow::Borrowed(path),
        timeout,
    }
}

// The budgets were bare literals at each call, which is why `/mount/quiesce` (a full IDE stop) and `/status`
// (one field read) had once been given the same one.
pub const STATUS: Endpoint = fixed(Route::Status, Method::Get, "/status", Some(Duration::from_secs(5)));
pub const MISSING_JARS: Endpoint = fixed(Route::MissingJars, Method::Post, "/jars", Some(Duration::from_secs(15)));
pub const RUN: Endpoint = fixed(Route::Run, Method::Post, "/run", None);
pub const MOUNT_QUIESCE: Endpoint = fixed(Route::MountQuiesce, Method::Post, "/mount/quiesce", Some(Duration::from_secs(180)));
pub const MOUNT_RESUME: Endpoint = fixed(Route::MountResume, Method::Post, "/mount/resume", Some(Duration::from_secs(30)));
pub const IDE_STOP: Endpoint = fixed(Route::IdeStop, Method::Post, "/ide/stop", Some(Duration::from_secs(180)));
pub const SHUTDOWN: Endpoint = fixed(Route::Shutdown, Method::Post, "/shutdown", Some(Duration::from_secs(10)));

/// Every fixed route, in declared order. A list rather than a map, so the first match is a fact.
pub const FIXED_ENDPOINTS: [Endpoint; 7] = [STATUS, MISSING_JARS, RUN, MOUNT_QUIESCE, MOUNT_RESUME, IDE_STOP, SHUTDOWN];

pub const UPLOAD_JAR_TIMEOUT: Duration = Duration::from_secs(120);
pub const RESULTS_TIMEOUT: Duration = Duration::from_secs(10);

/// The route one jar's bytes are pushed to. The digest is a path segment and text; it is never parsed as a
/// number anywhere on this wire.
pub fn upload_jar(sha256: &str) -> Endpoint {
    Endpoint {
        route: Route::UploadJar,
        method: Method::Put,
        path: Cow::Owned(format!("/jars/{sha256}")),
        timeout: Some(UPLOAD_JAR_TIMEOUT),
    }
}

/// Where one iteration's JUnit XML is read from.
pub fn iteration_result(iteration_id: &str) -> Endpoint {
    Endpoint {
        route: Route::Results,
        method: Method::Get,
        path: Cow::Owned(format!("/results/{iteration_id}/test.xml")),
        timeout: Some(RESULTS_TIMEOUT),
    }
}

/// Which route a request is for, or `None`.
///
/// Public so a test double routes exactly as the daemon does. The fake used to re-implement this table, and the
/// failure mode of that is a fake that agrees with the client while disagreeing with the server, which is how a
/// wrong cancel spelling passed for months.
pub fn match_route(method: &str, path: &str) -> Option<Route> {
    if let Some(endpoint) = FIXED_ENDPOINTS
        .iter()
        .find(|endpoint| endpoint.path == path && endpoint.method.as_str() == method)
    {
        return Some(endpoint.route);
    }
    if method == "PUT" && path.strip_prefix("/jars/").is_some_and(crate::stage::is_sha256_hex) {
        return Some(Route::UploadJar);
    }
    // One segment, so a traversal in the iteration id never reaches the results handler.
    let iteration = path.strip_prefix("/results/").and_then(|rest| rest.strip_suffix("/test.xml"));
    if method == "GET" && iteration.is_some_and(|segment| !segment.is_empty() && !segment.contains('/')) {
        return Some(Route::Results);
    }
    None
}

// --- the bodies those routes answer with ------------------------------------------------------------------
//
// The Kotlin DTOs behind these carry no `@SerialName`, so every name below is a Kotlin property name spelled
// out by hand. `AirUiDaemonProtocolTest` asserts these keys as bytes on the Kotlin side.

/// `GET /status`.
///
/// All ten fields always cross: Kotlin declares every one non-nullable and encodes defaults. Four of them
/// (`product_stamp`, `busy`, `metaspace_used_bytes`, `pid`) are read by no decision here, and are declared
/// anyway - a field the daemon sends and this file omits is a field a fake is free to drop.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Status {
    pub protocol_version: u32,
    pub daemon_boot_stamp: String,
    pub controller_launch_digest: String,
    pub mount_quiesced: bool,
    pub product_stamp: String,
    pub ide_running: bool,
    pub busy: bool,
    pub iteration_count: u32,
    /// Bytes on a long-lived JVM; passes a billion routinely, so nothing a float would round.
    pub metaspace_used_bytes: i64,
    pub pid: i64,
}

/// `POST /jars`: which of the offered digests the guest does not already hold.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct MissingJars {
    pub missing: Vec<String>,
}

/// A 409 from any route that needs the run slot.
///
/// `reason` is a plain string on purpose. An unknown *record* is a schema break, but an unknown reason is data:
/// refusing it would leave the daemon no way to name a refusal this controller predates.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Conflict {
    pub reason: String,
    /// Accompanies `stale-daemon`, and names the generation the daemon actually serves.
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub launch_digest: Option<String>,
}

/// The refusals the daemon names today, each of which the controller answers differently.
pub mod conflict {
    pub const BUSY: &str = "busy";
    pub const MOUNT_QUIESCED: &str = "mount-quiesced";
    pub const STALE_DAEMON: &str = "stale-daemon";
}

/// What the daemon publishes for the controller to read over the hypervisor exec channel. The token never
/// travels the network unprotected, which is the whole reason this file exists rather than a handshake route.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct StateFile {
    pub protocol_version: u32,
    pub port: u16,
    pub token: String,
    pub pid: i64,
    pub daemon_boot_stamp: String,
}

// --- the run stream ---------------------------------------------------------------------------------------

vocabulary! {
    /// The discriminator of one NDJSON record.
    pub enum Event {
        RunStarted = "runStarted",
        /// What JUnit discovered, published before the first execution starts. Its class names are the only
        /// account a run gives of a suite it never reached: a killed IDE ends the stream, and a suite queued
        /// behind the failure emits no record of its own.
        PlanStarted = "planStarted",
        TestStarted = "testStarted",
        TestFinished = "testFinished",
        TestSkipped = "testSkipped",
        ContainerFailed = "containerFailed",
        /// A class a condition ruled out. Its reason is the only place the run says why nothing of it ran.
        ContainerSkipped = "containerSkipped",
        WatchdogState = "watchdogState",
        WatchdogExpired = "watchdogExpired",
        RunFailed = "runFailed",
        Summary = "summary",
        /// A line of the daemon's stdout that is not JSON, kept so nothing is dropped.
        Output = "output",
    }
}

/// The test or container a record is about.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ExecutionIdentity {
    pub id: String,
    pub display_name: String,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub class_name: Option<String>,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub method_name: Option<String>,
}

impl ExecutionIdentity {
    /// The execution the way every progress line names it; see [`describe_execution`].
    pub fn describe(&self) -> String {
        describe_execution(self.class_name.as_deref(), &self.display_name, self.method_name.as_deref())
    }
}

/// One execution, the way every progress line names it.
///
/// A dynamic node keeps its factory's method source, so its method name is the factory's and its display name
/// is the node's own - an Air scenario name. The display name is kept beside the method whenever it says
/// something the method does not, so a hang names the scenario and not only the suite.
pub fn describe_execution(class_name: Option<&str>, display_name: &str, method_name: Option<&str>) -> String {
    let Some(class_name) = class_name else {
        return display_name.to_owned();
    };
    let mut named = class_name.to_owned();
    if let Some(method) = method_name.filter(|method| !method.is_empty()) {
        named.push('#');
        named.push_str(method);
        let restates_method = display_name == method || display_name.strip_suffix("()") == Some(method);
        if !display_name.is_empty() && !restates_method {
            named.push('[');
            named.push_str(display_name);
            named.push(']');
        }
    }
    named
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RunStarted {
    pub iteration_id: String,
    pub daemon_boot_stamp: String,
    pub started_at: String,
}

/// A plan that discovered nothing and a record that carried no count are different facts: the first is a lane
/// whose selection matched nothing, the second a decoder that was bypassed. So the count is required.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PlanStarted {
    pub timestamp: String,
    pub discovered_executions: u32,
    pub class_names: Vec<String>,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct TestStarted {
    pub timestamp: String,
    #[serde(flatten)]
    pub execution: ExecutionIdentity,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TestFinished {
    pub timestamp: String,
    #[serde(flatten)]
    pub execution: ExecutionIdentity,
    pub status: String,
    pub duration_ms: i64,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub error: Option<String>,
}

/// A skipped test, or a skipped container: the same record under two names.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct Skipped {
    pub timestamp: String,
    #[serde(flatten)]
    pub execution: ExecutionIdentity,
    pub reason: String,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct ContainerFailed {
    pub timestamp: String,
    #[serde(flatten)]
    pub execution: ExecutionIdentity,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub error: Option<String>,
}

/// The daemon's watchdog, as it last published itself.
///
/// `next_deadline_in_ms` absent means the daemon named no next deadline, while zero means it expires now, and on
/// this wire the difference decides verdicts.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct WatchdogState {
    pub timestamp: String,
    pub phase: String,
    pub active_execution_timeout_ms: i64,
    pub progress_gap_timeout_ms: i64,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub phase_deadline: Option<String>,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub emergency_deadline: Option<String>,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub next_deadline_in_ms: Option<i64>,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub active_execution: Option<ExecutionIdentity>,
}

/// Why the daemon gave up on a run: the only account of why it was cut off, so the detail and the evidence
/// survive decoding rather than being flattened into the reason.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct WatchdogExpired {
    pub reason: String,
    pub deadline: String,
    pub expired_at: String,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub detail: Option<String>,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub active_execution: Option<ExecutionIdentity>,
    /// Guest files the daemon named; filtered by `report::safe_evidence_paths` before anything is fetched.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub evidence: Vec<String>,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct RunFailed {
    pub error: String,
}

/// The daemon's own count of what it ran.
///
/// Reached through an `Option` ([`find_summary`]), because "the stream ended without a summary" has to stay
/// representable: a zero-valued summary reads as a run that started nothing and failed nothing - a green
/// verdict manufactured out of a stream that was cut off.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Summary {
    pub iteration_id: String,
    pub daemon_boot_stamp: String,
    pub started_at: String,
    pub tests_started: u32,
    pub tests_failed: u32,
    pub tests_skipped: u32,
    pub containers_skipped: u32,
    pub container_failures: u32,
    pub has_failures: bool,
    pub ide_running: bool,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct Output {
    pub text: String,
}

/// The reading of one record, by kind. Serializes back to the record's own shape, `event` included.
#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
#[serde(tag = "event", rename_all = "camelCase")]
pub enum RunEventKind {
    RunStarted(RunStarted),
    PlanStarted(PlanStarted),
    TestStarted(TestStarted),
    TestFinished(TestFinished),
    TestSkipped(Skipped),
    ContainerFailed(ContainerFailed),
    ContainerSkipped(Skipped),
    WatchdogState(WatchdogState),
    WatchdogExpired(WatchdogExpired),
    RunFailed(RunFailed),
    Summary(Summary),
    Output(Output),
}

/// One decoded record: what the daemon sent, and the reading of it.
///
/// `raw` carries the wire, and a caller that republishes a record sends it, so the bytes an agent sees are the
/// bytes the daemon wrote. It is `None` only for a record this controller synthesized: an `output` line of
/// the daemon's stdout.
#[derive(Clone, Debug)]
pub struct RunEvent {
    pub raw: Option<Box<RawValue>>,
    pub kind: RunEventKind,
}

impl PartialEq for RunEvent {
    fn eq(&self, other: &Self) -> bool {
        self.kind == other.kind && self.raw.as_ref().map(|raw| raw.get()) == other.raw.as_ref().map(|raw| raw.get())
    }
}

impl RunEvent {
    /// A line of the daemon's stdout that is not a record.
    pub fn output(text: impl Into<String>) -> Self {
        Self {
            raw: None,
            kind: RunEventKind::Output(Output { text: text.into() }),
        }
    }

    /// A record that was built rather than read, such as a test double's.
    pub const fn synthesized(kind: RunEventKind) -> Self {
        Self { raw: None, kind }
    }

    pub const fn event(&self) -> Event {
        match &self.kind {
            RunEventKind::RunStarted(_) => Event::RunStarted,
            RunEventKind::PlanStarted(_) => Event::PlanStarted,
            RunEventKind::TestStarted(_) => Event::TestStarted,
            RunEventKind::TestFinished(_) => Event::TestFinished,
            RunEventKind::TestSkipped(_) => Event::TestSkipped,
            RunEventKind::ContainerFailed(_) => Event::ContainerFailed,
            RunEventKind::ContainerSkipped(_) => Event::ContainerSkipped,
            RunEventKind::WatchdogState(_) => Event::WatchdogState,
            RunEventKind::WatchdogExpired(_) => Event::WatchdogExpired,
            RunEventKind::RunFailed(_) => Event::RunFailed,
            RunEventKind::Summary(_) => Event::Summary,
            RunEventKind::Output(_) => Event::Output,
        }
    }

    /// The record as JSON: the daemon's bytes when it has them, else the reading serialized.
    pub fn to_raw(&self) -> Box<RawValue> {
        match &self.raw {
            Some(raw) => raw.clone(),
            // Every record type serializes to a JSON object with string keys, which cannot fail.
            None => serde_json::value::to_raw_value(&self.kind).expect("a run record always serializes"),
        }
    }
}

/// The run's summary, or `None` when the stream carried none. The last one wins.
pub fn find_summary(events: &[RunEvent]) -> Option<&Summary> {
    events.iter().rev().find_map(|event| match &event.kind {
        RunEventKind::Summary(summary) => Some(summary),
        _ => None,
    })
}

// --- decoding ---------------------------------------------------------------------------------------------

/// What every refusal on this wire is. `code` is the controller's agent-facing error code.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ProtocolError {
    pub code: &'static str,
    pub message: String,
}

impl ProtocolError {
    fn new(code: &'static str, detail: impl fmt::Display) -> Self {
        Self {
            code,
            message: format!(
                "{detail}. The guest daemon and this controller disagree about the run protocol (controller speaks \
                 v{PROTOCOL_VERSION}); a renamed field or record is the usual cause. Read the daemon tail with \
                 `daemon log`."
            ),
        }
    }
}

impl fmt::Display for ProtocolError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.message)
    }
}

impl std::error::Error for ProtocolError {}

pub const CODE_UNKNOWN_EVENT: &str = "daemon_protocol_unknown_event";
pub const CODE_FIELD_MISSING: &str = "daemon_protocol_field_missing";
pub const CODE_STATE_FILE_UNREADABLE: &str = "daemon_state_file_unreadable";

/// Why one line of the run stream is not a record.
#[derive(Debug)]
pub enum DecodeError {
    /// The line is not a JSON object at all: the daemon's stdout, not a record.
    Json(serde_json::Error),
    /// A JSON object that is not a record this controller reads: a rename.
    Protocol(ProtocolError),
}

impl fmt::Display for DecodeError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Json(error) => error.fmt(f),
            Self::Protocol(error) => error.fmt(f),
        }
    }
}

impl std::error::Error for DecodeError {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        match self {
            Self::Json(error) => Some(error),
            Self::Protocol(error) => Some(error),
        }
    }
}

/// Validates one NDJSON record and answers it.
///
/// An unknown record kind is refused: that is a rename, and a run reported from a stream whose records this
/// controller silently dropped is a run nobody watched.
pub fn decode_run_event(line: &[u8]) -> Result<RunEvent, DecodeError> {
    let raw: Box<RawValue> = serde_json::from_slice(line).map_err(DecodeError::Json)?;
    let fields: Map<String, Value> = serde_json::from_str(raw.get()).map_err(DecodeError::Json)?;
    let event = match fields.get("event") {
        Some(Value::String(name)) => name.parse::<Event>().map_err(|unknown| {
            DecodeError::Protocol(ProtocolError::new(
                CODE_UNKNOWN_EVENT,
                format_args!("the daemon sent an unknown run record {:?}", unknown.word),
            ))
        })?,
        other => {
            let shown = other.map_or_else(|| "undefined".to_owned(), Value::to_string);
            return Err(DecodeError::Protocol(ProtocolError::new(
                CODE_UNKNOWN_EVENT,
                format_args!("the daemon sent an unknown run record {shown}"),
            )));
        }
    };
    let body = Value::Object(fields);
    let kind = match event {
        Event::RunStarted => RunEventKind::RunStarted(record(event, body)?),
        Event::PlanStarted => RunEventKind::PlanStarted(record(event, body)?),
        Event::TestStarted => RunEventKind::TestStarted(record(event, body)?),
        Event::TestFinished => RunEventKind::TestFinished(record(event, body)?),
        Event::TestSkipped => RunEventKind::TestSkipped(record(event, body)?),
        Event::ContainerFailed => RunEventKind::ContainerFailed(record(event, body)?),
        Event::ContainerSkipped => RunEventKind::ContainerSkipped(record(event, body)?),
        Event::WatchdogState => RunEventKind::WatchdogState(record(event, body)?),
        Event::WatchdogExpired => RunEventKind::WatchdogExpired(record(event, body)?),
        Event::RunFailed => RunEventKind::RunFailed(record(event, body)?),
        Event::Summary => RunEventKind::Summary(record(event, body)?),
        Event::Output => RunEventKind::Output(record(event, body)?),
    };
    Ok(RunEvent { raw: Some(raw), kind })
}

fn record<T: DeserializeOwned>(event: Event, body: Value) -> Result<T, DecodeError> {
    serde_json::from_value(body).map_err(|error| {
        DecodeError::Protocol(ProtocolError::new(
            CODE_FIELD_MISSING,
            format_args!("the daemon's {event} record is not readable ({error})"),
        ))
    })
}

fn body<T: DeserializeOwned>(raw: &[u8], label: &str, code: &'static str) -> Result<T, ProtocolError> {
    serde_json::from_slice(raw).map_err(|error| ProtocolError::new(code, format_args!("{label} is not readable ({error})")))
}

/// Reads a `/status` body.
pub fn decode_status(raw: &[u8]) -> Result<Status, ProtocolError> {
    body(raw, "the daemon's /status body", CODE_FIELD_MISSING)
}

/// Reads a `/jars` reply.
pub fn decode_missing_jars(raw: &[u8]) -> Result<MissingJars, ProtocolError> {
    body(raw, "the daemon's /jars reply", CODE_FIELD_MISSING)
}

/// Reads a 409 body.
pub fn decode_conflict(raw: &[u8]) -> Result<Conflict, ProtocolError> {
    body(raw, "the daemon's 409 body", CODE_FIELD_MISSING)
}

/// Reads the daemon's state file, refusing a file that is present but unreadable.
///
/// The boot loop used to hand-check three of these fields and treat a mismatch as "not written yet", so a
/// renamed one was indistinguishable from a daemon that had not got there - and cost the whole boot timeout to
/// report as the wrong error.
pub fn decode_state_file(raw: &[u8]) -> Result<StateFile, ProtocolError> {
    body(raw, "the daemon's state file", CODE_STATE_FILE_UNREADABLE)
}

// --- the stream itself ------------------------------------------------------------------------------------

/// The incremental decoder for the daemon's chunked `/run` response.
///
/// A line that is not JSON becomes an `output` record rather than an error: the daemon's own stdout shares this
/// stream, and dropping it would lose the only account of a JVM that died before it could say so. Chunks are
/// bytes, and a chunk may end inside a line or inside a UTF-8 sequence; the partial line is carried.
#[derive(Debug, Default)]
pub struct NdjsonDecoder {
    pending: Vec<u8>,
}

/// What one chunk completed: the records, in order, and the protocol break that stopped the decoder, if any.
/// The records before a break are still the run's.
#[derive(Debug, Default)]
pub struct Decoded {
    pub events: Vec<RunEvent>,
    pub failure: Option<ProtocolError>,
}

impl NdjsonDecoder {
    /// Feeds one chunk and answers the records it completed.
    pub fn push(&mut self, chunk: &[u8]) -> Decoded {
        self.pending.extend_from_slice(chunk);
        let mut decoded = Decoded::default();
        let mut consumed = 0;
        while let Some(newline) = self.pending[consumed..].iter().position(|&byte| byte == b'\n') {
            let line = self.pending[consumed..consumed + newline].trim_ascii();
            consumed += newline + 1;
            if line.is_empty() {
                continue;
            }
            // Whether a line is even a candidate record keeps a non-JSON line and a malformed *record* apart:
            // the first is the daemon's stdout, the second a protocol break that must be reported.
            if !line.starts_with(b"{") {
                decoded.events.push(RunEvent::output(String::from_utf8_lossy(line)));
                continue;
            }
            match decode_run_event(line) {
                Ok(event) => decoded.events.push(event),
                Err(DecodeError::Json(_)) => decoded.events.push(RunEvent::output(String::from_utf8_lossy(line))),
                Err(DecodeError::Protocol(failure)) => {
                    decoded.failure = Some(failure);
                    break;
                }
            }
        }
        self.pending.drain(..consumed);
        decoded
    }
}
