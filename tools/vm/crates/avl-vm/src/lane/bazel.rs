//! The host's Bazel: the two questions this controller asks it, and the one build it runs.

use std::collections::HashMap;
use std::fs::File;
use std::io::{Read, Seek, SeekFrom};
use std::path::{Path, PathBuf};
use std::sync::{Arc, LazyLock};
use std::time::Duration;

use crate::worker::worker::GuestBootBuilder;
use async_trait::async_trait;
use avl_base::RefusalExt;
use avl_base::fs::create_private_dir;
use avl_base::{Config, Exit, GuestArch, GuestOs, Refusal, Reporter};
use avl_host_sys::guest::{BazelHost, agent_label};
use avl_host_sys::paths::lies_below;
use avl_host_sys::runfiles::HostRunfiles;
use avl_host_sys::{Ctx, ProcError, Runner, SpawnOptions};
use avl_wire::progress::{BuildSummary, Event, Phase};
use bt_core::{Platform, bazel_command};
use regex::Regex;
use tokio::sync::{Mutex, OnceCell};

/// How much of a failed build's log travels in the refusal.
///
/// Bounded because the refusal ends up inside a JSON envelope an agent reads, and a lane build's whole log is not a
/// message - the log itself stays on disk and the refusal names it.
const BUILD_LOG_TAIL_LINES: usize = 30;

/// The timeout of the host build of a lane. The build compiles the IDE when the remote cache misses, and Bazel waits
/// for the lock of its server while another command of the same output base runs, so the bound is far above both.
const BAZEL_BUILD_TIMEOUT: Duration = Duration::from_hours(4);

/// The timeout of a host Bazel query. A query analyzes the lane's targets in seconds, but Bazel waits for the lock of
/// its server while another command of the same output base runs.
const BAZEL_QUERY_TIMEOUT: Duration = Duration::from_hours(2);

/// How often a build in flight reads its log for the last progress line.
const BUILD_LOG_FOLLOW_EVERY: Duration = Duration::from_secs(2);

/// How much of a log's end [`log_end`] reads. Bazel's summary is its last four lines, and one progress line is
/// shorter than this.
const LOG_END_WINDOW: u64 = 4096;

/// The guest's `.bazelrc` config.
///
/// A config, not the build options themselves: `community/tools/vm.cmd` and `trace.cmd` pass the same config,
/// so their `bazel run` and a lane build share one analysis key. [`host_bazel_argv`] says why that key matters. The
/// Linux config adds `--define=air_lane_guest_os=linux`; see `plugins/air/tests/integration/ui_lane_ide.bzl`.
const fn guest_bazel_config(guest_os: GuestOs) -> &'static str {
    match guest_os {
        GuestOs::Macos => "--config=air-lane-macos",
        GuestOs::Linux => "--config=air-lane-linux",
    }
}

/// Every Bazel invocation the controller makes, so none of them can drift from the others.
///
/// The guest's `.bazelrc` config (`air-lane-macos` or `air-lane-linux`) holds *build* options, which means Bazel
/// keys its analysis cache on them and a `cquery` that omits what the preceding `build` passed discards the analysis
/// of every configured target - on every daemon start, on both guests. `--build_runfile_links` is in both configs
/// because `community/common.bazelrc` turns it off for `build`, and `cquery` and `info` inherit that line: the
/// descriptor names its inputs by logical runfiles path, so the tree it names has to exist.
///
/// The same key reaches beyond this process. `community/tools/vm.cmd` and `trace.cmd` build their binaries with
/// `bazel run --config=air-lane-linux`, the default guest's config. Without it, every wrapper call discarded the
/// analysis the last lane build made, so a `trace.cmd` call between `daemon warm` and `run` threw the warm-up away.
/// A macOS guest still differs from the wrappers, and a lane build for it pays one analysis.
///
/// The other option is not a build option, and is here for the opposite reason: it stops this controller's builds
/// from touching the checkout's convenience symlinks at all. [`host_build_targets`] puts the guest agent - built for
/// the guest's platform - beside the lane's own label, so one invocation's top-level targets span two
/// configurations, and Bazel's answer to that is to *clear* `out/bazel-bin` and `out/bazel-testlogs`. Building the
/// agent in an invocation of its own instead repoints `out/bazel-bin` at the guest's configuration, which is worse:
/// a plausible-looking symlink into the wrong configuration, in a working copy several sessions share. A lane build
/// is not this checkout's main build and has no business doing either. It is not part of the analysis key, so it
/// is carried on every command at no cost.
pub(crate) fn host_bazel_argv(settings: &Config, command: &str, args: &[String]) -> Result<Vec<String>, Refusal> {
    let repo = settings.host_repo()?;
    let line: Vec<String> = [
        command,
        "--color=no",
        "--curses=no",
        guest_bazel_config(settings.guest_os),
        // A lane build must neither clear nor retarget this checkout's `out/bazel-bin`; see above.
        "--experimental_convenience_symlinks=ignore",
    ]
    .into_iter()
    .map(str::to_owned)
    .chain(args.iter().cloned())
    .collect();
    Ok(bazel_command(repo, Platform::current(), &line)
        .into_iter()
        .map(|word| word.to_string_lossy().into_owned())
        .collect())
}

