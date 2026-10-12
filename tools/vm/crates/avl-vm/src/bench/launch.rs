//! The IDE processes of a session: the Starter-shaped command line over a generation, and the supervision of one run.
//!
//! The command line is the one the IDE Starter gives a locally built distribution (`IdeFromCodeInstaller`):
//! `<java> @<argfile>`, where the argfile holds the JVM options of the distribution (`bin/*.vmoptions`,
//! `-Djb.vmOptionsFile` and the `additionalJvmArguments` of `product-info.json`), the test options of the Starter, the
//! options of the run with the JVM class-load log, the `--add-opens` lines of `OpenedPackages.txt` for this OS, `-classpath` with the entries of
//! `core-classpath.txt`, and the main class of the dist config. Every path in it is a path of the generation.
//!
//! A run starts the IDE under a child context, waits for the event of its gate, holds, reads the FUS log, and quits the IDE
//! through a second JVM with the program argument `exit`. When the IDE does not exit, the run cancels the child
//! context, which sends SIGTERM to its process group, and then drops the spawn, which sends SIGKILL. The spawn
//! timeout is the ceiling of the whole run. An interrupt cancels the context of the session, which reaches the child
//! context too, so no IDE outlives the controller.

use std::future::Future;
use std::path::{Path, PathBuf};
use std::pin::Pin;
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use anyhow::{Context, bail};
use avl_base::{Exit, Refusal};
use avl_host_sys::{Ctx, ProcError, Runner, SpawnOptions};
use serde::Deserialize;

use super::arm::{Arm, Gate};
use super::files;
use super::fus;
use super::record::{self, EDITOR_HIGHLIGHTED, LaunchFacts, RunKind};
use super::session::{CLASS_LOAD_LOG, ProjectPaths, Sandbox, TRACE_FILE};
use super::stage::{DistConfig, Generation};
use super::stats;
use super::trace;

/// How often a wait polls.
const POLL: Duration = Duration::from_millis(200);
/// How long a run waits for its event.
pub(crate) const EVENT_TIMEOUT: Duration = Duration::from_secs(180);
/// How long a run waits for the event of its gate after `startup-stats.json` appeared.
const AFTER_REPORT_TIMEOUT: Duration = Duration::from_secs(20);
/// How long a run of a span gate waits after `startup-stats.json` appeared, when the trace does not show the span yet.
const SPAN_SETTLE: Duration = Duration::from_secs(10);
/// How long the quit command, the open request and the IDE after the quit can take.
const QUIT_TIMEOUT: Duration = Duration::from_secs(60);
/// How long the IDE can take after SIGTERM.
const TERM_TIMEOUT: Duration = Duration::from_secs(15);
/// How long the headless scheme run can take.
const SCHEME_TIMEOUT: Duration = Duration::from_secs(300);

/// The option that comes after every JVM option of the distribution: `-da` wins over the `-ea` of the dev
/// `idea.vmoptions`, so the run measures the code a release runs.
pub(crate) const FIDELITY_FLAG: &str = "-da";

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

/// The properties of the `project` arm: the project of the command line is trusted, so no trust dialog stops a run,
/// and the run is an integration test, as every perf test of the IDE Starter is. Air offers its welcome page in a
/// project window unless that property is set. The page is an editor tab that takes the selected tab from the file,
/// so the daemon never highlights the file, and the gate never fires.
pub(crate) const PROJECT_PROPERTIES: [&str; 2] = ["idea.trust.all.projects=true", "idea.is.integration.test=true"];

/// The file of the opened packages in a generation, a copy of
/// `community/platform/platform-impl/resources/META-INF/OpenedPackages.txt`.
pub(crate) const OPENED_PACKAGES_FILE: &str = "OpenedPackages.txt";

fn property(key: &str, value: &Path) -> String {
    format!("-D{key}={}", value.display())
}

