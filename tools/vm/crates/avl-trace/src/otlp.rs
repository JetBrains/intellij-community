//! The bundle's spans and log records, in the OTLP JSON encoding
//! (<https://opentelemetry.io/docs/specs/otlp/#json-protobuf-encoding>).
//!
//! The structs are written by hand instead of generated from the OTLP protos, because the standard proto3 JSON
//! mapping is the wrong JSON for this encoding. OTLP JSON departs from it in two places: trace and span ids are hex
//! where proto3 writes base64, and the enums are integers. The 64-bit fields do follow proto3:
//! `startTimeUnixNano` and the other nanosecond fields are decimal strings, and so is an `intValue`. That matters
//! more here than anywhere, because the reader is the viewer's JavaScript, which cannot hold a nanosecond
//! timestamp in a number. So [UnixNano] and [I64String] refuse a bare number rather than accept one and round it.
//!
//! Only what the bundle uses is declared. Span events are deprecated in favour of log records correlated with a
//! span, which is what `logs.jsonl` holds. A proto3 field at its zero value is omitted, as protobuf's JSON writers
//! do.

use std::fmt;

use serde::de::{self, Visitor};
use serde::{Deserialize, Deserializer, Serialize, Serializer};
use sha2::{Digest, Sha256};

use crate::bundle::{LOGS_FILE, SPANS_FILE};
use crate::protocol::Status;
use crate::{Error, is_zero_u32};

// --- the scalar encodings ------------------------------------------------------------------------------------

/// A time in nanoseconds since the epoch, written as a decimal string.
#[derive(Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, Debug, Default)]
pub struct UnixNano(pub u64);

impl UnixNano {
    /// Epoch milliseconds, the lane's resolution, in OTLP nanoseconds. A time before the epoch is the epoch.
    pub fn from_ms(ms: i64) -> Self {
        Self(u64::try_from(ms).unwrap_or(0).saturating_mul(1_000_000))
    }

    /// The time in epoch milliseconds.
    #[expect(clippy::cast_possible_wrap, reason = "u64::MAX / 10^6 fits an i64")]
    pub const fn ms(self) -> i64 {
        (self.0 / 1_000_000) as i64
    }

    pub const fn is_zero(&self) -> bool {
        self.0 == 0
    }
}

/// An OTLP `intValue`, written as a decimal string.
#[derive(Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, Debug, Default)]
pub struct I64String(pub i64);

impl Serialize for UnixNano {
    fn serialize<S: Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        serializer.collect_str(&self.0)
    }
}

impl Serialize for I64String {
    fn serialize<S: Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        serializer.collect_str(&self.0)
    }
}

impl<'de> Deserialize<'de> for UnixNano {
    fn deserialize<D: Deserializer<'de>>(deserializer: D) -> Result<Self, D::Error> {
        let digits = deserializer.deserialize_str(DecimalString {
            signed: false,
            what: "a nanosecond time",
        })?;
        digits
            .parse()
            .map(UnixNano)
            .map_err(|error| de::Error::custom(format!("a nanosecond time {digits:?} is out of range: {error}")))
    }
}

impl<'de> Deserialize<'de> for I64String {
    fn deserialize<D: Deserializer<'de>>(deserializer: D) -> Result<Self, D::Error> {
        let digits = deserializer.deserialize_str(DecimalString {
            signed: true,
            what: "an intValue",
        })?;
        digits
            .parse()
            .map(I64String)
            .map_err(|error| de::Error::custom(format!("an intValue {digits:?} is out of range: {error}")))
    }
}

/// Accepts a JSON string that holds a decimal integer and nothing else: no sign but an optional `-`, no `+`, no
/// fraction, no space.
struct DecimalString {
    signed: bool,
    what: &'static str,
}