/// Everything one lane build produces: the label the caller asked for, then [`guest_boot_targets`].
///
/// The agent is built here and nowhere else, and that is the whole answer to a stale agent driving a lane. The
/// guest-agent resolution deliberately never builds - it runs under the worker's lease-operation lock, where a build
/// would spend minutes - so it can only refuse a *missing* binary; a stale one it installs silently, and the receipt
/// cannot see that either, because a source signature answers "the host still holds those bytes" and never "those
/// bytes were built from this controller's sources". Measured on `air-linux-1` on 2026-08-25: a lane installed an
/// agent binary built three days earlier from this same checkout, and the run died in `launch-prep` with the exit
/// 64 of a verb that agent did not have.
///
/// The lane build is the right owner of that cost. It already runs on the host, before any worker is touched and
/// outside every lease lock, and it already knows the guest - which is what selects the platform-specific label. So
/// freshness holds by construction for `run`, `shard`, `flake` and `daemon start`, the four paths that reach a
/// worker, and all four reach it through [`Bazel::build`].
///
/// Lease-only operations - `lease acquire`, `lease release`, `status`, `exec`, `pull` - build nothing and still
/// install, which is deliberate: they judge no test, and a build inside them would put minutes under a held lock on
/// the path that *frees* a worker.
pub(crate) fn host_build_targets(guest_os: GuestOs, guest_arch: GuestArch, label: &str) -> Vec<String> {
    std::iter::once(label.to_owned())
        .chain(guest_boot_targets(guest_os, guest_arch))
        .collect()
}

/// What a *boot* installs into a guest: the agent for the guest's OS and architecture. The lane label is not here,
/// because a boot judges no test.
///
/// A boot runs verbs that only a fresh agent has. `pool start` and `pool recycle` build no lane, so until 2026-08-27
/// they installed the agent that the last build left in the output base. On that day a cold clone received an agent
/// built the day before and answered a boot verb with `exited with 64: Usage:`. [`host_build_targets`] is this set
/// plus the lane's own label, so a lane build and a boot build give a worker the same agent bytes.
pub(crate) fn guest_boot_targets(guest_os: GuestOs, guest_arch: GuestArch) -> Vec<String> {
    vec![agent_label(guest_os, guest_arch).to_owned()]
}

/// The host Bazel over one invocation's settings, with the two memos its answers share.
///
/// The settings are the key: one value per invocation, so the guest and the repository are fixed for its lifetime
/// and the descriptor memo needs only the label.
///
/// This is the implementation of [`BazelHost`]. That trait exists so a test of the guest-agent install never spawns
/// Bazel, and so the controller-wide argv above - the options a `cquery` must repeat or it throws away the analysis -
/// stays owned by whoever owns the lane's build.
///
/// Both memos are held across their Bazel call rather than only around the write, and that is the point: `shard` and
/// `flake` fan out over workers of one guest, and a memo published after the call would let N tasks each pay the
/// 7.3 s the pair costs. Two Bazel invocations of one server serialize behind its own lock anyway.
pub(crate) struct Bazel {
    settings: Arc<Config>,
    runner: Runner,
    reporter: Reporter,
    execution_root: OnceCell<PathBuf>,
    descriptors: Mutex<HashMap<String, PathBuf>>,
}