/// The options that select a sandbox. The quit command needs the same options to reach the running IDE.
pub(crate) fn sandbox_options(sandbox: &Sandbox) -> Vec<String> {
    vec![
        property("idea.config.path", &sandbox.config()),
        property("idea.system.path", &sandbox.system()),
        property("idea.plugins.path", &sandbox.plugins()),
    ]
}

/// The option that makes the JVM write [`CLASS_LOAD_LOG`] with the uptime of each class. The `-Xlog` grammar ends the
/// file name at a `:`, so a run directory with a `:` in its path is an error that names it. A space is no problem,
/// because the argfile quotes it.
pub(crate) fn class_load_option(run_dir: &Path) -> Result<String, String> {
    let log = run_dir.join(CLASS_LOAD_LOG);
    let path = log.display().to_string();
    if path.contains(':') {
        return Err(format!(
            "the run directory {} holds a `:`, which ends the file name of -Xlog:class+load; use a session directory without one",
            run_dir.display()
        ));
    }
    Ok(format!("-Xlog:class+load:file={path}:uptime,tags"))
}

/// The options of a measured start, in order: the sandbox, the files of the run, the class-load log, the properties,
/// the arm and the profiler. A run directory that the class-load log cannot name is an error, see
/// [`class_load_option`].
pub(crate) fn measure_options(run_dir: &Path, sandbox: &Sandbox, arm: Arm, profiler: Option<&Path>) -> Result<Vec<String>, String> {
    let mut options = sandbox_options(sandbox);
    options.extend([
        property("idea.log.path", &run_dir.join("log")),
        property("idea.log.perf.stats.file", &run_dir.join("startup-stats.json")),
        property("idea.diagnostic.opentelemetry.file", &run_dir.join(TRACE_FILE)),
        property("idea.log.class.list.file", &run_dir.join("class-report.txt")),
        property("plugin.classloader.debug", &run_dir.join("plugin-classes.txt")),
        class_load_option(run_dir)?,
    ]);
    options.extend(MEASURE_PROPERTIES.iter().map(|property| format!("-D{property}")));
    match arm {
        Arm::Modal => options.push(format!("-D{FORCE_MODAL_PROPERTY}")),
        Arm::Project | Arm::EmptyEditor => options.extend(PROJECT_PROPERTIES.iter().map(|property| format!("-D{property}"))),
        Arm::NonModal | Arm::OpenProject => {}
    }
    if let Some(library) = profiler {
        options.push(format!(
            "-agentpath:{}=start,event=cpu,interval=1ms,threads,collapsed,file={}",
            library.display(),
            run_dir.join("cpu.collapsed").display()
        ));
    }
    Ok(options)
}

/// What one IDE run starts: the arm, the kind of the run, and the project.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct RunPlan<'a> {
    pub(crate) arm: Arm,
    pub(crate) kind: RunKind,
    /// The project of the `project` arm, inside the live sandbox.
    pub(crate) project: Option<ProjectPaths>,
    /// The project that the running IDE opens in the `open-project` arm.
    pub(crate) open_project: Option<&'a Path>,
}

impl RunPlan<'_> {
    /// The program arguments of the IDE. The prime run of the `project` arm passes the project and its first file, so
    /// the IDE saves the editor state. A measured run passes the project only, and the IDE restores the editor. Each
    /// run of the `empty-editor` arm passes the project only, so the IDE saves and restores no editor.
    pub(crate) fn program_arguments(&self) -> Vec<String> {
        match (&self.project, self.arm, self.kind) {
            (Some(project), Arm::Project, RunKind::Prime) => vec![project.dir.display().to_string(), project.file.display().to_string()],
            (Some(project), _, _) => vec![project.dir.display().to_string()],
            (None, _, _) => Vec::new(),
        }
    }
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

/// The macro of `additionalJvmArguments` that names the home of the IDE on this OS.
const fn home_macro() -> &'static str {
    if cfg!(windows) {
        "%IDE_HOME%"
    } else if cfg!(target_os = "macos") {
        "$APP_PACKAGE/Contents"
    } else {
        "$IDE_HOME"
    }
}

