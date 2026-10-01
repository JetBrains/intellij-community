//! The processes of a session: the Bazel build, the JVM flag check, the profiler library, and the IDE runs.
//!
//! An IDE run starts `launch.sh` with the measurement properties, waits for its event, holds, reads the FUS log, and
//! quits the IDE through a second `launch.sh` with the program argument `exit`. The dev launcher replaces itself with
//! the JVM, so the child process of `launch.sh` is the IDE.

use std::fs::File;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::{Child, Command, ExitStatus, Stdio};
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use anyhow::{Context, bail};
use bt_core::{Refusal, exit};
use serde::Deserialize;

use crate::arm::Arm;
use crate::files;
use crate::fus;
use crate::record::{self, LaunchFacts};
use crate::session::{GitInfo, Sandbox};
use crate::stats;
use crate::trace;

/// How often a wait polls.
const POLL: Duration = Duration::from_millis(200);
/// How long a run waits for its event.
pub(crate) const EVENT_TIMEOUT: Duration = Duration::from_secs(180);
/// How long a run waits for the welcome event after `startup-stats.json` appeared.
const AFTER_REPORT_TIMEOUT: Duration = Duration::from_secs(20);
/// How long the quit command, the open request and the IDE after the quit can take.
const QUIT_TIMEOUT: Duration = Duration::from_secs(60);
/// How long the IDE can take after SIGTERM.
const TERM_TIMEOUT: Duration = Duration::from_secs(15);
/// How long the headless scheme run can take.
const SCHEME_TIMEOUT: Duration = Duration::from_secs(300);

/// The flags that the dev launcher drops from `bin/idea.vmoptions`. They come last, so `-da` wins over the `-ea` of
/// the launch manifest.
pub(crate) const FIDELITY_FLAGS: [&str; 5] = [
    "-da",
    "-Xmx2048m",
    "-XX:ReservedCodeCacheSize=512m",
    "-XX:CICompilerCount=2",
    "-XX:+UseCompactObjectHeaders",
];

/// The properties of every measured start.
const MEASURE_PROPERTIES: [&str; 7] = [
    "idea.record.classpath.info=true",
    "idea.record.classloading.stats=true",
    "nosplash=true",
    "jb.consents.confirmation.enabled=false",
    "ide.newUsersOnboarding=false",
    "idea.initially.ask.config=never",
    "fus.internal.test.mode=true",
];

/// The property that forces the modal welcome screen.
pub(crate) const FORCE_MODAL_PROPERTY: &str = "idea.force.disable.non.modal.welcome.screen=true";

/// The target whose dependencies hold the async-profiler jar.
pub(crate) const PROFILER_TARGET: &str = "//plugins/profiler/ultimate/idea-async-profiler";
/// The macOS library inside the async-profiler jar.
const PROFILER_ENTRY: &str = "binaries/macos/libasyncProfiler.dylib";

/// A refusal for Bazel or a process that could not start.
pub(crate) fn fail_infra(code: &'static str, message: impl Into<String>) -> Refusal {
    Refusal::new(code, exit::INFRA, message)
}

/// Writes progress lines. They go to stderr, so stdout keeps only the answer.
pub(crate) struct Progress<'a> {
    out: &'a mut dyn Write,
}

impl<'a> Progress<'a> {
    pub(crate) fn new(out: &'a mut dyn Write) -> Self {
        Self { out }
    }

    pub(crate) fn line(&mut self, text: &str) {
        let _ = writeln!(self.out, "startup-bench: {text}");
        let _ = self.out.flush();
    }
}

/// `--jvm_flag=<flag>`, the form that the dev launcher takes before the first program argument.
fn jvm_flag(flag: &str) -> String {
    format!("--jvm_flag={flag}")
}

fn property(key: &str, value: &Path) -> String {
    jvm_flag(&format!("-D{key}={}", value.display()))
}

/// The flags that select a sandbox. The quit command needs the same flags to reach the running IDE.
pub(crate) fn sandbox_flags(sandbox: &Sandbox) -> Vec<String> {
    vec![
        property("idea.config.path", &sandbox.config()),
        property("idea.system.path", &sandbox.system()),
        property("idea.plugins.path", &sandbox.plugins()),
    ]
}

