//! The IDE processes of a session: the Starter-shaped command line over a generation, and the supervision of one run.
//!
//! The command line is the one the IDE Starter gives a locally built distribution (`IdeFromCodeInstaller`):
//! `<java> @<argfile>`, where the argfile holds the JVM options of the distribution (the vmoptions file that
//! `product-info.json` names, `-Djb.vmOptionsFile` and the `additionalJvmArguments`), the test options of the Starter, the
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

use anyhow::{Context, anyhow, bail};
use avl_base::{Exit, Refusal};
use avl_host_sys::{Ctx, ProcError, Runner, SpawnOptions};
use serde::Deserialize;

use super::arm::{Arm, ArmKind, Gate};
use super::files;
use super::fus;
use super::profile::{GATE_PROFILE, ProfileEvent, WHOLE_PROFILE};
use super::record::{self, EDITOR_HIGHLIGHTED, LaunchFacts, RunKind, THREAD_DUMP_FILE};
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
/// How long the stop of the profiler can take: a `jcmd` JVM and the dump of the profile.
const PROFILE_STOP_TIMEOUT: Duration = Duration::from_secs(30);

/// The JVM option that lets `jcmd` load the profiler library into the running IDE, which stops the profiler at the
/// gate. Without it, a JVM warns about the load or refuses it.
pub(crate) const DYNAMIC_AGENT_OPTION: &str = "-XX:+EnableDynamicAgentLoading";

/// The log of the `jcmd` that stops the profiler, in the run directory.
const PROFILE_STOP_LOG: &str = "profile-stop.log";

/// The profiler of a session: the async-profiler library and the event that it samples.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Profiler {
    pub(crate) library: PathBuf,
    pub(crate) event: ProfileEvent,
}

/// The option that comes after every JVM option of the distribution: `-da` wins over the `-ea` of the dev
/// `idea.vmoptions`, so the run measures the code a release runs.
pub(crate) const FIDELITY_FLAG: &str = "-da";

/// The property that makes a run an integration test, as the IDE Starter makes every run
/// (`IDERunContext.calculateVmOptions`). The IDE skips what it offers a person in such a run. Air, for one, offers its
/// welcome page in a project window unless the property is set. The page is an editor tab that takes the selected tab
/// from the file, so the daemon never highlights the file, and the gate of the `project` arm never fires.
pub(crate) const INTEGRATION_TEST_PROPERTY: &str = "idea.is.integration.test=true";

/// The properties of every measured start, whatever its arm. With [`INTEGRATION_TEST_PROPERTY`], a welcome run starts
/// the IDE that the perf test of the welcome screen starts.
const MEASURE_PROPERTIES: [&str; 8] = [
    "idea.record.classpath.info=true",
    "idea.record.classloading.stats=true",
    "nosplash=true",
    "jb.consents.confirmation.enabled=false",
    "ide.newUsersOnboarding=false",
    "idea.initially.ask.config=never",
    "fus.internal.test.mode=true",
    INTEGRATION_TEST_PROPERTY,
];

/// The property that forces the modal welcome screen.
pub(crate) const FORCE_MODAL_PROPERTY: &str = "idea.force.disable.non.modal.welcome.screen=true";

/// The property of the `project` and `empty-editor` arms: the project of the command line is trusted, so no trust
/// dialog stops a run.
pub(crate) const TRUST_PROPERTY: &str = "idea.trust.all.projects=true";

/// The properties of the cargo-sync arm that the context of `RustRoverIndexingTest` sets (`rustRoverVmOptions`) and
/// that act before the first Cargo sync: no AI promotion window, and no external linter on the fly.
pub(crate) const CARGO_SYNC_PROPERTIES: [&str; 2] = [
    "llm.show.ai.promotion.window.on.start=false",
    "org.rust.external.linter.on.the.fly=false",
];

/// The property of a twin arm, see [`Arm::suppressed`]. The IDE reads it as a list of plugin ids at the start and loads
/// none of them.
pub(crate) const SUPPRESSED_PLUGINS_PROPERTY: &str = "idea.suppressed.plugins.id";

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