/// The JVM options of a distribution, as `readVmOptions` of the dev launch reads them, then the test options of the
/// Starter and the platform prefix of the config.
///
/// One test option differs from the Starter: `idea.is.internal` is `true`. The FUS validator reads the test scheme of
/// the sandbox only in an internal application (`StatisticsRecorderUtil.isAnyTestModeEnabled`), and without it the
/// welcome events of the gate arrive as `validation.unmatched_rule`. The dev launcher of the BT version passed `true`
/// too.
pub(crate) fn dist_options(dist: &Path, config: &DistConfig, repo_root: &Path) -> anyhow::Result<Vec<String>> {
    let bin = dist.join("bin");
    let mut vm_options: Vec<PathBuf> = std::fs::read_dir(&bin)
        .with_context(|| format!("cannot list {}", bin.display()))?
        .filter_map(Result::ok)
        .map(|entry| entry.path())
        .filter(|path| path.extension().is_some_and(|extension| extension == "vmoptions"))
        .collect();
    let Some(vm_options_file) = vm_options.pop().filter(|_| vm_options.is_empty()) else {
        bail!("{} has no single *.vmoptions file", bin.display());
    };
    let mut options: Vec<String> = files::read_text(&vm_options_file)?
        .lines()
        .map(str::trim)
        .filter(|line| !line.is_empty() && !line.starts_with('#'))
        .map(str::to_owned)
        .collect();
    options.push(property("jb.vmOptionsFile", &vm_options_file));

    #[derive(Deserialize)]
    #[serde(rename_all = "camelCase")]
    struct ProductInfo {
        launch: Vec<LaunchData>,
    }
    #[derive(Deserialize)]
    #[serde(rename_all = "camelCase")]
    struct LaunchData {
        #[serde(default)]
        additional_jvm_arguments: Vec<String>,
    }
    let product_info_path = bin.join("product-info.json");
    let product_info: ProductInfo = serde_json::from_str(&files::read_text(&product_info_path)?)
        .with_context(|| format!("{} has no `launch` list", product_info_path.display()))?;
    let home = dist.display().to_string();
    if let Some(launch) = product_info.launch.first() {
        options.extend(
            launch
                .additional_jvm_arguments
                .iter()
                .map(|argument| argument.replace(home_macro(), &home)),
        );
    }

    let dist_name = dist.file_name().map(|name| name.to_string_lossy().into_owned()).unwrap_or_default();
    options.extend([
        "-Didea.use.dev.build.server=true".to_owned(),
        "-Didea.ui.icons.svg.disk.cache=false".to_owned(),
        "-Didea.is.internal=true".to_owned(),
        format!("-Ddev.build.dir={dist_name}"),
        property("idea.dev.project.root", repo_root),
        property("idea.home.path", dist),
        property("idea.properties.file", &bin.join("idea.properties")),
    ]);
    if let Some(prefix) = &config.platform_prefix {
        options.push(format!("-Didea.platform.prefix={prefix}"));
    }
    Ok(options)
}

/// The tail of every argfile: the opened packages for this OS, the class path and the main class.
pub(crate) fn entry_options(dist: &Path, opened_packages: &str, classpath: &str, main_class: &str) -> anyhow::Result<Vec<String>> {
    let mut options: Vec<String> = opened_packages
        .lines()
        .map(str::trim)
        .filter(|line| !line.is_empty() && !excluded_on_this_os(line))
        .map(str::to_owned)
        .collect();
    let mut entries = Vec::new();
    for line in classpath.lines().map(str::trim).filter(|line| !line.is_empty()) {
        let path = Path::new(line);
        if path.is_absolute() && !path.starts_with(dist) {
            bail!("core-classpath.txt names {line}, outside the distribution; the bench runs a self-contained one");
        }
        entries.push(dist.join(path).display().to_string());
    }
    if entries.is_empty() {
        bail!("core-classpath.txt of {} is empty", dist.display());
    }
    let separator = if cfg!(windows) { ";" } else { ":" };
    options.extend(["-classpath".to_owned(), entries.join(separator), main_class.to_owned()]);
    Ok(options)
}