impl Visitor<'_> for DecimalString {
    type Value = String;

    fn expecting(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(formatter, "{} as a decimal string", self.what)
    }

    fn visit_str<E: de::Error>(self, text: &str) -> Result<String, E> {
        let body = if self.signed {
            text.strip_prefix('-').unwrap_or(text)
        } else {
            text
        };
        if body.is_empty() || !body.bytes().all(|byte| byte.is_ascii_digit()) {
            return Err(E::custom(format!("{} must be a decimal string, not {text:?}", self.what)));
        }
        Ok(text.to_owned())
    }
}

// --- ids -----------------------------------------------------------------------------------------------------

/// The trace id of one scenario's bundle: the first 16 bytes of the SHA-256 of the run id, the test class and the
/// scenario name, as lowercase hex.
///
/// Derived rather than random, so that replaying one transcript twice writes the same bundle, which is what lets a
/// golden bundle be compared with a replay. A scenario normally runs once in a run, so the three names identify its
/// trace; a second attempt shares the id and lives in its own bundle ([crate::bundle::bundle_dir]).
pub fn trace_id(run_id: &str, test_class: &str, scenario: &str) -> String {
    let digest = Sha256::new()
        .chain_update(run_id)
        .chain_update([0])
        .chain_update(test_class)
        .chain_update([0])
        .chain_update(scenario)
        .finalize();
    hex::encode(&digest[..16])
}

/// The OTLP span id of a lane span id: the id plus one, as 16 lowercase hex digits. The scenario's root, lane id
/// 0, is therefore `0000000000000001`, and no span gets the all-zero id OTLP reserves for "none".
pub fn span_id(lane_id: u32) -> String {
    format!("{:016x}", u64::from(lane_id) + 1)
}

/// Whether this is a trace id this crate writes: 32 lowercase hex digits, not all zero.
///
/// Lowercase is this crate's rule rather than OTLP's, which accepts either case. The viewer joins log records to
/// spans by comparing the ids as strings, so one spelling is what makes that join exact.
pub fn is_valid_trace_id(id: &str) -> bool {
    is_valid_hex_id(id, 32)
}

/// Whether this is a span id this crate writes: 16 lowercase hex digits, not all zero.
pub fn is_valid_span_id(id: &str) -> bool {
    is_valid_hex_id(id, 16)
}

fn is_valid_hex_id(id: &str, length: usize) -> bool {
    id.len() == length && id.bytes().all(|byte| matches!(byte, b'0'..=b'9' | b'a'..=b'f')) && id.bytes().any(|byte| byte != b'0')
}

// --- values --------------------------------------------------------------------------------------------------

/// One attribute value. OTLP JSON writes it as an object with exactly one field, which is serde's externally
/// tagged enum: `{"stringValue":"x"}`. An object with none or two is refused by the decoder.
#[derive(Serialize, Deserialize, Clone, PartialEq, Debug)]
#[serde(rename_all = "camelCase")]
pub enum AnyValue {
    StringValue(String),
    BoolValue(bool),
    IntValue(I64String),
    DoubleValue(f64),
    ArrayValue(ArrayValue),
    KvlistValue(KeyValueList),
    /// Base64, the one binary field whose encoding OTLP JSON leaves at the proto3 default.
    BytesValue(String),
}

/// An OTLP array of values.
#[derive(Serialize, Deserialize, Clone, PartialEq, Debug, Default)]
#[serde(default, deny_unknown_fields)]
pub struct ArrayValue {
    pub values: Vec<AnyValue>,
}

/// An OTLP map, as an ordered list of pairs.
#[derive(Serialize, Deserialize, Clone, PartialEq, Debug, Default)]
#[serde(default, deny_unknown_fields)]
pub struct KeyValueList {
    pub values: Vec<KeyValue>,
}

/// One attribute.
#[derive(Serialize, Deserialize, Clone, PartialEq, Debug)]
#[serde(deny_unknown_fields)]
pub struct KeyValue {
    pub key: String,
    pub value: AnyValue,
}

