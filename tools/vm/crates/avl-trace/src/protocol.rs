//! The lane protocol: what the lane writes to the recorder, one command per line, and the acks the recorder answers
//! with.
//!
//! A refusal is never a test failure. Evidence must not decide a verdict: the recorder answers a line it refuses
//! with a negative ack and records the refusal in the bundle as an [crate::otlp::event::TRACE_ERROR] log record,
//! and the lane never lets either reach a test. The recorder acks a refused line even when its op is not an acked
//! one, or cannot be read at all, because the lane may be waiting on it; [Ack::line] is what lets a lane that was not
//! waiting discard that ack.

use std::collections::BTreeMap;

use serde::de::DeserializeOwned;
use serde::{Deserialize, Deserializer, Serialize};
use serde_json::value::RawValue;

use crate::{Error, is_false, is_zero_u32};

// --- the vocabularies ----------------------------------------------------------------------------------------

vocabulary! {
    /// One command of the lane protocol: the command's `op` field, in the order a scenario first uses them.
    pub enum Op {
        /// Opens the session. It is the first line and is sent exactly once. One recorder process serves one
        /// root, so the daemon's per-iteration root means one recorder per iteration.
        Hello = "hello",
        /// Opens one scenario's bundle and its root span. Every command up to the matching [Op::Done] belongs to
        /// that scenario.
        Scenario = "scenario",
        Span = "span",
        End = "end",
        Snap = "snap",
        Call = "call",
        /// Hands the recorder a file the lane wrote under the trace root. The recorder moves it into the bundle.
        Attach = "attach",
        Restart = "restart",
        /// Closes the scenario, writes its manifest and answers only once the bundle is complete on disk.
        Done = "done",
    }
}

impl Op {
    /// Whether the lane waits for an ack line after sending this op.
    ///
    /// Four of them, and each has a reason to wait. The lane waits for the hello ack to learn whether anything
    /// will be captured, and for the scenario ack to know the bundle directory exists. It waits for a snap ack so
    /// the moment is captured before the next instruction runs, and for the done ack so the bundle is complete
    /// before the lane moves on or exits. Everything else is fire-and-forget: a span or a call record
    /// costs the scenario no time.
    pub const fn is_acked(self) -> bool {
        matches!(self, Self::Hello | Self::Scenario | Self::Snap | Self::Done)
    }
}

vocabulary! {
    /// What a span stands for in the scenario: the `air.span.kind` attribute.
    pub enum SpanKind {
        /// The verified state reset on either side of a scenario.
        Reset = "reset",
        /// One setup operation of a generated profile, run before its first flow step.
        Setup = "setup",
        /// One flow step of a generated profile.
        Step = "step",
        /// One generated operation, the unit a budget is applied to.
        Operation = "operation",
        /// One action instruction.
        Action = "action",
        /// One assertion instruction. It is the only kind that carries an expectation.
        Assertion = "assertion",
        /// One step of a hand-authored journey, which has no generated program.
        Journey = "journey",
        /// An IDE relaunch: a restart a scenario asked for, or a recycle of an IDE the lane cannot drive.
        Restart = "restart",
        /// One part of an IDE relaunch: the quit, the launch, the readiness gate, or a part of one of them.
        Relaunch = "relaunch",
    }
}

impl SpanKind {
    /// Whether a span of this kind runs one instruction of a generated program: an action or an assertion. Only such
    /// a span states the [Side] of its id.
    pub const fn is_instruction(self) -> bool {
        matches!(self, Self::Action | Self::Assertion)
    }
}

vocabulary! {
    /// Where the id of an instruction runs, as the suite document of its scenario states it.
    pub enum Side {
        /// In the lane IDE, through the bridge.
        Ide = "ide",
        /// On the host of the lane, against a lane service or the IDE process.
        Host = "host",
    }
}

/// The stable key of a span, the `air.span.key` attribute: `<kind>:<id>`, where the id is the flow step, operation
/// or instruction id, `before` or `after` for a reset, `ide` or `recycle` for a restart, the part for a relaunch,
/// such as `quit/exit`, and the journey step's title for a journey.
///
/// It is how two runs of one scenario are compared step by step, so it must not contain anything that differs
/// between runs: no counter, no timestamp, no path. The kind prefix keeps a setup and the operation it runs apart,
/// since they share an id.
pub fn span_key(kind: SpanKind, id: &str) -> String {
    format!("{kind}:{id}")
}