/// The opened packages that `JavaModuleOptions.readOptions` leaves out on this OS.
fn excluded_on_this_os(line: &str) -> bool {
    let mut excluded = Vec::new();
    if !cfg!(windows) {
        excluded.push("/sun.awt.windows");
    }
    if !cfg!(target_os = "macos") {
        excluded.extend(["/sun.lwawt", "/com.apple"]);
    }
    if !cfg!(any(target_os = "linux", target_os = "freebsd")) {
        excluded.extend(["/sun.awt.X11", "/com.sun.java.swing.plaf.gtk"]);
    }
    excluded.iter().any(|part| line.contains(part))
}

/// One argument of a Java argfile, quoted as `JvmUtils.quoteArg` of the Starter quotes it.
pub(crate) fn quote_argument(argument: &str) -> String {
    const SPECIALS: [char; 8] = [' ', '#', '\'', '"', '\n', '\r', '\t', '\u{c}'];
    if !argument.contains(SPECIALS) {
        return argument.to_owned();
    }
    let mut quoted = String::with_capacity(argument.len() * 2);
    for character in argument.chars() {
        match character {
            ' ' | '#' | '\'' => {
                quoted.push('"');
                quoted.push(character);
                quoted.push('"');
            }
            '"' => quoted.push_str("\"\\\"\""),
            '\n' => quoted.push_str("\"\\n\""),
            '\r' => quoted.push_str("\"\\r\""),
            '\t' => quoted.push_str("\"\\t\""),
            other => quoted.push(other),
        }
    }
    quoted
}

/// The text of an argfile, one quoted argument per line.
pub(crate) fn argfile_text(arguments: &[String]) -> String {
    arguments.iter().map(|argument| format!("{}\n", quote_argument(argument))).collect()
}

fn now_us() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map_or(0, |since| i64::try_from(since.as_micros()).unwrap_or(i64::MAX))
}

/// How the IDE process ended: its exit code, or `None` when it had none, such as at the spawn ceiling.
type Ended = Option<i32>;

/// The exit code of a finished spawn of [`Runner::checked_to_file`].
const fn ended(result: &Result<String, ProcError>) -> Ended {
    match result {
        Ok(_) => Some(0),
        Err(ProcError::Exited { exit_code, .. }) => Some(*exit_code),
        Err(_) => None,
    }
}

fn describe(result: &Result<String, ProcError>) -> String {
    match result {
        Ok(_) => "exit code 0".to_owned(),
        Err(error) => error.to_string(),
    }
}

/// The settings that every IDE run of a session shares.
pub(crate) struct Launcher {
    pub(crate) runner: Runner,
    pub(crate) java: PathBuf,
    pub(crate) repo_root: PathBuf,
    /// The JVM options of the distribution: [`dist_options`].
    pub(crate) dist_options: Vec<String>,
    /// The tail of every argfile: [`entry_options`].
    pub(crate) entry_options: Vec<String>,
    pub(crate) profiler: Option<PathBuf>,
    pub(crate) hold: Duration,
}

impl Launcher {
    /// The launcher of a generation. A generation of another shape is a refusal that names the file.
    pub(crate) fn of(
        generation: &Generation,
        runner: Runner,
        repo_root: &Path,
        profiler: Option<PathBuf>,
        hold: Duration,
    ) -> Result<Self, Refusal> {
        let refuse = |error: anyhow::Error| {
            Refusal::new(
                "bench_dist_unsupported",
                Exit::USAGE,
                format!("the generation {} cannot start: {error:#}", generation.root.display()),
            )
        };
        let dist_options = dist_options(&generation.dist, &generation.config, repo_root).map_err(refuse)?;
        let opened_packages = files::read_text(&generation.root.join(OPENED_PACKAGES_FILE)).map_err(refuse)?;
        let classpath = files::read_text(&generation.dist.join("core-classpath.txt")).map_err(refuse)?;
        let entry_options = entry_options(&generation.dist, &opened_packages, &classpath, &generation.config.main_class).map_err(refuse)?;
        Ok(Self {
            runner,
            java: generation.java.clone(),
            repo_root: repo_root.to_path_buf(),
            dist_options,
            entry_options,
            profiler,
            hold,
        })
    }

