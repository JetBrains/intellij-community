//! The run supervisor's wire, declared once for both halves of the UI lane controller.
//!
//! The two halves used to declare it twice, and the cost of that is on the record: a fake supervisor answered
//! `cancelled` for months while the real one has always written `canceled`. So nothing outside this module
//! spells a phase or an outcome; a list that exists once cannot disagree with itself.
//!
//! What this module deliberately does *not* replace is the `contract` verb. The verb answers a different
//! question: what does the agent that is already installed in this guest speak? The controller compares the
//! agent's self-reported digest before deciding whether to reinstall, so `contract` is on the fast path of
//! every lease operation, not only in a test. The host and the agent share this module, so every document here
//! decodes exactly what it writes.

use std::fmt;
use std::str::FromStr;

use serde::{Deserialize, Deserializer, Serialize, Serializer};
use serde_json::value::RawValue;

use crate::verb::AgentVerb;
use crate::vocabulary::UnknownWord;

#[cfg(test)]
mod tests;

/// The version the whole controller's state schema carries. Deliberately controller-wide: a shape change here
/// is a change to lease files, worker state and tart state at once, and that is meant to be expensive.
pub const SCHEMA_VERSION: u32 = 1;

/// One verb of the run supervisor: the subset of [`AgentVerb`] that answers an [`Envelope`], sent by the host
/// over the exec channel. It spells nothing itself. Unlike [`Phase`] and [`Outcome`], the order of
/// [`Command::ALL`] is not part of any contract.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum Command {
    Start,
    Status,
    Cancel,
    Active,
    Log,
    /// Never crosses the host boundary: the detached re-invocation that `start` spawns. Declared because the
    /// dispatch accepts it like any other verb.
    Supervise,
}

impl Command {
    /// Every supervisor verb.
    pub const ALL: &'static [Self] = &[Self::Start, Self::Status, Self::Cancel, Self::Active, Self::Log, Self::Supervise];

    /// The agent verb this command is.
    pub const fn verb(self) -> AgentVerb {
        match self {
            Self::Start => AgentVerb::Start,
            Self::Status => AgentVerb::Status,
            Self::Cancel => AgentVerb::Cancel,
            Self::Active => AgentVerb::Active,
            Self::Log => AgentVerb::Log,
            Self::Supervise => AgentVerb::Supervise,
        }
    }

    /// The verb as the wire spells it.
    pub const fn as_str(self) -> &'static str {
        self.verb().as_str()
    }
}

impl fmt::Display for Command {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

impl FromStr for Command {
    type Err = UnknownWord;

    fn from_str(text: &str) -> Result<Self, Self::Err> {
        Self::ALL
            .iter()
            .copied()
            .find(|command| command.as_str() == text)
            .ok_or_else(|| UnknownWord {
                vocabulary: "Command",
                word: text.to_owned(),
            })
    }
}

vocabulary! {
    /// Where a run is.
    ///
    /// An undeclared phase is *refused* on read, and that asymmetry with [`Outcome`] is the contract. The host
    /// decides a daemon died by matching `finished`, so a phase neither half knows would read as "still
    /// booting" and burn the whole boot budget before reporting the wrong thing. There is no safe default.
    ///
    /// [`Phase::ALL`] is in the order the `contract` verb publishes; the host compares it order-sensitively.
    pub enum Phase {
        Starting = "starting",
        Running = "running",
        /// The one word that is both a phase and an outcome: a supervisor that vanished leaves the run in it.
        Orphaned = "orphaned",
        Finished = "finished",
    }
}

/// What a finished run did.
///
/// Unlike a phase, an undeclared outcome is *kept and reported* rather than refused: nothing branches on it, it
/// is only ever quoted into a message, and refusing it would stop a run from reporting a failure the reading
/// half predates. The declared list is for the writing side, [`validate_outcome_to_write`].
#[derive(Clone, Debug, PartialEq, Eq, Hash)]
pub enum Outcome {
    Succeeded,
    Failed,
    /// Spelled with one `l`. The fake that spelled it `cancelled` is why this module exists.
    Canceled,
    CanceledOrphan,
    Rejected,
    FailedToStart,
    SupervisorLost,
    Orphaned,
    /// A word no declaration names, kept verbatim.
    Unknown(String),
}

impl Outcome {
    /// Every declared outcome, in the order the `contract` verb publishes them.
    pub const ALL: [Self; 8] = [
        Self::Succeeded,
        Self::Failed,
        Self::Canceled,
        Self::CanceledOrphan,
        Self::Rejected,
        Self::FailedToStart,
        Self::SupervisorLost,
        Self::Orphaned,
    ];

