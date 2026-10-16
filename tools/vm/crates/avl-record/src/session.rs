//! One recorder process's conversation with its lane.

use std::fs;
use std::io::Write;
use std::path::Path;
use std::sync::Arc;
use std::time::{Duration, Instant, SystemTime};

use avl_trace::bridge::Facts;
use avl_trace::bundle::{AllureStatusDetails, BundleStatus, IDE_ROOT_RETAINED_RUNS, sanitize_name};
use avl_trace::protocol::{Ack, Bridge, CaptureSource, Command, HelloAck, HelloCommand, Launcher, VideoCodec, decode_command};

use crate::bridge::Client;
use crate::capture::Source;
use crate::ladder::ScreenAccess;
use crate::retention::prune_runs;
use crate::scenario::{ERROR_SOURCE_PROTOCOL, FLUSHED_SPANS_TIMEOUT, Scenario};
use crate::{Options, join_reasons};

#[cfg(test)]
mod tests;

/// Bounds one read of the facts route. The lane waits on the hello, scenario and done acks that read them, so a
/// bridge that is slow to answer costs a bundle its log slice rather than the lane its time.
const FACTS_TIMEOUT: Duration = Duration::from_secs(1);

/// One recorder process's conversation with its lane: the hello, then scenarios one after another.
///
/// It is driven one line at a time by [Session::handle], on one thread, which is what lets the replay test feed it
/// the golden transcript with a scripted clock.
pub(crate) struct Session<W: Write> {
    pub(crate) options: Options,
    acks: W,
    /// Counts every line read, so each ack names the line it answers.
    pub(crate) line: u32,
    pub(crate) hello: Option<HelloCommand>,
    /// Why this session records nothing, after a hello whose root could not be created.
    disabled: Option<String>,
    /// The endpoint the client asks, the hello's or a later scenario's.
    bridge: Option<Bridge>,
    pub(crate) client: Option<Client>,
    pub(crate) source: Option<Arc<dyn Source>>,
    /// Why no better rung than the source was available.
    pub(crate) source_reason: String,
    pub(crate) scenario: Option<Scenario>,
}

impl<W: Write> Session<W> {
    pub(crate) fn new(options: Options, acks: W) -> Self {
        Self {
            options,
            acks,
            line: 0,
            hello: None,
            disabled: None,
            bridge: None,
            client: None,
            source: None,
            source_reason: String::new(),
            scenario: None,
        }
    }

    /// Serves one line that arrived `at`.
    pub(crate) fn handle(&mut self, content: &[u8], at: SystemTime) {
        self.line += 1;
        let command = match decode_command(content) {
            Ok(command) => command,
            Err(error) => return self.refuse(at, error.message()),
        };
        let command = match command {
            Command::Hello(hello) => return self.say_hello(&hello),
            command => command,
        };
        if self.hello.is_none() {
            return self.refuse(at, &format!("a {} before the hello", command.op()));
        }
        if let Some(disabled) = &self.disabled {
            if command.op().is_acked() {
                let error = format!("this session records nothing: {disabled}");
                self.ack(false, Some(error));
            }
            return;
        }
        let command = match command {
            Command::Scenario(scenario) => return self.open_scenario(scenario, at),
            command => command,
        };
        if let Command::Done(done) = command {
            if self.scenario.is_none() {
                return self.refuse(at, "a done outside a scenario");
            }
            let details = AllureStatusDetails {
                message: done.message,
                trace: done.trace,
            };
            self.finish_scenario(done.status.into(), details, at);
            return self.ack(true, None);
        }
        let op = command.op();
        let budget = self.snap_budget();
        let Some(scenario) = self.scenario.as_mut() else {
            return self.refuse(at, &format!("a {op} outside a scenario"));
        };
        let problem = match command {
            Command::Span(span) => scenario.open_span(&span, at).err(),
            Command::End(end) => scenario.end_span(&end, at).err(),
            Command::Snap(snap) => {
                if let Err(problem) = scenario.check_snap(&snap) {
                    Some(problem)
                } else {
                    let failure = scenario.snap(&snap, at, self.client.as_ref(), budget);
                    return self.ack(failure.is_empty(), (!failure.is_empty()).then_some(failure));
                }
            }
            Command::Call(call) => scenario.call(&call).err(),
            Command::Restart => {
                say!(self.options.diagnostics, "{}: the IDE restarts", scenario.command.name);
                // The IDE that goes away takes its unexported spans with it, and the one that comes up starts a ring
                // of its own.
                scenario.read_ide_spans(
                    self.client.as_ref(),
                    true,
                    Instant::now() + FLUSHED_SPANS_TIMEOUT,
                    crate::unix_ms(at),
                );
                None
            }
            Command::Attach(attach) => {
                let root = self.hello.as_ref().map_or("", |hello| hello.root.as_str());
                scenario.attach(&attach, Path::new(root), at).err()
            }
            Command::Hello(_) | Command::Scenario(_) | Command::Done(_) => None,
        };
        if let Some(problem) = problem {
            self.refuse(at, &problem);
        }
    }

