//! The build log channel of `lane-run` on TeamCity.
//!
//! A Bazel test reports through its JUnit XML, and TeamCity reads no service message from its standard output. So the
//! TeamCity step before the Bazel step creates a file, streams it into the build log for the rest of the build, and
//! names it in [`VARIABLE`]. `lane-run` appends service messages to that file, and the build log shows the run while it
//! goes on:
//!
//! - `progressMessage` at the start of each scenario, `<lane> <n>/<total>: <scenario>`, and at its end with its status
//!   and duration. A rerun of a scenario is `<lane> rerun: <scenario>`.
//! - `publishArtifacts` of the video and the failure picture of a failed or aborted scenario. A scan of the trace root
//!   after each scenario and at the summary of the run finds the bundle. The paths are the ones of
//!   [`avl_trace_tools::artifacts`], so the publish of `air-trace teamcity-publish` after the test replaces the same
//!   files.
//!
//! The channel carries no test event, because the results come from the JUnit XML. The channel is on only when
//! `TEAMCITY_VERSION` and [`VARIABLE`] are set, which the TeamCity build passes to the test. Only `lane-run` opens it,
//! so the lane daemon of a worker writes none.
//!
//! Each message is one line, and one write call writes it. When a write fails, the log gets one line, and the channel
//! stays off for the rest of the run. The channel never fails a run.

use std::collections::{HashMap, HashSet};
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::{Path, PathBuf};

use avl_junit::teamcity;
use avl_trace::bundle::{ROOT_DIR_NAME, VIDEO_FILE, bundle_path};
use avl_trace_tools::artifacts::{Stems, failed_target, failure_picture, testlogs_lane};
use avl_trace_tools::discover::{Bundle, Location, Options, Root, RootKind, Scanner, Status};
use avl_wire::daemon::{RunEventKind, decode_run_event};

use super::channel::log;
use super::stream::CaseStatus;

#[cfg(test)]
pub(crate) mod tests;

/// The variable that names the file of the channel, which the TeamCity build streams into its build log.
pub(crate) const VARIABLE: &str = "AIR_LANE_LIVE_LOG";

/// The channel of one run.
pub(crate) struct BuildLog {
    /// Where the messages go, until a write fails.
    out: Option<Box<dyn Write + Send>>,
    /// The file of [`VARIABLE`], which the log line of a failure names.
    path: PathBuf,
    /// The catalog lane of the run, such as `ui`, which each progress message names.
    lane: String,
    /// The bundles of the run, when a Bazel test names its outputs directory.
    traces: Option<Traces>,
    /// The end of the last write that is no whole line yet.
    pending: Vec<u8>,
    /// The scenarios of the plan.
    total: u32,
    /// The scenarios that started so far, without the reruns.
    started: u32,
    /// The ids of the scenarios that started, and whether the last start of each was a rerun.
    attempts: HashMap<String, bool>,
}

/// The bundles of a run and the artifacts of its failed scenarios.
struct Traces {
    /// The trace root, `<outputs>/air-traces`.
    root: PathBuf,
    /// The lane of the test as `air-trace teamcity-publish` names it: the directory of its `test.xml`.
    lane: String,
    /// Where a failure picture is copied under its artifact name: the directory of the channel's file.
    staging: PathBuf,
    scanner: Scanner,
    /// The bundles that took a stem, by their ids.
    seen: HashSet<String>,
    stems: Stems,
}

impl BuildLog {
    /// The channel of a run of `lane` on a TeamCity build that names its file, or `None`. `outputs` is the outputs
    /// directory of the Bazel test. Without it the channel reports progress and publishes no artifact.
    pub(crate) fn open(variable: &dyn Fn(&str) -> Option<String>, lane: &str, outputs: Option<&Path>) -> Option<Self> {
        if !teamcity::is_team_city(variable) {
            return None;
        }
        let path = PathBuf::from(variable(VARIABLE).filter(|value| !value.trim().is_empty())?);
        let file = match OpenOptions::new().append(true).create(true).open(&path) {
            Ok(file) => file,
            Err(error) => {
                log(&format!("the build log channel {} is off: {error}", path.display()));
                return None;
            }
        };
        let staging = path.parent().map_or_else(PathBuf::new, Path::to_path_buf);
        Some(Self::to(Box::new(file), path, lane, outputs, staging))
    }

    /// The channel into `out`, which [`BuildLog::open`] names `path`. The failure pictures are copied below `staging`.
    pub(crate) fn to(out: Box<dyn Write + Send>, path: PathBuf, lane: &str, outputs: Option<&Path>, staging: PathBuf) -> Self {
        let traces = outputs.map(|outputs| Traces {
            root: outputs.join(ROOT_DIR_NAME),
            lane: outputs.parent().map_or_else(|| testlogs_lane(Path::new("")), testlogs_lane),
            staging,
            scanner: Scanner::new(Options::default()),
            seen: HashSet::new(),
            stems: Stems::default(),
        });
        Self {
            out: Some(out),
            path,
            lane: lane.to_owned(),
            traces,
            pending: Vec::new(),
            total: 0,
            started: 0,
            attempts: HashMap::new(),
        }
    }

    /// Reads `bytes` of the run stream, and writes the messages of each whole record in it.
    pub(crate) fn observe(&mut self, bytes: &[u8]) {
        if self.out.is_none() {
            return;
        }
        self.pending.extend_from_slice(bytes);
        while let Some(end) = self.pending.iter().position(|byte| *byte == b'\n') {
            let line: Vec<u8> = self.pending.drain(..=end).collect();
            if let Ok(event) = decode_run_event(&line[..end]) {
                self.record(&event.kind);
            }
        }
    }