/// The flags of a measured start, in order: the sandbox, the files of the run, the properties, the arm, the
/// profiler, and the fidelity flags last.
pub(crate) fn measure_flags(run_dir: &Path, sandbox: &Sandbox, arm: Arm, profiler: Option<&Path>, fidelity: &[String]) -> Vec<String> {
    let mut flags = sandbox_flags(sandbox);
    flags.extend([
        property("idea.log.path", &run_dir.join("log")),
        property("idea.log.perf.stats.file", &run_dir.join("startup-stats.json")),
        property("idea.diagnostic.opentelemetry.file", &run_dir.join("opentelemetry.json")),
        property("idea.log.class.list.file", &run_dir.join("class-report.txt")),
        property("plugin.classloader.debug", &run_dir.join("plugin-classes.txt")),
    ]);
    flags.extend(MEASURE_PROPERTIES.iter().map(|property| jvm_flag(&format!("-D{property}"))));
    if arm == Arm::Modal {
        flags.push(jvm_flag(&format!("-D{FORCE_MODAL_PROPERTY}")));
    }
    if let Some(library) = profiler {
        flags.push(jvm_flag(&format!(
            "-agentpath:{}=start,event=cpu,interval=1ms,threads,collapsed,file={}",
            library.display(),
            run_dir.join("cpu.collapsed").display()
        )));
    }
    flags.extend(fidelity.iter().map(|flag| jvm_flag(flag)));
    flags
}

/// The program arguments of the headless run that writes the FUS test scheme.
pub(crate) fn scheme_arguments(template: &Sandbox) -> Vec<String> {
    vec![
        "buildEventsScheme".to_owned(),
        format!("--outputFile={}", template.test_scheme().display()),
        "--recorderId=FUS".to_owned(),
        "--testEventScheme=true".to_owned(),
    ]
}

/// Runs `./bazel.cmd run --script_path=<script> <target>` in the checkout root, with the output in `log`.
pub(crate) fn build(repo_root: &Path, target: &str, script: &Path, log: &Path) -> Result<(), Refusal> {
    let output = log_file(log).map_err(|error| fail_infra("bazel_failed", format!("{error:#}")))?;
    let status = Command::new(repo_root.join("bazel.cmd"))
        .arg("run")
        .arg(format!("--script_path={}", script.display()))
        .arg(target)
        .current_dir(repo_root)
        .stdin(Stdio::null())
        .stdout(output.try_clone().map_err(|error| fail_infra("bazel_failed", error.to_string()))?)
        .stderr(output)
        .status()
        .map_err(|error| fail_infra("bazel_failed", format!("cannot start {}/bazel.cmd: {error}", repo_root.display())))?;
    if !status.success() {
        return Err(fail_infra(
            "bazel_failed",
            format!(
                "bazel run of {target} failed ({status}); the last lines of {}:\n{}",
                log.display(),
                tail(log, 15)
            ),
        ));
    }
    Ok(())
}

/// The last lines of a log file.
pub(crate) fn tail(log: &Path, lines: usize) -> String {
    let text = files::read_optional(log).ok().flatten().unwrap_or_default();
    let all: Vec<&str> = text.lines().collect();
    all[all.len().saturating_sub(lines)..].join("\n")
}

fn log_file(path: &Path) -> anyhow::Result<File> {
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent).with_context(|| format!("cannot create {}", parent.display()))?;
    }
    File::create(path).with_context(|| format!("cannot create {}", path.display()))
}

/// The binary that a `bazel run --script_path` script runs: the first word of its last line.
pub(crate) fn script_binary(script: &str) -> Option<PathBuf> {
    let last = script.lines().rev().find(|line| !line.trim().is_empty())?;
    last.split_whitespace().next().map(PathBuf::from)
}

/// The `java` of the launch manifest beside the launcher binary, as a runfiles path of the binary.
pub(crate) fn launcher_java(binary: &Path) -> anyhow::Result<PathBuf> {
    #[derive(Deserialize)]
    struct Manifest {
        java: String,
    }
    let manifest_path = PathBuf::from(format!("{}.launch.json", binary.display()));
    let text = files::read_text(&manifest_path)?;
    let manifest: Manifest = serde_json::from_str(&text).with_context(|| format!("{} has no `java` field", manifest_path.display()))?;
    Ok(PathBuf::from(format!("{}.runfiles", binary.display())).join(manifest.java))
}