    /// Writes the argfile of one JVM and answers its argv with the program arguments.
    fn argv(&self, argfile: &Path, options: &[String], program: &[String]) -> anyhow::Result<Vec<String>> {
        let mut arguments = self.dist_options.clone();
        arguments.extend_from_slice(options);
        arguments.push(FIDELITY_FLAG.to_owned());
        arguments.extend_from_slice(&self.entry_options);
        files::write_text(argfile, &argfile_text(&arguments))?;
        let mut argv = vec![self.java.display().to_string(), format!("@{}", argfile.display())];
        argv.extend_from_slice(program);
        Ok(argv)
    }

    /// The options of a spawn whose output is a log, with a timeout.
    fn logged(&self, timeout: Duration, code: &'static str) -> SpawnOptions {
        SpawnOptions {
            dir: Some(self.repo_root.clone()),
            merge_stderr_into_file: true,
            keep_destination_on_failure: true,
            ..SpawnOptions::timeout(timeout, code)
        }
    }

    /// The ceiling of one IDE process: every wait of a run, and the hold.
    fn ceiling(&self) -> Duration {
        EVENT_TIMEOUT * 2 + self.hold + QUIT_TIMEOUT * 3 + TERM_TIMEOUT
    }

    /// Writes the FUS test scheme into the template with one headless run.
    pub(crate) async fn write_scheme(&self, ctx: &Ctx, template: &Sandbox, log_dir: &Path) -> Result<(), Refusal> {
        let failed = |message: String| Refusal::new("bench_scheme_failed", Exit::FAILURE, message);
        let mut options = sandbox_options(template);
        options.push(property("idea.log.path", log_dir));
        options.push("-Djava.awt.headless=true".to_owned());
        let argv = self
            .argv(&log_dir.join("launch.args"), &options, &scheme_arguments(template))
            .map_err(|error| failed(format!("{error:#}")))?;
        let log = log_dir.join("launch.log");
        let result = self
            .runner
            .checked_to_file(ctx, &argv, &log, &self.logged(SCHEME_TIMEOUT, "bench_scheme_timeout"))
            .await;
        if !template.test_scheme().exists() {
            return Err(failed(format!(
                "the buildEventsScheme run wrote no {} ({}); see {}",
                template.test_scheme().display(),
                describe(&result),
                log.display()
            )));
        }
        Ok(())
    }

