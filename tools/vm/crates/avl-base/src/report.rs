//! The controller's command envelope and its progress fan-out.
//!
//! The contract is the one an agent depends on, and it is deliberately narrow:
//!
//! - A successful command writes exactly one `{"schemaVersion":1,"ok":true,"command":…,"data":…}` object to
//!   **stdout** and exits 0.
//! - A failed command writes exactly one `{…,"ok":false,"command":…,"error":{code,message,details}}` object to
//!   **stderr** and exits nonzero.
//! - Progress, when asked for (`--stream`), is NDJSON on **stderr** and never on stdout, so a caller can read the
//!   one envelope off stdout without knowing whether progress was on. In human mode the same progress is prose on
//!   stderr; [`crate::plain`] renders it, or a renderer of the caller's.
//!
//! Nothing here decides *what* a command answers; it decides how an answer and a refusal are shaped.

use std::fmt;
use std::io::{self, Write};
use std::path::Path;
use std::sync::{Arc, Mutex, MutexGuard, Weak};

use avl_wire::progress::{Event, Phase, PhaseChange, PhaseState, Record, Verdict};
use jiff::Timestamp;
use serde::Serialize;

use crate::clock::{self, Clock, SystemClock, millis};
use crate::plain::Screen;
use crate::refusal::RefusalExt;
use crate::refusal::{Exit, Refusal};
use crate::sync::lock;

#[cfg(test)]
mod tests;

/// The version every envelope and progress record carries. The wire owns the number: the guest agent stamps the
/// same one on its replies, and it versions lease files and worker state too.
pub const SCHEMA_VERSION: u32 = avl_wire::SCHEMA_VERSION;

/// A writer several owners share: the reporter, its plain renderer and the renderer's footer thread.
#[derive(Clone)]
pub struct SharedWriter(Arc<Mutex<Box<dyn Write + Send>>>);

impl SharedWriter {
    pub fn new(writer: impl Write + Send + 'static) -> Self {
        Self(Arc::new(Mutex::new(Box::new(writer))))
    }

    /// Writes all of `bytes` at once, so two writers never interleave inside one line. A failed write to a
    /// closed terminal or pipe has nowhere to be reported, so it is dropped.
    pub fn emit(&self, bytes: &[u8]) {
        let mut writer = lock(&self.0);
        let _ = writer.write_all(bytes);
        let _ = writer.flush();
    }
}

impl Write for SharedWriter {
    fn write(&mut self, bytes: &[u8]) -> io::Result<usize> {
        lock(&self.0).write(bytes)
    }

    fn flush(&mut self) -> io::Result<()> {
        lock(&self.0).flush()
    }
}

/// An in-memory writer a test reads back: what a command wrote, without touching the process's descriptors.
#[derive(Clone, Default)]
pub struct Buffer(Arc<Mutex<Vec<u8>>>);

impl Buffer {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn text(&self) -> String {
        String::from_utf8_lossy(&lock(&self.0)).into_owned()
    }

    pub fn bytes(&self) -> Vec<u8> {
        lock(&self.0).clone()
    }

    pub fn is_empty(&self) -> bool {
        lock(&self.0).is_empty()
    }

    pub fn clear(&self) {
        lock(&self.0).clear();
    }
}

impl Write for Buffer {
    fn write(&mut self, bytes: &[u8]) -> io::Result<usize> {
        lock(&self.0).extend_from_slice(bytes);
        Ok(bytes.len())
    }

    fn flush(&mut self) -> io::Result<()> {
        Ok(())
    }
}

impl fmt::Debug for Buffer {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_tuple("Buffer").field(&self.text()).finish()
    }
}

/// What the human renderer may do with stderr. The default is plain lines.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct Terminal {
    /// Keeps a one-line status footer at the bottom of the terminal and redraws it in place. Only for a terminal:
    /// in a log file, a redraw is a line of control bytes.
    pub redraw: bool,
    /// Marks a passed and a failed test in green and red.
    pub color: bool,
    /// The terminal's width in columns, which bounds the footer; 0 is unknown.
    pub width: u16,
}

