//! One iteration of the lane daemon: the run stream of `/run` from `runStarted` to `summary`.
//!
//! The iteration reads the lane document, the digest of the flow vocabulary and the suite documents from the jars that
//! the push stored, selects the documents of the lane, makes the IDE of the lane ready, and names the plan. The program runner of
//! [`super::program`] then runs each scenario of each selected document, under the watchdog of [`super::watchdog`] and
//! with the recorder of [`super::trace`]. The bundles of the recorder go to `<iterationDir>/air-traces`, which the
//! trace pull of the controller packs, or to the outputs directory of the Bazel test of `lane-run`. The JUnit XML of the stream goes to `<iterationDir>/test.xml`, which `/results`
//! serves.

use std::collections::{BTreeMap, BTreeSet};
use std::fs;
use std::io::Write;
use std::panic::{self, AssertUnwindSafe};
use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::time::{Duration, Instant};

use avl_trace::protocol;
use avl_wire::daemon::{LaneClass, LaneInputs, PlanStarted, RunEventKind, RunFailed, RunRequest, RunStarted, Summary, WatchdogState};
use avl_wire::program::lane::{LaneDocument, parse_lane_document};
use avl_wire::program::{parse_suite_program, read_lane_documents, read_suite_documents, read_vocabulary_digest};
use regex::Regex;
use serde_json::{Value, json};

use super::channel::{Daemon, log};
use super::context::option_files_digest;
use super::evidence;
use super::ide::{ContextPlugin, KillTarget, LaneLaunch, Records, Relaunch, RelaunchParts};
use super::identity::{BRIDGE_PORT, BRIDGE_TOKEN_PURPOSE, KeyInputs, LaneIdentity};
use super::inputs::TraceInputs;
use super::live::LiveRun;
use super::package::sha256_hex;
use super::program::{Failure, IdeProcess, Lane, Runner, Suite, case_identity, class_label, kind};
use super::readiness::{self, Bridge, READY_TIMEOUT, request};
use super::rerun::Rerun;
use super::services::cloud::spawner::ControlEndpoint;
use super::session_logs;
use super::stream::Stream;
use super::trace::{MAX_LOOSE_BYTES, RecorderProcess, Trace};
use super::watchdog::{self, DISMISS_TIMEOUT, INTRUDER_GRACE_MS, PROBE_TIMEOUT, Policy, Probes, WATCHDOG_THREAD, Watch};
use crate::cli::{CancelArgs, RootArgs, RunArgs};
use crate::clock::now_stamp;
use crate::supervisor::{self, LiveSystem};

#[cfg(test)]
mod tests;

/// The budget that the watchdog record names for the IDE part: a fresh launch, then readiness.
const IDE_BUDGET: Duration = Duration::from_mins(5);

/// The JUnit XML of an iteration in its directory.
pub(crate) const TEST_XML_FILE: &str = "test.xml";

/// The suite documents that an iteration runs, the lane document of their lane, and the digest of the flow vocabulary.
#[derive(Debug)]
pub(crate) struct Selection {
    pub lane_bytes: Vec<u8>,
    pub lane: LaneDocument,
    /// The digest of the flow vocabulary file beside the lane documents, which the bridge of the lane must serve.
    pub vocabulary_digest: String,
    pub suites: Vec<Suite>,
    /// The suites of the lane that the controller names and that have no suite document. The plan names each one, and
    /// each ends as a failed container [`kind::UNSUPPORTED`].
    pub undocumented: Vec<LaneClass>,
    /// The count of the selected suites before the runner split, documented and undocumented.
    pub selected: usize,
}

/// The share of one runner when a TeamCity job splits a lane across its runners. Of the selected suites in the order
/// of the lane, runner `index` takes each suite whose position is `index` modulo `count`. So the runners of one job
/// together run each selected suite once. The whole lane is runner 0 of 1.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct RunnerSplit {
    pub index: u32,
    pub count: u32,
}

impl RunnerSplit {
    /// The whole lane: the split of the lane daemon and of a run that names no runner.
    pub(crate) const WHOLE: Self = Self { index: 0, count: 1 };

    /// Whether this runner takes the selected suite at `position`.
    fn takes(self, position: usize) -> bool {
        u32::try_from(position).is_ok_and(|position| position % self.count == self.index)
    }

    /// The items at the positions that this runner takes, in their order.
    fn share<T>(self, items: Vec<T>) -> Vec<T> {
        items
            .into_iter()
            .enumerate()
            .filter_map(|(position, item)| self.takes(position).then_some(item))
            .collect()
    }
}

impl Selection {
    /// The test class of every planned suite, in name order: the suite documents and the undocumented suites.
    pub(crate) fn class_names(&self) -> Vec<String> {
        let names: BTreeSet<String> = self
            .suites
            .iter()
            .map(|suite| suite.program.test_class_name.clone())
            .chain(self.undocumented.iter().map(|class| class.test_class_name.clone()))
            .collect();
        names.into_iter().collect()
    }
}