vocabulary! {
    /// Why a snapshot was taken.
    pub enum SnapshotPhase {
        /// Taken at an instruction boundary: before every instruction, inside its span, and once more at the end
        /// of every operation, inside the operation's span. The Before picture of instruction k is the boundary
        /// taken as k started, and its After picture is the next boundary.
        Boundary = "boundary",
        /// Taken the moment an assertion held, inside the assertion's span.
        Check = "check",
        /// Taken when a span fails, before anything cleans up after it.
        Failure = "failure",
        /// Taken after each step of a hand-authored journey.
        Journey = "journey",
    }
}

vocabulary! {
    /// How a span or a scenario ended, as the lane saw it.
    pub enum Status {
        Passed = "passed",
        Failed = "failed",
        /// A span or scenario that did not run to a verdict: a JUnit assumption, a scenario the isolation skipped,
        /// or a span its budget cancelled.
        Aborted = "aborted",
    }
}

vocabulary! {
    /// What started the lane. It decides where the bundles go. The owner of that root cleans it up, not the recorder.
    pub enum Launcher {
        /// The lane daemon, which roots each iteration at `<iterationDir>/air-traces`.
        Daemon = "daemon",
        /// A Bazel test, which roots the bundles in `$TEST_UNDECLARED_OUTPUTS_DIR/air-traces`.
        Bazel = "bazel",
    }
}

vocabulary! {
    /// Where a scenario's pixels came from, best first: the order the recorder tries them.
    pub enum CaptureSource {
        /// Reads the X server directly: real pixels, native popups and the cursor, at no cost to the IDE's event
        /// thread. It is the Linux guest's `Xvfb` or a Linux host.
        X11 = "x11",
        /// Asks the bridge to paint the showing windows. It sees no native popup and no cursor, and it costs the
        /// event thread a paint, so it is used only where X11 is not available.
        IdePaint = "ide-paint",
        /// Records no pixels at all. The reason says why.
        None = "none",
    }
}

vocabulary! {
    /// How a scenario's video is encoded.
    pub enum VideoCodec {
        /// H.264 in fragmented MP4: the one codec with hardware decode in both Chrome and Safari on every Mac, and a
        /// container that survives a killed encoder.
        H264 = "h264",
        /// The bundle has no video. The reason says why.
        None = "none",
    }
}

vocabulary! {
    /// Which scenarios keep their video after they end: the `keepVideo` field of the hello.
    #[derive(Default)]
    pub enum VideoRetention {
        /// Every scenario keeps its video. A hello without the field means this.
        #[default]
        Always = "always",
        /// Only a scenario that did not pass keeps its video: a failed, an aborted or a truncated one. The recorder
        /// records the video of a passed scenario too, and deletes it at the end of the scenario.
        Failed = "failed",
    }
}

impl VideoRetention {
    const fn is_always(&self) -> bool {
        matches!(self, Self::Always)
    }
}

// --- the commands --------------------------------------------------------------------------------------------

/// Where the recorder reaches the IDE's trace routes: the UI-test bridge's port and token.
#[derive(Serialize, Deserialize, Clone, PartialEq, Eq, Debug)]
#[serde(deny_unknown_fields)]
pub struct Bridge {
    pub port: u16,
    pub token: String,
}

fn validate_bridge(bridge: Option<&Bridge>, op: Op) -> Result<(), Error> {
    if let Some(bridge) = bridge
        && (bridge.port == 0 || bridge.token.is_empty())
    {
        refuse!("{op} names a bridge without a port or a token");
    }
    Ok(())
}