impl KeyValue {
    pub fn string(key: impl Into<String>, value: impl Into<String>) -> Self {
        Self {
            key: key.into(),
            value: AnyValue::StringValue(value.into()),
        }
    }

    pub fn int(key: impl Into<String>, value: i64) -> Self {
        Self {
            key: key.into(),
            value: AnyValue::IntValue(I64String(value)),
        }
    }

    pub fn bool(key: impl Into<String>, value: bool) -> Self {
        Self {
            key: key.into(),
            value: AnyValue::BoolValue(value),
        }
    }

    /// An array of integers, the shape of a bounds attribute.
    pub fn int_array(key: impl Into<String>, values: &[i64]) -> Self {
        let values = values.iter().map(|value| AnyValue::IntValue(I64String(*value))).collect();
        Self {
            key: key.into(),
            value: AnyValue::ArrayValue(ArrayValue { values }),
        }
    }
}

/// The value of the first attribute with this key.
pub fn lookup<'a>(attributes: &'a [KeyValue], key: &str) -> Option<&'a AnyValue> {
    attributes
        .iter()
        .find(|attribute| attribute.key == key)
        .map(|attribute| &attribute.value)
}

/// The string value of the first attribute with this key, or `None` when it is absent or not a string.
pub fn lookup_str<'a>(attributes: &'a [KeyValue], key: &str) -> Option<&'a str> {
    match lookup(attributes, key) {
        Some(AnyValue::StringValue(value)) => Some(value),
        _ => None,
    }
}

/// The integer value of the first attribute with this key, or `None` when it is absent or not an integer.
pub fn lookup_int(attributes: &[KeyValue], key: &str) -> Option<i64> {
    match lookup(attributes, key) {
        Some(AnyValue::IntValue(value)) => Some(value.0),
        _ => None,
    }
}

fn validate_attributes(attributes: &[KeyValue], owner: &str) -> Result<(), Error> {
    for attribute in attributes {
        if attribute.key.is_empty() {
            refuse!("{owner} has an attribute without a key");
        }
        validate_value(&attribute.value)?;
    }
    Ok(())
}

fn validate_value(value: &AnyValue) -> Result<(), Error> {
    match value {
        AnyValue::ArrayValue(array) => array.values.iter().try_for_each(validate_value),
        AnyValue::KvlistValue(list) => validate_attributes(&list.values, "a kvlist"),
        _ => Ok(()),
    }
}

// --- resource and scope --------------------------------------------------------------------------------------

/// What produced the telemetry: for a bundle, one lane run.
#[derive(Serialize, Deserialize, Clone, PartialEq, Debug, Default)]
#[serde(rename_all = "camelCase", default, deny_unknown_fields)]
pub struct Resource {
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub attributes: Vec<KeyValue>,
    #[serde(skip_serializing_if = "is_zero_u32")]
    pub dropped_attributes_count: u32,
}

/// Names the code that recorded the telemetry.
#[derive(Serialize, Deserialize, Clone, PartialEq, Debug, Default)]
#[serde(rename_all = "camelCase", default, deny_unknown_fields)]
pub struct InstrumentationScope {
    pub name: String,
    #[serde(skip_serializing_if = "String::is_empty")]
    pub version: String,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub attributes: Vec<KeyValue>,
    #[serde(skip_serializing_if = "is_zero_u32")]
    pub dropped_attributes_count: u32,
}

/// The bundle's `service.name`.
pub const SERVICE_NAME: &str = "air-ui-lane";

/// The `service.name` of the bundle's second resource: the IDE under test, whose own spans the recorder reads from
/// [crate::bridge::SPANS_ROUTE]. That resource carries the lane resource's other attributes too. Its spans keep the
/// IDE's instrumentation scopes, so a scope other than [SCOPE_NAME] appears only under it.
pub const IDE_SERVICE_NAME: &str = "air-ui-lane-ide";