/// The options that make the JVM write its log, the class-load log among it, from a thread of its own. A flush of each
/// class line under the global log lock costs a start about half a second of CPU over all its threads, the EDT too. The
/// default mode, `drop`, loses a line when the buffer is full. In the `stall` mode a full buffer makes the logging
/// thread wait instead, so the class count stays exact. The buffer holds the class log of a whole start, so the wait
/// stays rare.
pub(crate) const ASYNC_LOG_OPTIONS: [&str; 2] = ["-Xlog:async:stall", "-XX:AsyncLogBufferSize=32M"];

/// The option that makes the JVM write [`CLASS_LOAD_LOG`] with the uptime and the thread of each class, and the start
/// of each thread. The `-Xlog` grammar ends the file name at a `:`, so a run directory with a `:` in its path is an
/// error that names it. A space is no problem, because the argfile quotes it.
pub(crate) fn class_load_option(run_dir: &Path) -> Result<String, String> {
    let log = run_dir.join(CLASS_LOAD_LOG);
    let path = log.display().to_string();
    if path.contains(':') {
        return Err(format!(
            "the run directory {} holds a `:`, which ends the file name of -Xlog:class+load; use a session directory without one",
            run_dir.display()
        ));
    }
    Ok(format!("-Xlog:class+load,os+thread:file={path}:uptime,tid,tags"))
}

/// The options of a measured start, in order: the sandbox, the files of the run, the asynchronous log, see
/// [`ASYNC_LOG_OPTIONS`], the class-load log, the properties, the arm, the suppressed plugin of a twin and the
/// profiler. A run directory that the class-load log cannot name is an error, see [`class_load_option`]. The profiler
/// starts with the JVM and writes [`WHOLE_PROFILE`] at the exit, unless the run stops it at the gate.
pub(crate) fn measure_options(run_dir: &Path, sandbox: &Sandbox, arm: &Arm, profiler: Option<&Profiler>) -> Result<Vec<String>, String> {
    let mut options = sandbox_options(sandbox);
    options.extend([
        property("idea.log.path", &run_dir.join("log")),
        property("idea.log.perf.stats.file", &run_dir.join("startup-stats.json")),
        property("idea.diagnostic.opentelemetry.file", &run_dir.join(TRACE_FILE)),
        property("idea.log.class.list.file", &run_dir.join("class-report.txt")),
        property("plugin.classloader.debug", &run_dir.join("plugin-classes.txt")),
    ]);
    options.extend(ASYNC_LOG_OPTIONS.map(str::to_owned));
    options.push(class_load_option(run_dir)?);
    options.extend(MEASURE_PROPERTIES.iter().map(|property| format!("-D{property}")));
    match arm.kind {
        ArmKind::Modal => options.push(format!("-D{FORCE_MODAL_PROPERTY}")),
        ArmKind::Project | ArmKind::EmptyEditor => options.push(format!("-D{TRUST_PROPERTY}")),
        ArmKind::CargoSync => {
            options.push(format!("-D{TRUST_PROPERTY}"));
            options.extend(CARGO_SYNC_PROPERTIES.iter().map(|property| format!("-D{property}")));
        }
        ArmKind::NonModal | ArmKind::OpenProject => {}
    }
    if let Some(plugin) = &arm.suppressed {
        options.push(format!("-D{SUPPRESSED_PLUGINS_PROPERTY}={plugin}"));
    }
    if let Some(profiler) = profiler {
        options.push(DYNAMIC_AGENT_OPTION.to_owned());
        options.push(format!(
            "-agentpath:{}=start,event={},interval=1ms,threads,collapsed,file={}",
            profiler.library.display(),
            profiler.event.option(),
            run_dir.join(WHOLE_PROFILE).display()
        ));
    }
    Ok(options)
}