/// Keeps each fidelity flag that the JVM accepts. A `-XX` flag that the JVM refuses is dropped with a warning.
pub(crate) fn verify_flags(java: &Path) -> (Vec<String>, Vec<String>) {
    let mut kept = Vec::new();
    let mut warnings = Vec::new();
    for flag in FIDELITY_FLAGS {
        if !flag.starts_with("-XX:") {
            kept.push(flag.to_owned());
            continue;
        }
        let accepted = Command::new(java)
            .arg(flag)
            .arg("-version")
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .status()
            .is_ok_and(|status| status.success());
        if accepted {
            kept.push(flag.to_owned());
        } else {
            warnings.push(format!("dropped {flag}: {} refuses it", java.display()));
        }
    }
    (kept, warnings)
}

/// The commit and the state of the checkout.
pub(crate) fn git_info(repo_root: &Path) -> GitInfo {
    let git = |args: &[&str]| {
        Command::new("git")
            .args(args)
            .current_dir(repo_root)
            .stdin(Stdio::null())
            .stderr(Stdio::null())
            .output()
            .ok()
            .filter(|output| output.status.success())
            .map(|output| String::from_utf8_lossy(&output.stdout).trim().to_owned())
    };
    GitInfo {
        commit: git(&["rev-parse", "HEAD"]).unwrap_or_default(),
        dirty: git(&["status", "--porcelain", "--untracked-files=no"]).is_some_and(|status| !status.is_empty()),
    }
}

/// Extracts the macOS async-profiler library from the jar that Bazel resolves for [`PROFILER_TARGET`].
pub(crate) fn resolve_profiler(repo_root: &Path, session: &Path) -> Result<PathBuf, Refusal> {
    let refuse = |message: String| Refusal::new("profiler_unavailable", exit::USAGE, format!("--profile: {message}"));
    let bazel = |args: &[&str]| -> Result<String, Refusal> {
        let output = Command::new(repo_root.join("bazel.cmd"))
            .args(args)
            .current_dir(repo_root)
            .stdin(Stdio::null())
            .stderr(Stdio::null())
            .output()
            .map_err(|error| fail_infra("bazel_failed", format!("cannot start bazel: {error}")))?;
        if !output.status.success() {
            return Err(fail_infra(
                "bazel_failed",
                format!("--profile: bazel {} failed ({})", args.join(" "), output.status),
            ));
        }
        Ok(String::from_utf8_lossy(&output.stdout).into_owned())
    };
    let query = format!("filter(\"async-profiler\", deps({PROFILER_TARGET}))");
    let files = bazel(&["cquery", "--output=files", &query])?;
    let Some(jar) = profiler_jar(&files) else {
        return Err(refuse(format!("no async-profiler jar among the files of {query}")));
    };
    let base = if jar.starts_with("external/") {
        bazel(&["info", "output_base"])?
    } else {
        bazel(&["info", "execution_root"])?
    };
    let jar = Path::new(base.trim()).join(jar);
    let library = session.join("async-profiler").join("libasyncProfiler.dylib");
    let extracted = extract_entry(&jar, PROFILER_ENTRY, &library);
    match extracted {
        Ok(()) => Ok(library),
        Err(error) => Err(refuse(format!("{error:#}"))),
    }
}

/// The async-profiler jar among the lines of `cquery --output=files`.
pub(crate) fn profiler_jar(files: &str) -> Option<&str> {
    files.lines().map(str::trim).find(|line| {
        let name = line.rsplit('/').next().unwrap_or(line);
        name.starts_with("async-profiler-")
            && Path::new(name).extension().is_some_and(|extension| extension == "jar")
            && !name.ends_with("-sources.jar")
    })
}

/// Extracts one entry of a jar with `unzip -p`.
fn extract_entry(jar: &Path, entry: &str, to: &Path) -> anyhow::Result<()> {
    let output = log_file(to)?;
    let status = Command::new("unzip")
        .arg("-p")
        .arg(jar)
        .arg(entry)
        .stdin(Stdio::null())
        .stdout(output)
        .stderr(Stdio::null())
        .status()
        .with_context(|| format!("cannot start unzip for {}", jar.display()))?;
    let size = std::fs::metadata(to).map_or(0, |metadata| metadata.len());
    if !status.success() || size == 0 {
        bail!("{} has no {entry}", jar.display());
    }
    Ok(())
}

/// Starts `launch.sh` with the arguments, the output in `log`, in the checkout root.
fn start(script: &Path, repo_root: &Path, args: &[String], log: &Path) -> anyhow::Result<Child> {
    let output = log_file(log)?;
    Command::new(script)
        .args(args)
        .env("BUILD_WORKSPACE_DIRECTORY", repo_root)
        .current_dir(repo_root)
        .stdin(Stdio::null())
        .stdout(output.try_clone().with_context(|| format!("cannot open {}", log.display()))?)
        .stderr(output)
        .spawn()
        .with_context(|| format!("cannot start {}", script.display()))
}