/// The [attr::SPAN_KIND] of a span the IDE ended. Only the recorder writes it. It is not a
/// [crate::protocol::SpanKind], because the lane never opens such a span.
pub const IDE_SPAN_KIND: &str = "ide";

/// The instrumentation scope every span and record of a bundle is written under, and
/// [crate::bundle::MANIFEST_SCHEMA] is its version.
pub const SCOPE_NAME: &str = "air-trace";

// --- spans ---------------------------------------------------------------------------------------------------

/// One line of `spans.jsonl`.
#[derive(Serialize, Deserialize, Clone, PartialEq, Debug, Default)]
#[serde(rename_all = "camelCase", default, deny_unknown_fields)]
pub struct TracesData {
    pub resource_spans: Vec<ResourceSpans>,
}

/// The spans of one resource.
#[derive(Serialize, Deserialize, Clone, PartialEq, Debug, Default)]
#[serde(rename_all = "camelCase", default, deny_unknown_fields)]
pub struct ResourceSpans {
    pub resource: Resource,
    pub scope_spans: Vec<ScopeSpans>,
    #[serde(skip_serializing_if = "String::is_empty")]
    pub schema_url: String,
}

/// The spans of one instrumentation scope.
#[derive(Serialize, Deserialize, Clone, PartialEq, Debug, Default)]
#[serde(rename_all = "camelCase", default, deny_unknown_fields)]
pub struct ScopeSpans {
    pub scope: InstrumentationScope,
    pub spans: Vec<Span>,
    #[serde(skip_serializing_if = "String::is_empty")]
    pub schema_url: String,
}

/// OTLP's span kind, an integer in the JSON encoding. It is not [crate::protocol::SpanKind], which is what the
/// span stands for in the scenario.
#[derive(Serialize, Deserialize, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, Debug, Default)]
#[serde(transparent)]
pub struct SpanKindCode(pub i32);

impl SpanKindCode {
    pub const UNSPECIFIED: Self = Self(0);
    /// Every span of a bundle: none of them is a remote call.
    pub const INTERNAL: Self = Self(1);
    pub const SERVER: Self = Self(2);
    pub const CLIENT: Self = Self(3);
    pub const PRODUCER: Self = Self(4);
    pub const CONSUMER: Self = Self(5);

    fn is_unspecified(&self) -> bool {
        *self == Self::UNSPECIFIED
    }
}

/// OTLP's span status code.
#[derive(Serialize, Deserialize, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, Debug, Default)]
#[serde(transparent)]
pub struct StatusCode(pub i32);

impl StatusCode {
    pub const UNSET: Self = Self(0);
    pub const OK: Self = Self(1);
    /// Marks a failed span. The failure itself is an [event::EXCEPTION] log record on the span.
    pub const ERROR: Self = Self(2);

    fn is_unset(&self) -> bool {
        *self == Self::UNSET
    }
}

/// The OTLP status code a span with this lane status is written with. An aborted span reached no verdict, so it
/// is left unset rather than called an error.
pub const fn status_code_of(status: Status) -> StatusCode {
    match status {
        Status::Passed => StatusCode::OK,
        Status::Failed => StatusCode::ERROR,
        Status::Aborted => StatusCode::UNSET,
    }
}

/// A span's OTLP status.
#[derive(Serialize, Deserialize, Clone, PartialEq, Eq, Debug, Default)]
#[serde(default, deny_unknown_fields)]
pub struct SpanStatus {
    #[serde(skip_serializing_if = "String::is_empty")]
    pub message: String,
    #[serde(skip_serializing_if = "StatusCode::is_unset")]
    pub code: StatusCode,
}

