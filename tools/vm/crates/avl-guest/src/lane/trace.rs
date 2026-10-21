//! The trace of an iteration: the recorder process, and the lane's half of the lane protocol of
//! [`avl_trace::protocol`].
//!
//! The lane daemon starts the recorder once per iteration, with no argument, and roots its bundles at
//! `<iterationDir>/air-traces`. Under a Bazel test, `lane-run` roots them at `$TEST_UNDECLARED_OUTPUTS_DIR/air-traces`
//! instead, so Bazel keeps them. It sends `hello`, then per scenario `scenario`, the spans, one
//! `call` per bridge request, the snapshots, the attachments and `done`. Its standard input reaching EOF ends the
//! session, so the recorder finishes a truncated bundle when the lane daemon dies.
//!
//! The session owns what the recorder cannot work out: the span ids, numbered from 1 in each scenario in the order the
//! spans open; the one failure snapshot of a scenario; and two bounds on the waits for acks. A scenario stops waiting
//! after [`MAX_MISSED_ACKS`] unhealthy acks in a row, and its snapshots may keep it waiting [`SNAP_WAIT_ALLOWANCE`] in
//! all. Evidence never decides a verdict, so nothing here fails a scenario: a recorder that is gone makes the session
//! quiet.

use std::fs;
use std::io::{BufRead, BufReader, Write};
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::mpsc::{self, Receiver, RecvTimeoutError, SyncSender, TrySendError};
use std::sync::{Arc, Mutex, PoisonError};
use std::thread;
use std::time::{Duration, Instant};

use avl_trace::protocol::{
    self, AttachCommand, Bridge, CallCommand, DoneCommand, EndCommand, HelloCommand, Launcher, Op, ScenarioCommand, Side, SnapCommand,
    SnapshotPhase, SpanCommand, SpanKind, Status,
};

use super::channel::log;
use super::inputs::TraceInputs;
use super::watchdog::slug;

#[cfg(test)]
pub(crate) mod tests;

/// The directory of the bundles in the directory of an iteration, `avl_trace::bundle::ROOT_DIR_NAME`.
pub(crate) const ROOT_DIR_NAME: &str = avl_trace::bundle::ROOT_DIR_NAME;

/// How long the lane waits for the ack of a snapshot, so the moment is captured before the next instruction runs.
const SNAP_ACK: Duration = Duration::from_secs(2);
/// How long the lane waits for the ack of `hello` and of `done`.
const DONE_ACK: Duration = Duration::from_secs(10);
/// How long the lane waits for the ack of `scenario`, which only says that the bundle directory exists.
const SCENARIO_ACK: Duration = Duration::from_secs(5);
/// A snapshot whose ack takes longer counts as missed.
const SLOW_SNAP_ACK: Duration = Duration::from_secs(1);
/// What the snapshots of one scenario may cost it in waiting, in all.
const SNAP_WAIT_ALLOWANCE: Duration = Duration::from_secs(10);
const MAX_MISSED_ACKS: u32 = 3;

/// How much of the error text of a failed call the `call` record carries.
const MAX_CALL_ERROR_CHARS: usize = 2_000;
/// How much of a failure message the wire carries.
const MAX_FAILURE_MESSAGE_CHARS: usize = 16_000;
/// How many lines of a stack the wire carries.
const MAX_STACK_LINES: usize = 200;

/// How many lines the writer of the recorder input may lag behind.
const MAX_PENDING_LINES: usize = 1_024;
/// A write this old is a recorder that stopped reading.
const STUCK_WRITE: Duration = Duration::from_secs(5);
/// How long the recorder gets to exit after EOF before the lane kills it.
const EXIT_GRACE: Duration = Duration::from_secs(30);

/// The directory of the files that the lane hands to the recorder, below `<root>/<runId>`.
const ATTACH_DIR_NAME: &str = "attach";
/// What the attachments outside a recorded scenario may take of the loose directory of `lane-run`, in bytes.
pub(crate) const MAX_LOOSE_BYTES: u64 = 64 << 20;

/// The recorder's answer to one acked line.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct Ack {
    pub ok: bool,
    /// The 1-based number of the command line it answers.
    pub line: u32,
    pub error: Option<String>,
    pub capture: Option<String>,
    pub video: Option<String>,
    pub reason: Option<String>,
}

