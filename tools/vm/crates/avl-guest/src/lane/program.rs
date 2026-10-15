//! The program runner of the lane daemon: each scenario of each selected suite document, between two verified resets,
//! against the lane IDE.
//!
//! Per suite, the prologue: the document must fit its vocabulary, every story fixture must carry its bridge payloads,
//! every declared CLI must have a runtime, and the managed-agent baseline puts the registry agents of the lane back to
//! never seen. A prologue that fails is one container failure of the suite. Per scenario, under the recorder `scenario`
//! command:
//! 1. `reset:before`: `Reset` in the IDE, then the registry fault, the feedback queue and the cloud record. A reset
//!    that does not hold recycles the IDE, see [`super::recycle`].
//! 2. The preparation of a story scenario, one `StepSequence`: `SetScenarioFixture`, `SetRegistryFlag` per flag,
//!    `SeedSessionPresets`, `RemountEditorEmptyState` for the inline prompt flag, and `SessionTabs`, whose terminal tabs
//!    are the runner value `terminalBaseline`.
//! 3. The setups and the flow steps, or the authored program. Each IDE id is one bridge request `<kind>/<id>`: its
//!    arguments in camel case, the values it takes, and `timeoutMs`. The transport waits `timeoutMs`, a margin and the
//!    grace of the bridge. A host id runs here, see [`super::host_ids`].
//! 4. A failure snapshot on failure, then `reset:after`, then `done`.
//!
//! The run thread owns every call of the loop. The watchdog thread of [`super::watchdog`] watches it.

use std::cell::{RefCell, RefMut};
use std::collections::BTreeSet;
use std::fs;
use std::path::{Path, PathBuf};
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use avl_bridge::{BridgeFailure, ErrorKind};
use avl_trace::protocol::{self, Label, ScenarioCommand, Side as TraceSide, SnapshotPhase, SpanKind};
use avl_wire::daemon::TypedFailure;
use avl_wire::program::lane::LaneDocument;
use avl_wire::program::{IdKind, IdSpec, Instruction, OperationProgram, ScenarioProgram, Side, SuiteFixture, SuiteProgram, SuiteReset};
use avl_wire::report::ScenarioReport;
use serde_json::value::RawValue;
use serde_json::{Map, Value, json};

use super::host_ids::{self, HostId, HostSide, ServiceControl};
use super::readiness::request;
use super::recycle::{Isolation, ResetPhase, never_ran};
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
}

/// What the transport of an instruction waits past its deadline, before the grace of the bridge client.
const INSTRUCTION_MARGIN: Duration = Duration::from_secs(10);

/// The deadline of `Reset` and `ResetManagedAgents`, `AIR_UI_TEST_RESET_TIMEOUT_MS`.
const RESET_TIMEOUT: Duration = Duration::from_mins(2);

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
/// Where an authored part writes its artifacts in the IDE context, `AIR_AUTHORED_ARTIFACTS_DIR`.
const AUTHORED_ARTIFACTS_DIR: &str = "air-authored-artifacts";
/// How many lines of a reset failure a skip reason and a failure carry.
const CARRIED_FAILURE_LINES: usize = 20;

/// What the program runner needs from the lane IDE: the bridge, the relaunch, the lane services, and the probes of the
/// watchdog. The lane daemon gives its IDE owner; a test gives a fake.
pub(crate) trait Lane: Probes {
    /// Sends one bridge request whose deadline in the IDE is `within`, and answers its value.
    fn call(&self, request: &Value, within: Duration) -> Result<Value, avl_bridge::Error>;

    /// Quits the IDE, performs `while_down` on its context directory, launches it again on the same context, and waits
    /// for readiness. With `keep_junie_offer`, the Junie offer of the new start stays open.
    fn relaunch(&self, keep_junie_offer: bool, while_down: &mut dyn FnMut(&Path) -> Result<(), String>) -> Result<(), String>;

    fn services(&self) -> &dyn ServiceControl;

    /// The address of the JetBrains Cloud emulator, the runner value `jetBrainsCloudUrl`.
    fn cloud_url(&self) -> Option<String>;

    /// The context directory of the IDE, where an authored part writes its artifacts.
    fn context_dir(&self) -> Option<PathBuf>;

    /// The bridge endpoint that the recorder asks for its snapshots.
    fn recorder_bridge(&self) -> Option<protocol::Bridge>;
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
    /// The scenario reached no verdict: the watchdog ended it.
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