    fn record(&mut self, kind: &RunEventKind) {
        match kind {
            RunEventKind::PlanStarted(plan) => self.total = plan.discovered_executions,
            RunEventKind::TestStarted(started) => {
                let rerun = self.attempts.contains_key(&started.execution.id);
                self.attempts.insert(started.execution.id.clone(), rerun);
                if !rerun {
                    self.started += 1;
                }
                let text = format!("{}: {}", self.position(rerun), started.execution.display_name);
                self.progress(&text);
            }
            RunEventKind::TestFinished(finished) => {
                let rerun = self.attempts.get(&finished.execution.id).copied().unwrap_or_default();
                let text = format!(
                    "{}: {} {} ({})",
                    self.position(rerun),
                    finished.execution.display_name,
                    status_word(&finished.status),
                    duration(finished.duration_ms)
                );
                self.progress(&text);
                self.publish_failed();
            }
            // The recorder can finish the bundle of the last scenario after its testFinished.
            RunEventKind::Summary(_) => self.publish_failed(),
            _ => {}
        }
    }

    /// Publishes the files of each failed bundle that is new since the last scan.
    fn publish_failed(&mut self) {
        let lines = self.traces.as_mut().map(Traces::failed_artifacts).unwrap_or_default();
        for line in lines {
            self.say(&line);
        }
    }

    /// `<lane> <n>/<total>` of the scenario that started last, or `<lane> rerun` of a rerun.
    fn position(&self, rerun: bool) -> String {
        if rerun {
            format!("{} rerun", self.lane)
        } else {
            format!("{} {}/{}", self.lane, self.started, self.total)
        }
    }

    fn progress(&mut self, text: &str) {
        self.say(&teamcity::message("progressMessage", &[], Some(text)));
    }

    /// Writes one message as one line in one write. A write that fails, or that writes a part of the line, turns the
    /// channel off. A part of a line gets its line end, so the next writer of the file starts a line of its own.
    fn say(&mut self, message: &str) {
        let Some(out) = self.out.as_mut() else { return };
        let line = format!("{message}\n");
        let problem = match out.write(line.as_bytes()) {
            Ok(written) if written == line.len() => return,
            Ok(written) => {
                let _ = out.write(b"\n");
                format!("it took {written} of the {} bytes of a message", line.len())
            }
            Err(error) => error.to_string(),
        };
        log(&format!("the build log channel {} is off: {problem}", self.path.display()));
        self.out = None;
    }
}

impl Traces {
    /// The `publishArtifacts` messages of each failed or aborted bundle that a scan finds for the first time. Each new
    /// finished bundle takes its stem in the order of `air-trace teamcity-publish`, so both name the same files.
    fn failed_artifacts(&mut self) -> Vec<String> {
        let snapshot = self.scanner.scan(&[Root::new(RootKind::Flag, &self.root)]);
        let mut found: Vec<&Bundle> = snapshot
            .bundles
            .iter()
            .map(AsRef::as_ref)
            .filter(|bundle| bundle.status != Status::Running && !self.seen.contains(&bundle.id))
            .collect();
        found.sort_by(|left, right| {
            (&left.run_id, &left.test_class, &left.scenario).cmp(&(&right.run_id, &right.test_class, &right.scenario))
        });
        let mut lines = Vec::new();
        for bundle in found {
            self.seen.insert(bundle.id.clone());
            let placed = bundle_path(&bundle.run_id, &bundle.test_class, &bundle.scenario);
            let (class_dir, scenario) = placed.rsplit_once('/').unwrap_or(("", &placed));
            let stem = self.stems.take(&self.lane, class_dir, scenario);
            let Location::Dir(dir) = &bundle.location else { continue };
            if !matches!(bundle.status, Status::Failed | Status::Aborted) {
                continue;
            }
            let target = failed_target(&self.lane, class_dir, &stem);
            let video = bundle.has_video.then(|| (VIDEO_FILE, VIDEO_FILE.to_owned()));
            let still = bundle.thumbnail.as_deref().map(|still| (still, failure_picture(still)));
            for (file, name) in video.into_iter().chain(still) {
                match self.stage(&dir.join(file), &target, &name) {
                    Ok(staged) => lines.push(artifact(&staged, &target)),
                    Err(problem) => log(&format!("the build log channel publishes no {name}: {problem}")),
                }
            }
        }
        lines
    }

    /// Copies the file `source` of a bundle to `name` in the staging directory of `target`. TeamCity reads the file of
    /// a message some seconds later, and the staged file outlives `test.outputs`, which Bazel can zip and remove
    /// after the test. An artifact also keeps the name of its file. A hard link saves the copy.
    fn stage(&self, source: &Path, target: &str, name: &str) -> Result<PathBuf, String> {
        let directory = self.staging.join(target);
        let file = directory.join(name);
        let failed = |error: std::io::Error| format!("{} to {}: {error}", source.display(), file.display());
        fs::create_dir_all(&directory).map_err(failed)?;
        let _ = fs::remove_file(&file);
        fs::hard_link(source, &file)
            .or_else(|_| fs::copy(source, &file).map(|_| ()))
            .map_err(failed)?;
        Ok(file)
    }
}

/// The `publishArtifacts` message of `file` into the artifact directory `target`.
fn artifact(file: &Path, target: &str) -> String {
    teamcity::message("publishArtifacts", &[], Some(&format!("{} => {target}", file.display())))
}

/// The word of the status `wire` of a case: `passed`, `failed` or `aborted`.
fn status_word(wire: &str) -> String {
    if wire == CaseStatus::Successful.wire() {
        "passed".to_owned()
    } else {
        wire.to_lowercase()
    }
}

/// `duration_ms` in seconds with one decimal, such as `12.3 s`.
fn duration(duration_ms: i64) -> String {
    format!("{}.{} s", duration_ms / 1000, (duration_ms % 1000).abs() / 100)
}
