//! The verb `lane-run`: one iteration of a lane from a runfiles tree, with no control channel.
//!
//! `lane-daemon` serves the channel in the run slot of a worker, and the controller pushes the lane inputs to it.
//! `lane-run` is the second front door of the same library, for a host with the runfiles of the lane rule: a Bazel test,
//! `bt --lane` and a TeamCity agent. It reads the runtime descriptor, stores the lane inputs and the context plugins of
//! the runfiles in the jar store by digest, and runs the iteration of [`super::iteration`] as a `/run` would. It writes
//! the run stream on standard output and into the run directory, and the JUnit XML of the stream into the run
//! directory and to the XML file of a Bazel test. It quits the IDE, and exits 0 when the run is green.
//!
//! With `--plan-only` it stops before the IDE: it stages the inputs and selects the documents as a run does, and it
//! writes the [`RunPlan`] on standard output. So a host proves a lane rule and its runfiles without a lane IDE.

use std::fs::{self, File};
use std::io::{self, Write};
use std::net::{IpAddr, Ipv4Addr};
use std::path::{Path, PathBuf};

use avl_wire::daemon::{IdePlugin, LaneInputs, RunEventKind, RunRequest, SessionLog, decode_run_event};
use avl_wire::runtime::{self, RuntimeFile};
use avl_wire::session_log;
use serde::Serialize;

use super::channel::{self, BOOT_CODE, log};
use super::inputs::{self, guest_platform};
use super::iteration;
use super::package::sha256_hex;
use crate::cli::{LaneDaemonArgs, LaneRunArgs};
use crate::reply::{AgentRefusal, AgentRefusalExt};

#[cfg(test)]
mod tests;

/// The launch digest of a run with no channel. No controller prepared it, so no other digest can be stale.
const LAUNCH_DIGEST: &str = "lane-run";
/// The port of the absent channel. A derived port of a lane service is never 0.
const NO_CHANNEL_PORT: u16 = 0;
/// The file of the run stream in the run directory.
pub(crate) const STREAM_FILE: &str = "run.ndjson";
/// The file of the JUnit XML in the run directory.
pub(crate) const XML_FILE: &str = "test.xml";
/// The variable that names the run directory, before `$TEST_TMPDIR/air-lane`.
pub(crate) const RUNTIME_ROOT_VARIABLE: &str = "AIR_LANE_RUNTIME_ROOT";
/// The variable of a Bazel `--test_filter`.
const TEST_FILTER_VARIABLE: &str = "TESTBRIDGE_TEST_ONLY";
/// The variable of the JUnit5 filters, `;`-separated, as the lane table spells them.
const JUNIT5_FILTERS_VARIABLE: &str = "JB_TEST_JUNIT5_FILTERS";
/// The variable of the shard count of a Bazel test.
const TOTAL_SHARDS_VARIABLE: &str = "TEST_TOTAL_SHARDS";
/// The refusal code of a run that Bazel splits into shards.
pub(crate) const SHARDED_CODE: &str = "lane_run_sharded";
/// The refusal code of a recorded session that the variable names and that cannot be packed.
pub(crate) const SESSION_LOG_CODE: &str = "lane_session_log";

/// Packs each recorded session that [`session_log::VARIABLE`] names into the jar store, as the controller pushes them
/// to a guest. The zip leaves the run directory once the store holds it.
pub(crate) fn session_logs(
    daemon: &channel::Daemon,
    root: &Path,
    variable: &dyn Fn(&str) -> Option<String>,
) -> Result<Vec<SessionLog>, AgentRefusal> {
    let Some(value) = variable(session_log::VARIABLE) else {
        return Ok(Vec::new());
    };
    let logs = session_log::named_logs(&value).map_err(|message| AgentRefusal::refused(SESSION_LOG_CODE, message))?;
    let directory = root.join("session-logs");
    fs::create_dir_all(&directory)
        .map_err(|error| AgentRefusal::failure(BOOT_CODE, format!("cannot create {}: {error}", directory.display())))?;
    logs.iter()
        .map(|log| {
            let zip = directory.join(format!("{}.zip", log.label));
            session_log::pack(log, &zip).map_err(|message| AgentRefusal::refused(SESSION_LOG_CODE, message))?;
            let sha256 = daemon.store_file(&zip);
            let _ = fs::remove_file(&zip);
            Ok(SessionLog {
                label: log.label.clone(),
                sha256: sha256?,
            })
        })
        .collect()
}