/// The argv that stops the profiler of the IDE `pid` and writes its profile to `file`:
/// `jcmd <pid> JVMTI.agent_load <library> stop,file=<file>,collapsed`. The second load of the library reaches the
/// agent that `-agentpath` started. `jcmd` joins its arguments into one command, which the JVM splits at the spaces
/// outside quotes, so the path and the options are quoted.
pub(crate) fn profile_stop_argv(jcmd: &Path, pid: u32, library: &Path, file: &Path) -> Vec<String> {
    vec![
        jcmd.display().to_string(),
        pid.to_string(),
        "JVMTI.agent_load".to_owned(),
        format!("\"{}\"", library.display()),
        format!("\"stop,file={},collapsed\"", file.display()),
    ]
}

/// The argv that writes the thread dump of the IDE `pid`: `jcmd <pid> Thread.print`.
pub(crate) fn thread_dump_argv(jcmd: &Path, pid: u32) -> Vec<String> {
    vec![jcmd.display().to_string(), pid.to_string(), "Thread.print".to_owned()]
}

/// The PID of the IDE: the line `PID: <pid>` that `logEssentialInfoAboutIde` writes into `idea.log` at the start. A
/// log without the line has none.
pub(crate) fn idea_log_pid(log: &str) -> Option<u32> {
    log.lines()
        .find_map(|line| line.split_once(" - PID: ").and_then(|(_, pid)| pid.trim().parse().ok()))
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
        match (&self.project, self.arm.kind, self.kind) {
            (Some(project), ArmKind::Project, RunKind::Prime) => {
                vec![project.dir.display().to_string(), project.file.display().to_string()]
            }
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

/// The place of `product-info.json` in a production distribution on this OS, relative to the home.
const PRODUCT_INFO_FILE: &str = if cfg!(target_os = "macos") {
    "Resources/product-info.json"
} else {
    "product-info.json"
};

/// The `product-info.json` of the distribution at `dist`, as `DevLaunchProperties.kt` finds it.
///
/// The first candidate is [`PRODUCT_INFO_FILE`]. The second is `bin/product-info.json`, the place in an older dev
/// distribution. When neither file exists, the answer is the first candidate, so that the read error names it.
fn product_info_file(dist: &Path) -> PathBuf {
    let production = dist.join(PRODUCT_INFO_FILE);
    if production.is_file() {
        return production;
    }
    let dev_build = dist.join("bin").join("product-info.json");
    if dev_build.is_file() { dev_build } else { production }
}

/// The main vmoptions file of the distribution at `dist`, as the launcher finds it.
///
/// It is `vm_options_file_path`, the `vmOptionsFilePath` of the first launch, relative to the directory of
/// [`PRODUCT_INFO_FILE`]. The `bin/product-info.json` of an older dev distribution holds the text of the production place,
/// so the same base applies to it. The path resolves by its text, and a path that leaves `dist` fails.
fn vm_options_file(dist: &Path, vm_options_file_path: &str) -> anyhow::Result<PathBuf> {
    let leaves = || anyhow!("the vmOptionsFilePath '{vm_options_file_path}' is not a file of {}", dist.display());
    if vm_options_file_path.starts_with('/') || vm_options_file_path.contains('\\') {
        return Err(leaves());
    }
    let mut parts: Vec<&str> = PRODUCT_INFO_FILE.split('/').collect();
    parts.pop();
    for part in vm_options_file_path.split('/') {
        match part {
            "" | "." => {}
            ".." => {
                parts.pop().ok_or_else(leaves)?;
            }
            _ => parts.push(part),
        }
    }
    if parts.is_empty() {
        return Err(leaves());
    }
    Ok(parts.iter().fold(dist.to_path_buf(), |path, part| path.join(part)))
}

/// The JVM options of a distribution, as `readVmOptions` of the dev launch reads them, then the test options of the
/// Starter and the platform prefix of the config.
///
/// One test option differs from the Starter: `idea.is.internal` is `true`. The FUS validator reads the test scheme of
/// the sandbox only in an internal application (`StatisticsRecorderUtil.isAnyTestModeEnabled`), and without it the
/// welcome events of the gate arrive as `validation.unmatched_rule`. The dev launcher of the BT version passed `true`
/// too.
pub(crate) fn dist_options(dist: &Path, config: &DistConfig, repo_root: &Path) -> anyhow::Result<Vec<String>> {
    #[derive(Deserialize)]
    #[serde(rename_all = "camelCase")]
    struct ProductInfo {
        launch: Vec<LaunchData>,
    }
    #[derive(Deserialize)]
    #[serde(rename_all = "camelCase")]
    struct LaunchData {
        vm_options_file_path: String,
        #[serde(default)]
        additional_jvm_arguments: Vec<String>,
    }
    let product_info_path = product_info_file(dist);
    let product_info: ProductInfo = serde_json::from_str(&files::read_text(&product_info_path)?)
        .with_context(|| format!("{} has no `launch` list with a vmOptionsFilePath", product_info_path.display()))?;
    let Some(launch) = product_info.launch.first() else {
        bail!("{} states no launch", product_info_path.display());
    };

    let vm_options_file = vm_options_file(dist, &launch.vm_options_file_path)?;
    let mut options: Vec<String> = files::read_text(&vm_options_file)?
        .lines()
        .map(str::trim)
        .filter(|line| !line.is_empty() && !line.starts_with('#'))
        .map(str::to_owned)
        .collect();
    options.push(property("jb.vmOptionsFile", &vm_options_file));
    let home = dist.display().to_string();
    options.extend(
        launch
            .additional_jvm_arguments
            .iter()
            .map(|argument| argument.replace(home_macro(), &home)),
    );

    let dist_name = dist.file_name().map(|name| name.to_string_lossy().into_owned()).unwrap_or_default();
    options.extend([
        "-Didea.use.dev.build.server=true".to_owned(),
        "-Didea.ui.icons.svg.disk.cache=false".to_owned(),
        "-Didea.is.internal=true".to_owned(),
        format!("-Ddev.build.dir={dist_name}"),
        property("idea.dev.project.root", repo_root),
        property("idea.home.path", dist),
        property("idea.properties.file", &dist.join("bin").join("idea.properties")),
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
    pub(crate) profiler: Option<Profiler>,
    pub(crate) hold: Duration,
}

impl Launcher {
    /// The launcher of a generation. A generation of another shape is a refusal that names the file.
    pub(crate) fn of(
        generation: &Generation,
        runner: Runner,
        repo_root: &Path,
        profiler: Option<Profiler>,
        hold: Duration,
    ) -> Result<Self, Refusal> {
        let refuse = |error: anyhow::Error| {
            Refusal::new(
                "bench_dist_unsupported",
                Exit::USAGE,
                format!("the generation {} cannot start: {error:#}", generation.root.display()),
            )
        };
        let dist_options = dist_options(&generation.home, &generation.config, repo_root).map_err(refuse)?;
        let opened_packages = files::read_text(&generation.root.join(OPENED_PACKAGES_FILE)).map_err(refuse)?;
        let classpath = files::read_text(&generation.home.join("core-classpath.txt")).map_err(refuse)?;
        let entry_options = entry_options(&generation.home, &opened_packages, &classpath, &generation.config.main_class).map_err(refuse)?;
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
        let options = match measure_options(run_dir, sandbox, &plan.arm, self.profiler.as_ref()) {
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
            Gate::LogLine { line, missing, .. } => wait_for_log_line(ctx, &mut ide, run_dir, line, missing, &mut facts).await,
        };
        facts.waited_ms = u64::try_from(started.elapsed().as_millis()).unwrap_or(u64::MAX);
        if exited.is_none() && facts.failure.is_none() {
            facts.thread_dump_failure = self.dump_threads(ctx, run_dir).await.err();
        }
        if exited.is_none()
            && facts.failure.is_none()
            && let Some(profiler) = &self.profiler
        {
            facts.profile_stop_failure = self.stop_profile(ctx, run_dir, profiler).await.err();
        }
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

    /// Stops the profiler of the running IDE when the run passed its gate, so [`GATE_PROFILE`] holds the start-up and
    /// not the hold and the quit. Answers why it could not: no `jcmd` in the JBR of the generation, no PID in
    /// `log/idea.log`, or a `jcmd` that wrote no profile. The run then keeps [`WHOLE_PROFILE`].
    async fn stop_profile(&self, ctx: &Ctx, run_dir: &Path, profiler: &Profiler) -> Result<(), String> {
        let jcmd = self.java.with_file_name(if cfg!(windows) { "jcmd.exe" } else { "jcmd" });
        if !jcmd.is_file() {
            return Err("the JBR of the generation has no bin/jcmd".to_owned());
        }
        let log = files::read_optional(&run_dir.join("log").join("idea.log"))
            .ok()
            .flatten()
            .unwrap_or_default();
        let pid = idea_log_pid(&log).ok_or_else(|| "log/idea.log names no PID".to_owned())?;
        let file = run_dir.join(GATE_PROFILE);
        let argv = profile_stop_argv(&jcmd, pid, &profiler.library, &file);
        let result = self
            .runner
            .checked_to_file(
                ctx,
                &argv,
                &run_dir.join(PROFILE_STOP_LOG),
                &self.logged(PROFILE_STOP_TIMEOUT, "bench_profile_stop_timeout"),
            )
            .await;
        if file.is_file() {
            return Ok(());
        }
        Err(format!(
            "jcmd wrote no {GATE_PROFILE} ({}); see {PROFILE_STOP_LOG}",
            describe(&result)
        ))
    }

    /// Writes [`THREAD_DUMP_FILE`] with `jcmd <pid> Thread.print` after the gate. The dump names the Java threads by
    /// the tid of the class-load log, the EDT among them, which the log names only when a thread ends. It runs before
    /// the stop of the profile, and both take their time from the hold. The timeout of the profile stop bounds it.
    /// Answers why it could not: no `jcmd`, no PID, or a `jcmd` that failed or timed out. The record makes that a note
    /// of the run, and the classes of the run then have no thread.
    async fn dump_threads(&self, ctx: &Ctx, run_dir: &Path) -> Result<(), String> {
        let jcmd = self.java.with_file_name(if cfg!(windows) { "jcmd.exe" } else { "jcmd" });
        if !jcmd.is_file() {
            return Err("the JBR of the generation has no bin/jcmd".to_owned());
        }
        let log = files::read_optional(&run_dir.join("log").join("idea.log"))
            .ok()
            .flatten()
            .unwrap_or_default();
        let pid = idea_log_pid(&log).ok_or_else(|| "log/idea.log names no PID".to_owned())?;
        let file = run_dir.join(THREAD_DUMP_FILE);
        let result = self
            .runner
            .checked_to_file(
                ctx,
                &thread_dump_argv(&jcmd, pid),
                &file,
                &self.logged(PROFILE_STOP_TIMEOUT, "bench_thread_dump_timeout"),
            )
            .await;
        match result {
            Ok(_) => Ok(()),
            Err(_) => Err(format!("jcmd Thread.print failed ({}); see {THREAD_DUMP_FILE}", describe(&result))),
        }
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

/// Waits for the first line of `log/idea.log` that holds `line`, an exit of the IDE, an interrupt or the timeout. An
/// exit, an interrupt and the timeout set the failure.
async fn wait_for_log_line<F>(ctx: &Ctx, ide: &mut F, run_dir: &Path, line: &str, missing: &str, facts: &mut LaunchFacts) -> Option<Ended>
where
    F: Future<Output = Result<String, ProcError>> + Unpin,
{
    let deadline = Instant::now() + EVENT_TIMEOUT;
    let log = run_dir.join("log").join("idea.log");
    loop {
        tokio::select! {
            result = &mut *ide => {
                facts.failure = Some(format!("the IDE exited ({}) before the line \"{line}\" of log/idea.log", describe(&result)));
                return Some(ended(&result));
            }
            alive = ctx.sleep(POLL) => {
                if !alive {
                    facts.failure = Some(interrupted_text());
                    return None;
                }
            }
        }
        if files::read_optional(&log).ok().flatten().is_some_and(|text| text.contains(line)) {
            return None;
        }
        if Instant::now() >= deadline {
            facts.failure = Some(format!(
                "no line \"{line}\" in log/idea.log within {} s: {missing}",
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
