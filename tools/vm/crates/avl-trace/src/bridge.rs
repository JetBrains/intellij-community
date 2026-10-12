//! The trace routes of the in-IDE bridge: what the recorder asks the IDE under test, and what it answers.
//!
//! The far end is `AirUiTraceHttpHandler` in `plugins/air/tests/integration/bridge/tests`, registered beside
//! `AirUiTestHttpHandler` on the same built-in-server port and guarded the same way: loopback only, and the
//! launch's token in [TOKEN_HEADER]. The port and the token reach the recorder in
//! [crate::protocol::HelloCommand::bridge].
//!
//! Only what needs the IDE's own process: the Swing tree, a paint, and the OpenTelemetry spans the IDE ended exist
//! nowhere else. Everything the recorder can learn from outside, the X server's pixels and the log file's bytes, it
//! reads itself, so that capturing a frame costs the IDE's event thread nothing.

use std::collections::BTreeMap;
use std::fmt;

use monostate::MustBeBool;
use serde::de::{self, Visitor};
use serde::{Deserialize, Deserializer, Serialize};

use crate::otlp::{AnyValue, I64String, KeyValue, is_valid_span_id, is_valid_trace_id};
use crate::{Error, is_false, is_zero_i64};

/// Carries the launch's UI-test token. The Kotlin side spells it `AIR_UI_TEST_HTTP_TOKEN_HEADER`.
pub const TOKEN_HEADER: &str = "X-Air-Ui-Test-Token";

/// Carries how many milliseconds the recorder still waits for the answer, a decimal integer. A snapshot's budget
/// covers the grab, or the paint, and the tree read together, so a tree request arrives with part of it spent; the
/// bridge skips an event-thread read that would start after this, rather than walk the whole tree for a recorder
/// that has stopped waiting. The Kotlin side spells it `AIR_UI_TRACE_WITHIN_HEADER`, and a request without it gets
/// the whole budget there.
pub const WITHIN_HEADER: &str = "X-Air-Trace-Within-Ms";

// The routes. Each is a GET with no body and no query.

/// The Kotlin side's `AIR_UI_TEST_TRACE_PREFIX` with its leading slash. It is not under the request endpoint's
/// `api/air/ui-test` prefix, because the platform's prefix check needs a `/` or `?` after a prefix, so the two
/// handlers cannot claim each other's requests.
pub const ROUTE_PREFIX: &str = "/api/air/ui-test-trace";
/// Answers [Facts].
pub const FACTS_ROUTE: &str = "/api/air/ui-test-trace/facts";
/// Answers [Tree], and drains the input log as it does.
pub const TREE_ROUTE: &str = "/api/air/ui-test-trace/tree";
/// Answers a PNG of the whole [Facts::screen], one pixel per screen point with no HiDPI scale, every showing window
/// drawn at its screen bounds. So pixel (0,0) is the top-left corner of the screen, which on one display is the
/// screen point (0,0) exactly as in an X11 root window, and a tree node's bounds need no scaling to be drawn over
/// it. It is only asked for where X11 capture is unavailable, since painting runs on the event thread. A screen
/// larger than 8K, or no showing window, answers an error rather than a picture.
pub const PAINT_ROUTE: &str = "/api/air/ui-test-trace/paint";
/// The paint route's content type.
pub const PAINT_CONTENT_TYPE: &str = "image/png";
/// Answers [IdeSpans]: the spans the IDE exported since the recorder's last read of either spans route. The read
/// moves the recorder's cursor, as a tree read drains the input log. It costs the IDE nothing it does not already
/// do, so the recorder asks it at every snapshot.
pub const SPANS_ROUTE: &str = "/api/air/ui-test-trace/spans";
/// Answers the same, after the IDE exported every span that ended before the read. The IDE exports a batch only
/// when 512 spans wait or a minute passes, so the recorder asks this route when the IDE is about to restart and when
/// a scenario ends. A flush can take seconds, so a snapshot never asks it.
pub const FLUSHED_SPANS_ROUTE: &str = "/api/air/ui-test-trace/spans/flushed";

/// A rectangle in screen coordinates.
#[derive(Serialize, Deserialize, Clone, Copy, PartialEq, Eq, Debug, Default)]
#[serde(deny_unknown_fields)]
pub struct Rect {
    pub x: i32,
    pub y: i32,
    pub width: i32,
    pub height: i32,
}

/// A point in screen coordinates.
#[derive(Serialize, Deserialize, Clone, Copy, PartialEq, Eq, Debug, Default)]
#[serde(deny_unknown_fields)]
pub struct Point {
    pub x: i32,
    pub y: i32,
}