impl Bazel {
    pub(crate) fn new(settings: Arc<Config>, runner: Runner, reporter: Reporter) -> Arc<Self> {
        Arc::new(Self {
            settings,
            runner,
            reporter,
            execution_root: OnceCell::new(),
            descriptors: Mutex::new(HashMap::new()),
        })
    }

    /// Runs one host build over [`host_build_targets`], logging to the path the caller names.
    ///
    /// The log path is a parameter rather than a worker's directory because the build itself is not per-worker: the
    /// Bazel outputs it produces are identical for every worker of a guest, and a fan-out that shares one build has
    /// to be able to say where its one log goes.
    pub(crate) async fn build(&self, ctx: &Ctx, log_path: &Path, label: &str) -> Result<(), Refusal> {
        let targets = host_build_targets(self.settings.guest_os, self.settings.guest_arch, label);
        self.build_targets(ctx, log_path, label, &targets).await
    }

    /// Builds the labels the caller names and nothing else, logging to `log_path`.
    ///
    /// No guest target rides along, because no worker runs what this builds: `vm bench` builds a distribution for
    /// this host. The argv is still [`host_bazel_argv`], so the build shares the analysis key of the lane builds and
    /// leaves the checkout's convenience symlinks alone.
    pub(crate) async fn build_labels(&self, ctx: &Ctx, log_path: &Path, labels: &[String]) -> Result<(), Refusal> {
        self.build_targets(ctx, log_path, &labels.join(" "), labels).await
    }

    /// Builds only what a boot installs into a guest; what a `pool start` or a `pool recycle` runs before it touches
    /// a worker.
    ///
    /// The log is [`Config::guest_boot_build_log_path`], pool-wide for the same reason the target set is: these
    /// outputs are identical for every worker of a guest.
    pub(crate) async fn build_guest_boot(&self, ctx: &Ctx) -> Result<(), Refusal> {
        let targets = guest_boot_targets(self.settings.guest_os, self.settings.guest_arch);
        let log_path = self.settings.guest_boot_build_log_path();
        // Created here rather than assumed: a boot build runs on paths that have already prepared the runtime root,
        // and on one that has not - the lazy start of a slot nobody has used. Private like the runtime root itself,
        // since this may be what creates it and its missing ancestors.
        if let Some(parent) = log_path.parent() {
            create_private_dir(parent)?;
        }
        self.build_targets(ctx, &log_path, "", &targets).await
    }

    /// [`Bazel::build_guest_boot`] as the worker manager's boot seam.
    pub(crate) fn boot_builder(self: &Arc<Self>) -> GuestBootBuilder {
        let bazel = Arc::clone(self);
        Arc::new(move |ctx: Ctx| {
            let bazel = Arc::clone(&bazel);
            Box::pin(async move { bazel.build_guest_boot(&ctx).await })
        })
    }

    /// The one invocation both build entry points make, so a failed boot build and a failed lane build read the
    /// same way and carry the same log tail.
    ///
    /// `label` is what the refusal's details name, and it is empty for a boot build: there is no lane label in that
    /// set, and an invented one would be a detail a caller could branch on wrongly.
    async fn build_targets(&self, ctx: &Ctx, log_path: &Path, label: &str, targets: &[String]) -> Result<(), Refusal> {
        let phase = self.reporter.start_phase(
            Phase::HostBuild,
            &format!("{} (log: {})", targets.join(" "), log_path.display()),
            None,
        );
        let build = self.build_logged(ctx, log_path, label, targets);
        tokio::pin!(build);
        // Bazel under `--curses=no` writes a progress line every few seconds, so the last line of the log is what
        // the build is doing now; it becomes the phase's progress each time it changes.
        let mut follow = tokio::time::interval_at(tokio::time::Instant::now() + BUILD_LOG_FOLLOW_EVERY, BUILD_LOG_FOLLOW_EVERY);
        let mut last = String::new();
        let result = loop {
            tokio::select! {
                result = &mut build => break result,
                _ = follow.tick() => {
                    if let Some(line) = last_log_line(log_path) && line != last {
                        phase.progress(&line);
                        last = line;
                    }
                }
            }
        };
        // Before the phase ends, so every reader has the summary when it reads the phase's time.
        if let Some(summary) = read_build_summary(log_path) {
            self.reporter.publish(Event::BuildSummary(summary), None);
        }
        phase.finish(&result);
        result
    }

