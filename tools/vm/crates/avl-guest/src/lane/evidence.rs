//! The evidence that `lane-run` keeps of a run that is not green.
//!
//! The IDE logs, the slow-step dumps and the authored artifacts of a run stay below `$TEST_TMPDIR`, which Bazel deletes
//! after the test. So the end of a run that is not green, and the hard stop of the monitor, copy them into
//! `$TEST_UNDECLARED_OUTPUTS_DIR/lane`, which Bazel keeps:
//!
//! - `ide/<launchKey>/<launchName>/`: the log directory of each affected IDE launch. It holds each `idea.log`, the
//!   thread dump of a kill, the other IDE logs and the heartbeat screenshots.
//! - `ide/<launchKey>/<launchName>/run/`: `run.log`, the standard output and error of the IDE process, and
//!   `supervisor.log` of its run. An IDE that dies before its logger starts leaves its reason only there.
//! - `iterations/<iterationId>/slow-steps/` and `iterations/<iterationId>/authored-artifacts/`.
//! - `evidence.txt`: when the copy ran, what it kept, and each file that it cut or left out, with the reason.
//!
//! `attachments/` beside them is no copy: the trace writes the attachments outside a recorded scenario there during the
//! run, whatever its verdict.
//!
//! A launch is affected when it ran during a scenario that did not pass, at a watchdog expiry, at the failure of a class
//! or of the run, or at the hard stop. A run that is not green and has no affected launch keeps every launch. A green
//! run keeps nothing. The lane daemon of a worker never copies, because the controller pulls its iterations.
//!
//! The copy is bounded by [`Limits`]. A file above the cap per file keeps its tail when it is a log or a text, because
//! the end of a run is there. Any other file above the cap is left out. A file that does not fit in what is left of the
//! total cap is left out. The files go in the order of their [`Worth`], and the newest launch comes first in each kind.

use std::collections::BTreeSet;
use std::fmt::Write as _;
use std::fs::{self, File};
use std::io::{self, Read, Seek, SeekFrom};
use std::path::{Path, PathBuf};

use avl_wire::daemon::{RunEvent, RunEventKind};
use avl_wire::ide::ide_run_id;
use walkdir::WalkDir;

use super::program::ITERATION_ARTIFACTS_DIR;
use super::stream::CaseStatus;
use super::watchdog::{SCREENSHOTS_DIR, SLOW_STEPS_DIR};
use crate::supervisor::{self, thread_dump};

#[cfg(test)]
mod tests;

/// The directory of the evidence in the outputs directory of a Bazel test.
pub(crate) const DIR: &str = "lane";
/// The directory of the output of the IDE process of a launch, in the directory of the launch.
const RUN_DIR: &str = "run";
/// The log of the IDE that a launch writes into its log directory.
const IDEA_LOG_FILE: &str = "idea.log";
/// The file that says what the copy kept and what it cut, in [`DIR`].
pub(crate) const MANIFEST_FILE: &str = "evidence.txt";
/// The directory of the trace attachments that came outside a recorded scenario, in [`DIR`].
pub(crate) const ATTACHMENTS_DIR: &str = "attachments";

/// The caps of one copy, in bytes.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct Limits {
    /// The cap per file.
    pub file: u64,
    /// The cap of all files.
    pub total: u64,
}

impl Limits {
    /// The caps of a lane run: 32 MiB per file and 256 MiB in all. A lane IDE writes some MiB of `idea.log` in a run of
    /// 17 minutes, and a heartbeat screenshot is some hundred KiB.
    pub(crate) const LANE: Self = Self {
        file: 32 << 20,
        total: 256 << 20,
    };
}

/// When the copy runs.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Moment {
    /// The run thread returned, and the run is not green.
    RunEnd,
    /// The monitor ended a run whose thread did not return.
    HardStop,
}

impl Moment {
    const fn describe(self) -> &'static str {
        match self {
            Self::RunEnd => "the end of a run that is not green",
            Self::HardStop => "the hard stop of the monitor",
        }
    }
}

/// Where the evidence of a run comes from and where it goes.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Evidence {
    /// The parent of the directories of the iterations, `<state>/iterations`.
    pub iterations: PathBuf,
    /// The parent of the contexts of the lane IDE. `<launchKey>/run-ide-<launchName>/` there is the run of one launch.
    pub ide_root: PathBuf,
    /// The directory of the copy, `<outputs>/lane`.
    pub destination: PathBuf,
}