/// One OTLP span.
#[derive(Serialize, Deserialize, Clone, PartialEq, Debug, Default)]
#[serde(rename_all = "camelCase", default, deny_unknown_fields)]
pub struct Span {
    pub trace_id: String,
    pub span_id: String,
    #[serde(skip_serializing_if = "String::is_empty")]
    pub trace_state: String,
    #[serde(skip_serializing_if = "String::is_empty")]
    pub parent_span_id: String,
    #[serde(skip_serializing_if = "is_zero_u32")]
    pub flags: u32,
    pub name: String,
    #[serde(skip_serializing_if = "SpanKindCode::is_unspecified")]
    pub kind: SpanKindCode,
    pub start_time_unix_nano: UnixNano,
    pub end_time_unix_nano: UnixNano,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub attributes: Vec<KeyValue>,
    #[serde(skip_serializing_if = "is_zero_u32")]
    pub dropped_attributes_count: u32,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub status: Option<SpanStatus>,
}

// --- log records ---------------------------------------------------------------------------------------------

/// One line of `logs.jsonl`.
#[derive(Serialize, Deserialize, Clone, PartialEq, Debug, Default)]
#[serde(rename_all = "camelCase", default, deny_unknown_fields)]
pub struct LogsData {
    pub resource_logs: Vec<ResourceLogs>,
}

/// The log records of one resource.
#[derive(Serialize, Deserialize, Clone, PartialEq, Debug, Default)]
#[serde(rename_all = "camelCase", default, deny_unknown_fields)]
pub struct ResourceLogs {
    pub resource: Resource,
    pub scope_logs: Vec<ScopeLogs>,
    #[serde(skip_serializing_if = "String::is_empty")]
    pub schema_url: String,
}

/// The log records of one instrumentation scope.
#[derive(Serialize, Deserialize, Clone, PartialEq, Debug, Default)]
#[serde(rename_all = "camelCase", default, deny_unknown_fields)]
pub struct ScopeLogs {
    pub scope: InstrumentationScope,
    pub log_records: Vec<LogRecord>,
    #[serde(skip_serializing_if = "String::is_empty")]
    pub schema_url: String,
}

/// OTLP's log severity.
#[derive(Serialize, Deserialize, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, Debug, Default)]
#[serde(transparent)]
pub struct SeverityNumber(pub i32);

impl SeverityNumber {
    pub const UNSPECIFIED: Self = Self(0);
    pub const INFO: Self = Self(9);
    pub const WARN: Self = Self(13);
    pub const ERROR: Self = Self(17);

    fn is_unspecified(&self) -> bool {
        *self == Self::UNSPECIFIED
    }
}

/// One OTLP log record. Every record of a bundle is an event: it has an event name and is correlated with a span
/// by its trace and span id.
#[derive(Serialize, Deserialize, Clone, PartialEq, Debug, Default)]
#[serde(rename_all = "camelCase", default, deny_unknown_fields)]
pub struct LogRecord {
    #[serde(skip_serializing_if = "UnixNano::is_zero")]
    pub time_unix_nano: UnixNano,
    #[serde(skip_serializing_if = "UnixNano::is_zero")]
    pub observed_time_unix_nano: UnixNano,
    #[serde(skip_serializing_if = "SeverityNumber::is_unspecified")]
    pub severity_number: SeverityNumber,
    #[serde(skip_serializing_if = "String::is_empty")]
    pub severity_text: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub body: Option<AnyValue>,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub attributes: Vec<KeyValue>,
    #[serde(skip_serializing_if = "is_zero_u32")]
    pub dropped_attributes_count: u32,
    #[serde(skip_serializing_if = "is_zero_u32")]
    pub flags: u32,
    #[serde(skip_serializing_if = "String::is_empty")]
    pub trace_id: String,
    #[serde(skip_serializing_if = "String::is_empty")]
    pub span_id: String,
    #[serde(skip_serializing_if = "String::is_empty")]
    pub event_name: String,
}