/// Waits for a child up to `timeout`.
fn wait_for(child: &mut Child, timeout: Duration) -> Option<ExitStatus> {
    let deadline = Instant::now() + timeout;
    loop {
        if let Ok(Some(status)) = child.try_wait() {
            return Some(status);
        }
        if Instant::now() >= deadline {
            return None;
        }
        std::thread::sleep(POLL);
    }
}

/// Sends SIGTERM through `kill`, so the crate needs no binding to libc.
fn terminate(child: &Child) {
    let _ = Command::new("kill")
        .arg("-TERM")
        .arg(child.id().to_string())
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .status();
}

fn now_us() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map_or(0, |since| i64::try_from(since.as_micros()).unwrap_or(i64::MAX))
}

/// The settings that every IDE run of a session shares.
pub(crate) struct Launcher {
    pub(crate) repo_root: PathBuf,
    pub(crate) script: PathBuf,
    pub(crate) fidelity: Vec<String>,
    pub(crate) profiler: Option<PathBuf>,
    pub(crate) hold: Duration,
}

impl Launcher {
    /// Writes the FUS test scheme into the template with one headless run.
    pub(crate) fn write_scheme(&self, template: &Sandbox, log_dir: &Path) -> Result<(), Refusal> {
        let mut args = sandbox_flags(template);
        args.push(property("idea.log.path", log_dir));
        args.push(jvm_flag("-Djava.awt.headless=true"));
        args.extend(scheme_arguments(template));
        let log = log_dir.join("launch.log");
        let mut child =
            start(&self.script, &self.repo_root, &args, &log).map_err(|error| fail_infra("launch_failed", format!("{error:#}")))?;
        let status = wait_for(&mut child, SCHEME_TIMEOUT);
        if status.is_none() {
            let _ = child.kill();
            let _ = child.wait();
        }
        if !template.test_scheme().exists() {
            return Err(fail_infra(
                "scheme_failed",
                format!(
                    "the buildEventsScheme run wrote no {} ({}); see {}",
                    template.test_scheme().display(),
                    status.map_or_else(|| "timed out".to_owned(), |status| status.to_string()),
                    log.display()
                ),
            ));
        }
        Ok(())
    }

    /// Runs the IDE once in `run_dir` and returns what the controller saw. `open_project` makes it the
    /// `open-project` run.
    pub(crate) fn run(&self, run_dir: &Path, sandbox: &Sandbox, arm: Arm, open_project: Option<&Path>) -> LaunchFacts {
        let mut facts = LaunchFacts::default();
        let args = measure_flags(run_dir, sandbox, arm, self.profiler.as_deref(), &self.fidelity);
        let started = Instant::now();
        let mut ide = match start(&self.script, &self.repo_root, &args, &run_dir.join("launch.log")) {
            Ok(child) => child,
            Err(error) => {
                facts.failure = Some(format!("{error:#}"));
                return facts;
            }
        };
        let mut collector = fus::Collector::default();
        let fus_dir = fus::log_dir(&sandbox.system());
        let mut exited = Self::wait_for_welcome(&mut ide, run_dir, &fus_dir, &mut collector, &mut facts);
        facts.waited_ms = u64::try_from(started.elapsed().as_millis()).unwrap_or(u64::MAX);
        if exited.is_none()
            && facts.failure.is_none()
            && let Some(project) = open_project
        {
            exited = self.open(&mut ide, run_dir, sandbox, project, &mut facts);
        }
        if exited.is_none() {
            if facts.failure.is_none() {
                std::thread::sleep(self.hold);
            }
            collector.read(&fus_dir);
        }
        if let Err(error) = files::write_text(&run_dir.join("fus.jsonl"), &collector.text()) {
            facts.failure.get_or_insert(format!("{error:#}"));
        }
        let status = match exited {
            Some(status) => Some(status),
            None => self.quit(&mut ide, run_dir, sandbox, &mut facts),
        };
        facts.ide_exit = status.and_then(|status| status.code());
        facts
    }