/// What a selection is read from: the lane, the jars of the documents, and the selectors.
pub(crate) struct SelectionInputs<'a> {
    pub lane: &'a str,
    pub lane_documents: &'a Path,
    pub suite_documents: &'a Path,
    pub selectors: &'a [String],
    /// The JUnit5 filters of the request, `<option>=<value>`.
    pub junit5_filters: &'a [String],
    /// The suites of the lane that the catalog names, `laneClasses` of the request.
    pub lane_classes: &'a [LaneClass],
    /// The share of this runner of the selected suites.
    pub split: RunnerSplit,
}

/// Reads the lane document `<lane>.json`, the digest of the flow vocabulary beside it, and the suite documents of its
/// lane. A selector names the `testClassName` of a
/// document, with or without a package. A `#<scenario>` part keeps only the scenario of that name or key. No selector
/// selects every document of the lane. The JUnit5 filters then narrow the documents, see [`apply_filters`].
///
/// A suite of `lane_classes` that has no suite document is planned too, so that the plan names every class that the JVM
/// runner runs. The selectors and the filters narrow these suites by their test class as well.
pub(crate) fn select(inputs: &SelectionInputs<'_>) -> Result<Selection, String> {
    let lanes = read_lane_documents(inputs.lane_documents)?;
    let name = format!("{}.json", inputs.lane);
    let lane_bytes = lanes.get(&name).cloned().ok_or_else(|| {
        format!(
            "the lane documents hold no {name}; they hold {:?}",
            lanes.keys().collect::<Vec<_>>()
        )
    })?;
    let lane = parse_lane_document(&lane_bytes).map_err(|problem| format!("the lane document {name} {problem}"))?;
    let vocabulary_digest = read_vocabulary_digest(inputs.lane_documents)?;
    let mut documents = Vec::new();
    for (file, content) in read_suite_documents(inputs.suite_documents)? {
        let suite = parse_suite_program(&content).map_err(|problem| format!("the suite document {file} {problem}"))?;
        if suite.lane == lane.lane {
            documents.push((suite, content));
        }
    }
    let mut suites = super::program::suites_of(documents)?;
    let documented: BTreeSet<&str> = suites.iter().map(|suite| suite.program.test_class_name.as_str()).collect();
    let mut undocumented: Vec<LaneClass> = inputs
        .lane_classes
        .iter()
        .filter(|class| !documented.contains(class.test_class_name.as_str()))
        .cloned()
        .collect();
    // The scenarios that the selectors name, by class: `None` keeps every scenario of the class.
    let mut wanted: BTreeMap<&str, Option<Vec<&str>>> = BTreeMap::new();
    for selector in inputs.selectors {
        let (class, scenario) = selector
            .split_once('#')
            .map_or((selector.as_str(), None), |(class, scenario)| (class, Some(scenario)));
        let class = class.rsplit('.').next().unwrap_or(class);
        let entry = wanted.entry(class).or_insert_with(|| Some(Vec::new()));
        match (entry.as_mut(), scenario) {
            (Some(scenarios), Some(scenario)) => scenarios.push(scenario),
            _ => *entry = None,
        }
    }
    if !wanted.is_empty() {
        suites.retain(|suite| wanted.contains_key(suite.program.test_class_name.as_str()));
        undocumented.retain(|class| wanted.contains_key(class.test_class_name.as_str()));
        if suites.is_empty() && undocumented.is_empty() {
            return Err(format!(
                "no suite document of the lane {} has the test class {:?}",
                lane.lane,
                wanted.keys().collect::<Vec<_>>()
            ));
        }
        for suite in &mut suites {
            if let Some(Some(scenarios)) = wanted.get(suite.program.test_class_name.as_str()) {
                suite.retain_scenarios(|profile| {
                    scenarios.contains(&profile.name.as_str()) || profile.key.as_deref().is_some_and(|key| scenarios.contains(&key))
                });
            }
        }
    }
    apply_filters(&mut suites, &mut undocumented, inputs.junit5_filters, &lane.lane)?;
    suites.sort_by(|left, right| left.program.test_class_name.cmp(&right.program.test_class_name));
    let selected = suites.len() + undocumented.len();
    Ok(Selection {
        lane_bytes,
        lane,
        vocabulary_digest,
        suites: inputs.split.share(suites),
        undocumented: inputs.split.share(undocumented),
        selected,
    })
}

/// The failure of a planned suite that has no suite document, [`kind::UNSUPPORTED`].
pub(crate) fn undocumented_failure(class: &LaneClass, lane: &str) -> Failure {
    Failure::runner(
        kind::UNSUPPORTED,
        format!(
            "the catalog names the suite '{}' ({}) for the lane {lane}, and the suite documents of the run hold no \
             document of it",
            class.suite, class.test_class_name
        ),
    )
}