/// Opens a recorder session.
#[derive(Serialize, Deserialize, Clone, PartialEq, Eq, Debug)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct HelloCommand {
    /// The directory bundles are written under. The lane decides it, not the recorder, because only the lane
    /// knows which root applies; see [Launcher].
    pub root: String,
    pub run_id: String,
    pub launcher: Launcher,
    /// Lets the recorder read the screen itself: the X server's root window, and on a Mac the screen ffmpeg
    /// records. Without it the recorder asks the IDE to paint its own windows and records no video.
    ///
    /// The lane sets it only where the display belongs to the lane or to a build. The descriptor of the Linux guest,
    /// whose `Xvfb` is the lane's own, carries `-Dair.flow.trace.screen=on`. `lane-run` sets it on a TeamCity build on
    /// Linux, because no person works on the display of a TeamCity agent. A direct lane on a developer's machine never
    /// sets it. A developer's own screen shows everything else they have open, and on macOS reading it needs the Screen
    /// Recording permission, whose prompt can take the keyboard focus from the IDE under test in the middle of a
    /// scenario. Evidence must not decide a verdict, so neither happens unasked.
    #[serde(default, skip_serializing_if = "is_false")]
    pub screen: bool,
    /// Lets the recorder write the Allure result of each scenario into `<root>/<runId>/allure-results/`. Without it
    /// the recorder writes no Allure result. A zip has no hard links, so the results carry each video and picture of
    /// the bundles a second time, and no CI step uploads them to Allure TestOps yet. The lane sets it only when a
    /// person asks for it with `-Dair.flow.trace.allure=on`.
    #[serde(default, skip_serializing_if = "is_false")]
    pub allure: bool,
    /// Which scenarios keep their video. `lane-run` takes it from `AIR_LANE_VIDEO`, which a TeamCity build sets to
    /// `failed` on a branch other than `master` and in a personal build. A passed scenario of `master` is the baseline
    /// that a failed scenario is compared with. Every other lane keeps every video.
    #[serde(default, skip_serializing_if = "VideoRetention::is_always")]
    pub keep_video: VideoRetention,
    /// Absent when the lane has no published endpoint yet. The recorder then captures pixels only, and says so in
    /// the hello ack's reason. Every scenario names the endpoint again; see [ScenarioCommand::bridge].
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub bridge: Option<Bridge>,
}

/// One report label of a scenario, such as the test method or a feature the test class declares.
#[derive(Serialize, Deserialize, Clone, PartialEq, Eq, Debug)]
#[serde(deny_unknown_fields)]
pub struct Label {
    pub name: String,
    pub value: String,
}

/// Opens one scenario. The recorder names the root span after the scenario.
#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct ScenarioCommand {
    pub name: String,
    pub test_class: String,
    /// The suite, flow and fixture name a generated profile's; a hand-authored journey has none of them.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub suite: Option<String>,
    pub lane: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub flow: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub fixture: Option<String>,
    #[serde(default, skip_serializing_if = "BTreeMap::is_empty")]
    pub flags: BTreeMap<String, String>,
    /// The scenario's report labels, in the order the lane read them: the test method and class names, and the
    /// labels the test class and method declare. A name can occur more than once.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub labels: Vec<Label>,
    /// The endpoint published when the scenario started, absent when there is none.
    ///
    /// The hello's endpoint is not enough, because one recorder serves a whole daemon iteration or Bazel target,
    /// and an IDE launched under another launch key answers only its own token. A lane that relaunches its IDE
    /// for one journey would otherwise leave every later scenario of the recorder asking with a stale token and
    /// getting no tree, no log slice and, off X11, no picture. So the recorder takes this endpoint, when it
    /// differs, as the one to ask from here on.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub bridge: Option<Bridge>,
    /// The generated profile as `flow-profiles/<suite>.json` holds it: a JSON object, kept as its bytes except for the
    /// whitespace between its tokens, which [`encode_command`] drops. It goes into the root span so the viewer can show
    /// the instructions a failure never reached. A journey has none.
    #[serde(default, skip_serializing_if = "Option::is_none", deserialize_with = "present_raw")]
    pub program: Option<Box<RawValue>>,
    /// The byte of the IDE's `idea.log` where the slice of the scenario begins. Absent, the slice begins at the size that
    /// the IDE reports as the scenario opens. A lane that opens the scenario after the IDE wrote its records of the
    /// scenario sends an earlier byte. The lane daemon sends 0 for an IDE on a fresh context, whose log holds its launch
    /// alone.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub log_from: Option<u64>,
}

/// A present `program` is kept even when it is `null`, so that validation refuses it rather than the decoder
/// forgetting it.
fn present_raw<'de, D: Deserializer<'de>>(deserializer: D) -> Result<Option<Box<RawValue>>, D::Error> {
    Box::<RawValue>::deserialize(deserializer).map(Some)
}

