//! The program runner of the lane daemon: each scenario of each selected suite document, between two verified resets,
//! against the lane IDE.
//!
//! Per suite, the prologue: the document must fit its vocabulary, every story fixture must carry its bridge payloads,
//! every declared CLI must have a runtime, and the managed-agent baseline puts the registry agents of the lane back to
//! never seen. A suite whose fixtures start with installed agents then sends its `managedAgentInstall`. A prologue that fails is one container failure of the suite. Per scenario, under the recorder `scenario`
//! command:
//! 1. `reset:before`: `Reset` in the IDE, then the registry fault, the feedback queue and the cloud record. A reset
//!    that does not hold recycles the IDE, see [`super::recycle`].
//! 2. The preparation of a story scenario, one `StepSequence`: `SetScenarioFixture`, `SetRegistryFlag` per flag,
//!    `SeedSessionPresets`, `RemountEditorEmptyState` for the inline prompt flag, and `SessionTabs`, whose terminal tabs
//!    are the runner value `terminalBaseline`.
//! 3. The setups and the flow steps, or the authored program. Each IDE id is one bridge request `<kind>/<id>`: its
//!    arguments in camel case, the values it takes, and `timeoutMs`. The transport waits `timeoutMs`, a margin and the
//!    grace of the bridge. A host id runs here, see [`super::host_ids`]. An authored program runs only the host ids of
//!    [`AUTHORED_HOST_IDS`].
//! 4. A failure snapshot on failure, then `reset:after`, then `done`. The `reset:after` of a failed scenario also sends
//!    the managed-agent baseline again when its suite has a `managedAgentInstall`, see [`Runner::managed_agent_baseline`].
//!
//! A runner of `lane-run` then runs each failed scenario of the shared IDE once more, on a recycled IDE, see
//! [`Runner::rerun_failed`] and [`super::rerun`].
//!
//! A suite of the launch profile `bare` runs after the suites of the shared IDE, on an IDE of its own, see
//! [`Runner::bare_suite`]. Its scenarios run their programs with no reset and no preparation, and the IDE gets no request
//! before the first instruction.
//!
//! An instruction that answers `attachments`, each `{title, contentType, fileName, text}`, gets each file attached to
//! its span in the trace, and its text written into the run log. After each part of an authored program, and before its
//! `restart-ide`, the runner reads the trace attachments that the IDE queued, see
//! [`Runner::collect_trace_attachments`].
//!
//! The run thread owns every call of the loop. The watchdog thread of [`super::watchdog`] watches it.

use std::cell::{Cell, RefCell, RefMut};
use std::collections::{BTreeMap, BTreeSet};
use std::fs;
use std::path::{Path, PathBuf};
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use avl_bridge::{BridgeFailure, ErrorKind};
use avl_trace::bridge::{ATTACHMENTS_ROUTE, AttachmentKind, attachment_route, decode_attachment_list};
use avl_trace::protocol::{self, Label, ScenarioCommand, Side as TraceSide, SnapshotPhase, SpanKind};
use avl_wire::daemon::{TimedStep, TypedFailure};
use avl_wire::ide::SYSTEM_DIR;
use avl_wire::program::lane::{Delivery, LIVE_LANE, LaneAgent, LaneDocument};
use avl_wire::program::{
    HostAction, IdKind, IdSpec, Instruction, OperationProgram, ScenarioProgram, Side, SuiteFixture, SuiteProgram, SuiteReset,
};
use avl_wire::report::ScenarioReport;
use avl_wire::session_log;
use serde::Deserialize;
use serde_json::value::RawValue;
use serde_json::{Map, Value, json};

use super::host_ids::{self, HostId, HostSide, ServiceControl};
use super::ide::{Relaunch, RelaunchPart, RelaunchParts};
use super::readiness::{READY_TIMEOUT, request};
use super::recycle::{Isolation, RecycleBudget, ResetPhase, never_ran};
use super::rerun::{self, Rerun};
use super::session_logs::{self, StagedLog};
use super::stream::{CaseIdentity, CaseStatus, Stream};
use super::trace::{Trace, TraceFailure};
use super::values::{JETBRAINS_CLOUD_URL, TERMINAL_BASELINE, Values};
use super::watchdog::{Probes, Watch};

#[cfg(test)]
pub(crate) mod tests;

/// The kinds of a failure that the runner names itself, beside the class name of a failure in the IDE.
pub(crate) mod kind {
    /// The suite document does not fit its vocabulary, or lacks what the runner sends.
    pub(crate) const DOCUMENT: &str = "lane_document";
    /// An instruction takes a value that no earlier instruction answered.
    pub(crate) const VALUE: &str = "lane_value_missing";
    /// A bridge request got no answer: a refused connect, a passed deadline, a refusal of the bridge.
    pub(crate) const BRIDGE: &str = "lane_bridge";
    /// A host id did not hold.
    pub(crate) const HOST: &str = "lane_host_id";
    /// A state reset did not hold.
    pub(crate) const RESET: &str = "lane_reset";
    /// The preparation chain of a scenario did not hold.
    pub(crate) const PREPARATION: &str = "lane_preparation";
    /// The suite needs what the lane daemon does not do yet.
    pub(crate) const UNSUPPORTED: &str = "lane_unsupported";
    /// The watchdog ended the run.
    pub(crate) const EXPIRED: &str = "lane_watchdog_expired";
    /// The IDE of a suite of its own did not launch or did not get ready, or the lane services of the IDE do not run.
    pub(crate) const LAUNCH: &str = "lane_launch";
    /// `AIR_LANE_INJECT_FAILURE` failed the scenario on purpose.
    pub(crate) const INJECTED: &str = "lane_injected_failure";
}

/// What the transport of an instruction waits past its deadline, before the grace of the bridge client. It is longer than
/// `AIR_UI_TEST_ANSWER_GRACE_MS`, so the IDE answers an overrun before the transport gives up.
const INSTRUCTION_MARGIN: Duration = Duration::from_secs(10);

/// The deadline of `Reset` and `ResetManagedAgents`, `AIR_UI_TEST_RESET_TIMEOUT_MS`.
const RESET_TIMEOUT: Duration = Duration::from_mins(2);
/// What one recycle can take: the readiness of the new IDE, the reset again, and a minute for the quit and the launch.
const RECYCLE_NEED: Duration = Duration::from_secs(READY_TIMEOUT.as_secs() + RESET_TIMEOUT.as_secs() + 60);

/// The default deadline of a bridge operation, `AIR_UI_TEST_DEFAULT_OPERATION_TIMEOUT_MS`.
const DEFAULT_OPERATION_MS: u64 = 10_000;
/// The deadline of the preset seed and of the remount of the editor empty state.
const PRESET_SEED_MS: u64 = 30_000;
const EMPTY_STATE_REMOUNT_MS: u64 = 30_000;
/// The grace of each step of a `StepSequence`, `STEP_ANSWER_GRACE_MS`.
const STEP_GRACE_MS: u64 = 2_000;

/// The registry flag whose value remounts the editor empty state in the preparation.
const INLINE_EMPTY_STATE_PROMPT: &str = "air.inline.empty.state.prompt";
/// The interaction surface of a terminal Session View tab.
const TERMINAL_SURFACE: &str = "terminal";
/// The prefix of the full id of a registry agent.
const REGISTRY_AGENT_PREFIX: &str = "acp.registry.";
/// The id of the part of an authored program.
const AUTHORED_STEP: &str = "authored-step";
/// The host ids that an authored program may run between its parts: the restart, the proof of the recycle, and the
/// reload of Air.
const AUTHORED_HOST_IDS: [HostAction; 3] = [HostAction::RestartIde, HostAction::RecycleThroughFailedReset, HostAction::ReloadAir];

/// The host ids of the live lane. A program of the live lane may run them, an authored program between its parts too.
const LIVE_HOST_IDS: [HostAction; 2] = [HostAction::RefreshLiveWiring, HostAction::AdoptLeftoverJunieSessions];
/// The agent id of Junie, which the session inventory of the IDE names a Junie session by.
const JUNIE_AGENT_ID: &str = "junie";
/// How long the IDE may take to hand one session to the reset, `AIR_UI_TEST_ADOPT_TIMEOUT_MS`.
const ADOPT_TIMEOUT: Duration = Duration::from_secs(30);
/// Where an authored part writes its artifacts in the IDE context, `AIR_AUTHORED_ARTIFACTS_DIR`.
const AUTHORED_ARTIFACTS_DIR: &str = "air-authored-artifacts";
/// Where the runner moves the artifacts of the authored parts in the directory of an iteration.
pub(crate) const ITERATION_ARTIFACTS_DIR: &str = "authored-artifacts";

/// The metrics file of a performance body in the artifacts of its part, `AIR_PERFORMANCE_FILE` of the bridge: one JSON
/// object of numbers, which the body rewrites after each metric.
const AIR_PERFORMANCE_FILE: &str = "air-performance.json";
/// How long one read of a trace attachment route may take: the list, or the fetch of one entry.
const TRACE_READ: Duration = Duration::from_secs(10);
/// How many lines of a reset failure a skip reason and a failure carry.
const CARRIED_FAILURE_LINES: usize = 20;
/// The directory of the Codex daemons in the system directory of the IDE. It holds the ledger [`CODEX_LEDGER_FILE`] and
/// the record `<id>.json` of each daemon. `CodexAppServerManager` and `FileCodexAppServerLedgerStore` of the Codex
/// provider own the layout.
const CODEX_DAEMONS_DIR: &str = "air/codex-app-server/generations";
/// The Codex daemon ledger in [`CODEX_DAEMONS_DIR`].
const CODEX_LEDGER_FILE: &str = "ledger.json";
/// The fields of a Codex daemon record that a scenario never attaches. The startup arguments can name the MCP endpoint
/// of the IDE, and the URL is the endpoint of the daemon.
const CODEX_PRIVATE_FIELDS: [&str; 2] = ["startupConfigArgs", "tcpUrl"];
/// How many Codex daemon records a failed scenario attaches beside the ledger, the newest first.
const MAX_CODEX_RECORDS: usize = 32;
/// The largest Codex file that a failed scenario attaches.
const MAX_CODEX_FILE_BYTES: u64 = 1024 * 1024;

/// What the program runner needs from the lane IDE: the bridge, the relaunch, the lane services, and the probes of the
/// watchdog. The lane daemon gives its IDE owner; a test gives a fake.
pub(crate) trait Lane: Probes {
    /// Sends one bridge request whose deadline in the IDE is `within`, and answers its value.
    fn call(&self, request: &Value, within: Duration) -> Result<Value, avl_bridge::Error>;

    /// Quits the IDE, performs `while_down` on its context directory, launches it again on the same context, and waits
    /// for readiness. `parts` gets the begin and the end of each part.
    fn relaunch(
        &self,
        cause: Relaunch,
        while_down: &mut dyn FnMut(&Path) -> Result<(), String>,
        parts: &mut dyn RelaunchParts,
    ) -> Result<(), String>;