/// Keeps the documents that the JUnit5 `filters` select, as JUnit applies them to the class of each document: the
/// `testClass` label. An `include-classname` pattern must match the whole class name, and a document passes when one
/// of them matches. An `exclude-classname` pattern that matches drops it. A package filter takes the package and its
/// sub-packages. The lane of the request selects the lane, so a tag filter, which the documents do not carry, is a
/// refusal, and so is a set of filters that keeps no suite.
///
/// The filters narrow the `undocumented` suites by their simple class name, which a simple-name pattern matches. A
/// package filter keeps none of them, because no document states their package.
pub(crate) fn apply_filters(
    suites: &mut Vec<Suite>,
    undocumented: &mut Vec<LaneClass>,
    filters: &[String],
    lane: &str,
) -> Result<(), String> {
    if filters.is_empty() {
        return Ok(());
    }
    let pattern = |value: &str| {
        Regex::new(&format!("^(?:{value})$")).map_err(|error| format!("the JUnit5 filter pattern {value:?} is not a pattern: {error}"))
    };
    let (mut include_classes, mut exclude_classes) = (Vec::new(), Vec::new());
    let (mut include_packages, mut exclude_packages) = (Vec::new(), Vec::new());
    for filter in filters {
        let (option, value) = filter
            .split_once('=')
            .ok_or_else(|| format!("the JUnit5 filter {filter:?} is not <option>=<value>"))?;
        match option {
            "include-classname" => include_classes.push(pattern(value)?),
            "exclude-classname" => exclude_classes.push(pattern(value)?),
            "include-package" => include_packages.push(value.to_owned()),
            "exclude-package" => exclude_packages.push(value.to_owned()),
            "include-tag" | "exclude-tag" => {
                return Err(format!(
                    "the lane daemon selects the documents of its lane and reads no JUnit tag, so it refuses the filter {filter}"
                ));
            }
            _ => {
                return Err(format!(
                    "the JUnit5 filter {filter:?} has an option that the lane daemon does not know"
                ));
            }
        }
    }
    let in_package = |class: &str, package: &str| class.strip_prefix(package).is_some_and(|rest| rest.starts_with('.'));
    let keeps = |class: &str| {
        (include_classes.is_empty() || include_classes.iter().any(|include| include.is_match(class)))
            && !exclude_classes.iter().any(|exclude| exclude.is_match(class))
            && (include_packages.is_empty() || include_packages.iter().any(|package| in_package(class, package)))
            && !exclude_packages.iter().any(|package| in_package(class, package))
    };
    suites.retain(|suite| keeps(&class_label(&suite.program)));
    undocumented.retain(|class| keeps(&class.test_class_name));
    if suites.is_empty() && undocumented.is_empty() {
        return Err(format!(
            "the JUnit5 filters {filters:?} select no suite document of the lane {lane}"
        ));
    }
    Ok(())
}

/// Runs one iteration of `request` and writes its records to `out`. A write that fails ends the stream, not the run.
/// The JUnit XML of the records goes to [`TEST_XML_FILE`] of the iteration directory, and the run answers it. The lane
/// daemon reruns no failed scenario.
pub(crate) fn run(daemon: &Daemon, request: &RunRequest, out: &mut (dyn Write + Send)) -> String {
    run_split(daemon, request, RunnerSplit::WHOLE, &[], None, out)
}