    async fn build_logged(&self, ctx: &Ctx, log_path: &Path, label: &str, targets: &[String]) -> Result<(), Refusal> {
        let repo = self.settings.host_repo()?;
        let argv = host_bazel_argv(&self.settings, "build", targets)?;
        // A previous run's log is this run's to replace, but two builders racing for the same log are not both to be
        // allowed to think they own it - so the removal is explicit and the create that follows is exclusive.
        match std::fs::remove_file(log_path) {
            Ok(()) => {}
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
            Err(error) => {
                return Err(Refusal::new(
                    "host_build_log_unavailable",
                    Exit::CANT_CREATE,
                    format!("cannot replace the build log at {}: {error}", log_path.display()),
                ));
            }
        }
        // Both streams into the log, because a build log with the diagnostics stripped out is the half nobody
        // needs, and the log is kept on failure, because a failure is exactly when someone reads it.
        let options = SpawnOptions {
            dir: Some(repo.to_path_buf()),
            merge_stderr_into_file: true,
            keep_destination_on_failure: true,
            ..SpawnOptions::within(BAZEL_BUILD_TIMEOUT)
        };
        let exit_code = match self.runner.checked_to_file(ctx, &argv, log_path, &options).await {
            // The log holds stderr too, so the runner answers nothing.
            Ok(_) => return Ok(()),
            Err(ProcError::Exited { exit_code, .. }) => exit_code,
            // The capture's own wording speaks about captures, and this destination is a log: "the build log is
            // unavailable" is actionable where "a capture destination exists" leaves a reader working out which.
            Err(ProcError::DestinationUnusable(refusal)) => {
                return Err(Refusal::new(
                    "host_build_log_unavailable",
                    Exit::CANT_CREATE,
                    format!("cannot open {} for this build's log: {}", log_path.display(), refusal.message),
                ));
            }
            // Something other than the build exiting non-zero, which must not be relabelled as a build failure.
            Err(other) => return Err(other.into()),
        };
        let said = log_tail(log_path).map(|tail| format!(":\n{tail}")).unwrap_or_default();
        Err(Refusal::new(
            "host_build_failed",
            Exit::BUILD_FAILED,
            format!("bazel build {} exited with {exit_code}{said}", targets.join(" ")),
        )
        .with_details(serde_json::json!({
            "exitCode": exit_code,
            "label": label,
            "targets": targets,
            "log": log_path.to_string_lossy(),
        })))
    }