impl Evidence {
    /// The evidence of a run whose state directory is `state_dir` and whose IDE contexts are below `ide_root`, for the
    /// outputs directory `outputs` of a Bazel test.
    pub(crate) fn new(state_dir: &Path, ide_root: &Path, outputs: &Path) -> Self {
        Self {
            iterations: state_dir.join("iterations"),
            ide_root: ide_root.to_path_buf(),
            destination: outputs.join(DIR),
        }
    }
}

/// The directory of the trace attachments outside a recorded scenario, for the outputs directory `outputs` of a Bazel
/// test. The trace writes them during the run, whatever its verdict.
pub(crate) fn attachments_dir(outputs: &Path) -> PathBuf {
    outputs.join(DIR).join(ATTACHMENTS_DIR)
}

/// One IDE launch of a run, as its `ideLaunched` record names it.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Launch {
    pub key: String,
    pub name: String,
    pub log_dir: PathBuf,
}

/// What one copy did.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct Kept {
    pub files: usize,
    pub bytes: u64,
    /// Each file that the copy cut or left out, with the reason.
    pub cut: Vec<String>,
    /// Why the copy could not write [`MANIFEST_FILE`], which then names nothing.
    pub manifest_error: Option<String>,
}

impl Kept {
    /// The line that says what the copy into `destination` kept. Without a manifest, the line names the reason instead.
    pub(crate) fn describe(&self, destination: &Path) -> String {
        let names = self.manifest_error.as_ref().map_or_else(
            || format!("which {MANIFEST_FILE} names"),
            |error| format!("and {MANIFEST_FILE} cannot be written: {error}"),
        );
        format!(
            "kept {} evidence file(s) of {} bytes in {}, and cut or left out {}, {names}",
            self.files,
            self.bytes,
            destination.display(),
            self.cut.len()
        )
    }
}

/// The launches of `events` in launch order, and the ones that a failure affected. An adopted IDE names its launch
/// again, which stays one launch. At the hard stop, each launch that runs and each launch of the open scenario is
/// affected too.
pub(crate) fn launches(events: &[RunEvent], hard_stop: bool) -> (Vec<Launch>, Vec<Launch>) {
    let mut launches: Vec<Launch> = Vec::new();
    let mut running: Vec<(i64, usize)> = Vec::new();
    let mut scenario: Option<BTreeSet<usize>> = None;
    let mut affected = BTreeSet::new();
    for event in events {
        match &event.kind {
            RunEventKind::IdeLaunched(launched) => {
                let log_dir = PathBuf::from(&launched.log_dir);
                let index = launches.iter().position(|launch| launch.log_dir == log_dir).unwrap_or_else(|| {
                    launches.push(Launch {
                        key: launched.launch_key.clone(),
                        name: launched.launch_name.clone(),
                        log_dir,
                    });
                    launches.len() - 1
                });
                running.retain(|(pid, _)| *pid != launched.pid);
                running.push((launched.pid, index));
                if let Some(scenario) = &mut scenario {
                    scenario.insert(index);
                }
            }
            RunEventKind::IdeExited(exited) => running.retain(|(pid, _)| *pid != exited.pid),
            RunEventKind::TestStarted(_) => scenario = Some(running.iter().map(|(_, index)| *index).collect()),
            RunEventKind::TestFinished(finished) => {
                if finished.status != CaseStatus::Successful.wire() {
                    mark_failure(&mut affected, &running, scenario.as_ref(), launches.len());
                }
                scenario = None;
            }
            RunEventKind::WatchdogExpired(_) | RunEventKind::ContainerFailed(_) | RunEventKind::RunFailed(_) => {
                mark_failure(&mut affected, &running, scenario.as_ref(), launches.len());
            }
            _ => {}
        }
    }
    if hard_stop {
        mark_failure(&mut affected, &running, scenario.as_ref(), launches.len());
    }
    let affected = affected.iter().map(|index| launches[*index].clone()).collect();
    (launches, affected)
}

/// Marks the launches of a failure as affected: each launch that runs, and each launch of the open `scenario`. A failure
/// with neither marks the newest of the `count` launches, because an IDE that died before the failure ran it.
fn mark_failure(affected: &mut BTreeSet<usize>, running: &[(i64, usize)], scenario: Option<&BTreeSet<usize>>, count: usize) {
    let mut marked: BTreeSet<usize> = running.iter().map(|(_, index)| *index).collect();
    marked.extend(scenario.into_iter().flatten());
    if marked.is_empty() && count > 0 {
        marked.insert(count - 1);
    }
    affected.extend(marked);
}