/// Opens a span.
#[derive(Serialize, Deserialize, Clone, PartialEq, Eq, Debug)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct SpanCommand {
    pub id: u32,
    /// The enclosing span, or 0 for the scenario's root.
    #[serde(default, skip_serializing_if = "is_zero_u32")]
    pub parent: u32,
    pub kind: SpanKind,
    /// The span's stable key; see [span_key].
    pub key: String,
    pub title: String,
    /// The sentence an assertion proves, only on a [SpanKind::Assertion].
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub expectation: Option<String>,
    /// Where the id of the instruction runs, only on a span whose kind [SpanKind::is_instruction].
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub side: Option<Side>,
    /// How long one use of the id of the instruction may take, in milliseconds, as the suite document states it. Only
    /// on a span whose kind [SpanKind::is_instruction].
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub timeout_ms: Option<u64>,
}

/// Why a span failed: the throwable's class, its message, and its stack.
#[derive(Serialize, Deserialize, Clone, PartialEq, Eq, Debug)]
#[serde(deny_unknown_fields)]
pub struct Failure {
    pub r#type: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub message: Option<String>,
    /// The printed stack trace without its header line, each line trimmed, in the order the JVM prints it: the
    /// frame that threw first, then its callers, then every `Caused by:` section. The lane caps it at 200 lines.
    /// The recorder joins it with newlines into `exception.stacktrace`, which the semantic conventions define as the
    /// runtime's own rendering, so neither end reorders it.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub stack: Vec<String>,
}

/// Closes a span.
#[derive(Serialize, Deserialize, Clone, PartialEq, Eq, Debug)]
#[serde(deny_unknown_fields)]
pub struct EndCommand {
    pub id: u32,
    pub status: Status,
    /// Present exactly when the status is not passed. An aborted span may carry the abort's reason.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub error: Option<Failure>,
}

/// Asks for a snapshot now. The lane waits for its ack, up to a bound of its own.
#[derive(Serialize, Deserialize, Clone, PartialEq, Eq, Debug)]
#[serde(deny_unknown_fields)]
pub struct SnapCommand {
    /// The span the snapshot belongs to, or 0 for the scenario's root.
    pub span: u32,
    pub phase: SnapshotPhase,
}

/// Records one bridge call the lane made.
#[derive(Serialize, Deserialize, Clone, PartialEq, Eq, Debug)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct CallCommand {
    /// The innermost span open when the call started, or 0 for the scenario's root.
    pub span: u32,
    /// The simple class name of the bridge request, such as `Ping`. A step of a `StepSequence` is
    /// `StepSequence step <n> of <count> (<name>)`.
    pub request: String,
    pub start_ms: i64,
    pub duration_ms: i64,
    pub ok: bool,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub error: Option<String>,
}

/// Hands the recorder one attachment of the scenario: a file the lane wrote under the trace root.
///
/// The recorder moves the file into the bundle as [crate::bundle::attach_path] and records an
/// [crate::otlp::event::ATTACHMENT] on the span. The file then belongs to the recorder, so the lane must not write it
/// again. The recorder refuses a file outside the hello's root.
#[derive(Serialize, Deserialize, Clone, PartialEq, Eq, Debug)]
#[serde(deny_unknown_fields)]
pub struct AttachCommand {
    /// The attachment's title, as a report shows it.
    pub name: String,
    /// The media type of the file, such as `image/png` or `text/plain`.
    pub mime: String,
    /// The absolute path of the file. Its extension becomes the extension in the bundle.
    pub file: String,
    /// The span the attachment belongs to, or 0 for the scenario's root.
    pub span: u32,
}

/// Closes the scenario.
#[derive(Serialize, Deserialize, Clone, PartialEq, Eq, Debug)]
#[serde(deny_unknown_fields)]
pub struct DoneCommand {
    pub status: Status,
    /// The message of the failure that ended the scenario. It is absent for a passed scenario and for a failure
    /// without a message. The lane caps it as it caps [Failure::message].
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub message: Option<String>,
    /// The stack trace of that failure: its class on the first line, then the lines of [Failure::stack]. Absent for
    /// a passed scenario.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub trace: Option<String>,
}