/// What the recorder needs to know about the IDE once per scenario: at its start, and again at its end, so the
/// log slice is the bytes written in between.
#[derive(Serialize, Deserialize, Clone, PartialEq, Eq, Debug)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct Facts {
    pub pid: i64,
    /// The IDE's `idea.log`, from `PathManager.getLogPath()`.
    pub log_path: String,
    /// The log's size in bytes when the facts were read. A size smaller than the one at the start means the log
    /// was rotated in between, and the slice is taken from the start of the new file.
    pub log_size: u64,
    /// The IDE's `DISPLAY`, absent where there is none. It is how the recorder knows which X server to capture.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub display: Option<String>,
    /// The union of every screen device's bounds.
    pub screen: Rect,
    /// The [crate::otlp::attr::OS_TYPE] of the IDE's machine.
    pub os: String,
}

/// Reads the facts route's answer.
pub fn decode_facts(document: &[u8]) -> Result<Facts, Error> {
    let facts: Facts =
        serde_json::from_slice(document).map_err(|error| Error::new(format!("the facts route answered outside the contract: {error}")))?;
    if facts.pid <= 0 || facts.log_path.is_empty() || facts.os.is_empty() {
        refuse!(
            "the facts route answered the pid {}, the log {:?} of {} bytes and the os {:?}",
            facts.pid,
            facts.log_path,
            facts.log_size,
            facts.os
        );
    }
    Ok(facts)
}

/// The showing Swing hierarchy at one moment, and the inputs the bridge performed since the previous tree was
/// read. It is also the format of a snapshot's `snap/NNNN.tree.json`.
#[derive(Serialize, Deserialize, Clone, PartialEq, Eq, Debug)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct Tree {
    pub captured_at_ms: i64,
    /// Every showing window, in the platform's order. An IDE with none answers an empty list; a tree without the
    /// list is refused.
    pub windows: Vec<Window>,
    /// The drained input log, oldest first. The log is a ring, so a long gap between two reads can lose the oldest
    /// entries; the viewer shows what is there.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub inputs: Vec<Input>,
}

/// One showing window.
#[derive(Serialize, Deserialize, Clone, PartialEq, Eq, Debug)]
#[serde(deny_unknown_fields)]
pub struct Window {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub title: Option<String>,
    /// The window's fully qualified class.
    pub class: String,
    pub bounds: Rect,
    pub root: Node,
}

/// The [Input::gesture] of a mouse press that the IDE dispatched outside every bridge gesture. A posted click
/// dispatches after its gesture returned, and a Remote Driver gesture never goes through the bridge, so both reach
/// the log only this way. The Kotlin side spells it `AIR_UI_TRACE_PRESS_GESTURE`.
pub const GESTURE_PRESS: &str = "press";

/// One input the bridge performed, or one [GESTURE_PRESS].
#[derive(Serialize, Deserialize, Clone, PartialEq, Eq, Debug)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct Input {
    pub at_ms: i64,
    /// The input's name in the bridge protocol, the `@SerialName` of its `AirUiTestTargetInput`, or
    /// [GESTURE_PRESS].
    pub gesture: String,
    /// Where the pointer went, absent for a keyboard-only gesture.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub point: Option<Point>,
    /// When the IDE dispatched the pointer event that gave [Input::point], present exactly when the point is. The
    /// lanes post synthetic events, so the X pointer never moves and the video shows no pointer. The viewer draws
    /// the point on the video at this time instead.
    #[serde(default, skip_serializing_if = "is_zero_i64")]
    pub point_at_ms: i64,
    pub target: InputTarget,
}

/// The component an input was aimed at.
#[derive(Serialize, Deserialize, Clone, PartialEq, Eq, Debug)]
#[serde(deny_unknown_fields)]
pub struct InputTarget {
    pub bounds: Rect,
    /// The target as the bridge's `airUiTraceTargetLabel` names it: its class, and what it says, cut to 200
    /// characters.
    pub component: String,
}

/// One showing component. The JSON names are single letters because a tree holds thousands of them and every
/// snapshot carries one; the bundle deflates them, and the viewer reads them through this type's names.
#[derive(Serialize, Deserialize, Clone, PartialEq, Eq, Debug, Default)]
#[serde(deny_unknown_fields)]
pub struct Node {
    /// The component's simple class name.
    #[serde(rename = "c")]
    pub class: String,
    /// Its fully qualified class, absent when it is the same as the simple name.
    #[serde(rename = "j", default, skip_serializing_if = "Option::is_none")]
    pub qualified_class: Option<String>,
    /// The component's name, which is where a test id lives.
    #[serde(rename = "n", default, skip_serializing_if = "Option::is_none")]
    pub name: Option<String>,
    /// The accessible name.
    #[serde(rename = "a", default, skip_serializing_if = "Option::is_none")]
    pub accessible_name: Option<String>,
    /// The semantic text the bridge's `airUiSemanticText` reads.
    #[serde(rename = "t", default, skip_serializing_if = "Option::is_none")]
    pub text: Option<String>,
    /// The screen rectangle, as x, y, width and height.
    #[serde(rename = "b")]
    pub bounds: [i32; 4],
    /// Written only as false: a component is enabled unless it says otherwise. The type refuses `true`.
    #[serde(rename = "e", default, skip_serializing_if = "Option::is_none")]
    pub disabled: Option<MustBeBool<false>>,
    /// Written only as true: only the focus owner says so. The type refuses `false`.
    #[serde(rename = "f", default, skip_serializing_if = "Option::is_none")]
    pub focused: Option<MustBeBool<true>>,
    #[serde(rename = "k", default, skip_serializing_if = "Vec::is_empty")]
    pub children: Vec<Self>,
}