/// The attribute keys. The ones that are not `air.*` are OpenTelemetry semantic conventions, so a generic OTLP
/// viewer shows them where it shows every other service's.
pub mod attr {
    // The resource.
    pub const SERVICE_NAME: &str = "service.name";
    pub const RUN_ID: &str = "air.run.id";
    pub const LANE: &str = "air.lane";
    pub const LAUNCHER: &str = "air.launcher";
    pub const HOST_NAME: &str = "host.name";
    /// `linux`, `darwin` or `windows`, as the semantic conventions spell them.
    pub const OS_TYPE: &str = "os.type";

    // The scenario's root span. The flags and the program are JSON text rather than an OTLP map, so a generic
    // viewer shows them as one readable value and the trace viewer parses them.
    pub const SCENARIO_NAME: &str = "air.scenario.name";
    pub const TEST_CLASS: &str = "air.test.class";
    pub const SUITE: &str = "air.suite";
    pub const FLOW_ID: &str = "air.flow.id";
    pub const FIXTURE: &str = "air.fixture";
    /// The scenario's registry flags, a JSON object of strings.
    pub const FLAGS: &str = "air.flags";
    /// The generated profile, the scenario command's program verbatim.
    pub const PROGRAM: &str = "air.program";

    // Every other span.
    /// A lane [crate::protocol::SpanKind], or [super::IDE_SPAN_KIND] on a span of the IDE.
    pub const SPAN_KIND: &str = "air.span.kind";
    pub const SPAN_KEY: &str = "air.span.key";
    /// Only on an assertion span.
    pub const EXPECTATION: &str = "air.assertion.expectation";
    /// The lane's status of a span, the root included. It is on every span because the OTLP status code cannot
    /// hold it: OTLP has no word for aborted. [super::status_code_of] is the code written beside it.
    pub const SPAN_STATUS: &str = "air.span.status";

    // What the recorder adds to a span of the IDE. The bundle has one trace, so an IDE span is written into it with
    // its own span id, and these keep what that move would lose.
    /// The IDE's own trace id of the span.
    pub const IDE_TRACE_ID: &str = "air.ide.trace.id";
    /// The IDE's parent span id, written only when that parent is not in the bundle. The span is then the child of
    /// the innermost lane span that contains its start.
    pub const IDE_SPAN_PARENT: &str = "air.ide.span.parent";

    // [super::event::SPAN_STARTED].
    /// The span's name, which the span itself carries only once it ends.
    pub const SPAN_TITLE: &str = "air.span.title";
    /// The OTLP id of the enclosing span.
    pub const SPAN_PARENT: &str = "air.span.parent";

    // [super::event::SNAPSHOT].
    pub const SNAPSHOT_PHASE: &str = "air.snapshot.phase";
    pub const SNAPSHOT_ORDINAL: &str = "air.snapshot.ordinal";
    /// The picture's bundle path: the [crate::bundle::snap_image_path] of this snapshot, or of the previous
    /// picture when the screen did not change since. Absent when there is no picture.
    pub const SNAPSHOT_IMAGE: &str = "air.snapshot.image";
    /// The tree's bundle path, [crate::bundle::snap_tree_path]. Absent when there is no tree.
    pub const SNAPSHOT_TREE: &str = "air.snapshot.tree";
    pub const SNAPSHOT_SOURCE: &str = "air.snapshot.source";
    /// Why the picture or the tree is missing.
    pub const SNAPSHOT_ERROR: &str = "air.snapshot.error";

    // [super::event::INPUT].
    /// The input's name in the bridge protocol, such as `click` or `replaceText`.
    pub const INPUT_GESTURE: &str = "air.input.gesture";
    pub const INPUT_X: &str = "air.input.x";
    pub const INPUT_Y: &str = "air.input.y";
    /// The epoch ms of [crate::bridge::Input::point_at_ms]. Written together with the point.
    pub const INPUT_POINT_AT_MS: &str = "air.input.point_at_ms";
    /// The target's screen bounds, an array of x, y, width and height.
    pub const INPUT_TARGET_BOUNDS: &str = "air.input.target.bounds";
    pub const INPUT_TARGET_COMPONENT: &str = "air.input.target.component";