    pub fn as_str(&self) -> &str {
        match self {
            Self::Succeeded => "succeeded",
            Self::Failed => "failed",
            Self::Canceled => "canceled",
            Self::CanceledOrphan => "canceled_orphan",
            Self::Rejected => "rejected",
            Self::FailedToStart => "failed_to_start",
            Self::SupervisorLost => "supervisor_lost",
            Self::Orphaned => "orphaned",
            Self::Unknown(word) => word,
        }
    }

    /// Whether the contract names this outcome. Keeping an outcome is not the same as knowing it.
    pub fn is_declared(&self) -> bool {
        Self::ALL.iter().any(|declared| declared.as_str() == self.as_str())
    }
}

impl FromStr for Outcome {
    type Err = std::convert::Infallible;

    fn from_str(word: &str) -> Result<Self, Self::Err> {
        Ok(Self::ALL
            .into_iter()
            .find(|declared| declared.as_str() == word)
            .unwrap_or_else(|| Self::Unknown(word.to_owned())))
    }
}

impl fmt::Display for Outcome {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

impl Serialize for Outcome {
    fn serialize<S: Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        serializer.serialize_str(self.as_str())
    }
}

impl<'de> Deserialize<'de> for Outcome {
    fn deserialize<D: Deserializer<'de>>(deserializer: D) -> Result<Self, D::Error> {
        let word = String::deserialize(deserializer)?;
        let Ok(outcome) = word.parse();
        Ok(outcome)
    }
}

/// An outcome nobody declared, refused on the writing side.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct UndeclaredOutcome(pub Outcome);

impl fmt::Display for UndeclaredOutcome {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{} is not one of the declared run outcomes", self.0)
    }
}

impl std::error::Error for UndeclaredOutcome {}

/// Refuses an outcome nobody declared, for the half that is about to write one, so that a half which
/// *invents* an outcome fails where the caller that invented it is still on the stack.
///
/// The asymmetry in one place: reading tolerates an unknown outcome, writing does not.
pub fn validate_outcome_to_write(outcome: &Outcome) -> Result<(), UndeclaredOutcome> {
    if outcome.is_declared() {
        Ok(())
    } else {
        Err(UndeclaredOutcome(outcome.clone()))
    }
}

/// The run-id shape both halves accept, which [is_run_id] checks. The `contract` verb publishes the text so the two
/// can be compared.
pub const RUN_ID_PATTERN_TEXT: &str = "^run-[a-zA-Z0-9-]+$";
/// The snapshot-id shape, which [is_snapshot_id] checks.
pub const SNAPSHOT_ID_PATTERN_TEXT: &str = "^snapshot-[a-zA-Z0-9._-]+$";

/// Whether `id` has the shape of [RUN_ID_PATTERN_TEXT].
///
/// It is written by hand, because a regex engine would be the largest part of the guest agent, and the controller
/// reinstalls the agent on every worker when its bytes change.
pub fn is_run_id(id: &str) -> bool {
    has_prefix_and_word(id, "run-", b"-")
}

/// Whether `id` has the shape of [SNAPSHOT_ID_PATTERN_TEXT].
pub fn is_snapshot_id(id: &str) -> bool {
    has_prefix_and_word(id, "snapshot-", b"._-")
}

/// Whether `id` is `prefix` and then one or more ASCII letters, digits and bytes of `punctuation`.
fn has_prefix_and_word(id: &str, prefix: &str, punctuation: &[u8]) -> bool {
    id.strip_prefix(prefix)
        .is_some_and(|word| !word.is_empty() && word.bytes().all(|byte| byte.is_ascii_alphanumeric() || punctuation.contains(&byte)))
}