/// Runs one iteration of `args`, and answers the exit status: 0 for a green run, 1 for any other. `variable` reads the
/// environment.
pub(crate) fn run(args: &LaneRunArgs, variable: &dyn Fn(&str) -> Option<String>) -> Result<u8, AgentRefusal> {
    refuse_shards(variable)?;
    let root = runtime_root(args, variable)?;
    let runfiles_root = args
        .runfiles_root
        .clone()
        .unwrap_or_else(|| runtime::runfiles_root(&args.descriptor));
    let daemon_args = LaneDaemonArgs {
        state_dir: root.join("state"),
        port: NO_CHANNEL_PORT,
        bind: IpAddr::V4(Ipv4Addr::LOCALHOST),
        launch_digest: LAUNCH_DIGEST.to_owned(),
        descriptor: args.descriptor.clone(),
        runfiles_root: runfiles_root.clone(),
        ide_root: root.join("ide"),
        home: root.join("home"),
    };
    for directory in [&daemon_args.state_dir, &daemon_args.ide_root, &daemon_args.home] {
        fs::create_dir_all(directory)
            .map_err(|error| AgentRefusal::failure(BOOT_CODE, format!("cannot create {}: {error}", directory.display())))?;
    }
    let content = fs::read(&args.descriptor).map_err(|error| {
        AgentRefusal::failure(
            BOOT_CODE,
            format!("cannot read the runtime descriptor {}: {error}", args.descriptor.display()),
        )
    })?;
    let descriptor = runtime::parse_runtime_descriptor(&content, &args.descriptor, guest_platform())
        .map_err(|error| AgentRefusal::refused(error.code, error.message))?;
    let daemon = channel::boot(&daemon_args, inputs::from_descriptor(&descriptor, &daemon_args)?)?;
    let runfile = |file: &RuntimeFile| runtime::runtime_file_path(&runfiles_root, file);
    let lane_inputs = LaneInputs {
        fake_agent_jar: daemon.store_file(&runfile(&descriptor.ide.fake_agent_jar))?,
        lane_documents: daemon.store_file(&runfile(&descriptor.ide.lane_documents))?,
        suite_documents: daemon.store_file(&runfile(&descriptor.ide.suite_documents))?,
    };
    let ide_plugins = descriptor
        .ide
        .plugins
        .iter()
        .map(|plugin| {
            Ok(IdePlugin {
                destination: plugin.destination.clone(),
                sha256: daemon.store_file(&runfile(&plugin.file))?,
            })
        })
        .collect::<Result<Vec<_>, AgentRefusal>>()?;
    let fingerprint = runfile(&descriptor.dev_dist.fingerprint);
    let product_stamp = sha256_hex(
        &fs::read(&fingerprint)
            .map_err(|error| AgentRefusal::failure(BOOT_CODE, format!("cannot read {}: {error}", fingerprint.display())))?,
    );
    let request = RunRequest {
        selectors: selectors(args, variable),
        junit5_filters: junit5_filters(args, variable),
        product_stamp,
        launch_digest: LAUNCH_DIGEST.to_owned(),
        hot_jars: Vec::new(),
        active_execution_timeout_sec: args.active_execution_timeout_sec,
        progress_gap_timeout_sec: args.progress_gap_timeout_sec,
        ide_plugins,
        lane: Some(args.lane.clone()),
        lane_inputs: Some(lane_inputs),
        lane_classes: Vec::new(),
        session_logs: session_logs(&daemon, &root, variable)?,
    };
    if args.plan_only {
        let plan = plan(&daemon, &request)?;
        let mut out = io::stdout().lock();
        serde_json::to_writer_pretty(&mut out, &plan)
            .map_err(io::Error::from)
            .and_then(|()| writeln!(out))
            .map_err(|error| AgentRefusal::failure(BOOT_CODE, format!("cannot write the plan: {error}")))?;
        return Ok(0);
    }
    let stream_path = root.join(STREAM_FILE);
    let stream = File::create(&stream_path)
        .map_err(|error| AgentRefusal::failure(BOOT_CODE, format!("cannot create {}: {error}", stream_path.display())))?;
    let xml = iteration::run(&daemon, &request, &mut Tee(stream, io::stdout()));
    let written = write_xml(&root, &xml, variable);
    let finished = daemon.finish();
    if let Err(refusal) = &finished {
        log(&format!("the IDE did not quit: {}: {}", refusal.code, refusal.message));
    }
    let stream = fs::read(&stream_path)
        .map_err(|error| AgentRefusal::failure(BOOT_CODE, format!("cannot read {}: {error}", stream_path.display())))?;
    let green = is_green(&stream) && finished.is_ok();
    written?;
    Ok(u8::from(!green))
}