/// The output form.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub enum Mode {
    /// One envelope and nothing else: progress is silent.
    #[default]
    Json,
    /// The envelope, and NDJSON progress on stderr.
    Stream,
    /// Prose: progress rendered for a person, and the text answer on stdout.
    Human(Terminal),
}

/// Which worker a progress record came from.
///
/// Passed explicitly rather than held as "the current worker", because the case it exists for is several workers
/// streaming at once: an ambient value would be reassigned by whichever thread reached its next write first.
/// `None` for a command with one worker in play, or none, so those records carry no worker field.
#[derive(Clone, Debug, PartialEq, Eq, Hash)]
pub struct Scope {
    pub worker: String,
}

impl Scope {
    pub fn worker(name: impl Into<String>) -> Self {
        Self { worker: name.into() }
    }

    /// `[air-docker-1] `, short enough to stay in front of a test name without wrapping it.
    pub(crate) fn prefix(scope: Option<&Self>) -> String {
        scope.map_or_else(String::new, |scope| format!("[{}] ", scope.worker))
    }
}

/// A human form of the progress stream: the plain lines of [`crate::plain`], or a live dashboard.
///
/// Every method is called with the reporter's lock held, in publication order, so a renderer must not publish.
/// `finish` is called once, before the command's answer is written, and the renderer must not write after it.
///
/// `finish` renders the [`Verdict`] the renderer received, if one arrived, and answers it. The verdict is then
/// the command's answer: the reporter writes no text after it, and no refusal line for the refusal with the
/// verdict's code. A refusal with another code, such as a lock that could not be released after the verdict,
/// still prints.
pub trait Renderer: Send {
    fn render(&mut self, event: &Event, scope: Option<&Scope>);
    fn finish(&mut self) -> Option<Box<Verdict>>;
    /// The trace viewer answers at `port`, so a run and a trace can be named by their link. `note` is printed
    /// beside the run's link, such as "the viewer is starting"; it may be empty.
    fn set_viewer(&mut self, _port: u16, _note: &str) {}
}

/// Receives every progress record, in every output mode, before any renderer runs. The run journal is one.
///
/// Called with the reporter's lock held, in publication order, so a sink must not publish.
pub trait Sink: Send {
    fn write(&mut self, record: &Record);
}

impl<F: FnMut(&Record) + Send> Sink for F {
    fn write(&mut self, record: &Record) {
        self(record);
    }
}

/// What a command answers: the structured data for JSON mode, and the prose for human mode.
///
/// An empty `text` means "nothing to print": a command with nothing to say and a command that says the empty
/// string are the same command. The data is serialized when the outcome is built, so a value that is not JSON is
/// the command's own error rather than a torn envelope.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct Outcome {
    pub data: serde_json::Value,
    pub text: String,
}

impl Outcome {
    pub fn new(data: impl Serialize, text: impl Into<String>) -> Result<Self, Refusal> {
        Ok(Self {
            data: serde_json::to_value(data)
                .map_err(|error| Refusal::internal(format!("cannot encode this command's own output: {error}")))?,
            text: text.into(),
        })
    }

    /// An outcome with data and no prose.
    pub fn data(data: impl Serialize) -> Result<Self, Refusal> {
        Self::new(data, String::new())
    }
}

struct Inner {
    stdout: SharedWriter,
    stderr: SharedWriter,
    /// The prefix on every prose line, `basename(argv[0])`: a message in a caller's log has to say which tool
    /// said it.
    program: String,
    clock: Arc<dyn Clock>,
    mode: Mode,
    /// The human renderer; present only in human mode.
    renderer: Option<Box<dyn Renderer>>,
    viewer: Option<(u16, String)>,
    sinks: Vec<(u64, Box<dyn Sink>)>,
    next_sink: u64,
}

/// Writes a command's envelope, and fans out the progress the command publishes.
///
/// Progress is a stream of [`Event`] values, published once. Every reader derives its own form from the one
/// event: the human [`Renderer`] draws it, `--stream` writes the NDJSON record, and each [`Sink`] receives the
/// record whatever the mode, so the line a person reads and the record an agent parses cannot disagree.
///
/// A clone is another handle to the same reporter. It holds a mutex, and that is not defensive: `shard` and
/// `flake` run several workers at once, each publishing, and a torn NDJSON line is not a degraded record but an
/// unparseable one.
#[derive(Clone)]
pub struct Reporter(Arc<Mutex<Inner>>);