    fn services(&self) -> &dyn ServiceControl;

    /// The address of the JetBrains Cloud emulator, the runner value `jetBrainsCloudUrl`.
    fn cloud_url(&self) -> Option<String>;

    /// The context directory of the IDE, where an authored part writes its artifacts.
    fn context_dir(&self) -> Option<PathBuf>;

    /// The bridge endpoint that the recorder asks for its snapshots.
    fn recorder_bridge(&self) -> Option<protocol::Bridge>;

    /// The process of the IDE now, as the run supervisor and the bridge know it.
    fn ide_process(&self) -> Result<IdeProcess, String>;

    /// Quits the IDE of the lane, launches a bare IDE of the launch key `key` for a suite of the launch profile `bare`,
    /// and waits until it is ready. It sends the bare IDE no request.
    fn launch_bare(&self, key: &str) -> Result<(), String>;

    /// Quits the bare IDE. The next run launches the IDE of the lane again.
    fn end_bare(&self) -> Result<(), String>;

    /// GETs the trace route `route` of the bridge, whose answer has the media type `accept`, within `within`.
    fn trace_get(&self, route: &str, accept: &str, within: Duration) -> Result<Vec<u8>, avl_bridge::Error>;

    /// Reloads Air in place within `within`, waits until the lifecycle names the same bridge plugin again, and pings the
    /// new copy. The IDE keeps its process.
    fn reload_air(&self, within: Duration) -> Result<(), String>;

    /// Copies the Central wiring of the guest account into the lane home again when the account re-wired. Only the lane
    /// of a live run has one.
    fn refresh_live_wiring(&self) -> Result<(), String> {
        Err("the lane has no live run, so it has no Central wiring to refresh".to_owned())
    }

    /// The Junie sessions of the live project that the session index of the guest account names.
    fn leftover_junie_sessions(&self) -> Result<Vec<String>, String> {
        Err("the lane has no live run, so it has no Junie session to hand over".to_owned())
    }
}

/// One file that an instruction answered for the evidence of its scenario, an element of `attachments` of its answer.
#[derive(Clone, Debug, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "camelCase")]
struct AnswerAttachment {
    title: String,
    content_type: String,
    file_name: String,
    text: String,
}

/// One file for the trace of the open scenario, which the trace did not take yet.
#[derive(Clone, Debug, PartialEq, Eq)]
struct Attachment {
    name: String,
    mime: String,
    /// The extension of the file in the bundle.
    extension: String,
    bytes: Vec<u8>,
}

/// The process of the lane IDE as two readers know it: the pid of the run that the supervisor launched, and the pid
/// that the bridge answers with. The two differ where a launcher process starts the JVM.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct IdeProcess {
    pub launched: Option<i32>,
    pub bridge: i64,
}

/// One selected suite document, and each of its scenarios verbatim.
#[derive(Debug)]
pub(crate) struct Suite {
    pub program: SuiteProgram,
    pub raw_profiles: Vec<Box<RawValue>>,
}

impl Suite {
    /// Keeps the scenarios that `keep` selects, and their verbatim forms with them.
    pub(crate) fn retain_scenarios(&mut self, keep: impl Fn(&ScenarioProgram) -> bool) {
        let profiles = std::mem::take(&mut self.program.profiles);
        let raw = std::mem::take(&mut self.raw_profiles);
        for (profile, raw) in profiles.into_iter().zip(raw) {
            if keep(&profile) {
                self.program.profiles.push(profile);
                self.raw_profiles.push(raw);
            }
        }
    }
}

/// Why an instruction, a step or a scenario failed.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Failure {
    /// The class name of the failure in the IDE, or one word of [`kind`].
    pub kind: String,
    pub message: String,
    /// The stack lines of a failure in the IDE, each `at <frame>` or `Caused by: ...`.
    pub stack: Vec<String>,
    /// What a later failure added: an after reset that failed too, or a recycle that failed.
    pub suppressed: Vec<String>,
    /// The scenario reached no verdict: the watchdog ended it, or the body aborted it.
    pub aborted: bool,
}

impl Failure {
    pub(crate) fn runner(kind: &str, message: impl Into<String>) -> Self {
        Self {
            kind: kind.to_owned(),
            message: message.into(),
            stack: Vec::new(),
            suppressed: Vec::new(),
            aborted: false,
        }
    }

    /// The failure of a bridge request: the failure in the IDE, or why the request got no answer. The outermost failure
    /// in the IDE says whether the body aborted its scenario.
    pub(crate) fn of_bridge(error: &avl_bridge::Error) -> Self {
        match error.kind() {
            ErrorKind::Failed(failure) => Self {
                kind: failure.type_name.clone(),
                message: failure.message.clone().unwrap_or_default(),
                stack: stack_lines(failure),
                suppressed: Vec::new(),
                aborted: failure.aborted,
            },
            _ => Self::runner(kind::BRIDGE, error.to_string()),
        }
    }

    /// The typed failure of the run stream: the kind, the message, and the stack and the suppressed failures after it.
    pub(crate) fn typed(&self) -> TypedFailure {
        let detail: Vec<String> = self
            .stack
            .iter()
            .cloned()
            .chain(self.suppressed.iter().map(|suppressed| format!("Suppressed: {suppressed}")))
            .collect();
        TypedFailure {
            kind: Some(self.kind.clone()),
            message: Some(self.message.clone()),
            detail: (!detail.is_empty()).then(|| detail.join("\n")),
        }
    }

    fn trace(&self) -> TraceFailure {
        TraceFailure {
            type_name: self.kind.clone(),
            message: Some(self.message.clone()).filter(|message| !message.is_empty()),
            stack: self
                .stack
                .iter()
                .cloned()
                .chain(self.suppressed.iter().map(|suppressed| format!("Suppressed: {suppressed}")))
                .collect(),
            aborted: self.aborted,
        }
    }
}

/// The stack lines of `failure`: its frames, then each cause's first message line and frames.
fn stack_lines(failure: &BridgeFailure) -> Vec<String> {
    let mut lines: Vec<String> = failure.stack.iter().map(|frame| format!("at {frame}")).collect();
    let mut cause = failure.cause.as_deref();
    while let Some(current) = cause {
        let first = current.message.as_deref().and_then(|message| message.lines().next());
        lines.push(match first {
            Some(line) => format!("Caused by: {}: {line}", current.type_name),
            None => format!("Caused by: {}", current.type_name),
        });
        lines.extend(current.stack.iter().map(|frame| format!("at {frame}")));
        cause = current.cause.as_deref();
    }
    lines
}

/// The runner of one iteration.
pub(crate) struct Runner<'r, 's> {
    lane: &'r dyn Lane,
    stream: &'r Stream<'s>,
    watch: &'r Watch,
    document: &'r LaneDocument,
    iteration_dir: &'r Path,
    trace: RefCell<Trace>,
    isolation: RefCell<Isolation>,
    /// The notes and the metrics that the authored parts of the open scenario reported.
    report: RefCell<ScenarioReport>,
    /// The files that the instructions answered, which the trace did not take yet.
    attachments: RefCell<Vec<Attachment>>,
    /// Whether a suite of a bare IDE runs: the trace of its scenario opens after its program.
    bare: Cell<bool>,
    /// What the transport of an instruction waits past its deadline, [`INSTRUCTION_MARGIN`] outside a test.
    instruction_margin: Duration,
    /// The recorded sessions in the context of the lane IDE, the runner value [`session_log::RUNNER_VALUE`].
    session_logs: Vec<StagedLog>,
    /// The rerun of a `lane-run`, or `None` for the lane daemon, which reruns nothing.
    rerun: Option<Rerun>,
    /// Each scenario of the shared IDE that failed: the position of its suite and the index of its scenario.
    failed: RefCell<Vec<(usize, usize)>>,
    /// Whether the scenarios that run now are reruns.
    rerunning: Cell<bool>,
}

impl<'r, 's> Runner<'r, 's> {
    pub(crate) fn new(
        lane: &'r dyn Lane,
        stream: &'r Stream<'s>,
        watch: &'r Watch,
        document: &'r LaneDocument,
        iteration_dir: &'r Path,
        trace: Trace,
    ) -> Self {
        Self {
            lane,
            stream,
            watch,
            document,
            iteration_dir,
            trace: RefCell::new(trace),
            isolation: RefCell::new(Isolation::default()),
            report: RefCell::new(ScenarioReport::default()),
            attachments: RefCell::new(Vec::new()),
            bare: Cell::new(false),
            instruction_margin: INSTRUCTION_MARGIN,
            session_logs: Vec::new(),
            rerun: None,
            failed: RefCell::new(Vec::new()),
            rerunning: Cell::new(false),
        }
    }

    /// The same runner with the rerun `rerun` of a `lane-run`.
    pub(crate) fn with_rerun(mut self, rerun: Option<Rerun>) -> Self {
        self.rerun = rerun;
        self
    }

    /// The same runner with the recorded sessions `logs` of the run.
    pub(crate) fn with_session_logs(mut self, logs: Vec<StagedLog>) -> Self {
        self.session_logs = logs;
        self
    }

    /// The values that the runner answers before the first instruction of a scenario of `suite`: the address of the
    /// Cloud emulator and the recorded sessions, each when the vocabulary names it and the runner has it.
    fn runner_values(&self, suite: &SuiteProgram) -> Vec<(String, Value)> {
        let named = |name: &str| suite.vocabulary.runner_answers.iter().any(|answer| answer == name);
        let mut values = Vec::new();
        if named(JETBRAINS_CLOUD_URL)
            && let Some(url) = self.lane.cloud_url()
        {
            values.push((JETBRAINS_CLOUD_URL.to_owned(), Value::String(url)));
        }
        if named(session_log::RUNNER_VALUE)
            && let Some(logs) = session_logs::runner_value(&self.session_logs)
        {
            values.push((session_log::RUNNER_VALUE.to_owned(), logs));
        }
        values
    }

    /// Why `profile` cannot run: a runner value that it requires and that the runner has not. The preparation answers
    /// [`TERMINAL_BASELINE`] in every story scenario, so that value is never missing.
    fn missing_runner_value(&self, suite: &SuiteProgram, profile: &ScenarioProgram) -> Option<String> {
        let values = self.runner_values(suite);
        profile
            .requires_runner_values
            .iter()
            .find(|name| *name != TERMINAL_BASELINE && !values.iter().any(|(answered, _)| answered == *name))
            .map(|name| format!("the runner has no value '{name}'"))
    }

    /// The same runner with the transport margin `margin`, so a test sees a deadline pass in a second.
    #[cfg(test)]
    pub(crate) const fn with_instruction_margin(mut self, margin: Duration) -> Self {
        self.instruction_margin = margin;
        self
    }