    /// Runs the IDE once in `run_dir` and answers what the controller saw. The run waits for the gate of its arm.
    pub(crate) async fn run(&self, ctx: &Ctx, run_dir: &Path, sandbox: &Sandbox, plan: &RunPlan<'_>) -> LaunchFacts {
        let mut facts = LaunchFacts::default();
        let options = match measure_options(run_dir, sandbox, plan.arm, self.profiler.as_deref()) {
            Ok(options) => options,
            Err(failure) => {
                facts.failure = Some(failure);
                return facts;
            }
        };
        let argv = match self.argv(&run_dir.join("launch.args"), &options, &plan.program_arguments()) {
            Ok(argv) => argv,
            Err(error) => {
                facts.failure = Some(format!("{error:#}"));
                return facts;
            }
        };
        let child = Ctx::new(ctx.token().child_token());
        let log = run_dir.join("launch.log");
        let spawn_options = self.logged(self.ceiling(), "bench_ide_ceiling");
        let started = Instant::now();
        let mut ide = Box::pin(self.runner.checked_to_file(&child, &argv, &log, &spawn_options));
        let mut collector = fus::Collector::default();
        let fus_dir = fus::log_dir(&sandbox.system());
        let mut exited = match plan.arm.gate() {
            Gate::Welcome { .. } => wait_for_welcome(ctx, &mut ide, run_dir, &fus_dir, &mut collector, &mut facts).await,
            Gate::Highlighted => wait_for_highlighted(ctx, &mut ide, run_dir, plan.kind == RunKind::Prime, &mut facts).await,
            Gate::Span { name, missing } => wait_for_span(ctx, &mut ide, run_dir, name, missing, &mut facts).await,
        };
        facts.waited_ms = u64::try_from(started.elapsed().as_millis()).unwrap_or(u64::MAX);
        if exited.is_none()
            && facts.failure.is_none()
            && let Some(project) = plan.open_project
        {
            exited = self.open(ctx, &mut ide, run_dir, sandbox, project, &mut facts).await;
        }
        if exited.is_none() {
            if facts.failure.is_none() && !ctx.sleep(self.hold).await {
                facts.failure = Some(interrupted_text());
            }
            collector.read(&fus_dir);
        }
        if let Err(error) = files::write_text(&run_dir.join("fus.jsonl"), &collector.text()) {
            facts.failure.get_or_insert(format!("{error:#}"));
        }
        facts.ide_exit = match exited {
            Some(ended) => ended,
            None => self.quit(ctx, &child, ide, run_dir, sandbox, &mut facts).await,
        };
        facts
    }

    /// Asks the running IDE to open `project`, and polls the trace for the highlighted editor.
    async fn open<F>(
        &self,
        ctx: &Ctx,
        ide: &mut F,
        run_dir: &Path,
        sandbox: &Sandbox,
        project: &Path,
        facts: &mut LaunchFacts,
    ) -> Option<Ended>
    where
        F: Future<Output = Result<String, ProcError>> + Unpin,
    {
        let mut options = sandbox_options(sandbox);
        options.push(property("idea.log.path", &run_dir.join("open-log")));
        let argv = match self.argv(&run_dir.join("open.args"), &options, &[project.display().to_string()]) {
            Ok(argv) => argv,
            Err(error) => {
                facts.failure = Some(format!("the open request: {error:#}"));
                return None;
            }
        };
        let request_us = now_us();
        facts.open_request_us = Some(request_us);
        // The request is a second JVM that hands the path to the running IDE and exits; what it answers is in its log.
        let (request_log, request_options) = (run_dir.join("open.log"), self.logged(QUIT_TIMEOUT, "bench_open_timeout"));
        let request = self.runner.checked_to_file(ctx, &argv, &request_log, &request_options);
        tokio::pin!(request);
        tokio::select! {
            _ = &mut request => {}
            result = &mut *ide => {
                facts.failure = Some(format!("the IDE exited ({}) before the project opened", describe(&result)));
                return Some(ended(&result));
            }
        }
        let deadline = Instant::now() + EVENT_TIMEOUT;
        loop {
            tokio::select! {
                result = &mut *ide => {
                    facts.failure = Some(format!("the IDE exited ({}) before the project opened", describe(&result)));
                    return Some(ended(&result));
                }
                alive = ctx.sleep(POLL * 5) => {
                    if !alive {
                        facts.failure = Some(interrupted_text());
                        return None;
                    }
                }
            }
            if highlighted(run_dir, Some(request_us)) {
                return None;
            }
            if Instant::now() >= deadline {
                facts.failure = Some(format!(
                    "no \"editor highlighting completed\" within {} s after the open request",
                    EVENT_TIMEOUT.as_secs()
                ));
                return None;
            }
        }
    }