impl Node {
    /// Whether the component is enabled.
    pub const fn is_enabled(&self) -> bool {
        self.disabled.is_none()
    }

    /// Whether the component is the focus owner.
    pub const fn is_focused(&self) -> bool {
        self.focused.is_some()
    }

    pub fn set_enabled(&mut self, enabled: bool) {
        self.disabled = (!enabled).then_some(MustBeBool);
    }

    pub fn set_focused(&mut self, focused: bool) {
        self.focused = focused.then_some(MustBeBool);
    }

    fn validate(&self) -> Result<(), Error> {
        if self.class.is_empty() {
            refuse!("a node has no class");
        }
        if self.bounds[2] < 0 || self.bounds[3] < 0 {
            refuse!("the {} node has the size {}x{}", self.class, self.bounds[2], self.bounds[3]);
        }
        self.children.iter().try_for_each(Self::validate)
    }
}

/// Reads the tree route's answer, or a snapshot's tree file.
pub fn decode_tree(document: &[u8]) -> Result<Tree, Error> {
    let tree: Tree =
        serde_json::from_slice(document).map_err(|error| Error::new(format!("a Swing tree does not fit the contract: {error}")))?;
    tree.validate()?;
    Ok(tree)
}

impl Tree {
    /// Refuses a tree this contract does not describe: a window without a class, a node without one, a negative
    /// size, or an input without its gesture, its target, or a point and its time together. An `e` written as true
    /// or an `f` written as false is refused by the decoder already.
    pub fn validate(&self) -> Result<(), Error> {
        if self.captured_at_ms <= 0 {
            refuse!("a Swing tree was captured at {}", self.captured_at_ms);
        }
        for window in &self.windows {
            if window.class.is_empty() {
                refuse!("a Swing tree has a window without a class");
            }
            window
                .root
                .validate()
                .map_err(|error| Error::new(format!("the window {:?}: {error}", window.title.as_deref().unwrap_or_default())))?;
        }
        for input in &self.inputs {
            if input.at_ms <= 0 || input.gesture.is_empty() || input.target.component.is_empty() {
                refuse!(
                    "a Swing tree has the input {:?} at {} on {:?}",
                    input.gesture,
                    input.at_ms,
                    input.target.component
                );
            }
            if input.point.is_none() != (input.point_at_ms == 0) || input.point_at_ms < 0 {
                refuse!(
                    "the input {:?} at {} has the point {:?} at {}, and a point and its time come together",
                    input.gesture,
                    input.at_ms,
                    input.point,
                    input.point_at_ms
                );
            }
        }
        Ok(())
    }
}

/// The answer of both spans routes: the spans that the IDE under test ended, oldest first.
///
/// The far end is the bridge's span log (`AirUiTestSpanLog`), which the platform fills in-process through an
/// `openTelemetryExporterProvider`. The same log answers a scenario check through the typed bridge protocol. The
/// recorder reads it here as evidence only, so a check never depends on a trace.
#[derive(Serialize, Deserialize, Clone, PartialEq, Debug, Default)]
#[serde(deny_unknown_fields)]
pub struct IdeSpans {
    /// Refused when absent, so that an answer without the list is not read as a quiet IDE.
    pub spans: Vec<IdeSpan>,
    /// Counts the spans the IDE's ring dropped before the recorder read them. The recorder writes the loss down as
    /// an [crate::otlp::event::TRACE_ERROR].
    #[serde(default)]
    pub dropped: u64,
}

