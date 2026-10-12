//! The verbs: a `welcome`, an `open-project` or a `project` session from the build to the summary, `replay`, `gc`,
//! `trace`, `activities`, `classes`, `compare` and `ls`.

use std::path::{Path, PathBuf};
use std::time::{Duration, SystemTime};

use avl_base::fs::create_private_dir;
use avl_base::journal;
use avl_base::{Config, Environment, Exit, Outcome, Refusal, RefusalExt, Reporter};
use avl_host_sys::guest::ensure_host_paths;
use avl_host_sys::lock::{LockGuard, LockManager};
use avl_host_sys::{Ctx, Runner, SpawnOptions};
use avl_wire::progress::Event;
use jiff::tz::TimeZone;
use serde_json::json;

use super::activities;
use super::arm::Arm;
use super::binary::{self, BinaryAge};
use super::classes;
use super::compare::{self, Compared};
use super::digest;
use super::files;
use super::gc;
use super::launch::{Launcher, RunPlan};
use super::load;
use super::ls;
use super::options::{BenchVerb, LaunchArgs};
use super::record::{self, RunId, RunKind, RunRecord};
use super::resolve;
use super::session::{
    self, BENCH_DIR, GenerationRef, GitInfo, MARKDOWN_PROJECT, Project, ProjectSource, RESULT_FILE, SESSION_FILE, SUMMARY_FILE, Sandbox,
    SessionInfo, TEMPLATE_DIR,
};
use super::spans;
use super::stage::{self, Generation};
use super::summary::Summary;
use crate::lane::Bazel;

/// The directories below a run sandbox that a copy must not carry: the FUS logs of the run that wrote the source.
const STALE_DIRS: [&str; 1] = ["system/event-log-data/logs"];

/// Bounds each of the two host `git` calls of a session. A `git status` of a large checkout takes some seconds.
const GIT_INFO_TIMEOUT: Duration = Duration::from_secs(60);

/// What a bench verb needs of the controller: one invocation's collaborators, borrowed.
pub(crate) struct Bench<'a> {
    pub(crate) settings: &'a Config,
    pub(crate) runner: &'a Runner,
    pub(crate) reporter: &'a Reporter,
    pub(crate) bazel: &'a Bazel,
    pub(crate) locks: &'a LockManager,
    pub(crate) environment: &'a Environment,
}

/// One planned session.
struct Plan {
    command: &'static str,
    arms: Vec<Arm>,
    runs: u32,
    cold: bool,
    profile: bool,
    project: Option<PlannedProject>,
}

/// The project of a session.
enum PlannedProject {
    /// The project outside the sandbox that the running IDE opens in `open-project`.
    Open(PathBuf),
    /// The project of `project`, which the session writes into the template.
    Start(ProjectSource),
}

/// What every run of a session shares.
struct Running<'a> {
    session: &'a Path,
    info: &'a SessionInfo,
    plan: &'a Plan,
    generation: &'a Generation,
    launcher: &'a Launcher,
    /// The project of `project` in the template.
    project: Option<&'a Project>,
}

/// A refusal for a session that could not write its own files.
fn session_failed(message: impl Into<String>) -> Refusal {
    Refusal::new("bench_session_failed", Exit::FAILURE, message)
}

fn working_dir() -> Result<PathBuf, Refusal> {
    std::env::current_dir().map_err(|error| session_failed(format!("cannot read the working directory: {error}")))
}