/// The kind of a file of the evidence, in the order of its worth: the copy takes the files of a kind before the files of
/// the next kind.
#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord)]
pub(crate) enum Worth {
    /// The thread dump that a kill wrote, `threadDump-before-kill-*`.
    KillDump,
    /// `idea.log`, which holds the end of the run.
    IdeaLog,
    /// The standard output and error of the IDE process, and the log of its supervisor.
    ProcessOutput,
    /// `idea.<n>.log`, which the IDE rotated out of `idea.log`. A lower `n` is newer.
    RotatedLog(u32),
    /// A file of `slow-steps/`.
    SlowStep,
    /// Any other file of the log directory of a launch.
    OtherLog,
    /// A heartbeat screenshot. The newest set comes first.
    Screenshot,
    /// A file of `authored-artifacts/`.
    AuthoredArtifact,
}

/// One file that the copy may take, with its worth.
struct Candidate {
    worth: Worth,
    source: PathBuf,
    /// The path below the destination.
    target: PathBuf,
}

/// Copies the evidence of the run of `events` into the destination of `evidence`, within `limits`, and writes the
/// manifest. A file that cannot be read or written is a line of the manifest, because evidence never fails a run.
pub(crate) fn keep(evidence: &Evidence, events: &[RunEvent], moment: Moment, limits: Limits) -> Kept {
    let (all, affected) = launches(events, moment == Moment::HardStop);
    let chosen = if affected.is_empty() { all } else { affected };
    let mut candidates = Vec::new();
    let mut kept = Kept::default();
    for launch in chosen.iter().rev() {
        let target = Path::new("ide").join(&launch.key).join(&launch.name);
        // The output of the IDE process still counts then: an IDE that died before its logger leaves no log directory.
        if !launch.log_dir.is_dir() {
            kept.cut.push(format!(
                "{}: the log directory {} is gone",
                target.display(),
                launch.log_dir.display()
            ));
        }
        let mut screenshots = Vec::new();
        for file in files_below(&launch.log_dir) {
            let candidate = Candidate {
                worth: worth_of_log(&file),
                source: launch.log_dir.join(&file),
                target: target.join(&file),
            };
            if candidate.worth == Worth::Screenshot {
                screenshots.push(candidate);
            } else {
                candidates.push(candidate);
            }
        }
        // A heartbeat set is `<NNN>_heartbeat`, so the newest set is the last by name.
        candidates.extend(screenshots.into_iter().rev());
        let context = evidence.ide_root.join(&launch.key);
        let run_logs = ide_run_id(&launch.name).map(|run_id| supervisor::run_logs(&context, &run_id));
        for source in run_logs.into_iter().flatten().filter(|source| source.is_file()) {
            if let Some(name) = source.file_name() {
                candidates.push(Candidate {
                    worth: Worth::ProcessOutput,
                    target: target.join(RUN_DIR).join(name),
                    source,
                });
            }
        }
    }
    let mut iterations: Vec<PathBuf> = fs::read_dir(&evidence.iterations)
        .into_iter()
        .flatten()
        .filter_map(Result::ok)
        .map(|entry| entry.path())
        .filter(|path| path.is_dir())
        .collect();
    iterations.sort();
    for iteration in iterations.iter().rev() {
        let Some(id) = iteration.file_name() else { continue };
        for (directory, worth) in [
            (SLOW_STEPS_DIR, Worth::SlowStep),
            (ITERATION_ARTIFACTS_DIR, Worth::AuthoredArtifact),
        ] {
            for file in files_below(&iteration.join(directory)) {
                candidates.push(Candidate {
                    worth,
                    source: iteration.join(directory).join(&file),
                    target: Path::new("iterations").join(id).join(directory).join(&file),
                });
            }
        }
    }
    // A stable sort, so the newest launch stays first within a kind.
    candidates.sort_by_key(|candidate| candidate.worth);

    for candidate in candidates {
        let shown = candidate.target.display().to_string();
        let Ok(size) = fs::metadata(&candidate.source).map(|metadata| metadata.len()) else {
            kept.cut.push(format!("{shown}: it went away before the copy"));
            continue;
        };
        let (start, length) = if size <= limits.file {
            (0, size)
        } else if is_text(&candidate.source) {
            (size - limits.file, limits.file)
        } else {
            kept.cut.push(format!(
                "{shown}: left out, {size} bytes are above the cap of {} bytes per file",
                limits.file
            ));
            continue;
        };
        if kept.bytes + length > limits.total {
            kept.cut.push(format!(
                "{shown}: left out, {length} bytes do not fit in the cap of {} bytes in all",
                limits.total
            ));
            continue;
        }
        match copy_tail(&candidate.source, &evidence.destination.join(&candidate.target), start, length) {
            Ok(copied) => {
                kept.files += 1;
                kept.bytes += copied;
                if start > 0 {
                    kept.cut.push(format!(
                        "{shown}: kept the last {copied} of {size} bytes, because the cap per file is {} bytes",
                        limits.file
                    ));
                }
            }
            Err(error) => kept
                .cut
                .push(format!("{shown}: cannot copy {}: {error}", candidate.source.display())),
        }
    }
    let manifest_file = evidence.destination.join(MANIFEST_FILE);
    if let Err(error) =
        fs::create_dir_all(&evidence.destination).and_then(|()| fs::write(&manifest_file, manifest(&kept, &chosen, moment, limits)))
    {
        kept.manifest_error = Some(format!("{}: {error}", manifest_file.display()));
    }
    kept
}