    /// Quits the IDE with the `exit` command, then SIGTERM to its process group, then SIGKILL. An interrupted
    /// session skips the `exit` command: the interrupt already reached the group.
    async fn quit<F>(
        &self,
        ctx: &Ctx,
        child: &Ctx,
        mut ide: Pin<Box<F>>,
        run_dir: &Path,
        sandbox: &Sandbox,
        facts: &mut LaunchFacts,
    ) -> Ended
    where
        F: Future<Output = Result<String, ProcError>>,
    {
        if !ctx.is_cancelled() {
            let mut options = sandbox_options(sandbox);
            options.push(property("idea.log.path", &run_dir.join("exit-log")));
            if let Ok(argv) = self.argv(&run_dir.join("exit.args"), &options, &["exit".to_owned()]) {
                let (quit_log, quit_options) = (run_dir.join("exit.log"), self.logged(QUIT_TIMEOUT, "bench_exit_timeout"));
                let quit = self.runner.checked_to_file(ctx, &argv, &quit_log, &quit_options);
                tokio::pin!(quit);
                tokio::select! {
                    _ = &mut quit => {}
                    result = &mut ide => return ended(&result),
                }
            }
            if let Ok(result) = tokio::time::timeout(QUIT_TIMEOUT, &mut ide).await {
                return ended(&result);
            }
        }
        facts.terminated = true;
        child.token().cancel();
        if let Ok(result) = tokio::time::timeout(TERM_TIMEOUT, &mut ide).await {
            return ended(&result);
        }
        // The drop of the spawn sends SIGKILL to the IDE, and SIGTERM to the rest of its group.
        drop(ide);
        None
    }
}

fn interrupted_text() -> String {
    "the session was interrupted".to_owned()
}