/// One line the lane writes to the recorder.
///
/// Only two fields carry a wall-clock time: a call's start and duration. Only the lane knows those. The recorder
/// stamps everything else when the line arrives, which is what keeps a transcript free of timestamps and therefore
/// comparable byte for byte with the golden one.
///
/// Span ids are the lane's own: positive integers, numbered from 1 in each scenario in the order the spans open.
/// Id 0 is the scenario's root span, which the scenario command opens, so a parent or a call span of 0 means
/// "directly under the scenario". [crate::otlp::span_id] maps them to OTLP ids.
#[derive(Clone, Debug)]
pub enum Command {
    Hello(HelloCommand),
    Scenario(ScenarioCommand),
    Span(SpanCommand),
    End(EndCommand),
    Snap(SnapCommand),
    Call(CallCommand),
    Attach(AttachCommand),
    /// The IDE is about to be restarted, so its bridge is about to go away and its log may be rotated. It is sent
    /// inside the [SpanKind::Restart] span, and has no field.
    Restart,
    Done(DoneCommand),
}

impl Command {
    /// The discriminator the command is written with.
    pub const fn op(&self) -> Op {
        match self {
            Self::Hello(_) => Op::Hello,
            Self::Scenario(_) => Op::Scenario,
            Self::Span(_) => Op::Span,
            Self::End(_) => Op::End,
            Self::Snap(_) => Op::Snap,
            Self::Call(_) => Op::Call,
            Self::Attach(_) => Op::Attach,
            Self::Restart => Op::Restart,
            Self::Done(_) => Op::Done,
        }
    }

    /// Refuses a command no correct lane sends, whatever lines came before it.
    ///
    /// It does not check that the command fits the session, for example a span under a parent that was never
    /// opened. That depends on the lines before it, so it is the recorder's state machine's job.
    pub fn validate(&self) -> Result<(), Error> {
        match self {
            Self::Hello(hello) => {
                if hello.root.is_empty() || hello.run_id.is_empty() {
                    refuse!("hello needs a root and a runId");
                }
                validate_bridge(hello.bridge.as_ref(), Op::Hello)
            }
            Self::Scenario(scenario) => {
                if scenario.name.is_empty() || scenario.test_class.is_empty() || scenario.lane.is_empty() {
                    refuse!("scenario needs a name, a testClass and a lane");
                }
                if let Some(label) = scenario.labels.iter().find(|label| label.name.is_empty() || label.value.is_empty()) {
                    refuse!(
                        "scenario carries the label {:?} = {:?}, and a label needs a name and a value",
                        label.name,
                        label.value
                    );
                }
                validate_bridge(scenario.bridge.as_ref(), Op::Scenario)?;
                // A raw value is valid JSON already, so one that opens with a brace is an object.
                if let Some(program) = &scenario.program
                    && !program.get().starts_with('{')
                {
                    refuse!("scenario carries a program that is not a JSON object");
                }
                Ok(())
            }
            Self::Span(span) => {
                if span.id == 0 {
                    refuse!(
                        "span {} under {}: ids are positive and 0 is the scenario's root",
                        span.id,
                        span.parent
                    );
                }
                if span.parent >= span.id {
                    refuse!("span {} cannot be under {}, which was not opened before it", span.id, span.parent);
                }
                if span.key.is_empty() || span.title.is_empty() {
                    refuse!("span {} needs a key and a title", span.id);
                }
                if span.expectation.is_some() && span.kind != SpanKind::Assertion {
                    refuse!(
                        "span {} is a {} and carries an expectation, which only an assertion has",
                        span.id,
                        span.kind
                    );
                }
                if span.side.is_some() && !span.kind.is_instruction() {
                    refuse!(
                        "span {} is a {} and carries a side, which only an action or an assertion has",
                        span.id,
                        span.kind
                    );
                }
                if span.timeout_ms.is_some() && !span.kind.is_instruction() {
                    refuse!(
                        "span {} is a {} and carries a deadline, which only an action or an assertion has",
                        span.id,
                        span.kind
                    );
                }
                if span.timeout_ms == Some(0) {
                    refuse!("span {} carries a deadline of 0 ms, and a deadline is positive", span.id);
                }
                Ok(())
            }
            Self::End(end) => {
                if end.id == 0 {
                    refuse!("end {}: span ids are positive, and the root is closed by done", end.id);
                }
                match (&end.status, &end.error) {
                    (Status::Failed, None) => refuse!("end {} failed without saying why", end.id),
                    (Status::Passed, Some(_)) => {
                        refuse!("end {} passed and carries an error", end.id)
                    }
                    (_, Some(error)) if error.r#type.is_empty() => {
                        refuse!("end {} carries an error without a type", end.id)
                    }
                    _ => Ok(()),
                }
            }
            Self::Snap(_) | Self::Restart => Ok(()),
            Self::Done(done) => {
                if done.status == Status::Passed && (done.message.is_some() || done.trace.is_some()) {
                    refuse!("done passed and carries a failure's message or trace");
                }
                Ok(())
            }
            Self::Attach(attach) => {
                if attach.name.is_empty() || attach.mime.is_empty() || attach.file.is_empty() {
                    refuse!("attach needs a name, a mime and a file");
                }
                Ok(())
            }
            Self::Call(call) => {
                if call.request.is_empty() {
                    refuse!("call needs a span and a request");
                }
                if call.start_ms <= 0 || call.duration_ms < 0 {
                    refuse!(
                        "call {} has the start {} and the duration {}",
                        call.request,
                        call.start_ms,
                        call.duration_ms
                    );
                }
                if call.ok && call.error.is_some() {
                    refuse!("call {} succeeded and carries an error", call.request);
                }
                Ok(())
            }
        }
    }
}