    // [super::event::BRIDGE_CALL].
    pub const CALL_REQUEST: &str = "air.call.request";
    pub const CALL_DURATION_MS: &str = "air.call.duration_ms";
    pub const CALL_OK: &str = "air.call.ok";
    pub const CALL_ERROR: &str = "air.call.error";

    // [super::event::VIDEO].
    pub const VIDEO_CODEC: &str = "air.video.codec";
    pub const VIDEO_FILE: &str = "air.video.file";
    pub const VIDEO_INDEX: &str = "air.video.index";
    pub const VIDEO_REASON: &str = "air.video.reason";

    // [super::event::DRIVER_STEP]. Nested steps are flattened in pre-order: the id counts from 1 in that order, and
    // the parent is the enclosing step's id, 0 at the top.
    pub const DRIVER_STEP_NAME: &str = "air.driver.step.name";
    pub const DRIVER_STEP_STATUS: &str = "air.driver.step.status";
    pub const DRIVER_STEP_DURATION_MS: &str = "air.driver.step.duration_ms";
    pub const DRIVER_STEP_ID: &str = "air.driver.step.id";
    pub const DRIVER_STEP_PARENT: &str = "air.driver.step.parent";

    // [super::event::EXCEPTION], from the semantic conventions.
    pub const EXCEPTION_TYPE: &str = "exception.type";
    pub const EXCEPTION_MESSAGE: &str = "exception.message";
    pub const EXCEPTION_STACKTRACE: &str = "exception.stacktrace";

    // [super::event::TRACE_ERROR].
    /// What failed: `x11`, `ide-paint`, `bridge.facts`, `bridge.tree`, `bridge.spans`, `video` (the video encoder or
    /// the screen recording), `webp` (a still the encoder could not write), `idea.log` or `protocol`.
    pub const TRACE_ERROR_SOURCE: &str = "air.trace.error.source";
    pub const TRACE_ERROR_MESSAGE: &str = "air.trace.error.message";
}

/// The event names, each a [LogRecord::event_name].
pub mod event {
    /// Written when a span opens. Spans reach `spans.jsonl` only when they end, so this is how a truncated bundle
    /// still names the spans that were running.
    pub const SPAN_STARTED: &str = "air.span.started";
    /// One snapshot: its phase, picture, tree and source.
    pub const SNAPSHOT: &str = "air.snapshot";
    /// One input the bridge performed: gesture, point, and the target's bounds and component.
    pub const INPUT: &str = "air.input";
    /// One bridge call the lane made, timed at its start.
    pub const BRIDGE_CALL: &str = "air.bridge.call";
    /// Written when the video starts, at its first frame's time, or when it cannot start.
    pub const VIDEO: &str = "air.video";
    /// One Allure step of the Driver, timed at its start and correlated with the innermost span that contains that
    /// start. The steps arrive only at the scenario's end, when every span's interval is known, so the recorder can
    /// make that choice once instead of every reader making it.
    pub const DRIVER_STEP: &str = "air.driver.step";
    /// A span's failure, spelled with the semantic conventions' event name and attributes.
    pub const EXCEPTION: &str = "exception";
    /// Evidence that could not be collected: a capture, a bridge route, the encoder, the log slice, or a lane
    /// command the recorder refused. It never changes a verdict; it is how a missing picture explains itself.
    pub const TRACE_ERROR: &str = "air.trace.error";
}

// --- decoding and checking -----------------------------------------------------------------------------------

/// Reads one line of `spans.jsonl`, refusing a field OTLP does not declare.
pub fn decode_traces_line(line: &[u8]) -> Result<TracesData, Error> {
    serde_json::from_slice(line).map_err(|error| Error::new(format!("a {SPANS_FILE} line is not OTLP TracesData: {error}")))
}