/// Polls the FUS log for the welcome event. Answers how the IDE ended when it exited before the event.
async fn wait_for_welcome<F>(
    ctx: &Ctx,
    ide: &mut F,
    run_dir: &Path,
    fus_dir: &Path,
    collector: &mut fus::Collector,
    facts: &mut LaunchFacts,
) -> Option<Ended>
where
    F: Future<Output = Result<String, ProcError>> + Unpin,
{
    let deadline = Instant::now() + EVENT_TIMEOUT;
    let report = run_dir.join("startup-stats.json");
    let mut report_deadline: Option<Instant> = None;
    loop {
        tokio::select! {
            result = &mut *ide => {
                collector.read(fus_dir);
                facts.failure = Some(format!("the IDE exited ({}) before {}", describe(&result), fus::WELCOME_BECAME_VISIBLE));
                return Some(ended(&result));
            }
            alive = ctx.sleep(POLL) => {
                if !alive {
                    facts.failure = Some(interrupted_text());
                    return None;
                }
            }
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
    }
}

/// Polls the trace and the report for the highlighted editor. Answers how the IDE ended when it exited before the
/// event.
///
/// A prime run opens the file from the command line, not from the saved editor state. The report does not wait for
/// the highlighting of such an editor, so the event lands after the drain of the report and never reaches a file.
/// The prime run therefore waits for the report only. Its result counts for nothing.
async fn wait_for_highlighted<F>(ctx: &Ctx, ide: &mut F, run_dir: &Path, prime: bool, facts: &mut LaunchFacts) -> Option<Ended>
where
    F: Future<Output = Result<String, ProcError>> + Unpin,
{
    let deadline = Instant::now() + EVENT_TIMEOUT;
    let report = run_dir.join("startup-stats.json");
    let mut report_deadline: Option<Instant> = None;
    loop {
        tokio::select! {
            result = &mut *ide => {
                facts.failure = Some(format!("the IDE exited ({}) before \"{EDITOR_HIGHLIGHTED}\"", describe(&result)));
                return Some(ended(&result));
            }
            alive = ctx.sleep(POLL) => {
                if !alive {
                    facts.failure = Some(interrupted_text());
                    return None;
                }
            }
        }
        if highlighted(run_dir, None) {
            return None;
        }
        let now = Instant::now();
        if report_deadline.is_none() && report.exists() {
            if prime {
                return None;
            }
            report_deadline = Some(now + AFTER_REPORT_TIMEOUT);
        }
        if report_deadline.is_some_and(|limit| now >= limit) {
            facts.failure = Some(format!(
                "no \"{EDITOR_HIGHLIGHTED}\" within {} s after startup-stats.json",
                AFTER_REPORT_TIMEOUT.as_secs()
            ));
            return None;
        }
        if now >= deadline {
            facts.failure = Some(format!("no \"{EDITOR_HIGHLIGHTED}\" within {} s", EVENT_TIMEOUT.as_secs()));
            return None;
        }
    }
}

/// Polls the trace for the span `name`. Answers how the IDE ended when it exited before the span. A prime run waits
/// for the span too, so its sandbox holds the caches of a start that built what the span measures.
///
/// The IDE buffers the spans of its trace and writes them in chunks and at the exit. A span that ends late in the
/// start can therefore reach the file only at the exit. The wait thus also ends [`SPAN_SETTLE`] after
/// `startup-stats.json`, without a failure, and the record decides the gate from the closed trace. The quit ends a
/// build that has not finished, so the record also refuses a span that ended with an error or after the quit started.
async fn wait_for_span<F>(ctx: &Ctx, ide: &mut F, run_dir: &Path, name: &str, missing: &str, facts: &mut LaunchFacts) -> Option<Ended>
where
    F: Future<Output = Result<String, ProcError>> + Unpin,
{
    let deadline = Instant::now() + EVENT_TIMEOUT;
    let report = run_dir.join("startup-stats.json");
    let mut settle_deadline: Option<Instant> = None;
    loop {
        tokio::select! {
            result = &mut *ide => {
                facts.failure = Some(format!("the IDE exited ({}) before the {name} span ended", describe(&result)));
                return Some(ended(&result));
            }
            alive = ctx.sleep(POLL) => {
                if !alive {
                    facts.failure = Some(interrupted_text());
                    return None;
                }
            }
        }
        if span_ended(run_dir, name) {
            return None;
        }
        let now = Instant::now();
        if settle_deadline.is_none() && report.exists() {
            settle_deadline = Some(now + SPAN_SETTLE);
        }
        if settle_deadline.is_some_and(|limit| now >= limit) {
            return None;
        }
        if now >= deadline {
            facts.failure = Some(format!(
                "no {name} span and no startup-stats.json within {} s: {missing}",
                EVENT_TIMEOUT.as_secs()
            ));
            return None;
        }
    }
}

/// Tells whether the trace that the IDE wrote so far has the span `name`. The IDE writes a span when it ends, into a
/// buffer that reaches the file in chunks. A trace without the name is not parsed, so a poll of a large trace is cheap.
pub(crate) fn span_ended(run_dir: &Path, name: &str) -> bool {
    files::read_optional(&run_dir.join(TRACE_FILE))
        .ok()
        .flatten()
        .filter(|text| text.contains(name))
        .and_then(|text| trace::parse(&text).ok())
        .is_some_and(|trace| trace.first(name).is_some())
}

/// Tells whether the trace or the report that the IDE wrote so far has the highlighted editor at or after `from_us`.
/// `None` is the process start. A file without the event name is not parsed, so a poll of a large trace is cheap.
fn highlighted(run_dir: &Path, from_us: Option<i64>) -> bool {
    let read = |name: &str| files::read_optional(&run_dir.join(name)).ok().flatten();
    let stats_text = read("startup-stats.json").filter(|text| text.contains(EDITOR_HIGHLIGHTED));
    let trace_text = read(TRACE_FILE);
    if stats_text.is_none() && !trace_text.as_ref().is_some_and(|text| text.contains(EDITOR_HIGHLIGHTED)) {
        return false;
    }
    let trace = trace_text.and_then(|text| trace::parse(&text).ok());
    let stats = stats_text.and_then(|text| stats::parse(&text).ok());
    match from_us {
        Some(from_us) => trace.is_some_and(|trace| record::highlighted_at_us(&trace, stats.as_ref(), from_us).is_some()),
        None => record::highlighted_ms(trace.as_ref(), stats.as_ref()).is_some(),
    }
}

#[cfg(test)]
mod tests;