    fn trace(&self) -> RefMut<'_, Trace> {
        self.trace.borrow_mut()
    }

    /// Says on the stream, once, why the trace lost a bundle: a recorder that did not start, refused the hello or a
    /// scenario, or went away. The controller makes it the `tracesError` of the report.
    fn report_trace_failure(&self) {
        let failure = self.trace().take_failure();
        if let Some(reason) = failure {
            self.stream.trace_failed(reason);
        }
    }

    /// Points the Agent Spawner of the Cloud emulator at the Cloud agent that the fixtures of `suite` use, or at no agent
    /// when they use none. A dial then reaches the control server in the IDE under the key of that agent. The spawner
    /// steers one key, and only a controlled agent has a credential, so the prologue refuses any other suite.
    fn attach_cloud_spawner(&self, suite: &SuiteProgram) -> Result<(), Failure> {
        let cloud: BTreeMap<&str, &LaneAgent> = suite
            .profiles
            .iter()
            .filter_map(|profile| suite.fixtures.get(&profile.fixture))
            .flat_map(|fixture| &fixture.agents)
            .filter_map(|key| {
                self.document
                    .agents
                    .iter()
                    .find(|agent| agent.key == *key && agent.delivery == Delivery::Cloud)
            })
            .map(|agent| (agent.key.as_str(), agent))
            .collect();
        let refused = |reason: String| Err(Failure::runner(kind::DOCUMENT, format!("the suite '{}' {reason}", suite.suite)));
        let agent = match cloud.values().collect::<Vec<_>>().as_slice() {
            [] => None,
            [agent] if agent.controlled => Some(agent.key.as_str()),
            [agent] => {
                return refused(format!(
                    "uses the Cloud agent '{}', which is not controlled, so the Agent Spawner has no credential for it",
                    agent.key
                ));
            }
            _ => {
                return refused(format!(
                    "uses the Cloud agents {:?}, and the Agent Spawner of the lane steers one",
                    cloud.keys().collect::<Vec<_>>()
                ));
            }
        };
        self.lane.services().cloud_attach_spawner(agent).map_err(|error| {
            Failure::runner(
                kind::LAUNCH,
                format!("the Agent Spawner of the suite '{}' cannot attach: {error}", suite.suite),
            )
        })
    }

    /// Runs every scenario of `suites`, in order, and closes the trace. A run that the watchdog ended starts no further
    /// scenario. The reruns follow the suites of the shared IDE. The suites of a bare IDE run last, so the IDE of the lane
    /// quits once per run.
    pub(crate) fn run(self, suites: &[Suite]) {
        self.report_trace_failure();
        let (bare, shared): (Vec<&Suite>, Vec<&Suite>) = suites.iter().partition(|suite| suite.program.launch.is_some());
        for (position, suite) in shared.iter().enumerate() {
            if self.watch.expired().is_some() {
                break;
            }
            self.suite(position, suite);
        }
        self.rerun_failed(&shared);
        for suite in bare {
            if self.watch.expired().is_some() {
                break;
            }
            self.bare_suite(suite);
        }
        if let Some(report) = self.isolation.borrow().report() {
            self.stream.note(report);
        }
        self.trace().close();
        self.report_trace_failure();
    }

    /// The suite at `position` of the suites of the shared IDE: its prologue, then each scenario.
    fn suite(&self, position: usize, suite: &Suite) {
        let program = &suite.program;
        let reset = match self.prologue(program) {
            Ok(reset) => reset,
            Err(failure) => {
                self.stream
                    .container_failed(&program.suite, &program.test_class_name, &class_label(program), failure.typed());
                return;
            }
        };
        for (index, (profile, raw)) in program.profiles.iter().zip(&suite.raw_profiles).enumerate() {
            if self.watch.expired().is_some() {
                return;
            }
            if self.scenario(program, profile, raw, &reset) == Some(CaseStatus::Failed) {
                self.failed.borrow_mut().push((position, index));
            }
        }
    }

    /// Runs each failed scenario of the shared IDE once more, when the runner has a rerun and [`rerun::refusal`] lets
    /// it. The IDE is recycled once first, so each rerun gets a new IDE process and then the reset of its scenario. The
    /// prologue of a suite runs again before its reruns. A recycle or a prologue that fails ends the reruns that depend on
    /// it, with a note, and the failures stay.
    fn rerun_failed(&self, shared: &[&Suite]) {
        if self.rerun.is_none() {
            return;
        }
        let failed = self.failed.take();
        if failed.is_empty() {
            return;
        }
        let counts = self.stream.counts();
        let refused = rerun::refusal(rerun::Moment {
            failed: counts.failed + counts.container_failures,
            expired: self.watch.expired().is_some(),
            exhausted: self.isolation.borrow().exhausted(),
            live: self.document.live.is_some(),
            budget_left: self.watch.budget_left(),
            recycle_need: RECYCLE_NEED,
        });
        if let Some(reason) = refused {
            self.stream.note(format!("lane-run reruns no failed scenario: {reason}"));
            return;
        }
        let names: Vec<String> = failed
            .iter()
            .map(|(position, index)| format!("'{}'", shared[*position].program.profiles[*index].name))
            .collect();
        self.stream.note(format!(
            "lane-run reruns {} failed scenario(s) once on a recycled IDE: {}",
            failed.len(),
            names.join(", ")
        ));
        if let Err(error) = self.relaunch_span(Relaunch::Recycle, &mut |_| Ok(())) {
            self.stream.note(format!(
                "lane-run reruns no failed scenario: the recycle before the rerun failed: {error}"
            ));
            return;
        }
        self.rerunning.set(true);
        let mut passed = 0;
        let mut position = 0;
        while position < failed.len() {
            let suite = shared[failed[position].0];
            let indexes: Vec<usize> = failed[position..]
                .iter()
                .take_while(|(next, _)| *next == failed[position].0)
                .map(|(_, index)| *index)
                .collect();
            position += indexes.len();
            if self.watch.expired().is_some() || self.isolation.borrow().exhausted() {
                break;
            }
            let program = &suite.program;
            let reset = match self.prologue(program) {
                Ok(reset) => reset,
                Err(failure) => {
                    self.stream.note(format!(
                        "lane-run reruns no scenario of the suite '{}': its prologue failed: {}",
                        program.suite, failure.message
                    ));
                    continue;
                }
            };
            for index in indexes {
                if self.watch.expired().is_some() {
                    break;
                }
                let status = self.scenario(program, &program.profiles[index], &suite.raw_profiles[index], &reset);
                passed += usize::from(status == Some(CaseStatus::Successful));
            }
        }
        self.rerunning.set(false);
        self.stream
            .note(format!("lane-run rerun: {passed} of {} failed scenario(s) passed", failed.len()));
    }

    /// The prologue of a suite: the document, the declared CLIs, the managed-agent baseline, and the Agent Spawner of the
    /// Cloud emulator. It answers what the reset of each scenario sends.
    fn prologue<'p>(&self, suite: &'p SuiteProgram) -> Result<ScenarioReset<'p>, Failure> {
        document_fits(suite)?;
        if self.document.lane != LIVE_LANE {
            refuse_live_host_ids(suite)?;
        }
        self.attach_cloud_spawner(suite)?;
        let reset = suite.reset.as_ref().ok_or_else(|| {
            Failure::runner(
                kind::DOCUMENT,
                format!(
                    "the suite document '{}' states no reset section, which the lane daemon sends as Reset",
                    suite.suite
                ),
            )
        })?;
        if !suite.authored {
            let missing: BTreeSet<&str> = suite
                .profiles
                .iter()
                .filter(|profile| suite.fixtures.get(&profile.fixture).is_none_or(|fixture| fixture.bridge.is_none()))
                .map(|profile| profile.fixture.as_str())
                .collect();
            if !missing.is_empty() {
                return Err(Failure::runner(
                    kind::DOCUMENT,
                    format!(
                        "the suite document '{}' states no bridge payload for the fixtures {missing:?}; the generator writes \
                         it as fixtures.<id>.bridge, and the lane daemon sends it as SetScenarioFixture and SeedSessionPresets",
                        suite.suite
                    ),
                ));
            }
        }
        let preinstalled: BTreeSet<&str> = suite
            .profiles
            .iter()
            .filter_map(|profile| suite.fixtures.get(&profile.fixture).map(|fixture| (profile, fixture)))
            .filter(|(_, fixture)| fixture.managed_agent_starts_installed || !fixture.preinstalled.is_empty())
            .map(|(profile, _)| profile.fixture.as_str())
            .collect();
        if !preinstalled.is_empty() && suite.managed_agent_install.is_none() {
            return Err(Failure::runner(
                kind::DOCUMENT,
                format!(
                    "the fixtures {preinstalled:?} of the suite '{}' start with installed managed agents, and the suite \
                     document states no managedAgentInstall, which the lane daemon sends before the first scenario",
                    suite.suite
                ),
            ));
        }
        let install = suite
            .managed_agent_install
            .as_deref()
            .map(|install| install_request(&suite.suite, install))
            .transpose()?;
        // A live lane declares the runtimes of its agents in its live context.
        let declared: Vec<&String> = self
            .document
            .cli_runtimes
            .iter()
            .chain(
                self.document
                    .live
                    .iter()
                    .flat_map(|live| live.runtimes.iter().map(|runtime| &runtime.name)),
            )
            .collect();
        for profile in &suite.profiles {
            if let Some(cli) = profile.required_executables.iter().find(|cli| !declared.contains(cli)) {
                return Err(Failure::runner(
                    kind::DOCUMENT,
                    format!(
                        "the scenario '{}' drives the CLI '{cli}', and the lane declares no runtime for it; it declares {declared:?}",
                        profile.name
                    ),
                ));
            }
        }
        let prologue: Vec<HostAction> = suite
            .prologue_host_ids
            .iter()
            .map(|id| {
                id.parse::<HostAction>()
                    .ok()
                    .filter(|action| LIVE_HOST_IDS.contains(action))
                    .ok_or_else(|| {
                        Failure::runner(
                            kind::DOCUMENT,
                            format!(
                                "the suite '{}' states the prologue host id '{id}', which no prologue runs",
                                suite.suite
                            ),
                        )
                    })
            })
            .collect::<Result<_, _>>()?;
        self.watch.step(&format!("{} managed-agent baseline", suite.suite), || {
            self.managed_agent_baseline(install.as_ref())?;
            for action in &prologue {
                self.live_host_id(*action).map_err(|message| Failure::runner(kind::HOST, message))?;
            }
            Ok(ScenarioReset { reset, install })
        })
    }

    /// The managed-agent baseline: it clears the registry fault, puts the registry agents of the lane back to never
    /// seen, and sends `install`, the `managedAgentInstall` of the suite. The prologue of a suite sends it, and the reset
    /// after a failed scenario sends it again.
    ///
    /// The runner does not verify the installed state before a scenario. The only probe is `awaitLaunchableRegistryAgent`,
    /// a private function of the authored journey code. No lifecycle request asks the IDE for that state. A scenario that
    /// removes an agent and passes puts the agent back itself. A failed scenario can stop before it does that, so the
    /// runner restores the baseline after each failed scenario.
    fn managed_agent_baseline(&self, install: Option<&ManagedAgentInstall>) -> Result<(), Failure> {
        self.lane
            .services()
            .registry_clear()
            .map_err(|error| Failure::runner(kind::RESET, format!("resetting the registry distribution fault failed: {error}")))?;
        let answer = self.lifecycle("ResetManagedAgents", json!({ "agentIds": self.managed_agent_ids() }), RESET_TIMEOUT)?;
        if !answer.is_null() {
            return Err(Failure::runner(
                kind::RESET,
                format!("Air managed-agent baseline failed: {}", text_of(&answer)),
            ));
        }
        if let Some((name, request, timeout)) = install {
            let answer = self.call(name, request, *timeout + self.instruction_margin)?;
            if !answer.is_null() {
                return Err(Failure::runner(
                    kind::RESET,
                    format!("Air managed-agent install failed: {}", text_of(&answer)),
                ));
            }
        }
        Ok(())
    }

    /// Runs the live host id `action`: the refresh of the Central wiring, or the hand-over of the Junie sessions that
    /// an earlier run left to the reset, one `AdoptProviderSession` each.
    fn live_host_id(&self, action: HostAction) -> Result<(), String> {
        match action {
            HostAction::RefreshLiveWiring => self.lane.refresh_live_wiring(),
            HostAction::AdoptLeftoverJunieSessions => {
                let ids = self.lane.leftover_junie_sessions()?;
                for id in &ids {
                    self.lifecycle(
                        "AdoptProviderSession",
                        json!({"agentId": JUNIE_AGENT_ID, "agentSessionId": id}),
                        ADOPT_TIMEOUT,
                    )
                    .map_err(|failure| failure.message)?;
                }
                if !ids.is_empty() {
                    self.stream.note(format!(
                        "handed {} Junie session(s) of the live project to the reset: {ids:?}",
                        ids.len()
                    ));
                }
                Ok(())
            }
            other => Err(format!("the host id '{other}' is no live host id")),
        }
    }

    /// The full ids of the registry agents of the lane, sorted.
    fn managed_agent_ids(&self) -> Vec<String> {
        let mut ids: Vec<String> = self
            .document
            .registry_agents
            .iter()
            .filter_map(|key| self.document.agents.iter().find(|agent| agent.key == *key))
            .filter_map(|agent| agent.registry_id.as_ref())
            .map(|id| format!("{REGISTRY_AGENT_PREFIX}{id}"))
            .collect();
        ids.sort();
        ids
    }

    /// Starts `case`, the case of `profile`, and answers its start. A case that needs a value that the run lacks is
    /// skipped, and then the answer is `None`.
    fn start_case(&self, suite: &SuiteProgram, profile: &ScenarioProgram, case: &CaseIdentity) -> Option<Instant> {
        if let Some(reason) = self.missing_runner_value(suite, profile) {
            self.stream.skipped(case, never_ran(&profile.name, &reason));
            return None;
        }
        let started = Instant::now();
        self.stream.started(case);
        self.watch.execution_started(self.stream, case.execution());
        Some(started)
    }

    /// Runs one scenario of the shared IDE, and answers its status, or `None` for a scenario that never started.
    fn scenario(&self, suite: &SuiteProgram, profile: &ScenarioProgram, raw: &RawValue, reset: &ScenarioReset<'_>) -> Option<CaseStatus> {
        let case = case_identity(suite, profile);
        if self.isolation.borrow().exhausted() {
            let reason = self.isolation.borrow().exhaustion_reason();
            self.stream.skipped(&case, never_ran(&profile.name, &reason));
            return None;
        }
        let started = self.start_case(suite, profile, &case)?;
        self.trace()
            .open_scenario(scenario_command(suite, profile, raw, self.lane.recorder_bridge()));
        let fixture = suite.fixtures.get(&profile.fixture).cloned().unwrap_or_default();

        let mut verdict = self
            .traced_reset(ResetPhase::Before, &profile.name, reset.reset, None)
            .and_then(|()| self.body(suite, profile, &fixture))
            .and_then(|()| self.injected_failure(profile));
        if verdict.is_err() {
            self.trace().snap_failure();
            self.attach_codex_daemons();
        }
        if !self.isolation.borrow().exhausted() && self.watch.expired().is_none() {
            // A failed scenario can leave a registry agent removed or half installed, so its reset restores the baseline.
            let baseline = reset.install.as_ref().filter(|_| verdict.is_err());
            let cleanup = self.traced_reset(ResetPhase::After, &profile.name, reset.reset, baseline);
            verdict = match (verdict, cleanup) {
                (Err(mut primary), Err(cleanup)) => {
                    primary.suppressed.push(cleanup.message);
                    primary.suppressed.extend(cleanup.suppressed);
                    Err(primary)
                }
                (Ok(()), cleanup) => cleanup,
                (primary, Ok(())) => primary,
            };
        }
        Some(self.finish(&case, started, verdict))
    }

    /// The failure that an injection of the rerun asks for in this attempt of `profile`, after a body that passed.
    fn injected_failure(&self, profile: &ScenarioProgram) -> Result<(), Failure> {
        let names: Vec<&str> = std::iter::once(profile.name.as_str()).chain(profile.key.as_deref()).collect();
        let rerunning = self.rerunning.get();
        match self
            .rerun
            .iter()
            .flat_map(|rerun| &rerun.injected)
            .find(|injection| injection.fails(&names, rerunning))
        {
            Some(injection) => Err(Failure::runner(kind::INJECTED, injection.describe())),
            None => Ok(()),
        }
    }

    /// Ends the case of a scenario that started: the end of its trace, its `testFinished` and its watchdog state. It
    /// answers the status of the case.
    fn finish(&self, case: &CaseIdentity, started: Instant, verdict: Result<(), Failure>) -> CaseStatus {
        // A watchdog that ended the run killed the IDE under this scenario, so its failure is no verdict about it.
        let expired = self.watch.expired().is_some();
        let failure = verdict.err().map(|failure| Failure {
            aborted: failure.aborted || expired,
            ..failure
        });
        let recorded = self.trace().recording();
        let closing = Instant::now();
        self.trace().finish_scenario(failure.as_ref().map(Failure::trace).as_ref());
        if recorded {
            self.stream.timing(TimedStep::RecorderClose, closing, Some(&case.name));
        }
        self.report_trace_failure();
        let status = match &failure {
            None => CaseStatus::Successful,
            Some(failure) if failure.aborted => CaseStatus::Aborted,
            Some(_) => CaseStatus::Failed,
        };
        let report = self.report.take();
        self.stream
            .finished(case, status, started.elapsed(), failure.as_ref().map(Failure::typed), report);
        self.watch.execution_finished(self.stream);
        status
    }

    /// A suite of the launch profile `bare`, on an IDE of its own. The lane quits its IDE and launches a bare one, each
    /// scenario runs its program, and the bare IDE quits at the end. The watchdog sends the bare IDE no request while the
    /// suite runs, so the IDE can measure its own start-up.
    fn bare_suite(&self, suite: &Suite) {
        let program = &suite.program;
        let Some(launch) = &program.launch else { return };
        if let Err(failure) = document_fits(program) {
            self.stream
                .container_failed(&program.suite, &program.test_class_name, &class_label(program), failure.typed());
            return;
        }
        self.bare.set(true);
        self.watch.quiet(|| {
            let started = Instant::now();
            let launched = self
                .watch
                .step(&format!("{} bare launch", program.suite), || self.lane.launch_bare(&launch.key));
            self.stream.timing(TimedStep::BareLaunch, started, Some(&program.suite));
            if let Err(error) = launched {
                let failure = Failure::runner(
                    kind::LAUNCH,
                    format!("the bare IDE of the suite '{}' did not get ready: {error}", program.suite),
                );
                self.stream
                    .container_failed(&program.suite, &program.test_class_name, &class_label(program), failure.typed());
                return;
            }
            for (profile, raw) in program.profiles.iter().zip(&suite.raw_profiles) {
                if self.watch.expired().is_some() {
                    break;
                }
                self.bare_scenario(program, profile, raw);
            }
        });
        self.bare.set(false);
        if let Err(error) = self.lane.end_bare() {
            self.stream
                .note(format!("the bare IDE of the suite '{}' did not quit: {error}", program.suite));
        }
    }

    /// One scenario of a bare IDE: its program with no reset, because the IDE is fresh and serves this suite alone. The
    /// trace of the scenario opens after the program, because the recorder asks the IDE for its facts as a scenario
    /// opens. Its log slice starts at the first byte of the log. The files that the program answered then go to the
    /// scenario.
    fn bare_scenario(&self, suite: &SuiteProgram, profile: &ScenarioProgram, raw: &RawValue) {
        let case = case_identity(suite, profile);
        let Some(started) = self.start_case(suite, profile, &case) else {
            return;
        };
        let verdict = self.body(suite, profile, &SuiteFixture::default());
        self.stream.timing(TimedStep::BareProgram, started, Some(&profile.name));
        // A bare IDE runs on a fresh context, so its log holds its launch alone. The slice starts at its first byte and
        // holds what the IDE wrote during the program, the start-up included.
        let mut command = scenario_command(suite, profile, raw, self.lane.recorder_bridge());
        command.log_from = Some(0);
        self.trace().open_scenario(command);
        self.attach_answers();
        if verdict.is_err() {
            self.trace().snap_failure();
        }
        self.finish(&case, started, verdict);
    }

    /// One side's reset as a `reset` span, under the isolation that recycles the IDE when it does not hold.
    /// `timeout_ms`, cut to what is left of the budget of the run, so no instruction waits past the end of the budget. A
    /// cut timeout is at least one millisecond, and the watchdog ends the run at the end of the budget.
    fn within_budget(&self, timeout_ms: u64) -> u64 {
        self.watch.budget_left().map_or(timeout_ms, |left| {
            timeout_ms.min(u64::try_from(left.as_millis()).unwrap_or(u64::MAX).max(1))
        })
    }

    /// What is left of the budget of the run for a recycle, or `None` for a run with no budget.
    fn recycle_budget(&self) -> Option<RecycleBudget> {
        self.watch.budget_left().map(|left| RecycleBudget { left, need: RECYCLE_NEED })
    }

    fn traced_reset(
        &self,
        phase: ResetPhase,
        scenario: &str,
        reset: &SuiteReset,
        baseline: Option<&ManagedAgentInstall>,
    ) -> Result<(), Failure> {
        let side = phase.as_str();
        let span = self
            .trace()
            .open_span(SpanKind::Reset, side, &format!("[reset] {side} {scenario}"), None);
        let outcome = self.watch.step(&format!("{side} reset of {scenario}"), || {
            self.isolation.borrow_mut().ensure_clean(
                phase,
                scenario,
                self.recycle_budget(),
                &mut || self.reset(phase, scenario, reset, baseline),
                &mut || self.relaunch_span(Relaunch::Recycle, &mut |_| Ok(())),
            )
        });
        self.attach_dumps();
        let failure = outcome.err().map(|failure| Failure {
            suppressed: failure.suppressed,
            ..Failure::runner(kind::RESET, failure.message)
        });
        self.trace().end_span(span, failure.as_ref().map(Failure::trace).as_ref());
        failure.map_or(Ok(()), Err)
    }

    /// The reset of one side: in the IDE, then the three lane services. Every failed step is named in one message. With
    /// `baseline`, a reset that holds then sends the managed-agent baseline with that install. A baseline that fails is a
    /// reset that does not hold, so the isolation recycles the IDE and sends the reset and the baseline again.
    fn reset(&self, phase: ResetPhase, scenario: &str, reset: &SuiteReset, baseline: Option<&ManagedAgentInstall>) -> Result<(), String> {
        let mut failures = Vec::new();
        let name = format!("Air state reset ({} '{scenario}')", phase.as_str());
        let fields = json!({
            "flagKeys": reset.flag_keys,
            "afterScenario": phase == ResetPhase::After,
            "laneFlags": reset.lane_flags,
            "agentLoginAcpAgentIds": reset.agent_login_acp_agent_ids,
        });
        match self.lifecycle("Reset", fields, RESET_TIMEOUT) {
            Ok(Value::Null) => {}
            Ok(answer) => failures.push(describe(&name, &text_of(&answer))),
            Err(failure) => failures.push(describe(&name, &failure.message)),
        }
        let services = self.lane.services();
        let started = Instant::now();
        for (what, cleared) in [
            ("resetting the registry distribution fault", services.registry_clear()),
            ("clearing captured feedback", services.feedback_clear()),
            ("clearing the emulated JetBrains Cloud record", services.cloud_clear()),
        ] {
            if let Err(error) = cleared {
                failures.push(describe(what, &error));
            }
        }
        self.stream.timing(TimedStep::ServicesReset, started, Some(scenario));
        if failures.is_empty()
            && let Some(install) = baseline
        {
            let restored = self.watch.step(&format!("{scenario} managed-agent baseline"), || {
                self.managed_agent_baseline(Some(install))
            });
            if let Err(failure) = restored {
                failures.push(describe("restoring the managed-agent baseline", &failure.message));
            }
        }
        if failures.is_empty() { Ok(()) } else { Err(failures.join("\n")) }
    }

    /// A relaunch of the IDE as a `restart` span with the key `restart:ide` for `restart-ide` and `restart:recycle` for a
    /// recycle, as the recorder keys them. Each part of the relaunch is a `relaunch` span inside it, see [`TracedParts`].
    fn relaunch_span(&self, cause: Relaunch, while_down: &mut dyn FnMut(&Path) -> Result<(), String>) -> Result<(), String> {
        let id = match cause {
            Relaunch::Restart => "ide",
            Relaunch::Recycle => "recycle",
        };
        let span = self.trace().open_span(SpanKind::Restart, id, &format!("[restart] {id}"), None);
        self.trace().restart();
        let mut parts = TracedParts {
            trace: &self.trace,
            open: Vec::new(),
        };
        let outcome = self.watch.step(&format!("relaunch for {id}"), || {
            self.watch.relaunching(|| self.lane.relaunch(cause, while_down, &mut parts))
        });
        let failure = outcome
            .as_ref()
            .err()
            .map(|error| Failure::runner(kind::BRIDGE, error.clone()).trace());
        self.trace().end_span(span, failure.as_ref());
        outcome
    }

    /// The body of a scenario: the preparation and the program of a story scenario, or the program of an authored one.
    fn body(&self, suite: &SuiteProgram, profile: &ScenarioProgram, fixture: &SuiteFixture) -> Result<(), Failure> {
        let mut runner_values = self.runner_values(suite);
        if suite.authored {
            let mut values = Values::new(runner_values);
            let label = format!("{}/{}", suite.suite, profile.key.as_deref().unwrap_or(&profile.name));
            for instruction in &profile.program {
                self.instruction(suite, &label, instruction, &mut values, fixture)?;
            }
            self.trace().snap(SnapshotPhase::Boundary);
            return Ok(());
        }
        let baseline = self.prepare(profile, fixture)?;
        runner_values.push((TERMINAL_BASELINE.to_owned(), baseline));
        let mut values = Values::new(runner_values);
        for operation in &profile.setups {
            let id = &operation.operation;
            self.span(SpanKind::Setup, id, &format!("[setup] {id}"), || {
                self.span(SpanKind::Operation, id, &format!("[operation] {id}"), || {
                    self.operation(suite, &format!("{}/setup/{id}", profile.name), operation, &mut values, fixture)
                })
            })?;
        }
        for step in &profile.steps {
            self.span(SpanKind::Step, &step.step, &format!("[flow] {}", step.step), || {
                for operation in &step.operations {
                    let id = &operation.operation;
                    self.span(SpanKind::Operation, id, &format!("[operation] {id}"), || {
                        self.operation(
                            suite,
                            &format!("{}/{}/{id}", profile.name, step.step),
                            operation,
                            &mut values,
                            fixture,
                        )
                    })?;
                }
                Ok(())
            })?;
        }
        Ok(())
    }

    /// Runs `work` in a span that ends with its failure.
    fn span(&self, kind: SpanKind, id: &str, title: &str, work: impl FnOnce() -> Result<(), Failure>) -> Result<(), Failure> {
        let span = self.trace().open_span(kind, id, title, None);
        let outcome = work();
        self.trace().end_span(span, outcome.as_ref().err().map(Failure::trace).as_ref());
        outcome
    }

    /// The instructions of one operation, then the boundary snapshot that closes it.
    fn operation(
        &self,
        suite: &SuiteProgram,
        label: &str,
        operation: &OperationProgram,
        values: &mut Values,
        fixture: &SuiteFixture,
    ) -> Result<(), Failure> {
        for instruction in &operation.instructions {
            self.instruction(suite, label, instruction, values, fixture)?;
        }
        self.trace().snap(SnapshotPhase::Boundary);
        Ok(())
    }

    /// One instruction as an `action` or `assertion` span: a boundary snapshot, the id, and a check snapshot after an
    /// assertion that held.
    fn instruction(
        &self,
        suite: &SuiteProgram,
        operation: &str,
        instruction: &Instruction,
        values: &mut Values,
        fixture: &SuiteFixture,
    ) -> Result<(), Failure> {
        if let Some(reason) = self.watch.expired() {
            return Err(Failure {
                aborted: true,
                ..Failure::runner(kind::EXPIRED, reason)
            });
        }
        let (Some((id_kind, id)), Some(spec)) = (instruction.id(), suite.spec_of(instruction)) else {
            return Err(Failure::runner(
                kind::DOCUMENT,
                format!(
                    "the instruction '{}' of '{operation}' is not in the vocabulary",
                    instruction.label()
                ),
            ));
        };
        let label = instruction.label();
        let (span_kind, title) = match id_kind {
            IdKind::Action => (SpanKind::Action, format!("[action] {label}")),
            IdKind::Assertion => (
                SpanKind::Assertion,
                format!("[assertion] {label}: {}", instruction.expectation.as_deref().unwrap_or_default()),
            ),
        };
        let side = match spec.side {
            Side::Ide => TraceSide::Ide,
            Side::Host => TraceSide::Host,
        };
        let timeout = authored_timeout(instruction).unwrap_or(spec.timeout_ms);
        let span = self
            .trace()
            .open_span(span_kind, &label, &title, Some((instruction.expectation.as_deref(), side, timeout)));
        self.trace().snap(SnapshotPhase::Boundary);
        let outcome = self.watch.step(&format!("{operation}/{label}"), || match spec.side {
            Side::Ide => self.ide_instruction(id_kind, id, &label, instruction, spec, values),
            Side::Host => self.host_instruction(suite, operation, id_kind, id, &label, instruction, values, fixture),
        });
        if outcome.is_ok() && id_kind == IdKind::Assertion {
            self.trace().snap(SnapshotPhase::Check);
        }
        self.attach_dumps();
        if !self.bare.get() {
            self.attach_answers();
        }
        self.trace().end_span(span, outcome.as_ref().err().map(Failure::trace).as_ref());
        outcome
    }

    /// An id that runs in the IDE: one bridge request with the arguments, the values it takes and its deadline.
    fn ide_instruction(
        &self,
        id_kind: IdKind,
        id: &str,
        label: &str,
        instruction: &Instruction,
        spec: &IdSpec,
        values: &mut Values,
    ) -> Result<(), Failure> {
        let mut fields = Map::new();
        fields.insert("type".to_owned(), Value::String(format!("{}/{id}", id_kind.as_str())));
        for (name, value) in &instruction.args {
            fields.insert(camel_case(name), value.clone());
        }
        let missing = |message: String| Failure::runner(kind::VALUE, message);
        let authored = id == AUTHORED_STEP && instruction.uses.is_some();
        if authored {
            let takes = instruction.uses.as_ref().map(|uses| uses.takes.as_slice()).unwrap_or_default();
            fields.insert(
                "values".to_owned(),
                Value::Object(values.part_values(label, takes).map_err(missing)?),
            );
        } else {
            fields.extend(values.request_fields(label, spec).map_err(missing)?);
        }
        let timeout = self.within_budget(authored_timeout(instruction).unwrap_or(spec.timeout_ms));
        fields.insert("timeoutMs".to_owned(), json!(timeout));
        let request_name = request_class(id_kind, id);
        let answer = self.call(
            &request_name,
            &Value::Object(fields),
            Duration::from_millis(timeout) + self.instruction_margin,
        );
        let artifacts = if authored {
            let artifacts = self.collect_authored_artifacts(instruction);
            self.collect_trace_attachments(label);
            artifacts
        } else {
            // A story instruction that the IDE failed queues the logs of the sessions that the IDE shows, so the runner reads
            // them too. A request without an answer has no IDE to read them from.
            if answer.as_ref().is_err_and(|failure| failure.kind != kind::BRIDGE) {
                self.collect_trace_attachments(label);
            }
            None
        };
        // The IDE names the id in a failure of its own. A request that got no answer is named here.
        let answer = answer.map_err(|mut failure| {
            if let Some(artifacts) = &artifacts {
                self.report_failed_part(label, artifacts);
            }
            if failure.kind == kind::BRIDGE {
                failure.message = format!("the instruction '{label}' got no answer: {}", failure.message);
            }
            failure
        })?;
        self.keep_attachments(label, &answer);
        if authored {
            let declared = instruction.uses.as_ref().map(|uses| uses.answers.as_slice()).unwrap_or_default();
            let answered = answer.get("values").and_then(Value::as_object).cloned().unwrap_or_default();
            values.keep_part_values(label, &answered, declared).map_err(missing)?;
            self.report_part(label, &answer);
            return Ok(());
        }
        values.keep_answers(label, spec, &answer).map_err(missing)
    }

    /// An id that runs on the host. An authored program may run the ids of [`AUTHORED_HOST_IDS`] only.
    #[expect(
        clippy::too_many_arguments,
        reason = "the parts of one instruction, which the caller resolved once"
    )]
    fn host_instruction(
        &self,
        suite: &SuiteProgram,
        operation: &str,
        id_kind: IdKind,
        id: &str,
        label: &str,
        instruction: &Instruction,
        values: &mut Values,
        fixture: &SuiteFixture,
    ) -> Result<(), Failure> {
        let host = HostId::of(id_kind == IdKind::Assertion, id)
            .ok_or_else(|| Failure::runner(kind::DOCUMENT, format!("the host id '{label}' has no word of the host vocabulary")))?;
        if suite.authored
            && !matches!(host, HostId::Action(action) if AUTHORED_HOST_IDS.contains(&action) || LIVE_HOST_IDS.contains(&action))
        {
            return Err(Failure::runner(
                kind::DOCUMENT,
                format!("the authored program runs '{label}', which no authored program may run"),
            ));
        }
        // A restart and a reload lose what the IDE queued and nobody read, so the runner reads it first.
        if suite.authored && matches!(host, HostId::Action(HostAction::RestartIde | HostAction::ReloadAir)) {
            self.collect_trace_attachments(label);
        }
        let mut side = RunnerHost {
            runner: self,
            fixture,
            authored: suite.authored,
            scenario: operation,
            reset: suite.reset.as_ref(),
            deadline: Duration::from_millis(self.within_budget(suite.spec_of(instruction).map_or(0, |spec| spec.timeout_ms))),
        };
        host_ids::perform(host, label, &instruction.args, values, &mut side).map_err(|message| Failure::runner(kind::HOST, message))
    }

    /// A reload of Air as a `restart` span with the key `restart:reload`. The bridge of the test plugin goes away during
    /// the reload, so the recorder hears of it first, and the watchdog probe waits.
    fn reload_air_span(&self, within: Duration) -> Result<(), String> {
        let span = self.trace().open_span(SpanKind::Restart, "reload", "[restart] reload of Air", None);
        self.trace().restart();
        let outcome = self.watch.relaunching(|| self.lane.reload_air(within));
        let failure = outcome
            .as_ref()
            .err()
            .map(|error| Failure::runner(kind::HOST, error.clone()).trace());
        self.trace().end_span(span, failure.as_ref());
        outcome
    }

    /// Fails the next reset on purpose, so the isolation recycles the IDE and resets the replacement, as after any reset
    /// that does not hold. The proof keeps an isolation of its own, so it spends none of the recycle budget of the run.
    /// It fails unless the reset ran twice, and unless the IDE after it is a new process that answers the bridge.
    fn recycle_through_failed_reset(&self, scenario: &str, reset: &SuiteReset) -> Result<(), String> {
        let before = self.lane.ide_process()?;
        let resets = Cell::new(0_u32);
        let mut isolation = Isolation::default();
        isolation
            .ensure_clean(
                ResetPhase::Before,
                scenario,
                self.recycle_budget(),
                &mut || {
                    resets.set(resets.get() + 1);
                    if resets.get() == 1 {
                        return Err(format!(
                            "the first reset of '{scenario}' fails on purpose, so the lane recycles the IDE"
                        ));
                    }
                    self.reset(ResetPhase::Before, scenario, reset, None)
                },
                &mut || self.relaunch_span(Relaunch::Recycle, &mut |_| Ok(())),
            )
            .map_err(|failure| failure.to_string())?;
        let after = self.lane.ide_process()?;
        let launched = after.launched.ok_or_else(|| "the recycle left no IDE process".to_owned())?;
        if before.launched == Some(launched) {
            return Err(format!("the recycle kept the IDE process {launched}"));
        }
        if after.bridge == before.bridge {
            return Err(format!("the recycle kept the IDE {} that answers the bridge", before.bridge));
        }
        if resets.get() != 2 {
            return Err(format!(
                "the reset ran {} time(s), and the recycle runs it once more on the replacement",
                resets.get()
            ));
        }
        if let Some(report) = isolation.report() {
            self.stream.note(report);
        }
        Ok(())
    }

    /// Keeps each file of `attachments` in the answer of the instruction `label` for the trace, and writes its text into
    /// the run log.
    fn keep_attachments(&self, label: &str, answer: &Value) {
        let Some(files) = answer.get("attachments") else { return };
        match serde_json::from_value::<Vec<AnswerAttachment>>(files.clone()) {
            Ok(files) => {
                for file in files {
                    self.stream.note(format!("[attachment] {label}: {}\n{}", file.title, file.text));
                    let extension = Path::new(&file.file_name)
                        .extension()
                        .and_then(|extension| extension.to_str())
                        .unwrap_or("txt")
                        .to_owned();
                    self.attachments.borrow_mut().push(Attachment {
                        name: file.title,
                        mime: file.content_type,
                        extension,
                        bytes: file.text.into_bytes(),
                    });
                }
            }
            Err(error) => self.stream.note(format!(
                "[attachment] {label}: the answer holds attachments that are no files: {error}"
            )),
        }
    }

    /// Attaches each file that the instructions answered to the innermost open span of the trace.
    fn attach_answers(&self) {
        for file in self.attachments.take() {
            self.trace().attach(&file.name, &file.mime, &file.bytes, &file.extension);
        }
    }

    /// Keeps the trace attachments that the IDE queued during the part `label` for the trace: the stills of its steps,
    /// and its named captures and files. The IDE forgets an entry that a fetch took, and its next list forgets the rest
    /// of this one, so the runner fetches each listed id at once. A read that fails is a note and fails no part, because
    /// an attachment is evidence.
    fn collect_trace_attachments(&self, label: &str) {
        let listed = self
            .lane
            .trace_get(ATTACHMENTS_ROUTE, avl_bridge::JSON, TRACE_READ)
            .map_err(|error| error.to_string())
            .and_then(|document| decode_attachment_list(&document).map_err(|error| error.to_string()));
        let list = match listed {
            Ok(list) => list,
            Err(error) => {
                self.stream.note(format!(
                    "[attachment] {label}: cannot list the trace attachments of the IDE: {error}"
                ));
                return;
            }
        };
        if list.dropped > 0 {
            self.stream.note(format!(
                "[attachment] {label}: the IDE dropped {} still(s) before the runner read them",
                list.dropped
            ));
        }
        for entry in list.attachments {
            let media_type = entry.mime.split(';').next().unwrap_or_default().trim().to_owned();
            match self.lane.trace_get(&attachment_route(&entry.id), &media_type, TRACE_READ) {
                Ok(bytes) => self.attachments.borrow_mut().push(Attachment {
                    extension: extension_of_media_type(&media_type).to_owned(),
                    name: entry.name,
                    mime: entry.mime,
                    bytes,
                }),
                Err(error) => {
                    let kind = match entry.kind {
                        AttachmentKind::Still => "still",
                        AttachmentKind::Capture => "capture",
                        AttachmentKind::File => "file",
                    };
                    self.stream.note(format!(
                        "[attachment] {label}: cannot fetch the {kind} '{}' ({}) of the IDE{}: {error}",
                        entry.name,
                        entry.id,
                        if entry.required { ", which its body named" } else { "" }
                    ));
                }
            }
        }
    }

    /// Attaches the Codex daemon ledger of the IDE and its newest [`MAX_CODEX_RECORDS`] daemon records to the trace of a
    /// failed scenario, so a failure of a Codex daemon shows the history of each daemon. A record goes without its
    /// [`CODEX_PRIVATE_FIELDS`]. An IDE with no Codex daemon directory attaches nothing. A file that cannot be read is a
    /// note, because an attachment is evidence.
    fn attach_codex_daemons(&self) {
        let Some(context) = self.lane.context_dir() else {
            return;
        };
        let (files, left_out) = codex_daemon_files(&context.join(SYSTEM_DIR).join(CODEX_DAEMONS_DIR));
        if left_out > 0 {
            self.stream.note(format!(
                "[attachment] the IDE holds {left_out} Codex daemon record(s) more than the {MAX_CODEX_RECORDS} that a failed \
                 scenario attaches"
            ));
        }
        for file in files {
            let name = file.file_name().map(|name| name.to_string_lossy().into_owned()).unwrap_or_default();
            match read_codex_file(&file, name != CODEX_LEDGER_FILE) {
                Ok(bytes) => {
                    self.trace()
                        .attach(&format!("Codex daemon {name}"), "application/json", &bytes, "json");
                }
                Err(error) => self
                    .stream
                    .note(format!("[attachment] cannot attach the Codex file {}: {error}", file.display())),
            }
        }
    }

    /// Attaches each IDE dump that the watchdog wrote for a slow step since the last call to the innermost open span.
    fn attach_dumps(&self) {
        for (step, file) in self.watch.take_dumps() {
            match fs::read(&file) {
                Ok(bytes) => {
                    self.trace()
                        .attach(&format!("IDE dump of the slow step {step}"), "text/plain", &bytes, "txt");
                }
                Err(error) => self.stream.note(format!("cannot attach the dump {}: {error}", file.display())),
            }
        }
    }

    /// Keeps the notes and the metrics of an authored part for the `testFinished` of the scenario, and writes each one as
    /// a line of the run log.
    fn report_part(&self, label: &str, answer: &Value) {
        let report = answer.get("report");
        let mut kept = self.report.borrow_mut();
        for note in report
            .and_then(|report| report.get("notes"))
            .and_then(Value::as_array)
            .into_iter()
            .flatten()
        {
            let note = text_of(note);
            self.stream.note(format!("[authored] {label} note: {note}"));
            kept.notes.push(note);
        }
        let metrics = report.and_then(|report| report.get("metrics")).and_then(Value::as_object);
        for (name, value) in metrics.into_iter().flatten() {
            self.stream.note(format!("[authored] {label} metric {name} = {value}"));
            if let Value::Number(number) = value {
                kept.metrics.insert(name.clone(), number.clone());
            }
        }
    }

    /// Keeps the metrics of a failed authored part from [`AIR_PERFORMANCE_FILE`] in its `artifacts`. A failed part
    /// answers no report, and a performance body rewrites the file after each metric. So the metrics measured before
    /// the failure still reach the `testFinished` of the scenario, and the IJ Perf file of its series.
    fn report_failed_part(&self, label: &str, artifacts: &Path) {
        let file = artifacts.join(AIR_PERFORMANCE_FILE);
        let text = match fs::read_to_string(&file) {
            Ok(text) => text,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => return,
            Err(error) => {
                self.stream
                    .note(format!("[authored] {label}: cannot read {}: {error}", file.display()));
                return;
            }
        };
        let Ok(Value::Object(metrics)) = serde_json::from_str::<Value>(&text) else {
            self.stream
                .note(format!("[authored] {label}: {} is no JSON object of metrics", file.display()));
            return;
        };
        let mut kept = self.report.borrow_mut();
        for (name, value) in metrics {
            match value {
                Value::Number(number) => {
                    self.stream.note(format!(
                        "[authored] {label} metric {name} = {number}, from {AIR_PERFORMANCE_FILE} of the failed part"
                    ));
                    kept.metrics.insert(name, number);
                }
                other => self.stream.note(format!(
                    "[authored] {label}: the metric {name} of {AIR_PERFORMANCE_FILE} is no number: {other}"
                )),
            }
        }
    }

    /// Moves the artifact directory of an authored part out of the IDE context into the iteration directory, also after
    /// a failed part, and answers where the artifacts are: the iteration directory, or the context when the move
    /// failed. `None` is a part that wrote no artifact.
    fn collect_authored_artifacts(&self, instruction: &Instruction) -> Option<PathBuf> {
        let arg = |name: &str| instruction.args.get(name).and_then(Value::as_str).unwrap_or_default().to_owned();
        let (suite, scenario, part) = (arg("suite"), arg("scenario"), arg("part"));
        let context = self.lane.context_dir()?;
        let source = context.join(AUTHORED_ARTIFACTS_DIR).join(&suite).join(&scenario).join(&part);
        if !source.is_dir() {
            return None;
        }
        let target = self
            .iteration_dir
            .join(ITERATION_ARTIFACTS_DIR)
            .join(&suite)
            .join(&scenario)
            .join(&part);
        let moved = target
            .parent()
            .map_or(Ok(()), fs::create_dir_all)
            .and_then(|()| fs::rename(&source, &target));
        match moved {
            Ok(()) => {
                self.stream
                    .note(format!("[authored] artifacts of {suite}/{scenario}/{part}: {}", target.display()));
                Some(target)
            }
            Err(error) => {
                self.stream
                    .note(format!("[authored] cannot move the artifacts {}: {error}", source.display()));
                Some(source)
            }
        }
    }

    /// The preparation of a story scenario as one `StepSequence`, and the terminal tabs that are open after it.
    fn prepare(&self, profile: &ScenarioProgram, fixture: &SuiteFixture) -> Result<Value, Failure> {
        let failed = |message: String| Failure::runner(kind::PREPARATION, message);
        let bridge = fixture
            .bridge
            .as_ref()
            .ok_or_else(|| failed(format!("the fixture '{}' has no bridge payload", profile.fixture)))?;
        let fixture_facts: Value = serde_json::from_str(bridge.set_scenario_fixture.get()).map_err(|error| {
            failed(format!(
                "the SetScenarioFixture of the fixture '{}' is no JSON: {error}",
                profile.fixture
            ))
        })?;
        // The generator refuses a profile that follows the last used preset on a fixture with additional presets, so
        // the seed of the fixture is the seed of every profile.
        let seed: Value = serde_json::from_str(bridge.seed_session_presets.get()).map_err(|error| {
            failed(format!(
                "the SeedSessionPresets of the fixture '{}' is no JSON: {error}",
                profile.fixture
            ))
        })?;
        let preset_ids: Vec<Value> = seed
            .get("presets")
            .and_then(Value::as_array)
            .into_iter()
            .flatten()
            .map(|preset| preset.get("id").cloned().unwrap_or(Value::Null))
            .collect();
        let seed_timeout = seed.get("timeoutMs").and_then(Value::as_u64).unwrap_or(PRESET_SEED_MS);
        let mut steps = vec![(fixture_facts, DEFAULT_OPERATION_MS)];
        for (key, value) in &profile.flags {
            steps.push((
                request("SetRegistryFlag", json!({ "key": key, "value": value })),
                DEFAULT_OPERATION_MS,
            ));
        }
        let seed_step = steps.len();
        steps.push((seed, seed_timeout));
        let remount_step = profile.flags.contains_key(INLINE_EMPTY_STATE_PROMPT).then(|| {
            steps.push((
                request("RemountEditorEmptyState", json!({ "timeoutMs": EMPTY_STATE_REMOUNT_MS })),
                EMPTY_STATE_REMOUNT_MS,
            ));
            steps.len() - 1
        });
        steps.push((request("SessionTabs", json!({})), DEFAULT_OPERATION_MS));
        let outcomes = self.chain(&steps)?;
        let seeded = outcomes[seed_step].get("presetIds").cloned().unwrap_or(Value::Null);
        if seeded != Value::Array(preset_ids.clone()) {
            return Err(failed(format!(
                "Session-preset seed returned {seeded}, expected ids={}",
                Value::Array(preset_ids)
            )));
        }
        if let Some(remount) = remount_step
            && !outcomes[remount].is_null()
        {
            return Err(failed(format!(
                "editor empty-state remount failed: {}",
                text_of(&outcomes[remount])
            )));
        }
        let tabs = outcomes.last().and_then(Value::as_array).cloned().unwrap_or_default();
        let baseline: Vec<Value> = tabs
            .iter()
            .filter(|tab| tab.get("interactionSurfaceId").and_then(Value::as_str) == Some(TERMINAL_SURFACE))
            .filter(|tab| tab.get("pending").and_then(Value::as_bool) != Some(true))
            .filter_map(|tab| tab.get("tabKey").cloned())
            .collect();
        Ok(Value::Array(baseline))
    }

    /// Sends `steps` as one `StepSequence`, writes a `call` record for it and for each step, and answers the value of
    /// each step. The first step that failed fails the chain.
    fn chain(&self, steps: &[(Value, u64)]) -> Result<Vec<Value>, Failure> {
        let timeout_ms: u64 = steps.iter().map(|(_, timeout)| timeout + STEP_GRACE_MS).sum();
        let sequence = request(
            "StepSequence",
            json!({ "steps": steps.iter().map(|(step, _)| step.clone()).collect::<Vec<_>>(), "timeoutMs": timeout_ms }),
        );
        let start = epoch_ms();
        let answer = self.call("StepSequence", &sequence, Duration::from_millis(timeout_ms))?;
        let outcomes = answer.as_array().filter(|outcomes| outcomes.len() == steps.len()).ok_or_else(|| {
            Failure::runner(
                kind::PREPARATION,
                format!("the preparation chain answered {answer}, not {} step outcomes", steps.len()),
            )
        })?;
        let mut at = start;
        let mut values = Vec::with_capacity(steps.len());
        let mut failure = None;
        for (index, ((step, _), outcome)) in steps.iter().zip(outcomes).enumerate() {
            let name = format!(
                "StepSequence step {} of {} ({})",
                index + 1,
                steps.len(),
                step.get("type").and_then(Value::as_str).map_or("", simple_name)
            );
            let duration = outcome.get("durationMs").and_then(Value::as_i64).unwrap_or(0);
            let failed = outcome.get("failure").filter(|failed| !failed.is_null());
            let ran = outcome.get("ran").and_then(Value::as_bool).unwrap_or(false);
            if ran {
                let error =
                    failed.map(|failed| BridgeFailure::decode(failed).map_or_else(|_| failed.to_string(), |decoded| decoded.to_string()));
                self.trace().call(&name, at, duration, error.as_deref());
            }
            at += duration;
            if failure.is_none()
                && let Some(failed) = failed
            {
                failure = Some(BridgeFailure::decode(failed).map_or_else(
                    |_| Failure::runner(kind::PREPARATION, format!("{name} failed: {failed}")),
                    |decoded| Failure {
                        message: format!("{name} failed: {}", decoded.message.clone().unwrap_or_default()),
                        stack: stack_lines(&decoded),
                        ..Failure::runner(&decoded.type_name, "")
                    },
                ));
            }
            values.push(outcome.get("value").cloned().unwrap_or(Value::Null));
        }
        failure.map_or(Ok(values), Err)
    }

    /// Sends one lifecycle request of the bridge, `com.intellij.air.integration.bridge.<name>`.
    fn lifecycle(&self, name: &str, fields: Value, within: Duration) -> Result<Value, Failure> {
        self.call(name, &request(name, fields), within)
    }

    /// Sends one bridge request and writes its `call` record.
    fn call(&self, name: &str, request: &Value, within: Duration) -> Result<Value, Failure> {
        let start = epoch_ms();
        let started = Instant::now();
        let answer = self.lane.call(request, within);
        let duration = i64::try_from(started.elapsed().as_millis()).unwrap_or(i64::MAX);
        let error = answer.as_ref().err().map(ToString::to_string);
        self.trace().call(name, start, duration, error.as_deref());
        answer.map_err(|error| Failure::of_bridge(&error))
    }
}