impl Ack {
    /// Reads one ack line, or `None` for a line that is no ack, such as a line of the recorder's own log.
    pub(crate) fn decode(line: &str) -> Option<Self> {
        let value: serde_json::Value = serde_json::from_str(line).ok()?;
        let text = |name: &str| value.get(name).and_then(serde_json::Value::as_str).map(str::to_owned);
        Some(Self {
            ok: value.get("ok").and_then(serde_json::Value::as_bool).unwrap_or(false),
            line: u32::try_from(value.get("line")?.as_u64()?).ok()?,
            error: text("error"),
            capture: text("capture"),
            video: text("video"),
            reason: text("reason"),
        })
    }
}

/// Where the lines of the session go, and where their acks come from: the recorder process, or a recording in a test.
pub(crate) trait Transport: Send {
    /// Hands `line` to the recorder. With `ack_within`, it waits that long at most for the ack of this line. A failure
    /// means the recorder is gone.
    fn send(&mut self, line: Vec<u8>, ack_within: Option<Duration>) -> Result<Option<Ack>, String>;

    /// Ends the session with EOF, and gives the recorder a bounded time to finish its bundle.
    fn close(&mut self);
}

/// The recorders that this process runs, by pid. The monitor of `lane-run` kills them at the hard stop, because the run
/// thread that owns them is then the thread that does not return.
static RUNNING_RECORDERS: Mutex<Vec<u32>> = Mutex::new(Vec::new());

/// The pids of the recorders that this process runs now.
pub(crate) fn running_recorders() -> Vec<u32> {
    RUNNING_RECORDERS.lock().unwrap_or_else(PoisonError::into_inner).clone()
}

/// The `air-trace-record` process, over its standard input and output.
///
/// No pipe write happens on the run thread: a recorder that stopped reading would block it in the kernel. One thread
/// writes the queued lines, one reads the acks, and one forwards the recorder's standard error to the daemon log.
pub(crate) struct RecorderProcess {
    child: Child,
    lines: Option<SyncSender<Vec<u8>>>,
    acks: Receiver<Ack>,
    writing_since: Arc<Mutex<Option<Instant>>>,
    queued: u32,
    gone: Option<String>,
}

impl RecorderProcess {
    /// Starts the recorder `binary` with no argument.
    pub(crate) fn start(binary: &Path) -> Result<Self, String> {
        let mut child = Command::new(binary)
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::piped())
            .spawn()
            .map_err(|error| format!("cannot start the recorder {}: {error}", binary.display()))?;
        let (Some(mut input), Some(output), Some(errors)) = (child.stdin.take(), child.stdout.take(), child.stderr.take()) else {
            let _ = child.kill();
            return Err("the recorder has no pipes".to_owned());
        };
        RUNNING_RECORDERS.lock().unwrap_or_else(PoisonError::into_inner).push(child.id());
        let (lines, pending) = mpsc::sync_channel::<Vec<u8>>(MAX_PENDING_LINES);
        let writing_since = Arc::new(Mutex::new(None));
        {
            let writing_since = Arc::clone(&writing_since);
            thread::Builder::new()
                .name("lane-trace-input".to_owned())
                .spawn(move || {
                    for mut line in pending {
                        *writing_since.lock().unwrap_or_else(PoisonError::into_inner) = Some(Instant::now());
                        line.push(b'\n');
                        let written = input.write_all(&line).and_then(|()| input.flush());
                        *writing_since.lock().unwrap_or_else(PoisonError::into_inner) = None;
                        if written.is_err() {
                            return;
                        }
                    }
                })
                .map_err(|error| format!("cannot start the recorder input thread: {error}"))?;
        }
        let (answered, acks) = mpsc::channel();
        thread::Builder::new()
            .name("lane-trace-acks".to_owned())
            .spawn(move || {
                for line in BufReader::new(output).lines().map_while(Result::ok) {
                    match Ack::decode(&line) {
                        Some(ack) => {
                            if answered.send(ack).is_err() {
                                return;
                            }
                        }
                        None => log(&format!("recorder: {line}")),
                    }
                }
            })
            .map_err(|error| format!("cannot start the recorder ack thread: {error}"))?;
        thread::Builder::new()
            .name("lane-trace-errors".to_owned())
            .spawn(move || {
                for line in BufReader::new(errors).lines().map_while(Result::ok) {
                    log(&format!("recorder: {line}"));
                }
            })
            .map_err(|error| format!("cannot start the recorder error thread: {error}"))?;
        Ok(Self {
            child,
            lines: Some(lines),
            acks,
            writing_since,
            queued: 0,
            gone: None,
        })
    }

    /// Waits [`EXIT_GRACE`] for the recorder to exit after EOF, and kills it after that.
    fn await_exit(&mut self) {
        let deadline = Instant::now() + EXIT_GRACE;
        loop {
            match self.child.try_wait() {
                Ok(Some(_)) => return,
                Ok(None) if Instant::now() < deadline => thread::sleep(Duration::from_millis(100)),
                _ => break,
            }
        }
        log(&format!(
            "the recorder did not exit within {} s of EOF; killing it",
            EXIT_GRACE.as_secs()
        ));
        let _ = self.child.kill();
        let _ = self.child.wait();
    }

    fn gone_because(&mut self, reason: String) -> String {
        self.gone.get_or_insert(reason).clone()
    }
}