/// Reads one line of `logs.jsonl`, refusing a field OTLP does not declare.
pub fn decode_logs_line(line: &[u8]) -> Result<LogsData, Error> {
    serde_json::from_slice(line).map_err(|error| Error::new(format!("a {LOGS_FILE} line is not OTLP LogsData: {error}")))
}

impl TracesData {
    /// Refuses spans this crate would not have written: an id that is not lowercase hex, a span without a name or
    /// ending before it starts, a kind or status code OTLP does not name, a malformed attribute.
    pub fn validate(&self) -> Result<(), Error> {
        if self.resource_spans.is_empty() {
            refuse!("a spans line holds no resource");
        }
        for resource in &self.resource_spans {
            validate_attributes(&resource.resource.attributes, "a resource")?;
            for scope in &resource.scope_spans {
                if scope.scope.name.is_empty() {
                    refuse!("a spans line has a scope without a name");
                }
                scope.spans.iter().try_for_each(Span::validate)?;
            }
        }
        Ok(())
    }
}

impl Span {
    fn validate(&self) -> Result<(), Error> {
        if !is_valid_trace_id(&self.trace_id) || !is_valid_span_id(&self.span_id) {
            refuse!(
                "the span {:?} has the trace id {:?} and the span id {:?}",
                self.name,
                self.trace_id,
                self.span_id
            );
        }
        if !self.parent_span_id.is_empty() && !is_valid_span_id(&self.parent_span_id) {
            refuse!("the span {:?} has the parent id {:?}", self.name, self.parent_span_id);
        }
        if self.name.is_empty() {
            refuse!("the span {} has no name", self.span_id);
        }
        if self.start_time_unix_nano.is_zero() || self.end_time_unix_nano < self.start_time_unix_nano {
            refuse!(
                "the span {:?} runs from {} to {}",
                self.name,
                self.start_time_unix_nano.0,
                self.end_time_unix_nano.0
            );
        }
        if !(SpanKindCode::UNSPECIFIED..=SpanKindCode::CONSUMER).contains(&self.kind) {
            refuse!("the span {:?} has the kind {}", self.name, self.kind.0);
        }
        if let Some(status) = &self.status
            && !(StatusCode::UNSET..=StatusCode::ERROR).contains(&status.code)
        {
            refuse!("the span {:?} has the status code {}", self.name, status.code.0);
        }
        validate_attributes(&self.attributes, &format!("the span {:?}", self.name))
    }
}

impl LogsData {
    /// Refuses log records this crate would not have written: one without a time, an event name or a span it
    /// belongs to, or with a malformed attribute.
    pub fn validate(&self) -> Result<(), Error> {
        if self.resource_logs.is_empty() {
            refuse!("a logs line holds no resource");
        }
        for resource in &self.resource_logs {
            validate_attributes(&resource.resource.attributes, "a resource")?;
            for scope in &resource.scope_logs {
                if scope.scope.name.is_empty() {
                    refuse!("a logs line has a scope without a name");
                }
                scope.log_records.iter().try_for_each(LogRecord::validate)?;
            }
        }
        Ok(())
    }
}

impl LogRecord {
    fn validate(&self) -> Result<(), Error> {
        if self.event_name.is_empty() {
            refuse!("a log record has no event name");
        }
        if self.time_unix_nano.is_zero() {
            refuse!("the {} record has no time", self.event_name);
        }
        if !is_valid_trace_id(&self.trace_id) || !is_valid_span_id(&self.span_id) {
            refuse!(
                "the {} record has the trace id {:?} and the span id {:?}",
                self.event_name,
                self.trace_id,
                self.span_id
            );
        }
        if let Some(body) = &self.body {
            validate_value(body).map_err(|error| Error::new(format!("the {} record's body: {error}", self.event_name)))?;
        }
        validate_attributes(&self.attributes, &format!("the {} record", self.event_name))
    }
}

#[cfg(test)]
mod tests;