/// The parts of one relaunch as the `relaunch` spans of the trace, each under the innermost open span: the `restart`
/// span, or the part it runs inside. A part that a refusal ended fails with the code of the refusal as its type.
struct TracedParts<'t> {
    trace: &'t RefCell<Trace>,
    /// The spans of the parts that run now, the innermost last.
    open: Vec<u32>,
}

impl RelaunchParts for TracedParts<'_> {
    fn begin(&mut self, part: RelaunchPart) {
        let title = format!("[relaunch] {}", part.title());
        self.open
            .push(self.trace.borrow_mut().open_span(SpanKind::Relaunch, part.as_str(), &title, None));
    }

    fn end(&mut self, _part: RelaunchPart, failure: Option<&crate::reply::AgentRefusal>) {
        let Some(span) = self.open.pop() else { return };
        let failure = failure.map(|refusal| Failure::runner(&refusal.code, refusal.message.clone()).trace());
        self.trace.borrow_mut().end_span(span, failure.as_ref());
    }
}

/// The host side of an instruction: the services of the lane, a restart that states the fixture again, and the proof of
/// the recycle over the reset of the suite.
struct RunnerHost<'a, 'r, 's> {
    runner: &'a Runner<'r, 's>,
    fixture: &'a SuiteFixture,
    authored: bool,
    /// The label of the scenario or operation of the instruction, which the reset names.
    scenario: &'a str,
    reset: Option<&'a SuiteReset>,
    /// The deadline of the instruction, its `timeoutMs`.
    deadline: Duration,
}