impl Bench<'_> {
    /// Runs one verb.
    pub(crate) async fn command(&self, ctx: &Ctx, verb: BenchVerb) -> Result<Outcome, Refusal> {
        match verb {
            BenchVerb::Welcome(args) => {
                let plan = Plan {
                    command: "welcome",
                    arms: args.arm.arms(),
                    runs: args.runs,
                    cold: args.cold,
                    profile: args.profile,
                    project: None,
                };
                self.session(ctx, &plan, &args.launch).await
            }
            BenchVerb::OpenProject(args) => {
                let project = working_dir()?.join(&args.project);
                if !project.is_dir() {
                    return Err(Refusal::usage(format!("the project {} is not a directory", project.display())));
                }
                let plan = Plan {
                    command: "open-project",
                    arms: vec![Arm::OpenProject],
                    runs: args.runs,
                    cold: false,
                    profile: false,
                    project: Some(PlannedProject::Open(project)),
                };
                self.session(ctx, &plan, &args.launch).await
            }
            BenchVerb::Project(args) => {
                let plan = Plan {
                    command: "project",
                    arms: args.arms().map_err(Refusal::usage)?,
                    runs: args.runs,
                    cold: args.cold,
                    profile: false,
                    project: Some(PlannedProject::Start(project_source(&args.project, &working_dir()?)?)),
                };
                self.session(ctx, &plan, &args.launch).await
            }
            BenchVerb::Replay(args) => self.answer(&replay(&self.locate(&args.session)?, self.reporter)?),
            BenchVerb::Trace(args) => {
                let report = spans::report(&self.locate(&args.selection.session)?, &args)?;
                Outcome::new(&report, report.text())
            }
            BenchVerb::Activities(args) => {
                let report = activities::report(&self.locate(&args.selection.session)?, &args)?;
                Outcome::new(&report, report.text())
            }
            BenchVerb::Classes(args) => {
                let report = classes::report(&self.locate(&args.session)?, &args)?;
                Outcome::new(&report, report.text())
            }
            BenchVerb::Compare(args) => {
                let sessions = args
                    .sessions
                    .iter()
                    .map(|arg| Compared::read(self.locate(arg)?))
                    .collect::<Result<Vec<_>, Refusal>>()?;
                let report = compare::report(&sessions, args.expect_different_dist)?;
                Outcome::new(&report, report.text())
            }
            BenchVerb::Ls(args) => {
                let report = ls::report(&session::runs_root(&self.settings.runtime_root), args.limit);
                Outcome::new(&report, report.text(&TimeZone::system()))
            }
            BenchVerb::Gc(args) => {
                let _session = self.session_lock(ctx, "bench gc").await?;
                let report = gc::collect(&self.settings.runtime_root, args.keep, SystemTime::now())?;
                Outcome::new(&report, report.text())
            }
        }
    }

    /// Prepares the session, stages the generation, runs the prime runs and the measured runs, and writes the
    /// summary.
    async fn session(&self, ctx: &Ctx, plan: &Plan, args: &LaunchArgs) -> Result<Outcome, Refusal> {
        if !cfg!(target_os = "macos") {
            return Err(Refusal::new(
                "unsupported_os",
                Exit::USAGE,
                format!(
                    "a bench session runs on a macOS host only, and this host is {}",
                    std::env::consts::OS
                ),
            ));
        }
        let launcher_label = resolve::launcher_label(&args.target)?;
        ensure_host_paths(ctx, self.runner, self.settings).await?;
        let repo = self.settings.host_repo()?.to_path_buf();
        let binary_age = binary::age(self.environment, &repo);
        if let Some(age) = &binary_age {
            self.reporter.note(age.note(), None);
        }
        let warnings: Vec<String> = binary_age.as_ref().and_then(BinaryAge::warning).into_iter().collect();
        for warning in &warnings {
            self.reporter.note(format!("warning: {warning}"), None);
        }
        let license = repo.join(session::LICENSE_FILE);
        if !license.exists() {
            return Err(Refusal::new(
                "license_missing",
                Exit::USAGE,
                format!(
                    "no license at {}: an unlicensed IDE shows a dialog instead of the welcome screen; start the dev IDE once and register it",
                    license.display()
                ),
            ));
        }
        let host_load = load::read(ctx, self.runner).await;
        if host_load.is_none() {
            self.reporter
                .note("this host has no load average, so the session checks no load", None);
        }
        let max_load = load::check_max(host_load.as_ref(), args.max_load)?;
        if host_load.is_some() && max_load.is_none() {
            self.reporter.note("the CPU count is unknown; the session checks no load", None);
        }
        let bench_root = self.settings.runtime_root.join(BENCH_DIR);
        let actor = avl_base::actor_id(self.environment, "bench");
        let session = match &args.session {
            Some(dir) => working_dir()?.join(dir),
            None => journal::run_dir(&bench_root, &actor)?,
        };
        if std::fs::read_dir(&session).is_ok_and(|mut entries| entries.next().is_some()) {
            return Err(Refusal::new(
                "session_exists",
                Exit::USAGE,
                format!("the session directory {} is not empty", session.display()),
            ));
        }
        let _session_lock = self.session_lock(ctx, "bench session").await?;
        let _journal = journal::attach(&bench_root, self.reporter, &actor);
        std::fs::create_dir_all(&session).map_err(|error| session_failed(format!("cannot create {}: {error}", session.display())))?;
        self.reporter.note(format!("session {}", session.display()), None);
        let result = self
            .measure(ctx, plan, args, max_load, &repo, &license, &session, &launcher_label, warnings)
            .await;
        self.reporter
            .publish(Event::RunFinished(journal::run_finished(result.as_ref().err())), None);
        result
    }

    async fn measure(
        &self,
        ctx: &Ctx,
        plan: &Plan,
        args: &LaunchArgs,
        max_load: Option<f64>,
        repo: &Path,
        license: &Path,
        session: &Path,
        launcher_label: &str,
        mut warnings: Vec<String>,
    ) -> Result<Outcome, Refusal> {
        let build_log = session.join("build.log");
        let labels = [args.target.clone(), launcher_label.to_owned()];
        self.bazel.build_labels(ctx, &build_log, &labels).await?;
        let sources = resolve::sources(ctx, self.bazel, repo, &args.target, launcher_label).await?;
        let generation = stage::stage(ctx, self.locks, &self.settings.runtime_root, &sources, self.reporter).await?;
        self.reporter.note(
            format!(
                "generation {} ({}): {}",
                &generation.digest[..12.min(generation.digest.len())],
                if generation.reused { "reused" } else { "staged" },
                generation.root.display()
            ),
            None,
        );

        let profiler = if plan.profile {
            self.reporter.note("resolving the async-profiler library", None);
            warnings.push("the profile adds the async-profiler agent; its runs are slower than a run without it".to_owned());
            Some(resolve::profiler(ctx, self.bazel, self.runner, session).await?)
        } else {
            None
        };
        let git = self.git_info(ctx, repo).await;
        let launcher = Launcher::of(&generation, self.runner.clone(), repo, profiler, args.hold)?;

        let template = Sandbox::new(session.join(TEMPLATE_DIR));
        session::write_template(&template, license).map_err(|error| session_failed(format!("{error:#}")))?;
        let project = match &plan.project {
            Some(PlannedProject::Start(source)) => {
                Some(session::write_project(&template, source).map_err(|error| session_failed(format!("{error:#}")))?)
            }
            Some(PlannedProject::Open(_)) | None => None,
        };
        self.reporter.note("writing the FUS test scheme with a headless run", None);
        launcher.write_scheme(ctx, &template, &session.join("template-log")).await?;
        if ctx.is_cancelled() {
            return Err(self.interrupted(session));
        }

        let info = SessionInfo {
            command: plan.command.to_owned(),
            target: args.target.clone(),
            git,
            generation: GenerationRef {
                path: generation.root.display().to_string(),
                dist_digest: generation.digest.clone(),
            },
            arms: plan.arms.clone(),
            runs: plan.runs,
            cold: plan.cold,
            hold_ms: u64::try_from(args.hold.as_millis()).unwrap_or(u64::MAX),
            profile: plan.profile,
            project: match (&plan.project, &project) {
                (_, Some(project)) => Some(project.paths(&template).dir.display().to_string()),
                (Some(PlannedProject::Open(dir)), None) => Some(dir.display().to_string()),
                (Some(PlannedProject::Start(_)) | None, None) => None,
            },
            project_file: project
                .as_ref()
                .filter(|_| plan.arms.contains(&Arm::Project))
                .map(|project| project.file.display().to_string()),
            additional_modules: generation.config.additional_modules.clone(),
            max_load,
            warnings,
        };
        files::write_json(&session.join(SESSION_FILE), &info).map_err(|error| session_failed(format!("{error:#}")))?;

        let running = Running {
            session,
            info: &info,
            plan,
            generation: &generation,
            launcher: &launcher,
            project: project.as_ref(),
        };
        let mut records = Vec::new();
        if !plan.cold {
            for arm in &plan.arms {
                let id = RunId {
                    arm: *arm,
                    kind: RunKind::Prime,
                    index: 0,
                };
                records.push(self.one_run(ctx, &running, &id, &template).await?);
            }
        }
        for index in 1..=plan.runs {
            for arm in &plan.arms {
                let id = RunId {
                    arm: *arm,
                    kind: RunKind::Measured,
                    index,
                };
                let source = if plan.cold {
                    template.clone()
                } else {
                    let prime = RunId {
                        arm: *arm,
                        kind: RunKind::Prime,
                        index: 0,
                    };
                    Sandbox::new(session.join(prime.dir_name()).join("sandbox"))
                };
                records.push(self.one_run(ctx, &running, &id, &source).await?);
            }
        }
        self.answer(&finish(session, &info, &records)?)
    }

    /// Copies the source sandbox, runs the IDE from the generation, collects the run and writes its `result.json`.
    async fn one_run(&self, ctx: &Ctx, running: &Running<'_>, id: &RunId, source: &Sandbox) -> Result<RunRecord, Refusal> {
        let session = running.session;
        let run_dir = session.join(id.dir_name());
        let failed = |error: anyhow::Error| session_failed(format!("{error:#}"));
        // The generation is immutable, and its record names its digest. A run whose generation names another digest
        // than the session is not appended.
        let digest = stage::read_digest(&running.generation.root)?;
        session::check_digest(running.info, Some(&digest)).map_err(|reason| digest_mismatch(&reason))?;
        let sandbox = session::live_sandbox(session, id.arm);
        if sandbox.root.exists() {
            std::fs::remove_dir_all(&sandbox.root)
                .map_err(|error| session_failed(format!("cannot remove {}: {error}", sandbox.root.display())))?;
        }
        files::copy_dir(&source.root, &sandbox.root).map_err(failed)?;
        for stale in STALE_DIRS {
            let _ = std::fs::remove_dir_all(sandbox.root.join(stale));
        }
        session::own_welcome_project(&sandbox).map_err(failed)?;
        if id.arm == Arm::EmptyEditor {
            session::open_no_readme(&sandbox).map_err(failed)?;
        }
        let name = match id.kind {
            RunKind::Prime => format!("{} prime", id.arm.label()),
            RunKind::Measured => format!("{} run {}/{}", id.arm.label(), id.index, running.plan.runs),
        };
        let host_load = load::read(ctx, self.runner).await;
        let load_text = host_load
            .as_ref()
            .map(|load| {
                format!(
                    " (host load {:.1} {:.1} {:.1}, {} CPUs)",
                    load.one, load.five, load.fifteen, load.cpus
                )
            })
            .unwrap_or_default();
        self.reporter.note(format!("{name}: starting{load_text}"), None);
        let plan = RunPlan {
            arm: id.arm,
            kind: id.kind,
            project: running.project.map(|project| project.paths(&sandbox)),
            open_project: match &running.plan.project {
                Some(PlannedProject::Open(dir)) => Some(dir),
                Some(PlannedProject::Start(_)) | None => None,
            },
        };
        let mut facts = running.launcher.run(ctx, &run_dir, &sandbox, &plan).await;
        facts.dist_digest = Some(digest);
        facts.load = host_load;
        let kept = run_dir.join("sandbox");
        std::fs::create_dir_all(&run_dir).map_err(|error| session_failed(format!("cannot create {}: {error}", run_dir.display())))?;
        std::fs::rename(&sandbox.root, &kept)
            .map_err(|error| session_failed(format!("cannot move {} to {}: {error}", sandbox.root.display(), kept.display())))?;
        let record = record::collect(&run_dir, id, facts);
        write_result(&run_dir, &record, self.reporter);
        self.publish_run(&name, &record);
        if ctx.is_cancelled() {
            return Err(self.interrupted(session));
        }
        Ok(record)
    }

    /// The lock that a session holds from its build to its summary, and that `gc` takes. Two sessions on one host
    /// would measure each other, so the second one is refused too.
    async fn session_lock(&self, ctx: &Ctx, operation: &str) -> Result<LockGuard, Refusal> {
        let bench_root = self.settings.runtime_root.join(BENCH_DIR);
        create_private_dir(&bench_root)?;
        let path = bench_root.join("session.lock");
        match self
            .locks
            .acquire(ctx, &path, operation, "a vm bench session runs on this host")
            .await
        {
            Err(refusal) if refusal.code == "lease_busy" => Err(Refusal::new(
                "bench_busy",
                Exit::TEMP_FAIL,
                format!(
                    "a vm bench session runs on this host and holds {}; try again when it is done",
                    path.display()
                ),
            )),
            other => other,
        }
    }

    /// The record of a finished run: one structured event, and its line for a person.
    fn publish_run(&self, name: &str, record: &RunRecord) {
        let main = record.arm.main_metric();
        let data = json!({
            "event": "benchRun",
            "arm": record.arm,
            "kind": record.kind,
            "index": record.index,
            "dir": record.dir,
            "valid": record.valid,
            "reason": record.reason,
            "terminated": record.launch.terminated,
            "metric": main,
            "ms": record.metrics.get(main),
            "load": record.launch.load,
        });
        self.reporter.publish(
            Event::Structured {
                data,
                line: format!("{name}: {}", run_line(record)),
            },
            None,
        );
    }

    /// The answer of a finished session: the summary and its digest, or the refusal for an arm without a valid
    /// run. The refusal carries the summary in its details, and the digest lines go out as notes, so a person reads
    /// the reasons in prose too.
    fn answer(&self, summary: &Summary) -> Result<Outcome, Refusal> {
        let text = digest::render(summary);
        let failed: Vec<&str> = summary.arms_without_valid_run().iter().map(|arm| arm.label()).collect();
        if failed.is_empty() {
            return Outcome::new(summary, text);
        }
        for line in text.lines() {
            self.reporter.note(line, None);
        }
        Err(Refusal::new(
            "no_valid_run",
            Exit::TESTS_FAILED,
            format!("no run passed the gate in the arm {}; see the reasons above", failed.join(", ")),
        )
        .with_details(summary))
    }

    /// The session directory that a verb argument names. See [`session::locate`].
    fn locate(&self, arg: &str) -> Result<PathBuf, Refusal> {
        session::locate(arg, &working_dir()?, &session::runs_root(&self.settings.runtime_root))
    }

    fn interrupted(&self, session: &Path) -> Refusal {
        let cause = self.runner.interrupts().received().map_or("a cancellation", |signal| signal.name());
        Refusal::new(
            "bench_interrupted",
            Exit::SOFTWARE,
            format!("{cause} interrupted the session; {} keeps the runs so far", session.display()),
        )
    }

    /// The commit and the state of the checkout. A failed `git` is an empty answer, not a failed session.
    async fn git_info(&self, ctx: &Ctx, repo: &Path) -> GitInfo {
        let options = SpawnOptions {
            dir: Some(repo.to_path_buf()),
            ..SpawnOptions::within(GIT_INFO_TIMEOUT)
        };
        let git = |args: &'static [&'static str]| {
            let argv: Vec<String> = std::iter::once(self.settings.git.clone())
                .chain(args.iter().map(|arg| (*arg).to_owned()))
                .collect();
            let options = options.clone();
            async move {
                self.runner
                    .checked(ctx, &argv, &options)
                    .await
                    .ok()
                    .map(|captured| captured.stdout.trim().to_owned())
            }
        };
        GitInfo {
            commit: git(&["rev-parse", "HEAD"]).await.unwrap_or_default(),
            dirty: git(&["status", "--porcelain", "--untracked-files=no"])
                .await
                .is_some_and(|status| !status.is_empty()),
        }
    }
}