    /// Where the lane's runtime descriptor landed, resolved once per process per label.
    ///
    /// The same two Bazel calls behind one answer as the guest agent's resolution - 7.3 s of a warm `daemon
    /// restart`. Where an output of a configured target lands is a function of the label and the configuration,
    /// not of the build's result, so a rerun in the same process cannot get a different answer.
    ///
    /// Still re-validated on every hit: a wiped output tree resolves again rather than answering a path that is no
    /// longer there. Both the descriptor and its runfiles tree are checked - a `bazel clean` between two iterations
    /// of one process is how a run stages a generation that is no longer on disk, and the two are removed
    /// independently.
    pub(crate) async fn runtime_descriptor(&self, ctx: &Ctx, label: &str) -> Result<PathBuf, Refusal> {
        let mut descriptors = self.descriptors.lock().await;
        if let Some(known) = descriptors.get(label)
            && staged(known)
        {
            return Ok(known.clone());
        }
        let stdout = self.query(ctx, "cquery", &["--output=files".to_owned(), label.to_owned()]).await?;
        let target_name = label.rsplit_once(':').map_or(label, |(_, name)| name);
        let suffix = format!("/{target_name}.runtime.json");
        let Some(candidate) = stdout.lines().map(str::trim).find(|line| line.ends_with(&suffix)) else {
            return Err(Refusal::new(
                "daemon_runtime_descriptor_missing",
                Exit::SOFTWARE,
                format!("bazel cquery did not report {target_name}.runtime.json for {label}"),
            ));
        };
        let candidate = Path::new(candidate);
        let descriptor = if candidate.is_absolute() {
            candidate.to_path_buf()
        } else {
            self.execution_root(ctx).await?.join(candidate)
        };
        if !staged(&descriptor) {
            return Err(Refusal::new(
                "daemon_runtime_descriptor_missing",
                Exit::SOFTWARE,
                format!("{} or its runfiles tree is missing after the build", descriptor.display()),
            ));
        }
        let share = self.settings.host_bazel_user_root()?;
        // Compared as `GuestPaths` compares: a Windows Bazel writes its output root in lower case.
        if !lies_below(share, &descriptor) {
            return Err(Refusal::new(
                "daemon_runtime_outside_share",
                Exit::DATA_ERR,
                format!(
                    "{} lies outside {}, so the guest cannot see it; set AIR_VM_BAZEL_USER_ROOT and run pool init \
                     again",
                    descriptor.display(),
                    share.display()
                ),
            ));
        }
        descriptors.insert(label.to_owned(), descriptor.clone());
        Ok(descriptor)
    }
}

#[async_trait]
impl BazelHost for Bazel {
    /// A host command, so it fails with what Bazel actually said: a `cquery` that refuses because a target does not
    /// exist names the target, and withholding that leaves the next step as "run it by hand and find out".
    async fn query(&self, ctx: &Ctx, command: &str, args: &[String]) -> Result<String, Refusal> {
        let argv = host_bazel_argv(&self.settings, command, args)?;
        let options = SpawnOptions {
            dir: Some(self.settings.host_repo()?.to_path_buf()),
            ..SpawnOptions::within(BAZEL_QUERY_TIMEOUT)
        };
        Ok(self.runner.checked(ctx, &argv, &options).await?.stdout)
    }

    /// Asked once per process. Every path `cquery --output=files` answers is relative to it, and it cannot move under
    /// a running controller: it is a function of the workspace and the output base, and a different output base
    /// means a different server than the one this process has been talking to.
    async fn execution_root(&self, ctx: &Ctx) -> Result<PathBuf, Refusal> {
        self.execution_root
            .get_or_try_init(|| async {
                let stdout = self.query(ctx, "info", &["execution_root".to_owned()]).await?;
                let answer = stdout.trim();
                if answer.is_empty() {
                    return Err(Refusal::new(
                        "bazel_execution_root_unknown",
                        Exit::SOFTWARE,
                        "bazel info execution_root answered nothing",
                    ));
                }
                Ok(PathBuf::from(answer))
            })
            .await
            .cloned()
    }
}

/// Whether a descriptor and its runfiles are both still there: the tree, or the MANIFEST of a host that builds no
/// tree.
fn staged(descriptor: &Path) -> bool {
    descriptor.exists() && HostRunfiles::present(descriptor)
}

/// The lines of the last [`LOG_END_WINDOW`] bytes of a log, or `None` when it cannot be read. The first line can
/// be a part of a line.
fn log_end(path: &Path) -> Option<Vec<String>> {
    let mut file = File::open(path).ok()?;
    let length = file.metadata().ok()?.len();
    file.seek(SeekFrom::Start(length.saturating_sub(LOG_END_WINDOW))).ok()?;
    let mut buffer = Vec::new();
    #[expect(
        clippy::verbose_file_reads,
        reason = "reads from the seeked offset: `fs::read` would read the whole log, not its last window"
    )]
    file.read_to_end(&mut buffer).ok()?;
    Some(String::from_utf8_lossy(&buffer).lines().map(str::to_owned).collect())
}