impl HostSide for RunnerHost<'_, '_, '_> {
    fn services(&self) -> &dyn ServiceControl {
        self.runner.lane.services()
    }

    fn restart_ide(&mut self, while_down: &mut dyn FnMut(&Path) -> Result<(), String>) -> Result<(), String> {
        self.runner.relaunch_span(Relaunch::Restart, while_down)?;
        // The restarted IDE holds no fixture facts, so a story scenario states them again.
        if !self.authored
            && let Some(bridge) = &self.fixture.bridge
        {
            let request: Value = serde_json::from_str(bridge.set_scenario_fixture.get()).map_err(|error| error.to_string())?;
            self.runner
                .call("SetScenarioFixture", &request, Duration::from_millis(DEFAULT_OPERATION_MS))
                .map_err(|failure| failure.message)?;
        }
        Ok(())
    }

    fn recycle_through_failed_reset(&mut self) -> Result<(), String> {
        let reset = self
            .reset
            .ok_or_else(|| "the suite document states no reset section, and the proof of the recycle runs that reset".to_owned())?;
        self.runner.recycle_through_failed_reset(self.scenario, reset)
    }

    fn reload_air(&mut self) -> Result<(), String> {
        self.runner.reload_air_span(self.deadline)
    }

    fn refresh_live_wiring(&mut self) -> Result<(), String> {
        self.runner.live_host_id(HostAction::RefreshLiveWiring)
    }

    fn adopt_leftover_junie_sessions(&mut self) -> Result<(), String> {
        self.runner.live_host_id(HostAction::AdoptLeftoverJunieSessions)
    }
}