/// What a cancelled run records about the request and the signals that followed.
///
/// `term_sent_at` and `kill_sent_at` are written as `null` when not sent: "not sent" has to stay
/// distinguishable from "sent at some time".
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CancellationRecord {
    pub requested_at: String,
    pub grace_ms: u64,
    pub term_sent_at: Option<String>,
    pub kill_sent_at: Option<String>,
}

/// One run, as its state file describes it and as every reply carries it.
///
/// Two opposite null conventions live here and both are load-bearing. `signal` and `native_exit_code` are
/// present-and-null in a terminal state, which the host asserts on, hence their tri-state
/// (`None` absent, `Some(None)` null). `outcome` and `exit_code` must be *absent* while a run is live, because
/// the host's decoder rejects a null where it expects a missing key.
///
/// Every optional scalar is an `Option` for the counter trap's reason: a run that reports `exitCode: 0`
/// because nobody set it is a green verdict manufactured out of nothing.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RunState {
    pub schema_version: u32,
    pub run_id: String,
    /// Always written; `null` when the run has no snapshot.
    #[serde(default)]
    pub snapshot_id: Option<String>,
    pub phase: Phase,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub argv: Vec<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub cwd: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub created_at: Option<String>,

    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub supervisor_pid: Option<i32>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub supervisor_pgid: Option<i32>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub supervisor_start: Option<String>,

    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub started_at: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub pid: Option<i32>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub pgid: Option<i32>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub process_start: Option<String>,

    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub outcome: Option<Outcome>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub exit_code: Option<i32>,
    /// Terminal only, and an explicit null when the child was signalled rather than exiting.
    #[serde(default, deserialize_with = "crate::json::double_option", skip_serializing_if = "Option::is_none")]
    pub native_exit_code: Option<Option<i32>>,
    #[serde(default, deserialize_with = "crate::json::double_option", skip_serializing_if = "Option::is_none")]
    pub signal: Option<Option<String>>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub finished_at: Option<String>,

    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub cancellation: Option<CancellationRecord>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub failure: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub orphaned_at: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub pending_outcome: Option<Outcome>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub orphaned_resolved_at: Option<String>,
}

impl RunState {
    /// A state in `phase` with nothing else known yet.
    pub fn new(run_id: impl Into<String>, phase: Phase) -> Self {
        Self {
            schema_version: SCHEMA_VERSION,
            run_id: run_id.into(),
            snapshot_id: None,
            phase,
            argv: Vec::new(),
            cwd: None,
            created_at: None,
            supervisor_pid: None,
            supervisor_pgid: None,
            supervisor_start: None,
            started_at: None,
            pid: None,
            pgid: None,
            process_start: None,
            outcome: None,
            exit_code: None,
            native_exit_code: None,
            signal: None,
            finished_at: None,
            cancellation: None,
            failure: None,
            orphaned_at: None,
            pending_outcome: None,
            orphaned_resolved_at: None,
        }
    }
}

/// The run request, as the host wrote it and the detached supervisor reads it back.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Spec {
    pub schema_version: u32,
    pub run_id: String,
    #[serde(default)]
    pub snapshot_id: Option<String>,
    pub cwd: String,
    pub argv: Vec<String>,
    /// The environment of the child. A spec without the field is a spec of [`EnvironmentPolicy::Inherit`].
    #[serde(default)]
    pub environment: EnvironmentPolicy,
    pub created_at: String,
}

/// Where the supervisor takes the environment of the child from.
///
/// The spec records a policy and never a value. The spec is a file of the run, and `status` answers its argv to every
/// caller. So a variable here would be readable outside the child, and a policy keeps the spec free of values.
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "policy", rename_all = "camelCase", rename_all_fields = "camelCase")]
pub enum EnvironmentPolicy {
    /// The environment of the supervisor, with the PATH of the guest OS. The UI daemon runs with this policy.
    #[default]
    Inherit,
    /// The closed environment of a lane IDE context: `HOME` and `PATH` from the layout of the context, and a fixed
    /// list of variables from the environment of the supervisor. Nothing else.
    Context {
        /// The context directory, an absolute guest path.
        context_dir: String,
    },
}

/// The file a cancel client drops for the supervisor that owns the grace period.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CancellationRequest {
    pub schema_version: u32,
    pub run_id: String,
    pub requested_at: String,
    pub grace_ms: u64,
}