/// [`run`] of the share `split` of the selected suites, with the rerun `rerun` of a `lane-run`. The stream carries
/// `notes` right after the start of the run.
pub(crate) fn run_split(
    daemon: &Daemon,
    request: &RunRequest,
    split: RunnerSplit,
    notes: &[String],
    rerun: Option<&Rerun>,
    out: &mut (dyn Write + Send),
) -> String {
    let stream = Stream::new(out);
    let iteration_id = daemon.next_iteration_id();
    let started_at = now_stamp();
    let iteration_dir = daemon.iteration_dir(&iteration_id);
    if let Err(error) = fs::create_dir_all(&iteration_dir) {
        stream.note(format!("cannot create {}: {error}", iteration_dir.display()));
    }
    stream.emit(&RunEventKind::RunStarted(RunStarted {
        iteration_id: iteration_id.clone(),
        daemon_boot_stamp: daemon.boot_stamp().to_owned(),
        started_at: started_at.clone(),
    }));
    for note in notes {
        stream.note(note.clone());
    }
    log(&format!(
        "iteration {iteration_id} started: lane {}",
        request.lane.as_deref().unwrap_or("<none>")
    ));
    // A panic of the run is a failed iteration with a summary, so the stream never ends without one.
    // The budget of the run counts from the request, so the boot of the IDE spends it too.
    let run_end = request.run_budget_sec.map(|seconds| Instant::now() + Duration::from_secs(seconds));
    let executed = panic::catch_unwind(AssertUnwindSafe(|| {
        execute(daemon, request, split, rerun, &stream, &iteration_dir, &iteration_id, run_end)
    }))
    .unwrap_or_else(|panic| {
        let message = panic
            .downcast_ref::<String>()
            .cloned()
            .or_else(|| panic.downcast_ref::<&str>().map(|text| (*text).to_owned()))
            .unwrap_or_default();
        Err(format!("the run thread panicked: {message}"))
    });
    let failed = match executed {
        Ok(expired) => expired,
        Err(error) => {
            stream.emit(&RunEventKind::RunFailed(RunFailed { error }));
            stream.emit(&watchdog_record("completed", request, None));
            true
        }
    };
    let counts = stream.counts();
    stream.emit(&RunEventKind::Summary(Summary {
        iteration_id: iteration_id.clone(),
        daemon_boot_stamp: daemon.boot_stamp().to_owned(),
        started_at,
        tests_started: counts.started,
        tests_failed: counts.failed,
        tests_skipped: counts.skipped,
        containers_skipped: 0,
        container_failures: counts.container_failures,
        has_failures: failed || counts.failed > 0 || counts.container_failures > 0,
        ide_running: daemon.ide_running(),
    }));
    log(&format!(
        "iteration {iteration_id} ended: {} started, {} failed, {} skipped, {} container failure(s){}",
        counts.started,
        counts.failed,
        counts.skipped,
        counts.container_failures,
        if failed { ", failed" } else { "" }
    ));
    let xml = stream.junit_xml();
    let file = iteration_dir.join(TEST_XML_FILE);
    if let Err(error) = fs::write(&file, &xml) {
        log(&format!("cannot write the JUnit XML {}: {error}", file.display()));
    }
    xml
}

/// The selection of `request` from the documents that the push stored: the documents of `lane`, narrowed by the
/// selectors and the filters of the request.
pub(crate) fn selection_of(
    daemon: &Daemon,
    lane: &str,
    lane_inputs: &LaneInputs,
    request: &RunRequest,
    split: RunnerSplit,
) -> Result<Selection, String> {
    let lane_documents = daemon.stored(&lane_inputs.lane_documents)?;
    let suite_documents = daemon.stored(&lane_inputs.suite_documents)?;
    select(&SelectionInputs {
        lane,
        lane_documents: &lane_documents,
        suite_documents: &suite_documents,
        selectors: &request.selectors,
        junit5_filters: &request.junit5_filters,
        lane_classes: &request.lane_classes,
        split,
    })
}

/// The config key of the IDE of the lane of `selection` for the product `product_stamp`: the lane, its document, the
/// identity of each declared CLI runtime and of each declared runtime of its live context, and the option files.
pub(crate) fn lane_key(daemon: &Daemon, selection: &Selection, product_stamp: &str) -> Result<String, String> {
    let mut runtimes: BTreeMap<String, String> = daemon
        .with_ide(|owner| owner.runtimes_named(&selection.lane.cli_runtimes))
        .map_err(|refusal| format!("{}: {}", refusal.code, refusal.message))?
        .into_iter()
        .map(|runtime| (runtime.name, runtime.identity))
        .collect();
    if let Some(live) = &selection.lane.live {
        let names: Vec<&String> = std::iter::once(&live.central_runtime)
            .chain(live.runtimes.iter().map(|runtime| &runtime.name))
            .collect();
        daemon.with_ide(|owner| {
            for runtime in owner.runtimes().iter().filter(|runtime| names.contains(&&runtime.name)) {
                runtimes.insert(runtime.name.clone(), runtime.identity.clone());
            }
        });
    }
    Ok(KeyInputs {
        lane: &selection.lane.lane,
        lane_document: &sha256_hex(&selection.lane_bytes),
        product_stamp: Some(product_stamp),
        runtimes: &runtimes,
        option_files: &option_files_digest(),
    }
    .config_key())
}

/// The part of an iteration that can fail as a whole: the documents, the live run, the IDE, the plan and the scenarios.
/// It answers whether the watchdog ended the run.
///
/// A live lane provisions its account before the IDE, and its run ends after the scenarios, also after a failure,
/// before the summary of the iteration.
#[expect(clippy::too_many_arguments, reason = "the parts of one iteration that the caller resolved once")]
fn execute(
    daemon: &Daemon,
    request: &RunRequest,
    split: RunnerSplit,
    rerun: Option<&Rerun>,
    stream: &Stream<'_>,
    iteration_dir: &Path,
    iteration_id: &str,
    run_end: Option<Instant>,
) -> Result<bool, String> {
    let (Some(lane), Some(lane_inputs)) = (&request.lane, &request.lane_inputs) else {
        return Err("the run request names no lane and no lane inputs, which the lane daemon needs".to_owned());
    };
    let fake_agent_jar = daemon.stored(&lane_inputs.fake_agent_jar)?;
    let selection = selection_of(daemon, lane, lane_inputs, request, split)?;
    if split != RunnerSplit::WHOLE {
        stream.note(format!(
            "lane {lane}: the runner with index {index} of {count} runners takes {} of the {} selected suite(s), each one \
             whose position in the lane order is {index} modulo {count}",
            selection.suites.len() + selection.undocumented.len(),
            selection.selected,
            index = split.index,
            count = split.count,
        ));
    }
    let live = start_live(daemon, &selection, stream)?;
    let outcome = execute_selection(
        daemon,
        request,
        stream,
        iteration_dir,
        iteration_id,
        &fake_agent_jar,
        &selection,
        live.as_ref(),
        rerun,
        run_end,
    );
    let Some(live) = live else {
        return outcome;
    };
    let ended = live.end();
    take_notes(stream, &live);
    match (outcome, ended) {
        (outcome, Ok(())) => outcome,
        (Ok(_), Err(refusal)) => Err(refusal_text(&refusal)),
        (Err(error), Err(refusal)) => Err(format!("{error}; and the end of the live run failed: {}", refusal_text(&refusal))),
    }
}