/// The `managedAgentInstall` of a suite: the simple name of its request type, the request, and its deadline in the IDE.
type ManagedAgentInstall = (String, Value, Duration);

/// What the resets of the scenarios of a suite send: the reset section of the suite document, and the install that the
/// reset after a failed scenario sends again with the managed-agent baseline.
struct ScenarioReset<'p> {
    reset: &'p SuiteReset,
    install: Option<ManagedAgentInstall>,
}

/// The `managedAgentInstall` of the suite `suite`. A body that is no object with a `type` and a `timeoutMs` fails the
/// prologue of the suite.
fn install_request(suite: &str, install: &RawValue) -> Result<ManagedAgentInstall, Failure> {
    let refused = |problem: &str| Failure::runner(kind::DOCUMENT, format!("the managedAgentInstall of the suite '{suite}' {problem}"));
    let request: Value = serde_json::from_str(install.get()).map_err(|error| refused(&format!("is no JSON: {error}")))?;
    let name = request
        .get("type")
        .and_then(Value::as_str)
        .map(simple_name)
        .ok_or_else(|| refused("names no request type"))?
        .to_owned();
    let timeout = request
        .get("timeoutMs")
        .and_then(Value::as_u64)
        .ok_or_else(|| refused("states no timeoutMs"))?;
    Ok((name, request, Duration::from_millis(timeout)))
}