/// One span the IDE ended, as the platform exported it. The ids are the IDE's own: they belong to the IDE's traces,
/// not to the bundle's.
#[derive(Serialize, Deserialize, Clone, PartialEq, Debug, Default)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct IdeSpan {
    /// The span's instrumentation scope, such as `air.sessionLog`.
    pub scope: String,
    pub name: String,
    pub trace_id: String,
    pub span_id: String,
    /// Absent for a root span.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub parent_span_id: Option<String>,
    /// Nanoseconds since the epoch, as JSON numbers, which a Kotlin `Long` and an `i64` both hold exactly.
    pub start_epoch_nanos: i64,
    pub end_epoch_nanos: i64,
    #[serde(default, skip_serializing_if = "BTreeMap::is_empty")]
    pub attributes: BTreeMap<String, IdeAttribute>,
    /// Says the span ended with the OpenTelemetry status ERROR.
    #[serde(default, skip_serializing_if = "is_false")]
    pub failed: bool,
}

/// One attribute of an [IdeSpan], which keeps its JSON type: a string, a number, or a boolean. A list attribute
/// arrives as its string. A number with a fraction or an exponent is a double, and so is an integer past `i64`;
/// every other number is an integer.
#[derive(Serialize, Clone, PartialEq, Debug)]
#[serde(untagged)]
pub enum IdeAttribute {
    String(String),
    Bool(bool),
    Int(i64),
    Double(f64),
}

impl IdeAttribute {
    /// The value as OTLP writes it.
    pub fn to_otlp(&self) -> AnyValue {
        match self {
            Self::String(value) => AnyValue::StringValue(value.clone()),
            Self::Bool(value) => AnyValue::BoolValue(*value),
            Self::Int(value) => AnyValue::IntValue(I64String(*value)),
            Self::Double(value) => AnyValue::DoubleValue(*value),
        }
    }
}

impl<'de> Deserialize<'de> for IdeAttribute {
    fn deserialize<D: Deserializer<'de>>(deserializer: D) -> Result<Self, D::Error> {
        deserializer.deserialize_any(IdeAttributeVisitor)
    }
}

/// Accepts a JSON scalar other than null. serde refuses every other type with [Visitor::expecting] as the reason.
struct IdeAttributeVisitor;

impl Visitor<'_> for IdeAttributeVisitor {
    type Value = IdeAttribute;

    fn expecting(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str("an IDE span attribute: a string, a number or a boolean")
    }

    fn visit_str<E: de::Error>(self, value: &str) -> Result<IdeAttribute, E> {
        Ok(IdeAttribute::String(value.to_owned()))
    }

    fn visit_string<E: de::Error>(self, value: String) -> Result<IdeAttribute, E> {
        Ok(IdeAttribute::String(value))
    }

    fn visit_bool<E: de::Error>(self, value: bool) -> Result<IdeAttribute, E> {
        Ok(IdeAttribute::Bool(value))
    }

    fn visit_i64<E: de::Error>(self, value: i64) -> Result<IdeAttribute, E> {
        Ok(IdeAttribute::Int(value))
    }

    fn visit_u64<E: de::Error>(self, value: u64) -> Result<IdeAttribute, E> {
        Ok(i64::try_from(value).map_or(IdeAttribute::Double(value as f64), IdeAttribute::Int))
    }

    fn visit_f64<E: de::Error>(self, value: f64) -> Result<IdeAttribute, E> {
        Ok(IdeAttribute::Double(value))
    }
}

impl IdeSpan {
    /// The span's attributes as OTLP values, sorted by key, so a bundle is the same every time.
    pub fn otlp_attributes(&self) -> Vec<KeyValue> {
        self.attributes
            .iter()
            .map(|(key, value)| KeyValue {
                key: key.clone(),
                value: value.to_otlp(),
            })
            .collect()
    }

    fn validate(&self) -> Result<(), Error> {
        if self.scope.is_empty() || self.name.is_empty() {
            refuse!("an IDE span has the scope {:?} and the name {:?}", self.scope, self.name);
        }
        let parent_valid = self.parent_span_id.as_deref().is_none_or(is_valid_span_id);
        if !is_valid_trace_id(&self.trace_id) || !is_valid_span_id(&self.span_id) || !parent_valid {
            refuse!(
                "the IDE span {:?} has the trace id {:?}, the span id {:?} and the parent id {:?}",
                self.name,
                self.trace_id,
                self.span_id,
                self.parent_span_id.as_deref().unwrap_or_default()
            );
        }
        if self.start_epoch_nanos <= 0 || self.end_epoch_nanos < self.start_epoch_nanos {
            refuse!(
                "the IDE span {:?} runs from {} to {}",
                self.name,
                self.start_epoch_nanos,
                self.end_epoch_nanos
            );
        }
        Ok(())
    }
}

/// Reads a spans route's answer.
pub fn decode_ide_spans(document: &[u8]) -> Result<IdeSpans, Error> {
    let spans: IdeSpans =
        serde_json::from_slice(document).map_err(|error| Error::new(format!("a spans route answered outside the contract: {error}")))?;
    spans.spans.iter().try_for_each(IdeSpan::validate)?;
    Ok(spans)
}

#[cfg(test)]
mod tests;