/// The live run of `selection` with its account provisioned, when the lane document states a live context. A run whose
/// provisioning fails ends before the refusal, so the account is torn down.
fn start_live(daemon: &Daemon, selection: &Selection, stream: &Stream<'_>) -> Result<Option<LiveRun>, String> {
    let Some(context) = &selection.lane.live else {
        return Ok(None);
    };
    let declared = daemon.with_ide(|owner| owner.runtimes().to_vec());
    let live = LiveRun::start(context, &declared, &|name| std::env::var(name).ok(), cfg!(target_os = "linux"))
        .map_err(|refusal| refusal_text(&refusal))?;
    stream.note(format!(
        "lane {}: provisioning the JetBrains Central account of the guest",
        selection.lane.lane
    ));
    let provisioned = live.provision();
    take_notes(stream, &live);
    if let Err(refusal) = provisioned {
        let ended = live.end();
        take_notes(stream, &live);
        return Err(match ended {
            Ok(()) => refusal_text(&refusal),
            Err(teardown) => format!(
                "{}; and the end of the live run failed: {}",
                refusal_text(&refusal),
                refusal_text(&teardown)
            ),
        });
    }
    Ok(Some(live))
}

/// The notes of `live` since the last call, on the stream.
fn take_notes(stream: &Stream<'_>, live: &LiveRun) {
    for line in live.take_notes() {
        stream.note(line);
    }
}

fn refusal_text(refusal: &crate::reply::AgentRefusal) -> String {
    format!("{}: {}", refusal.code, refusal.message)
}