impl Transport for RecorderProcess {
    fn send(&mut self, line: Vec<u8>, ack_within: Option<Duration>) -> Result<Option<Ack>, String> {
        if let Some(reason) = &self.gone {
            return Err(reason.clone());
        }
        let stuck = (*self.writing_since.lock().unwrap_or_else(PoisonError::into_inner)).is_some_and(|since| since.elapsed() > STUCK_WRITE);
        if stuck {
            return Err(self.gone_because(format!(
                "the recorder has not read a line of its input for over {} s",
                STUCK_WRITE.as_secs()
            )));
        }
        let Some(lines) = &self.lines else {
            return Err(self.gone_because("the recorder's input is closed".to_owned()));
        };
        match lines.try_send(line) {
            Ok(()) => {}
            Err(TrySendError::Full(_)) => {
                return Err(self.gone_because(format!("the recorder is {MAX_PENDING_LINES} lines behind its input")));
            }
            Err(TrySendError::Disconnected(_)) => return Err(self.gone_because("the recorder's input broke".to_owned())),
        }
        self.queued += 1;
        let Some(within) = ack_within else { return Ok(None) };
        let deadline = Instant::now() + within;
        loop {
            let left = deadline.saturating_duration_since(Instant::now());
            match self.acks.recv_timeout(left) {
                Ok(ack) if ack.line == self.queued => return Ok(Some(ack)),
                Ok(ack) => {
                    if !ack.ok {
                        log(&format!(
                            "the recorder refused line {}: {}",
                            ack.line,
                            ack.error.as_deref().unwrap_or("no reason given")
                        ));
                    }
                }
                Err(RecvTimeoutError::Timeout) => return Ok(None),
                Err(RecvTimeoutError::Disconnected) => return Err(self.gone_because("the recorder closed its output".to_owned())),
            }
        }
    }

    fn close(&mut self) {
        self.gone_because("the recorder's input is closed".to_owned());
        // EOF: the input thread ends when the queue closes, and then drops the pipe.
        self.lines = None;
        self.await_exit();
        let pid = self.child.id();
        RUNNING_RECORDERS
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
            .retain(|running| *running != pid);
    }
}

impl Drop for RecorderProcess {
    fn drop(&mut self) {
        if self.lines.is_some() {
            self.close();
        }
    }
}

/// Why a span or a scenario did not pass, as the wire carries it.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct TraceFailure {
    pub type_name: String,
    pub message: Option<String>,
    /// The stack lines, each `at <frame>` or `Caused by: <type>: <message>`, without the header line.
    pub stack: Vec<String>,
    /// A failure that did not reach a verdict: a scenario that never ran, a step the watchdog ended, or a body that
    /// aborted its scenario.
    pub aborted: bool,
}