impl Reporter {
    /// A reporter over explicit writers, in JSON mode.
    pub fn new(stdout: impl Write + Send + 'static, stderr: impl Write + Send + 'static, program: impl Into<String>) -> Self {
        Self(Arc::new(Mutex::new(Inner {
            stdout: SharedWriter::new(stdout),
            stderr: SharedWriter::new(stderr),
            program: program.into(),
            clock: Arc::new(SystemClock),
            mode: Mode::Json,
            renderer: None,
            viewer: None,
            sinks: Vec::new(),
            next_sink: 0,
        })))
    }

    /// A reporter over the process's stdout and stderr, named by [`program_name`].
    pub fn stdio() -> Self {
        Self::new(io::stdout(), io::stderr(), program_name())
    }

    /// A reporter over two [`Buffer`]s, which the test reads back: `(reporter, stdout, stderr)`.
    pub fn in_memory(program: impl Into<String>) -> (Self, Buffer, Buffer) {
        let (stdout, stderr) = (Buffer::new(), Buffer::new());
        (Self::new(stdout.clone(), stderr.clone(), program), stdout, stderr)
    }

    /// Replaces the clock that stamps each record and times each phase. Before [`Reporter::set_mode`], so the
    /// plain renderer reads the same clock.
    #[must_use]
    pub fn with_clock(self, clock: Arc<dyn Clock>) -> Self {
        self.inner().clock = clock;
        self
    }

    fn inner(&self) -> MutexGuard<'_, Inner> {
        lock(&self.0)
    }

    /// Selects the output form. Called once by `main` before any work starts; human mode starts with the plain
    /// renderer.
    pub fn set_mode(&self, mode: Mode) {
        let mut inner = self.inner();
        inner.mode = mode;
        inner.renderer = None;
        if let Mode::Human(terminal) = mode {
            let mut plain = Screen::new(
                inner.stdout.clone(),
                inner.stderr.clone(),
                inner.program.clone(),
                terminal,
                inner.clock.clone(),
            );
            if let Some((port, note)) = &inner.viewer {
                plain.set_viewer(*port, note);
            }
            inner.renderer = Some(Box::new(plain));
        }
    }

    pub fn mode(&self) -> Mode {
        self.inner().mode
    }

    pub fn program(&self) -> String {
        self.inner().program.clone()
    }

    pub fn now(&self) -> Timestamp {
        self.inner().clock.now()
    }

    /// Replaces the plain lines with `renderer`, in human mode; ignored otherwise. After [`Reporter::set_mode`]
    /// and before any work starts.
    pub fn set_renderer(&self, mut renderer: Box<dyn Renderer>) {
        let mut inner = self.inner();
        if !matches!(inner.mode, Mode::Human(_)) {
            return;
        }
        if let Some((port, note)) = &inner.viewer {
            renderer.set_viewer(*port, note);
        }
        inner.renderer = Some(renderer);
    }

    /// Says that the trace viewer answers, or will answer, at `port` on this machine. The human renderer then
    /// names a run and each scenario's trace by its viewer link rather than by a path.
    pub fn set_viewer(&self, port: u16, note: impl Into<String>) {
        let mut inner = self.inner();
        let note = note.into();
        if let Some(renderer) = inner.renderer.as_mut() {
            renderer.set_viewer(port, &note);
        }
        inner.viewer = Some((port, note));
    }

    /// Adds a sink until the answered guard drops.
    pub fn add_sink(&self, sink: impl Sink + 'static) -> SinkGuard {
        let mut inner = self.inner();
        let id = inner.next_sink;
        inner.next_sink += 1;
        inner.sinks.push((id, Box::new(sink)));
        SinkGuard {
            reporter: Arc::downgrade(&self.0),
            id,
        }
    }

    /// Reports one fact to every reader.
    #[expect(
        clippy::needless_pass_by_value,
        reason = "callers build the event inline; a borrow adds a `&` at every call site in five crates"
    )]
    pub fn publish(&self, event: Event, scope: Option<&Scope>) {
        self.inner().publish(&event, scope);
    }

    /// Reports a line of information that belongs to no phase.
    pub fn note(&self, message: impl Into<String>, scope: Option<&Scope>) {
        self.publish(Event::Note(message.into()), scope);
    }

    /// Publishes that a phase started, and answers the handle that reports its progress and its end.
    pub fn start_phase(&self, phase: Phase, detail: &str, scope: Option<&Scope>) -> PhaseRun {
        let mut inner = self.inner();
        let started = inner.clock.now();
        inner.publish(
            &Event::Phase(PhaseChange {
                phase,
                state: PhaseState::Started,
                detail: non_empty(detail),
                elapsed_ms: 0,
                error: None,
            }),
            scope,
        );
        PhaseRun {
            reporter: self.clone(),
            phase,
            scope: scope.cloned(),
            started,
            done: false,
        }
    }

    /// Writes the one success envelope, or the prose, depending on the mode.
    pub fn succeed(&self, command: &str, outcome: Outcome) {
        let mut inner = self.inner();
        let rendered = inner.finish_renderer();
        if !matches!(inner.mode, Mode::Human(_)) {
            let envelope = SuccessEnvelope {
                schema_version: SCHEMA_VERSION,
                ok: true,
                command,
                data: &outcome.data,
            };
            inner.write_json(Target::Stdout, &envelope);
            return;
        }
        if rendered.is_some() || outcome.text.is_empty() {
            return;
        }
        let mut text = outcome.text;
        if !text.ends_with('\n') {
            text.push('\n');
        }
        inner.stdout.emit(text.as_bytes());
    }

    /// Writes the one failure envelope, or the prose, and answers the status the process should leave with.
    ///
    /// An empty `command` is written as `null`: the invocation failed before a command was chosen, which is a
    /// different fact from a command named the empty string.
    pub fn refuse(&self, command: &str, failure: &Refusal) -> Exit {
        let mut inner = self.inner();
        let rendered = inner.finish_renderer();
        if matches!(inner.mode, Mode::Human(_)) {
            let answered = rendered
                .as_ref()
                .is_some_and(|verdict| verdict.code.as_deref() == Some(&*failure.code));
            if !answered {
                let line = format!("{}: {}\n", inner.program, failure.message);
                inner.stderr.emit(line.as_bytes());
            }
            return failure.exit;
        }
        let envelope = FailureEnvelope {
            schema_version: SCHEMA_VERSION,
            ok: false,
            command: non_empty(command),
            error: EnvelopeFailure {
                code: &failure.code,
                message: &failure.message,
                details: failure.details(),
            },
        };
        inner.write_json(Target::Stderr, &envelope);
        failure.exit
    }
}