/// The iteration after the documents and the live run: the IDE, the plan and the scenarios.
#[expect(clippy::too_many_arguments, reason = "the parts of one iteration that the caller resolved once")]
fn execute_selection(
    daemon: &Daemon,
    request: &RunRequest,
    stream: &Stream<'_>,
    iteration_dir: &Path,
    iteration_id: &str,
    fake_agent_jar: &Path,
    selection: &Selection,
    live: Option<&LiveRun>,
    rerun: Option<&Rerun>,
    run_end: Option<Instant>,
) -> Result<bool, String> {
    let lane = request.lane.as_deref().unwrap_or_default();
    let plugins: Vec<ContextPlugin> = request
        .ide_plugins
        .iter()
        .map(|plugin| {
            Ok(ContextPlugin {
                destination: plugin.destination.clone(),
                sha256: plugin.sha256.clone(),
                source: daemon.stored(&plugin.sha256)?,
            })
        })
        .collect::<Result<_, String>>()?;
    stream.note(format!(
        "lane {lane}: {} suite document(s) of {}, {} suite(s) with no document, {} context plugin file(s)",
        selection.suites.len(),
        selection.lane.lane,
        selection.undocumented.len(),
        plugins.len()
    ));
    stream.emit(&watchdog_record("ide", request, Some(IDE_BUDGET + READY_TIMEOUT)));
    let key = lane_key(daemon, selection, &request.product_stamp)?;
    let identity = LaneIdentity::new(key, daemon.port());
    let launch = LaneLaunch {
        document: &selection.lane,
        vocabulary_digest: &selection.vocabulary_digest,
        identity: &identity,
        product_stamp: &request.product_stamp,
        fake_agent_jar,
        plugins: &plugins,
        live,
    };
    // Each suite of a bare IDE launches its own, so a run of such suites alone needs no IDE of the lane.
    if selection.suites.iter().any(|suite| suite.program.launch.is_none()) {
        let decision = daemon
            .with_ide(|owner| owner.ensure(&launch, &mut Records(&mut |record| stream.emit(&record))))
            .map_err(|refusal| format!("{}: {}", refusal.code, refusal.message))?;
        stream.note(format!("lane {lane}: ide {}", decision.action.as_str()));
        // A fresh context got its seed from the context writer. A reused one lost its copies at the end of the last
        // iteration, and the proxy of this iteration has a new secret, so it gets its seed here.
        if let Some(live) = live {
            let seeded = daemon
                .with_ide(|owner| owner.held_paths())
                .map_or(Ok(()), |paths| live.seed(&paths));
            take_notes(stream, live);
            seeded.map_err(|refusal| refusal_text(&refusal))?;
        }
    } else {
        stream.note(format!(
            "lane {lane}: every selected suite launches a bare IDE, so the IDE of the lane does not start"
        ));
    }

    let discovered: usize = selection.suites.iter().map(|suite| suite.program.profiles.len()).sum();
    let discovered = u32::try_from(discovered).unwrap_or(u32::MAX);
    stream.emit(&RunEventKind::PlanStarted(PlanStarted {
        timestamp: now_stamp(),
        discovered_executions: discovered,
        class_names: selection.class_names(),
        executions: selection
            .suites
            .iter()
            .flat_map(|suite| {
                suite
                    .program
                    .profiles
                    .iter()
                    .map(|profile| case_identity(&suite.program, profile).execution())
            })
            .collect(),
    }));
    for class in &selection.undocumented {
        stream.container_failed(
            &class.suite,
            &class.test_class_name,
            &class.test_class_name,
            undocumented_failure(class, lane).typed(),
        );
    }
    let token = identity.secret(BRIDGE_TOKEN_PURPOSE);
    let daemon_lane = DaemonLane {
        daemon,
        launch: &launch,
        stream,
        bridge: Bridge::new(BRIDGE_PORT, &token).map_err(|refusal| refusal.message)?,
        token,
        target: daemon.with_ide(|owner| owner.kill_target()),
        live,
    };
    // Dropped at the end of the run, which removes the copies from the context.
    let staged = session_logs::stage(daemon_lane.context_dir().as_deref(), &request.session_logs, &|sha256| {
        daemon.stored(sha256)
    })?;
    if !request.session_logs.is_empty() {
        stream.note(match staged.logs.len() {
            0 => format!(
                "lane {lane}: no IDE of the lane runs, so the {} recorded session(s) stay unstaged",
                request.session_logs.len()
            ),
            count => format!("lane {lane}: {count} recorded session(s) in the context of the lane IDE"),
        });
    }
    let trace_inputs = daemon.with_ide(|owner| owner.trace_inputs().clone());
    let trace = start_trace(&trace_inputs, iteration_dir, iteration_id, daemon_lane.recorder_bridge());
    let watch = Arc::new(Watch::new(Policy {
        active_execution: Duration::from_secs(request.active_execution_timeout_sec),
        progress_gap: Duration::from_secs(request.progress_gap_timeout_sec),
        run_end,
    }));
    watch.plan_started(stream, discovered);
    daemon.publish_watch(Some(Arc::clone(&watch)));
    std::thread::scope(|scope| {
        // Named, so a hang report tells the watchdog from the run thread. A watchdog that does not start fails the run.
        std::thread::Builder::new()
            .name(WATCHDOG_THREAD.to_owned())
            .spawn_scoped(scope, || watchdog::watch(&watch, stream, &daemon_lane, iteration_dir))
            .expect("the watchdog thread starts");
        Runner::new(&daemon_lane, stream, &watch, &selection.lane, iteration_dir, trace)
            .with_session_logs(staged.logs.clone())
            .with_rerun(rerun.cloned())
            .run(&selection.suites);
        watch.completed(stream);
    });
    daemon.publish_watch(None);
    Ok(watch.expired().is_some())
}

/// The trace of the iteration: the recorder of `inputs`, or a quiet session when there is none or it does not start. A
/// recorder that does not start is a failure of the session, which the runner says on the stream. Under a Bazel test the
/// attachments outside a recorded scenario go to the outputs directory of the test.
fn start_trace(inputs: &TraceInputs, iteration_dir: &Path, iteration_id: &str, bridge: Option<protocol::Bridge>) -> Trace {
    let loose = inputs.bazel_outputs.as_deref().map(evidence::attachments_dir);
    let Some(recorder) = &inputs.recorder else {
        log("no recorder is named, so the iteration records no trace");
        return Trace::off().keep_loose_attachments_in(loose, MAX_LOOSE_BYTES);
    };
    let trace = match RecorderProcess::start(recorder) {
        Ok(process) => Trace::open(Box::new(process), Trace::hello(iteration_dir, iteration_id, inputs, bridge)),
        Err(error) => {
            let reason = format!("{error}; the iteration records no trace");
            log(&reason);
            Trace::failed(reason)
        }
    };
    trace.keep_loose_attachments_in(loose, MAX_LOOSE_BYTES)
}

/// The lane of the daemon: its IDE owner, its bridge and its lane services.
struct DaemonLane<'a, 's> {
    daemon: &'a Daemon,
    launch: &'a LaneLaunch<'a>,
    stream: &'a Stream<'s>,
    bridge: Bridge,
    token: String,
    /// The IDE run that a kill ends, as the owner publishes it after each launch and clears it in each quit. The
    /// watchdog reads it without the lock of the owner, which the run thread holds during a relaunch.
    target: KillTarget,
    /// The live run of the iteration, for the live lane alone.
    live: Option<&'a LiveRun>,
}