    const fn snap_budget(&self) -> Duration {
        if self.options.snap_budget.is_zero() {
            crate::DEFAULT_SNAP_BUDGET
        } else {
            self.options.snap_budget
        }
    }

    /// Opens the session: the run's directory, the bridge, the capture ladder and the video plan.
    fn say_hello(&mut self, hello: &HelloCommand) {
        if self.hello.is_some() {
            let now = (self.options.clock)();
            return self.refuse(now, "a second hello: one recorder serves one root");
        }
        let run = sanitize_name(&hello.run_id).into_owned();
        let run_dir = Path::new(&hello.root).join(&run);
        self.hello = Some(hello.clone());
        if let Err(error) = fs::create_dir_all(&run_dir) {
            let disabled = format!("cannot create {}: {error}", run_dir.display());
            self.disabled = Some(disabled.clone());
            return self.hello_ack(false, CaptureSource::None, VideoCodec::None, disabled);
        }
        if hello.launcher == Launcher::Ide
            && let Err(error) = prune_runs(Path::new(&hello.root), &run, IDE_ROOT_RETAINED_RUNS)
        {
            say!(self.options.diagnostics, "pruning {}: {error}", hello.root);
        }
        self.use_bridge(hello.bridge.as_ref());
        let facts = self.facts().ok();
        self.climb(facts.as_ref());
        let plan = self.options.video.plan(self.source.as_deref(), hello.screen);
        let reason = join_reasons([self.source_reason.as_str(), plan.reason.as_str()]);
        self.hello_ack(true, source_kind(self.source.as_deref()), plan.codec, reason);
    }

    /// Makes `endpoint` the bridge the session asks, when it is one and differs from the current one, and answers
    /// whether it changed.
    ///
    /// A scenario names the endpoint published as it started, and the IDE behind the port can be another launch than
    /// the hello's, with a token of its own. The current client stays when a scenario names none: the lane then has
    /// no endpoint to tell, which says nothing about the IDE the client reaches. A paint source asks through the
    /// client, so a changed one is dropped here and the next climb builds it anew.
    pub(crate) fn use_bridge(&mut self, endpoint: Option<&Bridge>) -> bool {
        let Some(endpoint) = endpoint else {
            return false;
        };
        if self.bridge.as_ref() == Some(endpoint) {
            return false;
        }
        let changed = self.bridge.is_some();
        self.bridge = Some(endpoint.clone());
        self.client = match Client::new(&(self.options.bridge_url)(endpoint.port), &endpoint.token) {
            Ok(client) => Some(client),
            Err(error) => {
                say!(self.options.diagnostics, "{error:#}");
                None
            }
        };
        if self.source.as_ref().is_some_and(|source| source.kind() != CaptureSource::X11) {
            self.source = None;
        }
        changed
    }