/// Which run owns the slot, or that it is free.
///
/// The one wire whose null convention is inverted: an explicit `{"active": null}` is the good case and the
/// answer the controller acts on most often, so a *missing* key is the protocol failure.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct ActiveReply {
    #[serde(deserialize_with = "Option::deserialize")]
    pub active: Option<RunState>,
}

/// One run's captured output, bounded by a budget the guest applies and does not publish.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LogReply {
    pub run_id: String,
    pub log_path: String,
    pub content: String,
    pub truncated: bool,
}

/// What the `contract` verb answers: the binary describing its own wire.
///
/// The lists let a host compare its own understanding against the agent the guest actually runs, because an
/// *older* agent may already be installed. `self_digest` is the fast-path half: the controller reinstalls only
/// when the installed bytes are not the bytes it holds.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Contract {
    pub schema_version: u32,
    pub phases: Vec<Phase>,
    pub outcomes: Vec<Outcome>,
    pub run_id_pattern: String,
    /// The sha256 of the agent's own bytes, as text: a digest that reaches a float rounds, and a rounded
    /// digest compares equal to bytes it does not describe. Empty when it cannot be read, which reads as
    /// "reinstall" on the host.
    pub self_digest: String,
}

impl Contract {
    /// This build's contract, for an agent whose own bytes digest to `self_digest`.
    pub fn current(self_digest: impl Into<String>) -> Self {
        Self {
            schema_version: SCHEMA_VERSION,
            phases: Phase::ALL.to_vec(),
            outcomes: Outcome::ALL.to_vec(),
            run_id_pattern: RUN_ID_PATTERN_TEXT.to_owned(),
            self_digest: self_digest.into(),
        }
    }
}

/// Every reply of the agent: `{schemaVersion, ok, command, data}` or `{schemaVersion, ok, command, error}`.
/// `command` is echoed so the host can check that the answer is to the question it asked.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Envelope<T> {
    pub schema_version: u32,
    pub ok: bool,
    pub command: String,
    #[serde(flatten)]
    pub body: EnvelopeBody<T>,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub enum EnvelopeBody<T> {
    #[serde(rename = "data")]
    Data(T),
    #[serde(rename = "error")]
    Error(EnvelopeError),
}

impl<T> Envelope<T> {
    pub fn success(command: impl Into<String>, data: T) -> Self {
        Self {
            schema_version: SCHEMA_VERSION,
            ok: true,
            command: command.into(),
            body: EnvelopeBody::Data(data),
        }
    }

    pub fn failure(command: impl Into<String>, error: EnvelopeError) -> Self {
        Self {
            schema_version: SCHEMA_VERSION,
            ok: false,
            command: command.into(),
            body: EnvelopeBody::Error(error),
        }
    }

    pub fn into_result(self) -> Result<T, EnvelopeError> {
        match self.body {
            EnvelopeBody::Data(data) => Ok(data),
            EnvelopeBody::Error(error) => Err(error),
        }
    }
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct EnvelopeError {
    pub code: String,
    pub message: String,
    /// `null` when the refusal carries no details.
    #[serde(default)]
    pub details: serde_json::Value,
}

impl fmt::Display for EnvelopeError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}: {}", self.code, self.message)
    }
}

impl std::error::Error for EnvelopeError {}

/// A reply of the agent as the controller receives it, read as one union: which half it is is `ok`'s to say, and
/// `data` stays undecoded until the command's own reply type is known.
///
/// The reading side of [`Envelope`], and lenient where [`Envelope`] is strict: every field defaults, and so does an
/// explicit `null`, so an agent that left one out is judged by what it did say. `command` stays text, so an echo
/// of a verb this controller does not know is reported rather than undecodable. The field names are
/// [`Envelope`]'s, and a test writes one and reads it back here.
#[derive(Debug, Default, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct ReceivedEnvelope {
    #[serde(deserialize_with = "crate::json::null_as_default")]
    pub schema_version: i64,
    #[serde(deserialize_with = "crate::json::null_as_default")]
    pub ok: bool,
    #[serde(deserialize_with = "crate::json::null_as_default")]
    pub command: String,
    pub data: Option<Box<RawValue>>,
    pub error: Option<ReceivedError>,
}