/// The extension of a file of the media type `media_type` in the bundle.
fn extension_of_media_type(media_type: &str) -> &'static str {
    match media_type {
        "image/png" => "png",
        "application/json" => "json",
        text if text.starts_with("text/") => "txt",
        _ => "bin",
    }
}

/// Refuses a suite document that does not fit its vocabulary.
fn document_fits(suite: &SuiteProgram) -> Result<(), Failure> {
    let refusals = suite.refusals();
    if refusals.is_empty() {
        return Ok(());
    }
    Err(Failure::runner(
        kind::DOCUMENT,
        format!(
            "the suite document '{}' does not fit its vocabulary: {}",
            suite.suite,
            refusals.join("; ")
        ),
    ))
}

/// Refuses a suite outside the live lane whose program or prologue runs a host id of [`LIVE_HOST_IDS`], before any
/// request reaches the IDE. Only a live run has the account that those ids read.
fn refuse_live_host_ids(suite: &SuiteProgram) -> Result<(), Failure> {
    let instructions = suite.profiles.iter().flat_map(|profile| {
        profile
            .setups
            .iter()
            .chain(profile.steps.iter().flat_map(|step| &step.operations))
            .flat_map(|operation| &operation.instructions)
            .chain(&profile.program)
    });
    let live = instructions
        .filter_map(Instruction::id)
        .filter(|(kind, _)| *kind == IdKind::Action)
        .filter_map(|(_, id)| id.parse::<HostAction>().ok())
        .chain(suite.prologue_host_ids.iter().filter_map(|id| id.parse::<HostAction>().ok()))
        .find(|action| LIVE_HOST_IDS.contains(action));
    match live {
        None => Ok(()),
        Some(action) => Err(Failure::runner(
            kind::DOCUMENT,
            format!("the suite '{}' runs the live host id '{action}' outside the live lane", suite.suite),
        )),
    }
}