impl DaemonLane<'_, '_> {
    fn with_services<T>(&self, work: impl FnOnce(&super::ide::LaneServices) -> Result<T, String>) -> Result<T, String> {
        self.daemon.with_ide(|owner| {
            owner
                .services()
                .ok_or_else(|| "the lane services do not run".to_owned())
                .and_then(work)
        })
    }
}

impl Lane for DaemonLane<'_, '_> {
    fn call(&self, request: &Value, within: Duration) -> Result<Value, avl_bridge::Error> {
        self.bridge.post(request, within)
    }

    fn relaunch(
        &self,
        cause: Relaunch,
        while_down: &mut dyn FnMut(&Path) -> Result<(), String>,
        parts: &mut dyn RelaunchParts,
    ) -> Result<(), String> {
        let restarted = self.daemon.with_ide(|owner| {
            owner.restart(
                self.launch,
                cause,
                while_down,
                &mut Records(&mut |record| self.stream.emit(&record)),
                parts,
            )
        });
        restarted.map_err(|refusal| format!("{}: {}", refusal.code, refusal.message))
    }

    fn services(&self) -> &dyn super::host_ids::ServiceControl {
        self
    }

    fn cloud_url(&self) -> Option<String> {
        self.with_services(|services| Ok(services.cloud().base_url().to_owned())).ok()
    }

    fn context_dir(&self) -> Option<PathBuf> {
        self.daemon.with_ide(|owner| owner.context_dir())
    }

    fn recorder_bridge(&self) -> Option<protocol::Bridge> {
        Some(protocol::Bridge {
            port: BRIDGE_PORT,
            token: self.token.clone(),
        })
    }

    fn ide_process(&self) -> Result<IdeProcess, String> {
        Ok(IdeProcess {
            launched: self.daemon.with_ide(|owner| owner.pid()),
            bridge: readiness::bridge_pid(&self.bridge, self.launch.identity.key())?,
        })
    }

    fn launch_bare(&self, key: &str) -> Result<(), String> {
        let launched = self
            .daemon
            .with_ide(|owner| owner.launch_bare(self.launch, key, &mut Records(&mut |record| self.stream.emit(&record))));
        launched.map_err(|refusal| format!("{}: {}", refusal.code, refusal.message))
    }

    fn trace_get(&self, route: &str, accept: &str, within: Duration) -> Result<Vec<u8>, avl_bridge::Error> {
        self.bridge.get(route, accept, within)
    }

    fn reload_air(&self, within: Duration) -> Result<(), String> {
        let client = avl_bridge::Client::new(&avl_bridge::url(BRIDGE_PORT), &self.token).map_err(|error| error.to_string())?;
        super::reload::reload_air(&client, self.launch.identity.key(), within, &mut |text| self.stream.note(text))?;
        readiness::ping(&self.bridge).map_err(|refusal| format!("the reloaded bridge plugin does not answer: {}", refusal.message))
    }

    fn end_bare(&self) -> Result<(), String> {
        let ended = self
            .daemon
            .with_ide(|owner| owner.end_bare(&mut Records(&mut |record| self.stream.emit(&record))));
        ended.map_err(|refusal| format!("{}: {}", refusal.code, refusal.message))
    }

    fn refresh_live_wiring(&self) -> Result<(), String> {
        let live = self
            .live
            .ok_or_else(|| "the lane has no live run, so it has no Central wiring to refresh".to_owned())?;
        let refreshed = live.refresh();
        take_notes(self.stream, live);
        refreshed
    }

    fn leftover_junie_sessions(&self) -> Result<Vec<String>, String> {
        self.live
            .map(LiveRun::leftover_junie_sessions)
            .ok_or_else(|| "the lane has no live run, so it has no Junie session to hand over".to_owned())
    }
}

impl super::host_ids::ServiceControl for DaemonLane<'_, '_> {
    fn registry_mark(&self) -> Result<usize, String> {
        self.with_services(|services| Ok(registry(services)?.mark()))
    }

    fn registry_requests_since(&self, mark: usize, prefix: &str) -> Result<usize, String> {
        self.with_services(|services| Ok(registry(services)?.events_since(mark, prefix).len()))
    }

    fn registry_log(&self) -> String {
        self.with_services(|services| Ok(registry(services)?.request_log()))
            .unwrap_or_else(|error| error)
    }

    fn registry_arm(&self, fault: &str, status: Option<u16>, hold_ms: Option<u64>) -> Result<(), String> {
        self.with_services(|services| registry(services)?.arm(fault, status, hold_ms))
    }