impl fmt::Debug for Reporter {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let inner = self.inner();
        f.debug_struct("Reporter")
            .field("program", &inner.program)
            .field("mode", &inner.mode)
            .finish_non_exhaustive()
    }
}

#[derive(Clone, Copy)]
enum Target {
    Stdout,
    Stderr,
}

impl Inner {
    fn publish(&mut self, event: &Event, scope: Option<&Scope>) {
        let record = Record::new(event, self.clock.now(), scope.map(|scope| scope.worker.as_str()));
        for (_, sink) in &mut self.sinks {
            sink.write(&record);
        }
        if let Some(renderer) = self.renderer.as_mut() {
            renderer.render(event, scope);
        } else if self.mode == Mode::Stream {
            self.write_json(Target::Stderr, &record);
        }
    }

    /// Ends the human renderer before the command's answer is written, so the answer is the last thing on the
    /// terminal, and answers the verdict the renderer rendered as that answer.
    fn finish_renderer(&mut self) -> Option<Box<Verdict>> {
        self.renderer.take().and_then(|mut renderer| renderer.finish())
    }

    /// One JSON object and one newline. A value that does not encode is a programming error, and it is said on
    /// stderr rather than swallowed: the caller is waiting for exactly one object.
    fn write_json(&self, target: Target, value: &impl Serialize) {
        match serde_json::to_vec(value) {
            Ok(mut line) => {
                line.push(b'\n');
                match target {
                    Target::Stdout => self.stdout.emit(&line),
                    Target::Stderr => self.stderr.emit(&line),
                }
            }
            Err(error) => {
                let line = format!("{}: cannot encode this command's own output: {error}\n", self.program);
                self.stderr.emit(line.as_bytes());
            }
        }
    }
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct SuccessEnvelope<'a> {
    schema_version: u32,
    ok: bool,
    command: &'a str,
    data: &'a serde_json::Value,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct FailureEnvelope<'a> {
    schema_version: u32,
    ok: bool,
    command: Option<String>,
    error: EnvelopeFailure<'a>,
}

#[derive(Serialize)]
struct EnvelopeFailure<'a> {
    code: &'a str,
    message: &'a str,
    /// Written even when there is none, as `null`.
    details: Option<serde_json::Value>,
}