    /// Picks the capture source, keeping a working X11 source once it has one: nothing is better, and the X server
    /// outlives every IDE restart. Anything worse is climbed again, since the IDE, and with it the display the facts
    /// name, may not have been up at the hello. So is an X11 source that broke: one grab that outlasted its bound,
    /// under a guest paused for a moment, would otherwise leave every later scenario of the session without a
    /// picture. It answers what a bundle should record about the climb.
    pub(crate) fn climb(&mut self, facts: Option<&Facts>) -> Option<String> {
        let mut note = if let Some(source) = &self.source
            && source.kind() == CaptureSource::X11
        {
            let broken = source.broken()?;
            Some(format!("the X11 source broke ({broken}), so it is opened again"))
        } else {
            None
        };
        let screen = ScreenAccess {
            display: facts.and_then(|facts| facts.display.clone()).unwrap_or_default(),
            os: facts.map(|facts| facts.os.clone()).unwrap_or_default(),
            allowed: self.hello.as_ref().is_some_and(|hello| hello.screen),
        };
        // The old source goes first, so an X11 connection is not open twice.
        self.source = None;
        let (source, reason) = self.options.ladder.climb(&screen, self.client.as_ref());
        let reopened = source_kind(source.as_deref()) == CaptureSource::X11;
        self.source = source;
        if let Some(note) = note.as_mut()
            && !reopened
        {
            note.push_str(", and that failed: ");
            note.push_str(&reason);
        }
        self.source_reason = reason;
        note
    }

    pub(crate) fn facts(&self) -> anyhow::Result<Facts> {
        match &self.client {
            None => anyhow::bail!("the lane named no bridge"),
            Some(client) => client.facts(Instant::now() + FACTS_TIMEOUT),
        }
    }

    /// Answers a line the recorder cannot use, whatever its op, since the lane may be waiting on it, and records the
    /// refusal in the open bundle. The first line is answered as a hello, because that is the one ack the lane waits
    /// for then.
    fn refuse(&mut self, at: SystemTime, problem: &str) {
        say!(self.options.diagnostics, "line {} refused: {problem}", self.line);
        if let Some(scenario) = &self.scenario {
            scenario.trace_error(ERROR_SOURCE_PROTOCOL, &format!("line {}: {problem}", self.line), crate::unix_ms(at));
        }
        if self.line == 1 {
            return self.hello_ack(false, CaptureSource::None, VideoCodec::None, problem.to_owned());
        }
        self.ack(false, Some(problem.to_owned()));
    }

    pub(crate) fn ack(&mut self, ok: bool, error: Option<String>) {
        let ack = Ack {
            ok,
            line: self.line,
            error,
        };
        self.write(avl_trace::encode(&ack));
    }

    fn hello_ack(&mut self, ok: bool, capture: CaptureSource, video: VideoCodec, reason: String) {
        let ack = HelloAck {
            ok,
            line: self.line,
            capture,
            video,
            reason: (!reason.is_empty()).then_some(reason),
        };
        self.write(avl_trace::encode(&ack));
    }

    /// Sends one ack. A lane that stopped reading makes it fail, which changes nothing: the recorder carries on until
    /// its input ends and closes the bundle then.
    fn write(&mut self, line: Result<Vec<u8>, avl_trace::Error>) {
        if let Ok(mut line) = line {
            line.push(b'\n');
            let _ = self.acks.write_all(&line).and_then(|()| self.acks.flush());
        }
    }

    /// Finishes the open scenario, reading the IDE's last spans and the facts at its end for the log slice.
    pub(crate) fn finish_scenario(&mut self, status: BundleStatus, details: AllureStatusDetails, at: SystemTime) {
        let Some(scenario) = self.scenario.take() else {
            return;
        };
        scenario.finish(status, details, crate::unix_ms(at), self.client.as_ref(), &|| self.facts().ok());
    }

    /// Ends the session: an open scenario is finished as truncated, and the capture source is released.
    pub(crate) fn close(&mut self, why: &str) {
        if let Some(scenario) = &self.scenario {
            say!(
                self.options.diagnostics,
                "{}: {why} before done, the bundle is truncated",
                scenario.command.name
            );
            let now = (self.options.clock)();
            self.finish_scenario(BundleStatus::Truncated, AllureStatusDetails::default(), now);
        }
        self.source = None;
    }
}

pub(crate) fn source_kind(source: Option<&dyn Source>) -> CaptureSource {
    source.map_or(CaptureSource::None, Source::kind)
}