// --- the acks ------------------------------------------------------------------------------------------------

/// Answers [HelloCommand]. When `ok` is false the recorder records nothing for this session, both sources are
/// none, and the reason says why.
#[derive(Serialize, Deserialize, Clone, PartialEq, Eq, Debug)]
#[serde(deny_unknown_fields)]
pub struct HelloAck {
    pub ok: bool,
    /// The number of the command line this answers; see [Ack::line].
    pub line: u32,
    pub capture: CaptureSource,
    pub video: VideoCodec,
    /// Why a better capture rung, or the video, is unavailable. Absent when nothing is missing.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub reason: Option<String>,
}

/// Answers every other acked command, and every line the recorder refuses.
#[derive(Serialize, Deserialize, Clone, PartialEq, Eq, Debug)]
#[serde(deny_unknown_fields)]
pub struct Ack {
    pub ok: bool,
    /// The 1-based number of the command line this answers, counting every line the recorder read, acked or not.
    ///
    /// It is what keeps a late ack from being taken for the next one. The lane gives up on a snap ack after a bound
    /// of its own, and a recorder that answers after that bound would otherwise answer the lane's *next* acked
    /// command, and every ack after it would be off by one. The lane counts the lines it writes, so it can discard
    /// any ack whose line is not the one it is waiting for.
    pub line: u32,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub error: Option<String>,
}

// --- encoding and decoding -----------------------------------------------------------------------------------

/// Writes a value the way every document of this crate is written: compact, one line with no trailing newline.
///
/// serde_json never HTML-escapes, which matters: kotlinx.serialization and `JSON.stringify` never do either, and an
/// expectation or a failure message full of `<`, `>` and `&` must be the same bytes in a golden transcript and in
/// the output of the lane that a test compares with it.
pub fn encode<T: Serialize + ?Sized>(value: &T) -> Result<Vec<u8>, Error> {
    serde_json::to_vec(value).map_err(|error| Error::new(format!("cannot encode a trace document: {error}")))
}

