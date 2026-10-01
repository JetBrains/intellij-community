//! The commands: a `welcome` or an `open-project` session from the build to the summary, and `replay`.

use std::path::{Path, PathBuf};

use bt_core::{Refusal, exit, fail_usage};

use crate::arm::Arm;
use crate::files;
use crate::launch::{self, Launcher, Progress, fail_infra};
use crate::options::{LaunchArgs, OpenProjectArgs, WelcomeArgs};
use crate::record::{self, RunId, RunKind, RunRecord};
use crate::session::{self, LAUNCH_SCRIPT, RESULT_FILE, SESSION_FILE, SUMMARY_FILE, Sandbox, SessionInfo, TEMPLATE_DIR};
use crate::summary::Summary;

/// The directories below a run sandbox that a copy must not carry: the FUS logs of the run that wrote the source.
const STALE_DIRS: [&str; 1] = ["system/event-log-data/logs"];

/// What a session command needs besides its options.
pub(crate) struct Host<'a> {
    pub(crate) repo_root: &'a Path,
    pub(crate) working_dir: &'a Path,
}

/// One planned session.
struct Plan {
    command: &'static str,
    arms: Vec<Arm>,
    runs: u32,
    cold: bool,
    profile: bool,
    project: Option<PathBuf>,
}

/// Runs `welcome`.
pub(crate) fn welcome(args: &WelcomeArgs, host: &Host<'_>, progress: &mut Progress<'_>) -> Result<Summary, Refusal> {
    let plan = Plan {
        command: "welcome",
        arms: args.arm.arms(),
        runs: args.runs,
        cold: args.cold,
        profile: args.profile,
        project: None,
    };
    run_session(&plan, &args.launch, host, progress)
}

/// Runs `open-project`.
pub(crate) fn open_project(args: &OpenProjectArgs, host: &Host<'_>, progress: &mut Progress<'_>) -> Result<Summary, Refusal> {
    let project = host.working_dir.join(&args.project);
    if !project.is_dir() {
        return Err(fail_usage(format!("the project {} is not a directory", project.display())));
    }
    let plan = Plan {
        command: "open-project",
        arms: vec![Arm::OpenProject],
        runs: args.runs,
        cold: false,
        profile: false,
        project: Some(project),
    };
    run_session(&plan, &args.launch, host, progress)
}

/// Parses a finished session again: each `result.json` gives the launch facts, the files give the rest.
pub(crate) fn replay(session_arg: &Path, host: &Host<'_>, progress: &mut Progress<'_>) -> Result<Summary, Refusal> {
    let session = host.working_dir.join(session_arg);
    if !session.join(SESSION_FILE).exists() {
        return Err(Refusal::new(
            "not_a_session",
            exit::USAGE,
            format!("{} is not a session directory: it has no {SESSION_FILE}", session.display()),
        ));
    }
    let info = session::read_info(&session).map_err(|error| fail_usage(format!("{error:#}")))?;
    let mut records = Vec::new();
    for (id, run_dir) in session::run_dirs(&session).map_err(|error| fail_usage(format!("{error:#}")))? {
        let result_path = run_dir.join(RESULT_FILE);
        let previous: RunRecord = files::read_text(&result_path)
            .and_then(|text| serde_json::from_str(&text).map_err(anyhow::Error::from))
            .map_err(|error| fail_usage(format!("{} is not a run result: {error:#}", result_path.display())))?;
        let record = record::collect(&run_dir, &id, previous.launch);
        write_result(&run_dir, &record, progress);
        records.push(record);
    }
    finish(&session, &info, &records)
}