impl ReceivedEnvelope {
    /// Reads one reply; a bare `null` is an envelope that said nothing, not invalid JSON.
    pub fn read(raw: &str) -> serde_json::Result<Self> {
        serde_json::from_str::<Option<Self>>(raw).map(Option::unwrap_or_default)
    }
}

/// The error half of a [`ReceivedEnvelope`], read leniently: a code with no message still says where a refusal came
/// from.
#[derive(Debug, Default, Deserialize)]
#[serde(default)]
pub struct ReceivedError {
    #[serde(deserialize_with = "crate::json::null_as_default")]
    pub code: String,
    #[serde(deserialize_with = "crate::json::null_as_default")]
    pub message: String,
}

/// The envelope code the agent refuses a verb or arguments it does not have with, at [`AgentExit::Usage`]. The
/// controller reads it, like the status, as an agent older than itself.
pub const USAGE_CODE: &str = "usage";

/// The statuses `vm-guest-agent` exits with, and so also the statuses the controller exits with: a refusal's
/// exit code stays the guest's own.
///
/// The set is closed, and that is a contract rather than tidiness. The host reads a 64 as the dispatch's usage
/// block and therefore as an agent older than its controller, and that inference is sound only because every
/// verb failing *inside* the agent answers [`AgentExit::Refused`] instead. A check that runs in the agent says
/// which check it was in [`EnvelopeError::message`], never in a number of its own; see
/// `community/tools/vm/docs/decisions/0108-the-guest-half-of-the-image-pipeline-is-go.md`. A fifth status is a wire
/// change, not an addition.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
#[repr(i32)]
pub enum AgentExit {
    /// The unclassified failure: every `internal_error`, and the supervisor's own timeouts, unreadable specs
    /// and unidentifiable processes.
    Failure = 1,
    /// `EX_USAGE`: the invocation was wrong before any guest work ran. The one status the host reads as a
    /// diagnosis rather than as a failure.
    Usage = 64,
    /// `EX_SOFTWARE`: the verb ran and refused. Which check it was belongs in the message.
    Refused = 70,
    /// `EX_CANTCREATE`: `start` was asked for a run that is already there - the same fact, and the same
    /// number, as the controller's own "can't create".
    CantCreate = 73,
}

impl AgentExit {
    /// Every status a verb of the agent may leave with.
    pub const ALL: [Self; 4] = [Self::Failure, Self::Usage, Self::Refused, Self::CantCreate];

    pub const fn code(self) -> i32 {
        self as i32
    }

    pub fn from_code(code: i32) -> Option<Self> {
        Self::ALL.into_iter().find(|status| status.code() == code)
    }
}

/// Why a state document was refused.
#[derive(Debug)]
pub enum RunStateError {
    Json(serde_json::Error),
    UnsupportedSchema { run_id: String },
    InvalidIdentity { run_id: String },
}

impl fmt::Display for RunStateError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Json(error) => error.fmt(f),
            Self::UnsupportedSchema { run_id } => {
                write!(f, "state for {run_id} has an unsupported schema")
            }
            Self::InvalidIdentity { run_id } => {
                write!(f, "state for {run_id} has invalid identity or phase")
            }
        }
    }
}

impl std::error::Error for RunStateError {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        match self {
            Self::Json(error) => Some(error),
            _ => None,
        }
    }
}

/// Reads one state document, refusing what neither half can act on.
///
/// Unknown *fields* are tolerated on purpose: the state carries a dozen fields no decision reads, and they
/// cross untouched so an active run can be republished verbatim. What is refused is a schema this half does not
/// speak, an identity that is not the run being asked about, and an undeclared phase (a decode error). An
/// undeclared *outcome* is kept; see [`Outcome`].
pub fn decode_run_state(raw: &[u8], run_id: &str) -> Result<RunState, RunStateError> {
    let state: RunState = serde_json::from_slice(raw).map_err(RunStateError::Json)?;
    if state.schema_version != SCHEMA_VERSION {
        return Err(RunStateError::UnsupportedSchema { run_id: run_id.to_owned() });
    }
    if state.run_id != run_id {
        return Err(RunStateError::InvalidIdentity { run_id: run_id.to_owned() });
    }
    Ok(state)
}