/// The `testClass` label of a suite, or its `testClassName` when it has none.
pub(crate) fn class_label(suite: &SuiteProgram) -> String {
    label_value(suite, "testClass").unwrap_or_else(|| suite.test_class_name.clone())
}

fn label_value(suite: &SuiteProgram, name: &str) -> Option<String> {
    suite
        .labels
        .iter()
        .find(|label| label.name == name)
        .map(|label| label.value.clone())
}

/// The identity of the case of `profile`: the `testClass` label of its suite as the class, and the factory method as
/// the method.
pub(crate) fn case_identity(suite: &SuiteProgram, profile: &ScenarioProgram) -> CaseIdentity {
    CaseIdentity {
        suite: suite.suite.clone(),
        class_name: class_label(suite),
        method_name: profile.method.clone().or_else(|| label_value(suite, "testMethod")),
        name: profile.name.clone(),
    }
}

/// The recorder `scenario` command of `profile`: the labels of the suite, with the factory method of an authored
/// scenario after its class.
fn scenario_command(suite: &SuiteProgram, profile: &ScenarioProgram, raw: &RawValue, bridge: Option<protocol::Bridge>) -> ScenarioCommand {
    let mut labels: Vec<Label> = Vec::new();
    for label in &suite.labels {
        labels.push(Label {
            name: label.name.clone(),
            value: label.value.clone(),
        });
        if label.name == "testClass"
            && let Some(method) = &profile.method
        {
            labels.push(Label {
                name: "testMethod".to_owned(),
                value: method.clone(),
            });
        }
    }
    ScenarioCommand {
        name: profile.name.clone(),
        test_class: suite.test_class_name.clone(),
        suite: Some(suite.suite.clone()),
        lane: suite.lane.clone(),
        flow: profile.flow.clone(),
        fixture: Some(profile.fixture.clone()),
        flags: profile.flags.clone(),
        labels,
        bridge,
        program: Some(raw.to_owned()),
        log_from: None,
    }
}

/// The deadline of an authored part, which its instruction states.
fn authored_timeout(instruction: &Instruction) -> Option<u64> {
    (instruction.action.as_deref() == Some(AUTHORED_STEP))
        .then(|| instruction.args.get("timeoutMs").and_then(Value::as_u64))
        .flatten()
}

/// The simple class name of the generated request of an id: `AirFlow`, the kind, and the id in Pascal case.
pub(crate) fn request_class(kind: IdKind, id: &str) -> String {
    let kind = match kind {
        IdKind::Action => "Action",
        IdKind::Assertion => "Assertion",
    };
    let words: String = id
        .split('-')
        .map(|word| {
            let mut characters = word.chars();
            characters
                .next()
                .map(|first| first.to_uppercase().chain(characters).collect::<String>())
                .unwrap_or_default()
        })
        .collect();
    format!("AirFlow{kind}{words}")
}

/// The field name of the argument `name` of a document: its kebab-case words in camel case.
pub(crate) fn camel_case(name: &str) -> String {
    let mut words = name.split('-');
    let mut camel = words.next().unwrap_or_default().to_owned();
    for word in words {
        let mut characters = word.chars();
        if let Some(first) = characters.next() {
            camel.extend(first.to_uppercase());
            camel.extend(characters);
        }
    }
    camel
}

/// The simple name of a request type: the last segment of a class name, or the id of a generated request.
fn simple_name(type_name: &str) -> &str {
    type_name.rsplit('.').next().unwrap_or(type_name)
}

/// A JSON answer as text: a string without its quotes.
fn text_of(value: &Value) -> String {
    value.as_str().map_or_else(|| value.to_string(), str::to_owned)
}

/// One failure of a reset step as the line a reader reads, cut to [`CARRIED_FAILURE_LINES`]. The run log keeps the
/// whole text.
fn describe(what: &str, detail: &str) -> String {
    let lines: Vec<&str> = detail.lines().collect();
    if lines.len() <= CARRIED_FAILURE_LINES {
        return format!("{what} failed: {detail}");
    }
    super::channel::log(&format!("{what} failed, in full:\n{detail}"));
    format!(
        "{what} failed: {}\n… truncated; the full text is in the daemon log",
        lines[..CARRIED_FAILURE_LINES].join("\n")
    )
}

fn epoch_ms() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map_or(0, |elapsed| i64::try_from(elapsed.as_millis()).unwrap_or(i64::MAX))
}

/// The suite documents of `selection`, each with its scenarios verbatim.
pub(crate) fn suites_of(documents: Vec<(SuiteProgram, Vec<u8>)>) -> Result<Vec<Suite>, String> {
    documents
        .into_iter()
        .map(|(program, bytes)| {
            let raw_profiles =
                avl_wire::program::raw_profiles(&bytes).map_err(|problem| format!("the suite document {} {problem}", program.suite))?;
            Ok(Suite { program, raw_profiles })
        })
        .collect()
}

/// The Codex files of the daemon directory `directory`: the ledger first, then the [`MAX_CODEX_RECORDS`] newest daemon
/// records, and how many older records stay out. Only regular `.json` files count, so a daemon log and a link stay out.
fn codex_daemon_files(directory: &Path) -> (Vec<PathBuf>, usize) {
    let mut ledger = None;
    let mut records: Vec<(SystemTime, PathBuf)> = Vec::new();
    for entry in fs::read_dir(directory).into_iter().flatten().filter_map(Result::ok) {
        let path = entry.path();
        if !entry.file_type().is_ok_and(|kind| kind.is_file()) || path.extension().is_none_or(|extension| extension != "json") {
            continue;
        }
        if entry.file_name() == CODEX_LEDGER_FILE {
            ledger = Some(path);
        } else {
            let modified = entry.metadata().and_then(|metadata| metadata.modified()).unwrap_or(UNIX_EPOCH);
            records.push((modified, path));
        }
    }
    records.sort_by(|(left_time, left), (right_time, right)| right_time.cmp(left_time).then_with(|| left.cmp(right)));
    let left_out = records.len().saturating_sub(MAX_CODEX_RECORDS);
    let files = ledger
        .into_iter()
        .chain(records.into_iter().take(MAX_CODEX_RECORDS).map(|(_, path)| path))
        .collect();
    (files, left_out)
}

/// The bytes of the Codex file `file` to attach, or why it is not attached: an error, or more than
/// [`MAX_CODEX_FILE_BYTES`]. A daemon `record` goes as its JSON object without the [`CODEX_PRIVATE_FIELDS`], and a record
/// that is no JSON object is not attached, because its private fields cannot be dropped.
fn read_codex_file(file: &Path, record: bool) -> Result<Vec<u8>, String> {
    let size = fs::metadata(file).map_err(|error| error.to_string())?.len();
    if size > MAX_CODEX_FILE_BYTES {
        return Err(format!(
            "it holds {size} bytes, more than the {MAX_CODEX_FILE_BYTES} that a scenario attaches"
        ));
    }
    let bytes = fs::read(file).map_err(|error| error.to_string())?;
    if !record {
        return Ok(bytes);
    }
    let Ok(Value::Object(mut fields)) = serde_json::from_slice::<Value>(&bytes) else {
        return Err("it is no JSON object, so its private fields cannot be dropped".to_owned());
    };
    for private in CODEX_PRIVATE_FIELDS {
        fields.remove(private);
    }
    serde_json::to_vec_pretty(&Value::Object(fields)).map_err(|error| error.to_string())
}