/// Removes its sink from the reporter when it drops.
#[must_use = "the sink is removed when the guard drops"]
pub struct SinkGuard {
    reporter: Weak<Mutex<Inner>>,
    id: u64,
}

impl Drop for SinkGuard {
    fn drop(&mut self) {
        if let Some(reporter) = self.reporter.upgrade() {
            lock(&reporter).sinks.retain(|(id, _)| *id != self.id);
        }
    }
}

/// One phase in flight, as [`Reporter::start_phase`] answers it.
///
/// Ending it consumes it, so it ends once. A phase dropped without an end (a `?` that returned early, a panic)
/// is published as failed: a green phase is something the code said, never something it forgot to say.
#[must_use = "a phase dropped without an end is reported as failed"]
pub struct PhaseRun {
    reporter: Reporter,
    phase: Phase,
    scope: Option<Scope>,
    started: Timestamp,
    done: bool,
}

impl PhaseRun {
    /// Reports what the phase is doing now, such as the last progress line of a build.
    pub fn progress(&self, detail: &str) {
        self.publish(PhaseState::Progress, detail, None);
    }

    /// The phase finished.
    pub fn succeed(mut self) {
        self.end(PhaseState::Finished, None);
    }

    /// The phase failed with `error`, whose whole chain is its message.
    pub fn fail(mut self, error: impl fmt::Display) {
        self.end(PhaseState::Failed, Some(format!("{error:#}")));
    }

    /// Finished for `Ok`, failed with the error otherwise.
    pub fn finish<T, E: fmt::Display>(self, result: &Result<T, E>) {
        match result {
            Ok(_) => self.succeed(),
            Err(error) => self.fail(error),
        }
    }

    pub const fn phase(&self) -> Phase {
        self.phase
    }

    fn end(&mut self, state: PhaseState, error: Option<String>) {
        self.done = true;
        self.publish(state, "", error);
    }

    fn publish(&self, state: PhaseState, detail: &str, error: Option<String>) {
        let mut inner = self.reporter.inner();
        let elapsed = clock::elapsed(self.started, inner.clock.now());
        inner.publish(
            &Event::Phase(PhaseChange {
                phase: self.phase,
                state,
                detail: non_empty(detail),
                elapsed_ms: millis(elapsed),
                error,
            }),
            self.scope.as_ref(),
        );
    }
}

impl Drop for PhaseRun {
    fn drop(&mut self) {
        if !self.done {
            self.end(PhaseState::Failed, Some("the phase ended without saying how".to_owned()));
        }
    }
}

/// `basename(argv[0])`, the prefix every prose line carries; `vm` when there is none.
pub fn program_name() -> String {
    std::env::args_os()
        .next()
        .and_then(|argv0| Path::new(&argv0).file_name().map(|name| name.to_string_lossy().into_owned()))
        .unwrap_or_else(|| "vm".to_owned())
}

/// Runs `body` and answers the process's exit status, having written exactly one envelope.
///
/// The status is answered rather than exited with, so every `Drop` (a lock released, a temporary removed) still
/// runs: `main` returns it as its `ExitCode`.
pub fn main(command: &str, reporter: &Reporter, body: impl FnOnce() -> Result<Outcome, Refusal>) -> Exit {
    match body() {
        Ok(outcome) => {
            reporter.succeed(command, outcome);
            Exit::OK
        }
        Err(refusal) => reporter.refuse(command, &refusal),
    }
}

fn non_empty(text: &str) -> Option<String> {
    (!text.is_empty()).then(|| text.to_owned())
}