/// The refusal code of a plan whose documents do not select.
pub(crate) const PLAN_CODE: &str = "lane_run_plan";

/// What a run would run, as `--plan-only` writes it: the lane, its IDE, its inputs, and its suites in run order.
#[derive(Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct RunPlan {
    pub lane: String,
    /// The config key of the IDE of the lane, or `None` when every suite launches a bare IDE of its own.
    pub lane_key: Option<String>,
    pub product_stamp: String,
    /// The digests of the lane inputs in the jar store.
    pub lane_inputs: LaneInputs,
    pub context_plugin_files: usize,
    /// The suites of the shared IDE, then the suites of a bare IDE, as the program runner runs them.
    pub suites: Vec<PlannedSuite>,
}

/// One suite of a [`RunPlan`].
#[derive(Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct PlannedSuite {
    pub test_class_name: String,
    pub suite: String,
    pub scenarios: Vec<String>,
    /// The launch key of a suite of a bare IDE.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub bare_launch_key: Option<String>,
}

/// The plan of `request`: the selection of a run, and the key of the IDE that the run would launch.
pub(crate) fn plan(daemon: &channel::Daemon, request: &RunRequest) -> Result<RunPlan, AgentRefusal> {
    let refused = |message: String| AgentRefusal::refused(PLAN_CODE, message);
    let (Some(lane), Some(lane_inputs)) = (&request.lane, &request.lane_inputs) else {
        return Err(refused("the run request names no lane and no lane inputs".to_owned()));
    };
    let selection = iteration::selection_of(daemon, lane, lane_inputs, request).map_err(refused)?;
    let shared = selection.suites.iter().any(|suite| suite.program.launch.is_none());
    let lane_key = if shared {
        Some(iteration::lane_key(daemon, &selection, &request.product_stamp).map_err(refused)?)
    } else {
        None
    };
    let (bare, shared): (Vec<_>, Vec<_>) = selection.suites.iter().partition(|suite| suite.program.launch.is_some());
    let suites = shared
        .into_iter()
        .chain(bare)
        .map(|suite| PlannedSuite {
            test_class_name: suite.program.test_class_name.clone(),
            suite: suite.program.suite.clone(),
            scenarios: suite.program.profiles.iter().map(|profile| profile.name.clone()).collect(),
            bare_launch_key: suite.program.launch.as_ref().map(|launch| launch.key.clone()),
        })
        .collect();
    Ok(RunPlan {
        lane: lane.clone(),
        lane_key,
        product_stamp: request.product_stamp.clone(),
        lane_inputs: lane_inputs.clone(),
        context_plugin_files: request.ide_plugins.len(),
        suites,
    })
}

/// Writes `xml` into the run directory `root`, and to the file that `$JB_XML_OUTPUT_FILE` or `$XML_OUTPUT_FILE` names.
/// Bazel and `bt` count the cases of that file, so a write that fails fails the run.
pub(crate) fn write_xml(root: &Path, xml: &str, variable: &dyn Fn(&str) -> Option<String>) -> Result<(), AgentRefusal> {
    let mut files = vec![root.join(XML_FILE)];
    files.extend(avl_junit::destination(variable).map(PathBuf::from));
    for file in files {
        if let Some(parent) = file.parent() {
            fs::create_dir_all(parent)
                .map_err(|error| AgentRefusal::failure(BOOT_CODE, format!("cannot create {}: {error}", parent.display())))?;
        }
        fs::write(&file, xml)
            .map_err(|error| AgentRefusal::failure(BOOT_CODE, format!("cannot write the JUnit XML {}: {error}", file.display())))?;
    }
    Ok(())
}