    /// The failure of a bridge request: the failure in the IDE, or why the request got no answer.
    pub(crate) fn of_bridge(error: &avl_bridge::Error) -> Self {
        match error.kind() {
            ErrorKind::Failed(failure) => Self {
                kind: failure.type_name.clone(),
                message: failure.message.clone().unwrap_or_default(),
                stack: stack_lines(failure),
                suppressed: Vec::new(),
                aborted: false,
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
    /// What the transport of an instruction waits past its deadline, [`INSTRUCTION_MARGIN`] outside a test.
    instruction_margin: Duration,
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
            instruction_margin: INSTRUCTION_MARGIN,
        }
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

    /// Runs every scenario of `suites`, in order, and closes the trace. A run that the watchdog ended starts no further
    /// scenario.
    pub(crate) fn run(self, suites: &[Suite]) {
        for suite in suites {
            if self.watch.expired().is_some() {
                break;
            }
            self.suite(suite);
        }
        if let Some(report) = self.isolation.borrow().report() {
            self.stream.note(report);
        }
        self.trace().close();
    }

    fn suite(&self, suite: &Suite) {
        let program = &suite.program;
        let reset = match self.prologue(program) {
            Ok(reset) => reset,
            Err(failure) => {
                self.stream
                    .container_failed(&program.suite, &program.test_class_name, &class_label(program), failure.typed());
                return;
            }
        };
        for (profile, raw) in program.profiles.iter().zip(&suite.raw_profiles) {
            if self.watch.expired().is_some() {
                return;
            }
            self.scenario(program, profile, raw, reset);
        }
    }

    /// The prologue of a suite: the document, the declared CLIs, and the managed-agent baseline. It answers what the
    /// reset of each scenario sends.
    fn prologue<'p>(&self, suite: &'p SuiteProgram) -> Result<&'p SuiteReset, Failure> {
        let refusals = suite.refusals();
        if !refusals.is_empty() {
            return Err(Failure::runner(
                kind::DOCUMENT,
                format!(
                    "the suite document '{}' does not fit its vocabulary: {}",
                    suite.suite,
                    refusals.join("; ")
                ),
            ));
        }
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
        if !preinstalled.is_empty() {
            return Err(Failure::runner(
                kind::UNSUPPORTED,
                format!(
                    "the suite '{}' installs the managed agents of the fixtures {preinstalled:?} through the Agents page before \
                     its first scenario, and the lane daemon has no install of its own yet",
                    suite.suite
                ),
            ));
        }
        for profile in &suite.profiles {
            if let Some(cli) = profile
                .required_executables
                .iter()
                .find(|cli| !self.document.cli_runtimes.contains(cli))
            {
                return Err(Failure::runner(
                    kind::DOCUMENT,
                    format!(
                        "the scenario '{}' drives the CLI '{cli}', and the lane declares no runtime for it; it declares {:?}",
                        profile.name, self.document.cli_runtimes
                    ),
                ));
            }
        }
        self.watch.step(&format!("{} managed-agent baseline", suite.suite), || {
            self.lane
                .services()
                .registry_clear()
                .map_err(|error| Failure::runner(kind::RESET, format!("resetting the registry distribution fault failed: {error}")))?;
            let answer = self.lifecycle("ResetManagedAgents", json!({ "agentIds": self.managed_agent_ids() }), RESET_TIMEOUT)?;
            match answer {
                Value::Null => Ok(reset),
                answer => Err(Failure::runner(
                    kind::RESET,
                    format!("Air managed-agent baseline failed: {}", text_of(&answer)),
                )),
            }
        })
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

    fn scenario(&self, suite: &SuiteProgram, profile: &ScenarioProgram, raw: &RawValue, reset: &SuiteReset) {
        let case = case_identity(suite, profile);
        if self.isolation.borrow().exhausted() {
            let reason = self.isolation.borrow().exhaustion_reason();
            self.stream.skipped(&case, never_ran(&profile.name, &reason));
            return;
        }
        let started = Instant::now();
        self.stream.started(&case);
        self.watch.execution_started(self.stream, case.execution());
        self.trace()
            .open_scenario(scenario_command(suite, profile, raw, self.lane.recorder_bridge()));
        let fixture = suite.fixtures.get(&profile.fixture).cloned().unwrap_or_default();

        let mut verdict = self
            .traced_reset(ResetPhase::Before, &profile.name, reset)
            .and_then(|()| self.body(suite, profile, &fixture));
        if verdict.is_err() {
            self.trace().snap_failure();
        }
        if !self.isolation.borrow().exhausted() && self.watch.expired().is_none() {
            let cleanup = self.traced_reset(ResetPhase::After, &profile.name, reset);
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
        let failure = verdict.err();
        self.trace().finish_scenario(failure.as_ref().map(Failure::trace).as_ref());
        let status = match &failure {
            None => CaseStatus::Successful,
            Some(failure) if failure.aborted => CaseStatus::Aborted,
            Some(_) => CaseStatus::Failed,
        };
        let report = self.report.take();
        self.stream
            .finished(&case, status, started.elapsed(), failure.as_ref().map(Failure::typed), report);
        self.watch.execution_finished(self.stream);
    }

    /// One side's reset as a `reset` span, under the isolation that recycles the IDE when it does not hold.
    fn traced_reset(&self, phase: ResetPhase, scenario: &str, reset: &SuiteReset) -> Result<(), Failure> {
        let side = phase.as_str();
        let span = self
            .trace()
            .open_span(SpanKind::Reset, side, &format!("[reset] {side} {scenario}"), None);
        let outcome = self.watch.step(&format!("{side} reset of {scenario}"), || {
            self.isolation
                .borrow_mut()
                .ensure_clean(phase, scenario, &mut || self.reset(phase, scenario, reset), &mut || {
                    self.relaunch_span("recycle", false, &mut |_| Ok(()))
                })
        });
        self.attach_dumps();
        let failure = outcome.err().map(|failure| Failure {
            suppressed: failure.suppressed,
            ..Failure::runner(kind::RESET, failure.message)
        });
        self.trace().end_span(span, failure.as_ref().map(Failure::trace).as_ref());
        failure.map_or(Ok(()), Err)
    }

    /// The reset of one side: in the IDE, then the three lane services. Every failed step is named in one message.
    fn reset(&self, phase: ResetPhase, scenario: &str, reset: &SuiteReset) -> Result<(), String> {
        let mut failures = Vec::new();
        let name = format!("Air state reset ({} '{scenario}')", phase.as_str());
        let fields = json!({
            "flagKeys": reset.flag_keys,
            "afterScenario": phase == ResetPhase::After,
            "offerJuniePromo": reset.offer_junie_promo,
            "laneFlags": reset.lane_flags,
        });
        match self.lifecycle("Reset", fields, RESET_TIMEOUT) {
            Ok(Value::Null) => {}
            Ok(answer) => failures.push(describe(&name, &text_of(&answer))),
            Err(failure) => failures.push(describe(&name, &failure.message)),
        }
        let services = self.lane.services();
        for (what, cleared) in [
            ("resetting the registry distribution fault", services.registry_clear()),
            ("clearing captured feedback", services.feedback_clear()),
            ("clearing the emulated JetBrains Cloud record", services.cloud_clear()),
        ] {
            if let Err(error) = cleared {
                failures.push(describe(what, &error));
            }
        }
        if failures.is_empty() { Ok(()) } else { Err(failures.join("\n")) }
    }

    /// A relaunch of the IDE as a `restart` span with the key `restart:<id>`.
    fn relaunch_span(
        &self,
        id: &str,
        keep_junie_offer: bool,
        while_down: &mut dyn FnMut(&Path) -> Result<(), String>,
    ) -> Result<(), String> {
        let span = self.trace().open_span(SpanKind::Restart, id, &format!("[restart] {id}"), None);
        self.trace().restart();
        let outcome = self.watch.step(&format!("relaunch for {id}"), || {
            self.watch.relaunching(|| self.lane.relaunch(keep_junie_offer, while_down))
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
        let mut runner_values = Vec::new();
        if suite.vocabulary.runner_answers.iter().any(|name| name == JETBRAINS_CLOUD_URL)
            && let Some(url) = self.lane.cloud_url()
        {
            runner_values.push((JETBRAINS_CLOUD_URL.to_owned(), Value::String(url)));
        }
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
            Side::Host => self.host_instruction(suite, id_kind, id, &label, instruction, values, fixture),
        });
        if outcome.is_ok() && id_kind == IdKind::Assertion {
            self.trace().snap(SnapshotPhase::Check);
        }
        self.attach_dumps();
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
        let timeout = authored_timeout(instruction).unwrap_or(spec.timeout_ms);
        fields.insert("timeoutMs".to_owned(), json!(timeout));
        let request_name = request_class(id_kind, id);
        let answer = self.call(
            &request_name,
            &Value::Object(fields),
            Duration::from_millis(timeout) + self.instruction_margin,
        );
        if authored {
            self.collect_authored_artifacts(instruction);
        }
        // The IDE names the id in a failure of its own. A request that got no answer is named here.
        let answer = answer.map_err(|mut failure| {
            if failure.kind == kind::BRIDGE {
                failure.message = format!("the instruction '{label}' got no answer: {}", failure.message);
            }
            failure
        })?;
        if authored {
            let declared = instruction.uses.as_ref().map(|uses| uses.answers.as_slice()).unwrap_or_default();
            let answered = answer.get("values").and_then(Value::as_object).cloned().unwrap_or_default();
            values.keep_part_values(label, &answered, declared).map_err(missing)?;
            self.report_part(label, &answer);
            return Ok(());
        }
        values.keep_answers(label, spec, &answer).map_err(missing)
    }

    /// An id that runs on the host. An authored program may run `restart-ide` only.
    #[expect(
        clippy::too_many_arguments,
        reason = "the parts of one instruction, which the caller resolved once"
    )]
    fn host_instruction(
        &self,
        suite: &SuiteProgram,
        id_kind: IdKind,
        id: &str,
        label: &str,
        instruction: &Instruction,
        values: &mut Values,
        fixture: &SuiteFixture,
    ) -> Result<(), Failure> {
        let host = HostId::of(id_kind == IdKind::Assertion, id)
            .ok_or_else(|| Failure::runner(kind::DOCUMENT, format!("the host id '{label}' has no word of the host vocabulary")))?;
        if suite.authored && host != HostId::Action(avl_wire::program::HostAction::RestartIde) {
            return Err(Failure::runner(
                kind::DOCUMENT,
                format!("the authored program runs '{label}', which no authored program may run"),
            ));
        }
        let mut side = RunnerHost {
            runner: self,
            fixture,
            authored: suite.authored,
        };
        host_ids::perform(host, label, &instruction.args, values, &mut side).map_err(|message| Failure::runner(kind::HOST, message))
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

    /// Moves the artifact directory of an authored part out of the IDE context into the iteration directory, also after
    /// a failed part.
    fn collect_authored_artifacts(&self, instruction: &Instruction) {
        let arg = |name: &str| instruction.args.get(name).and_then(Value::as_str).unwrap_or_default().to_owned();
        let (suite, scenario, part) = (arg("suite"), arg("scenario"), arg("part"));
        let Some(context) = self.lane.context_dir() else { return };
        let source = context.join(AUTHORED_ARTIFACTS_DIR).join(&suite).join(&scenario).join(&part);
        if !source.is_dir() {
            return;
        }
        let target = self
            .iteration_dir
            .join("authored-artifacts")
            .join(&suite)
            .join(&scenario)
            .join(&part);
        let moved = target
            .parent()
            .map_or(Ok(()), fs::create_dir_all)
            .and_then(|()| fs::rename(&source, &target));
        match moved {
            Ok(()) => self
                .stream
                .note(format!("[authored] artifacts of {suite}/{scenario}/{part}: {}", target.display())),
            Err(error) => self
                .stream
                .note(format!("[authored] cannot move the artifacts {}: {error}", source.display())),
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

/// The host side of an instruction: the services of the lane, and a restart that states the fixture again.
struct RunnerHost<'a, 'r, 's> {
    runner: &'a Runner<'r, 's>,
    fixture: &'a SuiteFixture,
    authored: bool,
}

impl HostSide for RunnerHost<'_, '_, '_> {
    fn services(&self) -> &dyn ServiceControl {
        self.runner.lane.services()
    }

    fn restart_ide(&mut self, while_down: &mut dyn FnMut(&Path) -> Result<(), String>) -> Result<(), String> {
        self.runner
            .relaunch_span("ide", self.fixture.keeps_junie_startup_offer, while_down)?;
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
}

/// The `testClass` label of a suite, or its `testClassName` when it has none.
fn class_label(suite: &SuiteProgram) -> String {
    label_value(suite, "testClass").unwrap_or_else(|| suite.test_class_name.clone())
}

fn label_value(suite: &SuiteProgram, name: &str) -> Option<String> {
    suite
        .labels
        .iter()
        .find(|label| label.name == name)
        .map(|label| label.value.clone())
}

/// The identity of the case of `profile`: the class and the factory method of the JUnit node of the JVM host.
fn case_identity(suite: &SuiteProgram, profile: &ScenarioProgram) -> CaseIdentity {
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