/// The last non-empty line of a log, or `None` when there is none yet.
fn last_log_line(path: &Path) -> Option<String> {
    log_end(path)?
        .iter()
        .rev()
        .map(|line| line.trim())
        .find(|line| !line.is_empty())
        .map(str::to_owned)
}

/// The last [`BUILD_LOG_TAIL_LINES`] lines of a log, or `None` when there is nothing to read.
fn log_tail(path: &Path) -> Option<String> {
    let content = std::fs::read(path).ok()?;
    let text = String::from_utf8_lossy(&content);
    let lines: Vec<&str> = text.split('\n').map(|line| line.trim_end_matches('\r')).collect();
    let tail = lines[lines.len().saturating_sub(BUILD_LOG_TAIL_LINES)..].join("\n");
    (!tail.is_empty()).then_some(tail)
}

/// Reads the summary lines of a Bazel build log: the processes, the actions that ran, the actions that a cache
/// answered, and the critical path. A log without the processes line, such as a build that Bazel never started, has
/// no summary. The two lines look like:
///
/// ```text
/// INFO: Elapsed time: 240.675s, Critical Path: 187.67s
/// INFO: 1025 processes: 6651 action cache hit, 1 disk cache hit, 32 remote cache hit, 22 internal, 936 worker.
/// ```
///
/// The processes line names a count for each way an action ended. A cache hit is cached. `internal` is Bazel's own
/// work, which did not run. Every other name is a strategy that executed the action: `worker`, `darwin-sandbox`,
/// `linux-sandbox`, `local`, `remote`. The action cache hits are not processes, and Bazel names them in the line all
/// the same.
pub(crate) fn read_build_summary(log_path: &Path) -> Option<BuildSummary> {
    static CRITICAL_PATH: LazyLock<Regex> = LazyLock::new(|| {
        // An invariant: a literal pattern, exercised by the tests.
        Regex::new(r"^INFO: Elapsed time: [0-9.]+s, Critical Path: ([0-9.]+)s").expect("a literal pattern compiles")
    });
    static PROCESSES: LazyLock<Regex> =
        LazyLock::new(|| Regex::new(r"^INFO: ([0-9,]+) process(?:es)?(?:: (.*?))?\.?$").expect("a literal pattern compiles"));
    static STRATEGY_COUNT: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"^([0-9,]+) (.+)$").expect("a literal pattern compiles"));
    let count_of = |text: &str| text.replace(',', "").parse::<u32>().unwrap_or(0);

    let mut summary = BuildSummary::default();
    let mut found = false;
    for line in log_end(log_path)? {
        let line = line.trim();
        if let Some(critical) = CRITICAL_PATH.captures(line) {
            if let Ok(seconds) = critical[1].parse::<f64>() {
                // Rounded rather than truncated: 187.67 s is 187 670 ms, which a truncating conversion of the
                // binary float reads as 187 669.
                #[expect(
                    clippy::cast_possible_truncation,
                    clippy::cast_sign_loss,
                    reason = "a rounded, non-negative seconds count from the log; `as` saturates"
                )]
                let millis = (seconds * 1000.0).round() as u64;
                summary.critical_path_ms = millis;
            }
            continue;
        }
        let Some(processes) = PROCESSES.captures(line) else {
            continue;
        };
        found = true;
        summary.processes = count_of(&processes[1]);
        summary.ran = 0;
        summary.cached = 0;
        let Some(strategies) = processes.get(2) else {
            continue;
        };
        for part in strategies.as_str().split(", ") {
            let Some(counted) = STRATEGY_COUNT.captures(part.trim()) else {
                continue;
            };
            let count = count_of(&counted[1]);
            let kind = &counted[2];
            if kind.ends_with("cache hit") {
                summary.cached += count;
            } else if kind != "internal" {
                summary.ran += count;
            }
        }
    }
    found.then_some(summary)
}

#[cfg(test)]
#[cfg(unix)]
mod tests;