/// The text of [`MANIFEST_FILE`].
fn manifest(kept: &Kept, launches: &[Launch], moment: Moment, limits: Limits) -> String {
    let mut text = format!(
        "lane-run kept this evidence at {}: {} file(s), {} bytes. The caps are {} bytes per file and {} bytes in all.\n",
        moment.describe(),
        kept.files,
        kept.bytes,
        limits.file,
        limits.total
    );
    for launch in launches {
        let _ = writeln!(text, "launch {}/{}: {}", launch.key, launch.name, launch.log_dir.display());
    }
    for cut in &kept.cut {
        let _ = writeln!(text, "cut {cut}");
    }
    text
}

/// The worth of `file`, a path below the log directory of an IDE launch. Only a file at the top of the directory is a
/// kill dump, `idea.log` or a rotated `idea.<n>.log`.
fn worth_of_log(file: &Path) -> Worth {
    if file.starts_with(SCREENSHOTS_DIR) {
        return Worth::Screenshot;
    }
    let top = file.parent().is_none_or(|parent| parent.as_os_str().is_empty());
    let name = file.file_name().and_then(|name| name.to_str()).unwrap_or_default();
    if !top {
        return Worth::OtherLog;
    }
    if name.starts_with(thread_dump::FILE_PREFIX) {
        return Worth::KillDump;
    }
    if name == IDEA_LOG_FILE {
        return Worth::IdeaLog;
    }
    name.strip_prefix("idea.")
        .and_then(|rest| rest.strip_suffix(".log"))
        .and_then(|number| number.parse().ok())
        .map_or(Worth::OtherLog, Worth::RotatedLog)
}

/// Whether the tail of `file` is worth more than nothing: a log or a text.
fn is_text(file: &Path) -> bool {
    file.extension()
        .and_then(|extension| extension.to_str())
        .is_some_and(|extension| extension == "log" || extension == "txt")
}

/// The regular files below `directory`, relative to it and sorted. A link is left out, and a directory that is missing
/// has no file.
fn files_below(directory: &Path) -> Vec<PathBuf> {
    WalkDir::new(directory)
        .follow_links(false)
        .sort_by_file_name()
        .into_iter()
        .filter_map(Result::ok)
        .filter(|entry| entry.file_type().is_file())
        .filter_map(|entry| entry.path().strip_prefix(directory).ok().map(Path::to_path_buf))
        .collect()
}

/// Copies `length` bytes of `source` from `start` into `target`, and answers how many bytes it copied. A file that
/// grows during the copy gives no more than `length`.
fn copy_tail(source: &Path, target: &Path, start: u64, length: u64) -> io::Result<u64> {
    if let Some(parent) = target.parent() {
        fs::create_dir_all(parent)?;
    }
    let mut input = File::open(source)?;
    input.seek(SeekFrom::Start(start))?;
    io::copy(&mut input.take(length), &mut File::create(target)?)
}