impl TraceFailure {
    fn wire(&self) -> protocol::Failure {
        protocol::Failure {
            r#type: self.type_name.clone(),
            message: self.message.as_ref().map(|message| cap(message, MAX_FAILURE_MESSAGE_CHARS)),
            stack: self.stack.iter().take(MAX_STACK_LINES).cloned().collect(),
        }
    }

    const fn status(&self) -> Status {
        if self.aborted { Status::Aborted } else { Status::Failed }
    }
}

/// The two bounds of the waits of one scenario for its acks.
#[derive(Clone, Copy, Debug)]
struct AckBounds {
    missed: u32,
    waits_for_acks: bool,
    snap_wait: Duration,
    waits_for_snaps: bool,
}

impl AckBounds {
    const FRESH: Self = Self {
        missed: 0,
        waits_for_acks: true,
        snap_wait: Duration::ZERO,
        waits_for_snaps: true,
    };
}

/// One recorder session, over one transport.
pub(crate) struct Trace {
    transport: Option<Box<dyn Transport>>,
    scenario_open: bool,
    next_span: u32,
    open_spans: Vec<u32>,
    failure_snapped: bool,
    bounds: AckBounds,
    attach_dir: Option<PathBuf>,
    attach_count: u32,
    /// Where an attachment outside a recorded scenario goes, or `None` to drop it. See [`Trace::keep_loose_attachments_in`].
    loose_dir: Option<PathBuf>,
    /// What the attachments in the loose directory may take, in bytes.
    loose_cap: u64,
    loose_bytes: u64,
    /// Whether the session met a failure that loses a trace, so a later one stays in the log only.
    failed: bool,
    /// The first such failure, until [`Trace::take_failure`] takes it for the stream.
    unreported: Option<String>,
}

impl Trace {
    /// A session that records nothing because tracing is off.
    pub(crate) const fn off() -> Self {
        Self {
            transport: None,
            scenario_open: false,
            next_span: 1,
            open_spans: Vec::new(),
            failure_snapped: false,
            bounds: AckBounds::FRESH,
            attach_dir: None,
            attach_count: 0,
            loose_dir: None,
            loose_cap: 0,
            loose_bytes: 0,
            failed: false,
            unreported: None,
        }
    }

    /// A session that records nothing because the recorder did not start, for `reason`.
    pub(crate) fn failed(reason: String) -> Self {
        let mut trace = Self::off();
        trace.fail(reason);
        trace
    }

    /// The session with `directory` for the attachments outside a recorded scenario: before a scenario opens, after the
    /// recorder refused one, after the recorder went away, and with the trace off. The attachments there take at most
    /// `cap` bytes. `lane-run` names `$TEST_UNDECLARED_OUTPUTS_DIR/lane/attachments` with [`MAX_LOOSE_BYTES`], so Bazel
    /// keeps them. The lane daemon of a worker names no directory, so it drops them.
    pub(crate) fn keep_loose_attachments_in(mut self, directory: Option<PathBuf>, cap: u64) -> Self {
        self.loose_dir = directory;
        self.loose_cap = cap;
        self
    }

    /// Answers, once, the first failure that lost a trace: a recorder that did not start, refused the hello or a
    /// scenario, or went away. The runner says it on the stream, so a run without bundles names its cause.
    pub(crate) const fn take_failure(&mut self) -> Option<String> {
        self.unreported.take()
    }

    fn fail(&mut self, reason: String) {
        if !self.failed {
            self.failed = true;
            self.unreported = Some(reason);
        }
    }

    /// A session over `transport`, opened with `hello`. A recorder that refuses the hello makes the session quiet.
    pub(crate) fn open(transport: Box<dyn Transport>, hello: HelloCommand) -> Self {
        let mut trace = Self::off();
        trace.transport = Some(transport);
        trace.attach_dir = Some(Path::new(&hello.root).join(&hello.run_id).join(ATTACH_DIR_NAME));
        let root = hello.root.clone();
        let run_id = hello.run_id.clone();
        match trace.send(&protocol::Command::Hello(hello)) {
            None => log(&format!(
                "the recorder did not answer hello; recording to {root} without its answer"
            )),
            Some(ack) if !ack.ok => {
                let reason = format!(
                    "the recorder refused to record: {}",
                    ack.error.or(ack.reason).as_deref().unwrap_or("no reason given")
                );
                log(&reason);
                trace.fail(reason);
                trace.quiet();
            }
            Some(ack) => log(&format!(
                "recording run {run_id} under {root}: capture {}, video {}{}",
                ack.capture.as_deref().unwrap_or("?"),
                ack.video.as_deref().unwrap_or("?"),
                ack.reason.map(|reason| format!(" ({reason})")).unwrap_or_default()
            )),
        }
        trace
    }