/// Prepares the session, runs the prime runs and the measured runs, and writes the summary.
fn run_session(plan: &Plan, args: &LaunchArgs, host: &Host<'_>, progress: &mut Progress<'_>) -> Result<Summary, Refusal> {
    if !cfg!(target_os = "macos") {
        return Err(Refusal::new(
            "unsupported_os",
            exit::USAGE,
            format!("a session runs on macOS only, and this host is {}", std::env::consts::OS),
        ));
    }
    let license = host.repo_root.join(session::LICENSE_FILE);
    if !license.exists() {
        return Err(Refusal::new(
            "license_missing",
            exit::USAGE,
            format!(
                "no license at {}: an unlicensed IDE shows a dialog instead of the welcome screen; start the dev IDE once and register it",
                license.display()
            ),
        ));
    }
    let session = args
        .session
        .as_ref()
        .map_or_else(|| session::default_dir(host.repo_root), |dir| host.working_dir.join(dir));
    if std::fs::read_dir(&session).is_ok_and(|mut entries| entries.next().is_some()) {
        return Err(Refusal::new(
            "session_exists",
            exit::USAGE,
            format!("the session directory {} is not empty", session.display()),
        ));
    }
    std::fs::create_dir_all(&session)
        .map_err(|error| fail_infra("session_failed", format!("cannot create {}: {error}", session.display())))?;
    progress.line(&format!("session {}", session.display()));

    let profiler = if plan.profile {
        progress.line("resolving the async-profiler library");
        Some(launch::resolve_profiler(host.repo_root, &session)?)
    } else {
        None
    };
    let git = launch::git_info(host.repo_root);
    let script = session.join(LAUNCH_SCRIPT);
    let build_log = session.join("build.log");
    progress.line(&format!("building {} (log: {})", args.target, build_log.display()));
    launch::build(host.repo_root, &args.target, &script, &build_log)?;

    let script_text = files::read_text(&script).map_err(|error| fail_infra("bazel_failed", format!("{error:#}")))?;
    let binary =
        launch::script_binary(&script_text).ok_or_else(|| fail_infra("bazel_failed", format!("{} names no binary", script.display())))?;
    let mut warnings = Vec::new();
    let fidelity = match launch::launcher_java(&binary) {
        Ok(java) => {
            let (kept, dropped) = launch::verify_flags(&java);
            warnings.extend(dropped);
            kept
        }
        Err(error) => {
            warnings.push(format!("the JVM flags are not checked: {error:#}"));
            launch::FIDELITY_FLAGS.iter().map(|flag| (*flag).to_owned()).collect()
        }
    };
    if plan.profile {
        warnings.push("the profile adds the async-profiler agent; its runs are slower than a run without it".to_owned());
    }

    let template = Sandbox::new(session.join(TEMPLATE_DIR));
    session::write_template(&template, &license).map_err(|error| fail_infra("session_failed", format!("{error:#}")))?;
    let launcher = Launcher {
        repo_root: host.repo_root.to_path_buf(),
        script,
        fidelity,
        profiler,
        hold: args.hold,
    };
    progress.line("writing the FUS test scheme with a headless run");
    launcher.write_scheme(&template, &session.join("template-log"))?;

    let info = SessionInfo {
        command: plan.command.to_owned(),
        target: args.target.clone(),
        git,
        arms: plan.arms.clone(),
        runs: plan.runs,
        cold: plan.cold,
        hold_ms: u64::try_from(args.hold.as_millis()).unwrap_or(u64::MAX),
        profile: plan.profile,
        project: plan.project.as_ref().map(|project| project.display().to_string()),
        warnings,
    };
    files::write_json(&session.join(SESSION_FILE), &info).map_err(|error| fail_infra("session_failed", format!("{error:#}")))?;

    let mut records = Vec::new();
    if !plan.cold {
        for arm in &plan.arms {
            let id = RunId {
                arm: *arm,
                kind: RunKind::Prime,
                index: 0,
            };
            records.push(one_run(&session, &launcher, &id, &template, plan, progress)?);
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
            records.push(one_run(&session, &launcher, &id, &source, plan, progress)?);
        }
    }
    finish(&session, &info, &records)
}

/// Copies the source sandbox, runs the IDE, collects the run and writes its `result.json`.
fn one_run(
    session: &Path,
    launcher: &Launcher,
    id: &RunId,
    source: &Sandbox,
    plan: &Plan,
    progress: &mut Progress<'_>,
) -> Result<RunRecord, Refusal> {
    let run_dir = session.join(id.dir_name());
    let infra = |error: anyhow::Error| fail_infra("session_failed", format!("{error:#}"));
    let sandbox = session::live_sandbox(session, id.arm);
    if sandbox.root.exists() {
        std::fs::remove_dir_all(&sandbox.root)
            .map_err(|error| fail_infra("session_failed", format!("cannot remove {}: {error}", sandbox.root.display())))?;
    }
    files::copy_dir(&source.root, &sandbox.root).map_err(infra)?;
    for stale in STALE_DIRS {
        let _ = std::fs::remove_dir_all(sandbox.root.join(stale));
    }
    session::own_welcome_project(&sandbox).map_err(infra)?;
    let name = match id.kind {
        RunKind::Prime => format!("{} prime", id.arm.label()),
        RunKind::Measured => format!("{} run {}/{}", id.arm.label(), id.index, plan.runs),
    };
    progress.line(&format!("{name}: starting"));
    let facts = launcher.run(&run_dir, &sandbox, id.arm, plan.project.as_deref());
    let kept = run_dir.join("sandbox");
    std::fs::create_dir_all(&run_dir)
        .map_err(|error| fail_infra("session_failed", format!("cannot create {}: {error}", run_dir.display())))?;
    std::fs::rename(&sandbox.root, &kept).map_err(|error| {
        fail_infra(
            "session_failed",
            format!("cannot move {} to {}: {error}", sandbox.root.display(), kept.display()),
        )
    })?;
    let record = record::collect(&run_dir, id, facts);
    write_result(&run_dir, &record, progress);
    progress.line(&format!("{name}: {}", run_line(&record)));
    Ok(record)
}

/// The progress line of a finished run.
fn run_line(record: &RunRecord) -> String {
    let terminated = if record.launch.terminated { ", terminated" } else { "" };
    if !record.valid {
        return format!("failed{terminated}: {}", record.reason.as_deref().unwrap_or("no reason"));
    }
    let main = if record.arm == Arm::OpenProject {
        record::OPEN_HIGHLIGHTED
    } else {
        record::WELCOME_BECAME_VISIBLE
    };
    record.metrics.get(main).map_or_else(
        || format!("valid{terminated}, no {main}"),
        |value| format!("{main} {value} ms{terminated}"),
    )
}

fn write_result(run_dir: &Path, record: &RunRecord, progress: &mut Progress<'_>) {
    if let Err(error) = files::write_json(&run_dir.join(RESULT_FILE), record) {
        progress.line(&format!("{error:#}"));
    }
}

/// Builds the summary and writes `summary.json`.
fn finish(session: &Path, info: &SessionInfo, records: &[RunRecord]) -> Result<Summary, Refusal> {
    let summary = Summary::build(info, &session.display().to_string(), records);
    files::write_json(&session.join(SUMMARY_FILE), &summary).map_err(|error| fail_infra("session_failed", format!("{error:#}")))?;
    Ok(summary)
}
