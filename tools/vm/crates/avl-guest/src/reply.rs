//! How a verb answers: one document on stdout, or one failure envelope on stderr, and one exit status.
//!
//! One shape for every failure, and the reason is the host. A bare line on stderr cannot be told apart from a
//! subprocess's own output, so the host withholds every such line; a diagnosis a check composed then never
//! reaches the operator, and a failure reads as `exited with 70` with the reason dropped. Inside the envelope
//! `avl_wire::supervisor` declares for both halves, the message is this process's own text by construction, so
//! it travels whole. That is also why a refusal message must never quote a subprocess's output: whatever is
//! appended to it travels too.

use std::borrow::Cow;
use std::fmt::Display;
use std::io::{Read, Write};

use avl_wire::supervisor::{AgentExit, Envelope, EnvelopeError, USAGE_CODE};
use avl_wire::verb::AgentVerb;
use serde::Serialize;

/// A verb's expected failure: the shared [`refusal::Refusal`] with a code the host can switch on, a message a human
/// reads, and one of the closed set of agent exit statuses ([`AgentExit`]).
///
/// The status is never a number of a check's own. The host reads 64 as "the agent is older than its controller",
/// which is sound only because every verb that fails *inside* the agent answers 70 instead; which check it was
/// belongs in the message.
pub(crate) type AgentRefusal = refusal::Refusal<AgentExit>;

/// The agent's constructors of an [`AgentRefusal`].
pub(crate) trait AgentRefusalExt: Sized {
    /// The invocation was wrong before any guest work ran.
    fn usage(message: impl Into<String>) -> Self;

    /// The unclassified failure: the supervisor's own timeouts, unreadable state, unidentifiable processes.
    fn failure(code: impl Into<Cow<'static, str>>, message: impl Into<String>) -> Self;

    /// The verb ran and one of its checks refused.
    fn refused(code: impl Into<Cow<'static, str>>, message: impl Into<String>) -> Self;

    /// A check's refusal stamped with the verb's own code ([`verb_refusal_code`]).
    ///
    /// Fifty codes for fifty checks would be vocabulary and not diagnosis: nothing switches on them, and what a
    /// reader acts on is the message. The verb is the one distinction a caller can act on.
    fn for_verb(verb: AgentVerb, message: impl Into<String>) -> Self;

    /// The same refusal under another code.
    #[must_use]
    fn with_code(self, code: impl Into<Cow<'static, str>>) -> Self;

    /// An error nothing classified, the way the supervisor verbs always answered one.
    fn internal(error: impl Display) -> Self;
}

impl AgentRefusalExt for AgentRefusal {
    fn usage(message: impl Into<String>) -> Self {
        Self::new(USAGE_CODE, AgentExit::Usage, message)
    }

    fn failure(code: impl Into<Cow<'static, str>>, message: impl Into<String>) -> Self {
        Self::new(code, AgentExit::Failure, message)
    }

    fn refused(code: impl Into<Cow<'static, str>>, message: impl Into<String>) -> Self {
        Self::new(code, AgentExit::Refused, message)
    }

    fn for_verb(verb: AgentVerb, message: impl Into<String>) -> Self {
        Self::refused(verb_refusal_code(verb), message)
    }

    fn with_code(mut self, code: impl Into<Cow<'static, str>>) -> Self {
        self.code = code.into();
        self
    }

    fn internal(error: impl Display) -> Self {
        Self::failure("internal_error", error.to_string())
    }
}

/// Returns early with the refusal of `verb` ([`AgentRefusalExt::for_verb`]), its message formatted like `format!`.
macro_rules! refuse {
    ($verb:expr, $($argument:tt)*) => {
        return Err(<$crate::reply::AgentRefusal as $crate::reply::AgentRefusalExt>::for_verb($verb, format!($($argument)*)))
    };
}
pub(crate) use refuse;

/// The refusal code one verb's failure answers with: `guest_gc_failed`, `guest_launch_prep_failed`,
/// `guest_stage_check_failed`. Derived from the verb, so it needs no list to stay accurate.
pub(crate) fn verb_refusal_code(verb: AgentVerb) -> String {
    format!("guest_{}_failed", verb.as_str().replace('-', "_"))
}

/// The three streams a verb answers on. Borrowed, so a test captures them in memory.
pub(crate) struct Streams<'a> {
    pub stdin: &'a mut dyn Read,
    pub stdout: &'a mut dyn Write,
    pub stderr: &'a mut dyn Write,
}

/// One JSON document and a newline, or `None` when the value cannot be encoded.
pub(crate) fn json_line(value: &impl Serialize) -> Option<Vec<u8>> {
    let mut encoded = serde_json::to_vec(value).ok()?;
    encoded.push(b'\n');
    Some(encoded)
}

/// Writes the failure envelope for `refusal` on stderr and answers its exit status.
///
/// `command` is the word the envelope echoes. Text rather than an [`AgentVerb`], because a usage refusal echoes
/// whatever verb the argv named, declared or not.
///
/// A write that fails is ignored: the exit status still says the verb failed, and there is nowhere else to
/// report that stderr is gone.
pub(crate) fn fail(streams: &mut Streams<'_>, command: &str, refusal: AgentRefusal) -> u8 {
    // No verb of the agent gives a refusal details, so the envelope writes `null`. Text that is no JSON value would
    // be a defect of this binary, and the envelope then carries it as a string rather than dropping it.
    let details = refusal.details_json_text().map_or(serde_json::Value::Null, |text| {
        serde_json::from_str(text).unwrap_or_else(|_| serde_json::Value::String(text.to_owned()))
    });
    let envelope: Envelope<()> = Envelope::failure(
        command,
        EnvelopeError {
            code: refusal.code.into_owned(),
            message: refusal.message,
            details,
        },
    );
    if let Some(line) = json_line(&envelope) {
        let _ = streams.stderr.write_all(&line);
    }
    exit_status(refusal.exit)
}

/// Answers `result` in the success envelope `{schemaVersion, ok, command, data}`, or fails.
pub(crate) fn answer_enveloped<T: Serialize>(streams: &mut Streams<'_>, verb: AgentVerb, result: Result<T, AgentRefusal>) -> u8 {
    match result {
        Ok(data) => write_document(streams, verb, &Envelope::success(verb.as_str(), data)),
        Err(refusal) => fail(streams, verb.as_str(), refusal),
    }
}

/// Answers `result` as the bare document, which is what the staging verbs' host decodes, or fails.
pub(crate) fn answer_bare<T: Serialize>(streams: &mut Streams<'_>, verb: AgentVerb, result: Result<T, AgentRefusal>) -> u8 {
    match result {
        Ok(document) => write_document(streams, verb, &document),
        Err(refusal) => fail(streams, verb.as_str(), refusal),
    }
}

fn write_document(streams: &mut Streams<'_>, verb: AgentVerb, document: &impl Serialize) -> u8 {
    let Some(line) = json_line(document) else {
        return fail(streams, verb.as_str(), AgentRefusal::internal("the reply could not be encoded"));
    };
    match streams.stdout.write_all(&line).and_then(|()| streams.stdout.flush()) {
        Ok(()) => 0,
        Err(error) => fail(streams, verb.as_str(), AgentRefusal::internal(error)),
    }
}

pub(crate) fn exit_status(exit: AgentExit) -> u8 {
    // Every agent status is below 256 by declaration.
    u8::try_from(exit.code()).unwrap_or(1)
}

/// JSON-quotes a value for a message, so an empty or whitespace-only value is still visible.
pub(crate) fn quoted(value: &str) -> String {
    serde_json::to_string(value).unwrap_or_else(|_| value.to_owned())
}