/// The project of `bench project`: [`MARKDOWN_PROJECT`], else a directory relative to `working_dir` with a file to
/// open. Any other argument is a usage refusal.
pub(crate) fn project_source(arg: &str, working_dir: &Path) -> Result<ProjectSource, Refusal> {
    if arg == MARKDOWN_PROJECT {
        return Ok(ProjectSource::Markdown);
    }
    let dir = working_dir.join(arg);
    if !dir.is_dir() {
        return Err(Refusal::usage(format!(
            "the project {} is not a directory, and it is not `{MARKDOWN_PROJECT}`",
            dir.display()
        )));
    }
    match session::first_file(&dir) {
        Ok(Some(_)) => Ok(ProjectSource::Dir(dir)),
        Ok(None) => Err(Refusal::usage(format!(
            "the project {} has no file to open: it needs a file that is not hidden at its top level",
            dir.display()
        ))),
        Err(error) => Err(Refusal::usage(format!("{error:#}"))),
    }
}

/// Parses a finished session again: each `result.json` gives the launch facts, the files give the rest. No IDE
/// starts, and the host is any host. [`session::locate`] gives the session.
pub(crate) fn replay(session: &Path, reporter: &Reporter) -> Result<Summary, Refusal> {
    let usage = |error: anyhow::Error| Refusal::usage(format!("{error:#}"));
    let info = session::read_info(session).map_err(usage)?;
    let mut records = Vec::new();
    for (id, run_dir) in session::run_dirs(session).map_err(usage)? {
        let result_path = run_dir.join(RESULT_FILE);
        let previous: RunRecord = files::read_text(&result_path)
            .and_then(|text| serde_json::from_str(&text).map_err(anyhow::Error::from))
            .map_err(|error| Refusal::usage(format!("{} is not a run result: {error:#}", result_path.display())))?;
        session::check_digest(&info, previous.launch.dist_digest.as_deref())
            .map_err(|reason| digest_mismatch(&format!("{}: {reason}", run_dir.display())))?;
        let record = record::collect(&run_dir, &id, previous.launch);
        write_result(&run_dir, &record, reporter);
        records.push(record);
    }
    finish(session, &info, &records)
}