    /// The `hello` of the iteration directory `iteration_dir`, whose run id is `run_id`. The root is the `air-traces` of
    /// the Bazel outputs of `inputs` with the launcher `bazel` when a Bazel test names its outputs directory, else the
    /// `air-traces` of the iteration directory with the launcher `daemon`. The screen and the Allure results are the
    /// ones of `inputs`.
    pub(crate) fn hello(iteration_dir: &Path, run_id: &str, inputs: &TraceInputs, bridge: Option<Bridge>) -> HelloCommand {
        let (parent, launcher) = inputs
            .bazel_outputs
            .as_deref()
            .map_or((iteration_dir, Launcher::Daemon), |outputs| (outputs, Launcher::Bazel));
        HelloCommand {
            root: parent.join(ROOT_DIR_NAME).display().to_string(),
            run_id: run_id.to_owned(),
            launcher,
            screen: inputs.screen,
            allure: inputs.allure,
            bridge,
        }
    }

    /// Opens one scenario. A scenario that the previous one never finished is closed first.
    pub(crate) fn open_scenario(&mut self, command: ScenarioCommand) {
        if self.transport.is_none() {
            return;
        }
        if self.scenario_open {
            self.finish_scenario(Some(&TraceFailure {
                type_name: "lane_scenario_unfinished".to_owned(),
                message: Some("the next scenario started before this one finished".to_owned()),
                stack: Vec::new(),
                aborted: true,
            }));
        }
        self.next_span = 1;
        self.open_spans.clear();
        self.failure_snapped = false;
        self.bounds = AckBounds::FRESH;
        self.scenario_open = true;
        let name = command.name.clone();
        // A scenario that the recorder refused has no bundle, so it gets no further command.
        if let Some(ack) = self.send(&protocol::Command::Scenario(command))
            && !ack.ok
        {
            self.scenario_open = false;
            self.fail(format!(
                "the recorder refused the scenario '{name}': {}",
                ack.error.as_deref().unwrap_or("no reason given")
            ));
        }
    }

    /// Opens a span under the innermost open one, and answers its id, or 0 when no scenario is recorded.
    pub(crate) fn open_span(&mut self, kind: SpanKind, id: &str, title: &str, instruction: Option<(Option<&str>, Side, u64)>) -> u32 {
        if !self.recording() {
            return 0;
        }
        let span = self.next_span;
        self.next_span += 1;
        let (expectation, side, timeout_ms) = match instruction {
            Some((expectation, side, timeout_ms)) if kind.is_instruction() => {
                (expectation.filter(|_| kind == SpanKind::Assertion), Some(side), Some(timeout_ms))
            }
            _ => (None, None, None),
        };
        self.send(&protocol::Command::Span(SpanCommand {
            id: span,
            parent: self.open_spans.last().copied().unwrap_or(0),
            kind,
            key: protocol::span_key(kind, id),
            title: title.to_owned(),
            expectation: expectation.map(str::to_owned),
            side,
            timeout_ms,
        }));
        self.open_spans.push(span);
        span
    }

    /// Ends the span `span`. A failure is photographed in the span that failed first, before the spans it unwinds
    /// through end too.
    pub(crate) fn end_span(&mut self, span: u32, failure: Option<&TraceFailure>) {
        if !self.recording() || !self.open_spans.contains(&span) {
            return;
        }
        if failure.is_some_and(|failure| !failure.aborted) {
            self.snap_failure();
        }
        while self.open_spans.last() != Some(&span) {
            let Some(inner) = self.open_spans.pop() else { break };
            self.send(&protocol::Command::End(EndCommand {
                id: inner,
                status: Status::Aborted,
                error: None,
            }));
        }
        self.open_spans.pop();
        self.send(&protocol::Command::End(EndCommand {
            id: span,
            status: failure.map_or(Status::Passed, TraceFailure::status),
            error: failure.map(TraceFailure::wire),
        }));
    }