/// Refuses a run that Bazel splits into more than one shard. A lane IDE takes the fixed bridge port, so the shards of one
/// lane cannot run beside each other, and one iteration runs the whole selection.
pub(crate) fn refuse_shards(variable: &dyn Fn(&str) -> Option<String>) -> Result<(), AgentRefusal> {
    match variable(TOTAL_SHARDS_VARIABLE).and_then(|total| total.trim().parse::<u32>().ok()) {
        Some(total) if total > 1 => Err(AgentRefusal::refused(
            SHARDED_CODE,
            format!(
                "Bazel splits the lane test into {total} shards, and lane-run runs one iteration of the lane on the \
                 fixed bridge port; run the lane test as one shard"
            ),
        )),
        _ => Ok(()),
    }
}

/// The run directory: `--runtime-root`, then `$AIR_LANE_RUNTIME_ROOT`, then `$TEST_TMPDIR/air-lane`.
pub(crate) fn runtime_root(args: &LaneRunArgs, variable: &dyn Fn(&str) -> Option<String>) -> Result<PathBuf, AgentRefusal> {
    if let Some(root) = &args.runtime_root {
        return Ok(root.clone());
    }
    let named = |name: &str| variable(name).filter(|value| !value.is_empty()).map(PathBuf::from);
    named(RUNTIME_ROOT_VARIABLE)
        .or_else(|| named("TEST_TMPDIR").map(|temporary| temporary.join("air-lane")))
        .filter(|root| root.is_absolute())
        .ok_or_else(|| {
            AgentRefusal::refused(
                "lane_run_root_missing",
                format!("lane-run needs an absolute --runtime-root, ${RUNTIME_ROOT_VARIABLE} or $TEST_TMPDIR"),
            )
        })
}

/// The selectors of `--selector`, and then those of a Bazel `--test_filter`.
pub(crate) fn selectors(args: &LaneRunArgs, variable: &dyn Fn(&str) -> Option<String>) -> Vec<String> {
    let filter = variable(TEST_FILTER_VARIABLE).unwrap_or_default();
    args.selectors
        .iter()
        .cloned()
        .chain(
            filter
                .split(',')
                .map(str::trim)
                .filter(|selector| !selector.is_empty())
                .map(str::to_owned),
        )
        .collect()
}

/// The filters of `--junit5-filter`, and then those of `$JB_TEST_JUNIT5_FILTERS`.
pub(crate) fn junit5_filters(args: &LaneRunArgs, variable: &dyn Fn(&str) -> Option<String>) -> Vec<String> {
    let listed = variable(JUNIT5_FILTERS_VARIABLE).unwrap_or_default();
    args.junit5_filters
        .iter()
        .cloned()
        .chain(
            listed
                .split(';')
                .map(str::trim)
                .filter(|filter| !filter.is_empty())
                .map(str::to_owned),
        )
        .collect()
}

/// Whether the run stream ends green: a summary with no failure, and no failed run. A stream without a summary is a run
/// that was cut off, and it is not green.
pub(crate) fn is_green(stream: &[u8]) -> bool {
    let mut summary = None;
    for line in stream.split(|byte| *byte == b'\n').filter(|line| !line.is_empty()) {
        match decode_run_event(line).map(|event| event.kind) {
            Ok(RunEventKind::Summary(found)) => summary = Some(found),
            Ok(RunEventKind::RunFailed(_)) | Err(_) => return false,
            Ok(_) => {}
        }
    }
    summary.is_some_and(|summary| !summary.has_failures)
}

/// Writes each record to the run file and to standard output. A failed write to standard output is not a failed run.
struct Tee<F, O>(F, O);

impl<F: Write, O: Write> Write for Tee<F, O> {
    fn write(&mut self, bytes: &[u8]) -> io::Result<usize> {
        self.0.write_all(bytes)?;
        let _ = self.1.write_all(bytes);
        Ok(bytes.len())
    }

    fn flush(&mut self) -> io::Result<()> {
        let _ = self.1.flush();
        self.0.flush()
    }
}