/// Writes one command line in its canonical form: `op` first, then the command's fields in declaration order,
/// absent optional fields omitted, and no whitespace between tokens.
///
/// The golden transcript is in exactly this form, and a test re-encodes every line of it to prove so. The lane writes
/// each line with this function, so the golden test of the lane daemon compares its output byte for byte.
///
/// A raw value keeps its bytes through serde_json. So a `program` that a lane read from a pretty-printed document
/// would put line breaks into the line, and the recorder reads one command per line. The line is therefore compacted.
pub fn encode_command(command: &Command) -> Result<Vec<u8>, Error> {
    command.validate()?;
    let body = match command {
        Command::Hello(hello) => encode(hello)?,
        Command::Scenario(scenario) => encode(scenario)?,
        Command::Span(span) => encode(span)?,
        Command::End(end) => encode(end)?,
        Command::Snap(snap) => encode(snap)?,
        Command::Call(call) => encode(call)?,
        Command::Attach(attach) => encode(attach)?,
        Command::Restart => b"{}".to_vec(),
        Command::Done(done) => encode(done)?,
    };
    let mut line = format!(r#"{{"op":"{}""#, command.op()).into_bytes();
    if body == b"{}" {
        line.push(b'}');
    } else {
        line.push(b',');
        line.extend_from_slice(&compact(&body)[1..]);
    }
    Ok(line)
}

/// Drops the whitespace between the tokens of `json`, which is JSON text. JSON escapes each control character in a
/// string, so a line break or a tab outside a string is never data, and a space outside a string is never data either.
fn compact(json: &[u8]) -> Vec<u8> {
    let mut out = Vec::with_capacity(json.len());
    let mut in_string = false;
    let mut escaped = false;
    for &byte in json {
        if in_string {
            out.push(byte);
            if escaped {
                escaped = false;
            } else if byte == b'\\' {
                escaped = true;
            } else if byte == b'"' {
                in_string = false;
            }
        } else if !matches!(byte, b' ' | b'\t' | b'\n' | b'\r') {
            in_string = byte == b'"';
            out.push(byte);
        }
    }
    out
}

/// Reads one command line, and refuses an unknown op, an unknown field, a field of the wrong kind, a vocabulary
/// word the contract does not name, and anything after the object.
///
/// The line is read in two steps: first as an object of raw fields, to take the `op` out, then the rest as the
/// op's command. That keeps `program` byte for byte, which a tagged enum would not: serde buffers the fields of an
/// internally tagged enum as values and cannot hand a raw value through.
pub fn decode_command(line: &[u8]) -> Result<Command, Error> {
    let mut fields: BTreeMap<String, Box<RawValue>> =
        serde_json::from_slice(line).map_err(|error| Error::new(format!("a command line is not a JSON object: {error}")))?;
    let Some(raw_op) = fields.remove("op") else {
        refuse!("a command line has no op");
    };
    let Ok(word) = serde_json::from_str::<String>(raw_op.get()) else {
        refuse!("a command line has the op {}, which is not a string", raw_op.get());
    };
    let Some(op) = Op::from_word(&word) else {
        refuse!("a command line has the unknown op {word:?}");
    };
    let command = match op {
        Op::Hello => Command::Hello(decode_body(op, &fields)?),
        Op::Scenario => Command::Scenario(decode_body(op, &fields)?),
        Op::Span => Command::Span(decode_body(op, &fields)?),
        Op::End => Command::End(decode_body(op, &fields)?),
        Op::Snap => Command::Snap(decode_body(op, &fields)?),
        Op::Call => Command::Call(decode_body(op, &fields)?),
        Op::Attach => Command::Attach(decode_body(op, &fields)?),
        Op::Restart => {
            if let Some(field) = fields.keys().next() {
                refuse!("a restart command does not fit the contract: unknown field `{field}`");
            }
            Command::Restart
        }
        Op::Done => Command::Done(decode_body(op, &fields)?),
    };
    command.validate()?;
    Ok(command)
}

/// Decodes the fields of one command strictly, `op` already taken out.
///
/// Re-encoding the field map is what lets a command struct carry no `op` field of its own: `op` is the
/// discriminator rather than data, and a struct field for it would be one more thing a caller could set wrong.
fn decode_body<T: DeserializeOwned>(op: Op, fields: &BTreeMap<String, Box<RawValue>>) -> Result<T, Error> {
    let body = encode(fields)?;
    serde_json::from_slice(&body).map_err(|error| Error::new(format!("a {op} command does not fit the contract: {error}")))
}

/// Reads the recorder's answer to hello.
pub fn decode_hello_ack(line: &[u8]) -> Result<HelloAck, Error> {
    let ack: HelloAck =
        serde_json::from_slice(line).map_err(|error| Error::new(format!("a hello ack does not fit the contract: {error}")))?;
    if ack.line == 0 {
        refuse!(
            "a hello ack answers line {} with the capture {} and the video {}",
            ack.line,
            ack.capture,
            ack.video
        );
    }
    Ok(ack)
}

#[cfg(test)]
mod tests;