    /// Asks for a snapshot in the innermost open span.
    pub(crate) fn snap(&mut self, phase: SnapshotPhase) {
        if !self.recording() {
            return;
        }
        self.send(&protocol::Command::Snap(SnapCommand {
            span: self.open_spans.last().copied().unwrap_or(0),
            phase,
        }));
    }

    /// The one failure snapshot of the scenario, unless one was taken.
    pub(crate) fn snap_failure(&mut self) {
        if !self.recording() || self.failure_snapped {
            return;
        }
        self.failure_snapped = true;
        self.snap(SnapshotPhase::Failure);
    }

    /// One `call` record in the innermost open span. `error` is `None` for a call that held.
    pub(crate) fn call(&mut self, request: &str, start_ms: i64, duration_ms: i64, error: Option<&str>) {
        if !self.recording() || start_ms <= 0 {
            return;
        }
        self.send(&protocol::Command::Call(CallCommand {
            span: self.open_spans.last().copied().unwrap_or(0),
            request: request.to_owned(),
            start_ms,
            duration_ms: duration_ms.max(0),
            ok: error.is_none(),
            error: error.map(|error| cap(error, MAX_CALL_ERROR_CHARS)),
        }));
    }

    /// Tells the recorder that the IDE is about to restart, inside the `restart` span.
    pub(crate) fn restart(&mut self) {
        if self.recording() {
            self.send(&protocol::Command::Restart);
        }
    }

    /// Writes `bytes` to `<root>/<runId>/attach/<n>.<extension>` and hands the file to the recorder as an attachment of
    /// the innermost open span. Answers whether the command went out; only then does the file belong to the recorder.
    /// Outside a recorded scenario the bytes go to `<n>-<name>.<extension>` of the loose directory instead, within the
    /// loose cap.
    pub(crate) fn attach(&mut self, name: &str, mime: &str, bytes: &[u8], extension: &str) -> bool {
        if !self.recording() {
            self.keep_loose(name, bytes, extension);
            return false;
        }
        let Some(directory) = self.attach_dir.clone() else { return false };
        self.attach_count += 1;
        let file = directory.join(format!("{}.{extension}", self.attach_count));
        if let Err(error) = fs::create_dir_all(&directory).and_then(|()| fs::write(&file, bytes)) {
            log(&format!("the attachment {name} cannot be written to {}: {error}", file.display()));
            return false;
        }
        self.send(&protocol::Command::Attach(AttachCommand {
            name: name.to_owned(),
            mime: mime.to_owned(),
            file: file.display().to_string(),
            span: self.open_spans.last().copied().unwrap_or(0),
        }));
        self.transport.is_some()
    }

    /// Writes one attachment outside a recorded scenario into the loose directory, while the attachments there stay within
    /// the loose cap. A failed write and the first attachment past the cap are a line of the log.
    fn keep_loose(&mut self, name: &str, bytes: &[u8], extension: &str) {
        let Some(directory) = &self.loose_dir else { return };
        let size = u64::try_from(bytes.len()).unwrap_or(u64::MAX);
        let cap = self.loose_cap;
        if self.loose_bytes.saturating_add(size) > cap {
            if self.loose_bytes < cap {
                log(&format!(
                    "the attachment {name} outside a recorded scenario is dropped, and so is every later one: the \
                     attachments in {} reached the cap of {cap} bytes",
                    directory.display()
                ));
                self.loose_bytes = cap;
            }
            return;
        }
        self.attach_count += 1;
        let file = directory.join(format!("{:03}-{}.{extension}", self.attach_count, slug(name)));
        match fs::create_dir_all(directory).and_then(|()| fs::write(&file, bytes)) {
            Ok(()) => self.loose_bytes += size,
            Err(error) => log(&format!("the attachment {name} cannot be written to {}: {error}", file.display())),
        }
    }