    fn registry_clear(&self) -> Result<(), String> {
        // A lane without registry agents runs no registry, and it has no fault to clear.
        self.with_services(|services| {
            if let Some(registry) = services.registry() {
                registry.clear();
            }
            Ok(())
        })
    }

    fn registry_fault(&self) -> Result<String, String> {
        self.with_services(|services| Ok(registry(services)?.armed_fault().to_owned()))
    }

    fn feedback_clear(&self) -> Result<(), String> {
        self.with_services(|services| {
            if let Some(feedback) = services.feedback() {
                feedback.clear();
            }
            Ok(())
        })
    }

    fn cloud_clear(&self) -> Result<(), String> {
        self.with_services(|services| {
            services.cloud().clear();
            Ok(())
        })
    }

    fn cloud_attach_spawner(&self, agent_key: Option<&str>) -> Result<(), String> {
        let control = agent_key.map(|key| ControlEndpoint {
            url: self.launch.identity.control_url(key),
            agent_key: key.to_owned(),
        });
        self.with_services(|services| {
            services.cloud().spawner().attach(control);
            Ok(())
        })
    }

    fn cloud_base_url(&self) -> Result<String, String> {
        self.with_services(|services| Ok(services.cloud().base_url().to_owned()))
    }
}

fn registry(services: &super::ide::LaneServices) -> Result<&super::services::registry::Registry, String> {
    services
        .registry()
        .ok_or_else(|| "the lane runs no fake ACP registry, because its lane document names no registry agent".to_owned())
}

impl Probes for DaemonLane<'_, '_> {
    fn intrusion(&self) -> Option<String> {
        let intruder = self
            .bridge
            .post(&request("ModalIntruder", json!({ "graceMs": INTRUDER_GRACE_MS })), PROBE_TIMEOUT)
            .ok()?;
        intruder_text(&intruder)
    }

    fn dismiss_intrusion(&self) -> Option<String> {
        let dismissed = match self.bridge.post(
            &request("DismissIntruder", json!({ "graceMs": INTRUDER_GRACE_MS })),
            DISMISS_TIMEOUT,
        ) {
            Ok(Value::Null) => return None,
            Ok(Value::String(text)) => text,
            Ok(other) => other.to_string(),
            Err(error) => format!("the dismissal request did not answer: {error}"),
        };
        log(&format!("the lane watchdog acted on a modal intruder: {dismissed}"));
        Some(dismissed)
    }

    fn diagnostic_dump(&self) -> Result<String, String> {
        watchdog::ide_dump(&self.bridge)
    }

    fn kill(&self) -> Result<(), String> {
        let Some((context, run_id, jcmd)) = self.target.current() else {
            return Err("no IDE run is held".to_owned());
        };
        supervisor::cancel(
            &LiveSystem,
            &CancelArgs {
                run: RunArgs {
                    root: RootArgs { root: context },
                    run_id,
                },
                grace_ms: 0,
                thread_dump: jcmd,
            },
        )
        .map(|_| ())
        .map_err(|refusal| refusal.message)
    }

    fn log_dir(&self) -> Option<PathBuf> {
        self.target
            .current()
            .map(|(context, run_id, _)| supervisor::thread_dump::directory(&context, &run_id))
    }
}

/// The verdict line of an intruder that `ModalIntruder` answered, or `None` for no intruder.
fn intruder_text(intruder: &Value) -> Option<String> {
    let fields = intruder.as_object()?;
    let text = |name: &str| {
        fields
            .get(name)
            .map_or_else(String::new, |value| value.as_str().map_or_else(|| value.to_string(), str::to_owned))
    };
    let title = text("title");
    Some(format!(
        "an unexpected modal dialog no scenario opened is showing: '{}' ({}), showing for {}ms, buttons {}, text: {}",
        if title.trim().is_empty() { "<untitled>" } else { title.as_str() },
        text("windowClass"),
        text("showingForMs"),
        text("buttons"),
        text("visibleText")
    ))
}

/// The watchdog record of the phase `phase` before the program runner owns the watchdog: the IDE part, or the end of a
/// run that failed as a whole.
fn watchdog_record(phase: &str, request: &RunRequest, next: Option<Duration>) -> RunEventKind {
    let millis = |seconds: u64| i64::try_from(seconds.saturating_mul(1000)).unwrap_or(i64::MAX);
    RunEventKind::WatchdogState(WatchdogState {
        timestamp: now_stamp(),
        phase: phase.to_owned(),
        active_execution_timeout_ms: millis(request.active_execution_timeout_sec),
        progress_gap_timeout_ms: millis(request.progress_gap_timeout_sec),
        phase_deadline: None,
        emergency_deadline: None,
        next_deadline_in_ms: next.map(|next| i64::try_from(next.as_millis()).unwrap_or(i64::MAX)),
        active_execution: None,
    })
}

/// Where the push stored the file of `sha256`.
pub(crate) fn stored_path(jars: &Path, sha256: &str) -> PathBuf {
    jars.join(sha256)
}