    /// Polls the FUS log for the welcome event. Returns the exit status when the IDE exited before it.
    fn wait_for_welcome(
        ide: &mut Child,
        run_dir: &Path,
        fus_dir: &Path,
        collector: &mut fus::Collector,
        facts: &mut LaunchFacts,
    ) -> Option<ExitStatus> {
        let deadline = Instant::now() + EVENT_TIMEOUT;
        let report = run_dir.join("startup-stats.json");
        let mut report_deadline: Option<Instant> = None;
        loop {
            if let Ok(Some(status)) = ide.try_wait() {
                collector.read(fus_dir);
                facts.failure = Some(format!("the IDE exited ({status}) before {}", fus::WELCOME_BECAME_VISIBLE));
                return Some(status);
            }
            collector.read(fus_dir);
            if collector.has_event(fus::WELCOME_BECAME_VISIBLE) {
                return None;
            }
            let now = Instant::now();
            if report_deadline.is_none() && report.exists() {
                report_deadline = Some(now + AFTER_REPORT_TIMEOUT);
            }
            if report_deadline.is_some_and(|limit| now >= limit) {
                facts.failure = Some(format!(
                    "no {} within {} s after startup-stats.json",
                    fus::WELCOME_BECAME_VISIBLE,
                    AFTER_REPORT_TIMEOUT.as_secs()
                ));
                return None;
            }
            if now >= deadline {
                facts.failure = Some(format!("no {} within {} s", fus::WELCOME_BECAME_VISIBLE, EVENT_TIMEOUT.as_secs()));
                return None;
            }
            std::thread::sleep(POLL);
        }
    }

    /// Asks the running IDE to open `project`, and polls the trace for the highlighted editor.
    fn open(&self, ide: &mut Child, run_dir: &Path, sandbox: &Sandbox, project: &Path, facts: &mut LaunchFacts) -> Option<ExitStatus> {
        let mut args = sandbox_flags(sandbox);
        args.push(property("idea.log.path", &run_dir.join("open-log")));
        args.push(project.display().to_string());
        let request_us = now_us();
        facts.open_request_us = Some(request_us);
        match start(&self.script, &self.repo_root, &args, &run_dir.join("open.log")) {
            Ok(mut request) => {
                if wait_for(&mut request, QUIT_TIMEOUT).is_none() {
                    let _ = request.kill();
                    let _ = request.wait();
                }
            }
            Err(error) => {
                facts.failure = Some(format!("the open request: {error:#}"));
                return None;
            }
        }
        let deadline = Instant::now() + EVENT_TIMEOUT;
        loop {
            if let Ok(Some(status)) = ide.try_wait() {
                facts.failure = Some(format!("the IDE exited ({status}) before the project opened"));
                return Some(status);
            }
            if Self::highlighted(run_dir, request_us) {
                return None;
            }
            if Instant::now() >= deadline {
                facts.failure = Some(format!(
                    "no \"editor highlighting completed\" within {} s after the open request",
                    EVENT_TIMEOUT.as_secs()
                ));
                return None;
            }
            std::thread::sleep(POLL * 5);
        }
    }

    /// Tells whether the trace that the IDE wrote so far has the highlighted editor after `request_us`.
    fn highlighted(run_dir: &Path, request_us: i64) -> bool {
        let Some(trace) = files::read_optional(&run_dir.join("opentelemetry.json"))
            .ok()
            .flatten()
            .and_then(|text| trace::parse(&text).ok())
        else {
            return false;
        };
        let stats = files::read_optional(&run_dir.join("startup-stats.json"))
            .ok()
            .flatten()
            .and_then(|text| stats::parse(&text).ok());
        record::highlighted_at_us(&trace, stats.as_ref(), request_us).is_some()
    }

    /// Quits the IDE with the `exit` command, then SIGTERM, then SIGKILL.
    fn quit(&self, ide: &mut Child, run_dir: &Path, sandbox: &Sandbox, facts: &mut LaunchFacts) -> Option<ExitStatus> {
        let mut args = sandbox_flags(sandbox);
        args.push(property("idea.log.path", &run_dir.join("exit-log")));
        args.push("exit".to_owned());
        if let Ok(mut command) = start(&self.script, &self.repo_root, &args, &run_dir.join("exit.log"))
            && wait_for(&mut command, QUIT_TIMEOUT).is_none()
        {
            let _ = command.kill();
            let _ = command.wait();
        }
        if let Some(status) = wait_for(ide, QUIT_TIMEOUT) {
            return Some(status);
        }
        facts.terminated = true;
        terminate(ide);
        if let Some(status) = wait_for(ide, TERM_TIMEOUT) {
            return Some(status);
        }
        let _ = ide.kill();
        ide.wait().ok()
    }
}

#[cfg(test)]
mod tests;