fn write_result(run_dir: &Path, record: &RunRecord, reporter: &Reporter) {
    if let Err(error) = files::write_json(&run_dir.join(RESULT_FILE), record) {
        reporter.note(format!("{error:#}"), None);
    }
}

fn digest_mismatch(reason: &str) -> Refusal {
    Refusal::new(
        "bench_dist_digest_mismatch",
        Exit::USAGE,
        format!("a session holds the runs of one distribution: {reason}"),
    )
}

/// The progress line of a finished run.
fn run_line(record: &RunRecord) -> String {
    let terminated = if record.launch.terminated { ", terminated" } else { "" };
    if !record.valid {
        return format!("failed{terminated}: {}", record.reason.as_deref().unwrap_or("no reason"));
    }
    let main = record.arm.main_metric();
    record.metrics.get(main).map_or_else(
        || format!("valid{terminated}, no {main}"),
        |value| format!("{main} {value} ms{terminated}"),
    )
}

/// Builds the summary and writes `summary.json`.
fn finish(session: &Path, info: &SessionInfo, records: &[RunRecord]) -> Result<Summary, Refusal> {
    let summary = Summary::build(info, &session.display().to_string(), records);
    files::write_json(&session.join(SUMMARY_FILE), &summary).map_err(|error| session_failed(format!("{error:#}")))?;
    Ok(summary)
}

#[cfg(test)]
mod tests;