    /// Ends the scenario: each span still open is aborted, then `done` with the status, message and trace of `failure`.
    pub(crate) fn finish_scenario(&mut self, failure: Option<&TraceFailure>) {
        if !self.recording() {
            return;
        }
        while let Some(span) = self.open_spans.pop() {
            self.send(&protocol::Command::End(EndCommand {
                id: span,
                status: Status::Aborted,
                error: None,
            }));
        }
        let wire = failure.map(TraceFailure::wire);
        self.send(&protocol::Command::Done(DoneCommand {
            status: failure.map_or(Status::Passed, TraceFailure::status),
            message: wire.as_ref().and_then(|wire| wire.message.clone()),
            trace: wire.map(|wire| std::iter::once(wire.r#type).chain(wire.stack).collect::<Vec<_>>().join("\n")),
        }));
        self.scenario_open = false;
    }

    /// Sends EOF. A scenario still open stays open, which the recorder records as truncated.
    pub(crate) fn close(&mut self) {
        if let Some(mut transport) = self.transport.take() {
            transport.close();
        }
    }

    /// Whether a scenario is recorded now: the recorder runs, and it took the open scenario.
    pub(crate) const fn recording(&self) -> bool {
        self.transport.is_some() && self.scenario_open
    }

    fn quiet(&mut self) {
        if let Some(mut transport) = self.transport.take() {
            transport.close();
        }
    }

    fn send(&mut self, command: &protocol::Command) -> Option<Ack> {
        let transport = self.transport.as_mut()?;
        let line = match protocol::encode_command(command) {
            Ok(line) => line,
            Err(error) => {
                log(&format!(
                    "the {} command cannot be encoded, so it is dropped: {error}",
                    command.op()
                ));
                return None;
            }
        };
        let op = command.op();
        let snapshot = op == Op::Snap;
        let waits = op.is_acked() && self.bounds.waits_for_acks && (!snapshot || self.bounds.waits_for_snaps);
        let ack_within = waits.then_some(match op {
            Op::Snap => SNAP_ACK,
            Op::Done | Op::Hello => DONE_ACK,
            _ => SCENARIO_ACK,
        });
        let sent_at = Instant::now();
        let ack = match transport.send(line, ack_within) {
            Ok(ack) => ack,
            Err(reason) => {
                let reason = format!("the recorder is gone, so the rest of this run is not traced: {reason}");
                log(&reason);
                self.fail(reason);
                self.quiet();
                return None;
            }
        };
        if ack_within.is_some() {
            self.note_wait(snapshot, ack.as_ref(), sent_at.elapsed());
        }
        if let Some(ack) = &ack
            && !ack.ok
            && op != Op::Hello
        {
            log(&format!(
                "the recorder refused the {op} command: {}",
                ack.error.as_deref().unwrap_or("no reason given")
            ));
        }
        ack
    }

    /// Counts one wait against the two bounds of the scenario.
    fn note_wait(&mut self, snapshot: bool, ack: Option<&Ack>, waited: Duration) {
        let bounds = &mut self.bounds;
        let healthy = ack.is_some_and(|ack| !snapshot || (ack.ok && waited < SLOW_SNAP_ACK));
        if healthy {
            bounds.missed = 0;
        } else {
            bounds.missed += 1;
            if bounds.missed >= MAX_MISSED_ACKS && bounds.waits_for_acks {
                bounds.waits_for_acks = false;
                log(&format!(
                    "the recorder answered {} acks in a row late, negatively or not at all; the rest of this scenario does \
                     not wait for its answers",
                    bounds.missed
                ));
            }
        }
        if !snapshot {
            return;
        }
        bounds.snap_wait += waited;
        if bounds.waits_for_snaps && bounds.snap_wait >= SNAP_WAIT_ALLOWANCE {
            bounds.waits_for_snaps = false;
            log(&format!(
                "the snapshots of this scenario kept the lane waiting {} s; the rest are not waited for",
                SNAP_WAIT_ALLOWANCE.as_secs()
            ));
        }
    }
}

impl Drop for Trace {
    fn drop(&mut self) {
        self.close();
    }
}

/// The first `limit` characters of `text`, and a note of how many more there were.
fn cap(text: &str, limit: usize) -> String {
    let count = text.chars().count();
    if count <= limit {
        return text.to_owned();
    }
    let kept: String = text.chars().take(limit).collect();
    format!("{kept}… ({} more characters)", count - limit)
}
