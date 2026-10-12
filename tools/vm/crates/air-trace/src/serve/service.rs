//! The server as a machine service: one detached server for each machine, which stops when nobody uses it, and
//! which builds the docs site that it serves when the site is missing or older than its sources.

use std::collections::VecDeque;
use std::ffi::OsString;
use std::fs;
use std::io::{self, Write};
use std::path::{Path, PathBuf};
use std::process::Stdio;
use std::sync::Arc;
use std::sync::Mutex;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::time::{Duration, Instant, SystemTime};

use avl_base::sync::lock;
use avl_host_sys::Runner;
use avl_host_sys::private::{PrivateOpen, open_private_file};
use avl_host_sys::viewer::{DetachRequest, PROBE_TIMEOUT, Started, format_duration, probe_viewer, start_detached};
use tokio::io::{AsyncBufReadExt, BufReader};
use tokio_util::sync::CancellationToken;

use super::state::{BuildOutput, Server};
use crate::{Exit, Refusal, code, report};

// --- the idle stop -------------------------------------------------------------------------------------------

/// The requests in progress, an open event stream included, and the time the last one began or ended.
pub(crate) struct Activity {
    active: AtomicUsize,
    last: Mutex<Instant>,
}

impl Default for Activity {
    fn default() -> Self {
        Self {
            active: AtomicUsize::new(0),
            last: Mutex::new(Instant::now()),
        }
    }
}

/// One request in progress, until it is dropped.
pub(crate) struct Active(Arc<Server>);

impl Drop for Active {
    fn drop(&mut self) {
        let activity = &self.0.activity;
        *lock(&activity.last) = Instant::now();
        activity.active.fetch_sub(1, Ordering::SeqCst);
    }
}

impl Activity {
    pub(crate) fn begin(server: &Arc<Server>) -> Active {
        let activity = &server.activity;
        activity.active.fetch_add(1, Ordering::SeqCst);
        *lock(&activity.last) = Instant::now();
        Active(Arc::clone(server))
    }

    /// How long no request has been in progress, zero while one is.
    fn idle_for(&self) -> Duration {
        if self.active.load(Ordering::SeqCst) > 0 {
            return Duration::ZERO;
        }
        lock(&self.last).elapsed()
    }
}

impl Server {
    /// Cancels stop once no request has been in progress for `idle`, and returns when stop ends.
    pub(crate) async fn stop_when_idle(self: Arc<Self>, idle: Duration, stop: CancellationToken) {
        let period = (idle / 4).clamp(Duration::from_millis(10), Duration::from_secs(30));
        let mut check = tokio::time::interval_at(tokio::time::Instant::now() + period, period);
        loop {
            tokio::select! {
                () = stop.cancelled() => return,
                _ = check.tick() => {
                    if self.activity.idle_for() >= idle {
                        (self.settings.log)(&format!(
                            "air-trace serve: no request and no open event stream for {}; stopping",
                            format_duration(idle)
                        ));
                        stop.cancel();
                        return;
                    }
                }
            }
        }
    }
}

// --- the pid file --------------------------------------------------------------------------------------------

/// The pid file of a listening server. Dropping it removes the file when the file still names this process: a
/// server started later may have written its own.
pub(crate) struct PidFile {
    path: PathBuf,
    own: String,
}

impl PidFile {
    pub(crate) fn write(path: &Path) -> io::Result<Self> {
        let own = std::process::id().to_string();
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent)?;
        }
        open_private_file(path, PrivateOpen::Truncate)?.write_all(format!("{own}\n").as_bytes())?;
        Ok(Self {
            path: path.to_path_buf(),
            own,
        })
    }
}

impl Drop for PidFile {
    fn drop(&mut self) {
        if fs::read_to_string(&self.path).is_ok_and(|content| content.trim() == self.own) {
            let _ = fs::remove_file(&self.path);
        }
    }
}

// --- detaching -----------------------------------------------------------------------------------------------

/// Bounds how long `--detach` waits for the server it started to answer.
const DETACH_WAIT: Duration = Duration::from_secs(30);

const DETACH_POLL: Duration = Duration::from_millis(100);

/// Makes sure a server answers on the port, and returns when one does. A server that already answers is kept.
/// Otherwise [`start_detached`] starts `command` with the server's arguments, and this waits until the new server
/// answers.
pub(crate) fn detach(
    request: &DetachRequest,
    runtime_root: Result<PathBuf, Refusal>,
    runner: &Runner,
    command: &[OsString],
    stdout: &mut dyn Write,
    stderr: &mut dyn Write,
) -> u8 {
    match find_or_start(request, runtime_root, runner, command, stdout) {
        Ok(()) => Exit::Ok.code(),
        Err(refused) => report(stderr, "serve --detach", &refused),
    }
}

/// The work of [`detach`]: the line that names the server on `stdout`, or why no server answers.
fn find_or_start(
    request: &DetachRequest,
    runtime_root: Result<PathBuf, Refusal>,
    runner: &Runner,
    command: &[OsString],
    stdout: &mut dyn Write,
) -> Result<(), Refusal> {
    let address = avl_trace_tools::viewer::runs_url(request.port);
    // A line that cannot be written has nowhere else to go; the server answers all the same.
    if probe_viewer(request.port, PROBE_TIMEOUT) {
        let _ = writeln!(stdout, "air-trace serve: {address} (already running)");
        return Ok(());
    }
    let root = runtime_root.map_err(|refused| {
        Refusal::new(
            refused.code,
            Exit::Broken,
            format!("the controller's runtime root is unknown: {}", refused.message),
        )
    })?;
    let Started { mut child, log_path } = start_detached(runner, command, None, request, &root)
        .map_err(|refused| Refusal::new(refused.code, Exit::Broken, refused.message))?;
    let deadline = Instant::now() + DETACH_WAIT;
    loop {
        if probe_viewer(request.port, PROBE_TIMEOUT) {
            let _ = writeln!(stdout, "air-trace serve: {address}");
            return Ok(());
        }
        if let Ok(Some(_)) = child.try_wait() {
            // Another server that started at the same moment took the port. It answers now, or soon.
            for _ in 0..20 {
                if probe_viewer(request.port, PROBE_TIMEOUT) {
                    let _ = writeln!(stdout, "air-trace serve: {address}");
                    return Ok(());
                }
                std::thread::sleep(DETACH_POLL);
            }
            return Err(Refusal::new(
                "detach_server_exited",
                Exit::Broken,
                format!("the server exited; its output is in {}", log_path.display()),
            ));
        }
        if Instant::now() >= deadline {
            let _ = child.kill();
            let _ = child.wait();
            return Err(Refusal::new(
                "detach_timeout",
                Exit::Broken,
                format!(
                    "the server did not answer within {}; its output is in {}",
                    format_duration(DETACH_WAIT),
                    log_path.display()
                ),
            ));
        }
        std::thread::sleep(DETACH_POLL);
    }
}

// --- the site build ------------------------------------------------------------------------------------------

/// Where the site build is, for the placeholder page.
#[derive(Default)]
pub(crate) struct SiteBuild {
    pub running: bool,
    /// Why the last build failed.
    pub problem: Option<String>,
    /// The last lines of the build's output.
    pub output: VecDeque<String>,
}

/// How many lines of the build's output the placeholder page shows.
const SITE_BUILD_OUTPUT_LINES: usize = 30;

/// The directory of the site's sources, under its project directory.
pub(crate) const SITE_SOURCES_DIR: &str = "src";

impl SiteBuild {
    fn keep(&mut self, chunk: &str) {
        for line in chunk.trim_end_matches('\n').split('\n') {
            if !line.trim().is_empty() {
                self.output.push_back(line.to_owned());
            }
        }
        while self.output.len() > SITE_BUILD_OUTPUT_LINES {
            self.output.pop_front();
        }
    }
}

/// Why the site needs a build: its `index.html` is missing, or older than the newest file under the project's
/// sources. `None` when the site is current, or when there is no project to build it from.
pub(crate) fn site_needs_build(project: Option<&Path>, site: Option<&Path>) -> Option<&'static str> {
    let (project, site) = (project?, site?);
    if !project.join("package.json").exists() {
        return None;
    }
    let Ok(index) = fs::metadata(site.join("index.html")).and_then(|info| info.modified()) else {
        return Some("the site is not built");
    };
    let newest = walkdir::WalkDir::new(project.join(SITE_SOURCES_DIR))
        .into_iter()
        .filter_map(Result::ok)
        .filter(|entry| !entry.file_type().is_dir())
        .filter_map(|entry| entry.metadata().ok()?.modified().ok())
        .max()
        .unwrap_or(SystemTime::UNIX_EPOCH);
    (newest > index).then_some("the site is older than its sources")
}

impl Server {
    /// Builds the site in the background when it needs a build. It builds once for the server's life: a failed
    /// build stays failed until the server starts again, and the placeholder page says why.
    pub(crate) async fn build_site_once(self: Arc<Self>, cancel: CancellationToken) {
        let reason = self
            .blocking(|server| site_needs_build(server.settings.site_project.as_deref(), server.settings.site_dir.as_deref()))
            .await;
        let (Some(reason), Some(project)) = (reason, self.settings.site_project.clone()) else {
            return;
        };
        lock(&self.site_build).running = true;
        (self.settings.log)(&format!("air-trace serve: {reason}; building it in {}", project.display()));
        let server = Arc::clone(&self);
        let output: BuildOutput = Arc::new(move |chunk: &str| {
            (server.settings.log)(chunk.trim_end_matches('\n'));
            lock(&server.site_build).keep(chunk);
        });
        let built = (self.settings.build_site)(project, output, cancel).await;
        {
            let mut state = lock(&self.site_build);
            state.running = false;
            if let Err(problem) = &built {
                state.problem = Some(problem.message.clone());
            }
        }
        match built {
            Ok(()) => (self.settings.log)("air-trace serve: the site is built"),
            Err(problem) => (self.settings.log)(&format!("air-trace serve: the site build failed: {problem}")),
        }
    }
}

// --- the site build ------------------------------------------------------------------------------------------

/// The Bazel labels of the node and the pnpm the site is built with; `//:MODULE.bazel` pins their versions.
pub(crate) const SITE_NODE_LABEL: &str = "//build:air_docs_node";
pub(crate) const SITE_PNPM_LABEL: &str = "//build:air_docs_pnpm";
/// Name the site's node and pnpm directly, together. For a server started outside a checkout, and for a test.
pub(crate) const SITE_NODE_VARIABLE: &str = "AIR_TRACE_NODE";
pub(crate) const SITE_PNPM_VARIABLE: &str = "AIR_TRACE_PNPM";
/// The checkout the wrapper ran from, which it exports and a detached server inherits; `bazel.cmd` is there.
const CHECKOUT_VARIABLE: &str = "BUILD_WORKSPACE_DIRECTORY";
const BAZEL_WRAPPER: &str = "bazel.cmd";
/// The options of every Bazel call here. `trace.cmd` builds with `--config=air-lane-linux`, and Bazel keys its
/// analysis cache on build options, so a call without it would discard the analysis the last lane build made; see
/// `host_bazel_argv` in the `lane` module of the controller. The convenience symlinks of a working copy that several sessions share are left
/// alone, and Bazel's progress is kept out of the log.
const BAZEL_OPTIONS: [&str; 6] = [
    "--color=no",
    "--curses=no",
    "--noshow_progress",
    "--ui_event_filters=-info",
    "--config=air-lane-linux",
    "--experimental_convenience_symlinks=ignore",
];

/// What the site is built with.
#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) enum SiteTools {
    /// A node and a pnpm named by their paths: the ones Bazel declares, or the two variables'.
    Pinned { node: PathBuf, pnpm: PathBuf },
    /// The `pnpm` on the PATH, and whatever node it finds: a server started outside a checkout.
    Path,
}

/// One step of the site build.
#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) struct SiteStep {
    /// Names the step in the log and in a failure, as the command a person would type.
    pub label: String,
    pub program: PathBuf,
    pub args: Vec<OsString>,
    /// Put first on the child's `PATH`: the directory of the pinned node, so that pnpm's scripts, which start
    /// with `#!/usr/bin/env node`, run that node and not another one.
    pub path_prefix: Option<PathBuf>,
}

/// The pnpm a server started outside a checkout runs from the PATH. On Windows the npm shim is `pnpm.cmd`, and a
/// spawn finds a `.cmd` on the PATH only by its full name.
pub(crate) const PATH_PNPM: &str = if cfg!(windows) { "pnpm.cmd" } else { "pnpm" };

/// The steps that build the site: `pnpm install --frozen-lockfile` when `install` says the dependencies are
/// missing, then `pnpm build`, which harvests the model and runs Vite.
pub(crate) fn site_steps(tools: &SiteTools, project: &Path, install: bool) -> Vec<SiteStep> {
    let (program, path_prefix) = match tools {
        SiteTools::Pinned { node, pnpm } => (pnpm.clone(), node.parent().map(Path::to_path_buf)),
        SiteTools::Path => (PathBuf::from(PATH_PNPM), None),
    };
    let step = |verb: &[&str]| SiteStep {
        label: format!("pnpm --dir {} {}", project.display(), verb.join(" ")),
        program: program.clone(),
        args: [OsString::from("--dir"), project.as_os_str().to_owned()]
            .into_iter()
            .chain(verb.iter().map(OsString::from))
            .collect(),
        path_prefix: path_prefix.clone(),
    };
    let mut steps = Vec::new();
    if install {
        steps.push(step(&["install", "--frozen-lockfile"]));
    }
    steps.push(step(&["build"]));
    steps
}

/// The site build with the tools the environment provides. The dependencies are installed first when the
/// project has no `node_modules`.
pub(crate) async fn build_site(project: PathBuf, output: BuildOutput, cancel: CancellationToken) -> Result<(), Refusal> {
    let tools = site_tools(&output, &cancel).await?;
    let install = !project.join("node_modules").exists();
    build_site_with(&tools, &project, install, &output, &cancel).await
}

/// Runs [`site_steps`] in order, and stops at the first that fails.
pub(crate) async fn build_site_with(
    tools: &SiteTools,
    project: &Path,
    install: bool,
    output: &BuildOutput,
    cancel: &CancellationToken,
) -> Result<(), Refusal> {
    match tools {
        SiteTools::Pinned { node, pnpm } => output(&format!(
            "air-trace serve: building the site with {} and {}\n",
            node.display(),
            pnpm.display()
        )),
        SiteTools::Path => output("air-trace serve: building the site with the pnpm on the PATH\n"),
    }
    for step in site_steps(tools, project, install) {
        output(&format!("$ {}\n", step.label));
        run_step(&step, output, cancel).await?;
    }
    Ok(())
}

/// Where the site's node and pnpm come from, in this order: the two variables, when both are set; Bazel, in the
/// checkout `BUILD_WORKSPACE_DIRECTORY` names; the `pnpm` on the PATH, for a server started outside a checkout.
pub(crate) async fn site_tools(output: &BuildOutput, cancel: &CancellationToken) -> Result<SiteTools, Refusal> {
    let variable = |name: &str| std::env::var_os(name).filter(|value| !value.is_empty()).map(PathBuf::from);
    match (variable(SITE_NODE_VARIABLE), variable(SITE_PNPM_VARIABLE)) {
        (Some(node), Some(pnpm)) => return Ok(SiteTools::Pinned { node, pnpm }),
        (None, None) => {}
        (node, _) => {
            let (set, unset) = if node.is_some() {
                (SITE_NODE_VARIABLE, SITE_PNPM_VARIABLE)
            } else {
                (SITE_PNPM_VARIABLE, SITE_NODE_VARIABLE)
            };
            return Err(Refusal::new(
                "site_tools_half_set",
                Exit::Usage,
                format!("{set} is set and {unset} is not; the two name the site's node and pnpm together"),
            ));
        }
    }
    let Some(checkout) = variable(CHECKOUT_VARIABLE) else {
        return Ok(SiteTools::Path);
    };
    if !checkout.join(BAZEL_WRAPPER).is_file() {
        return Ok(SiteTools::Path);
    }
    let node = bazel_file(&checkout, SITE_NODE_LABEL, &["node", "node.exe"], output, cancel).await?;
    let pnpm = bazel_file(&checkout, SITE_PNPM_LABEL, &["pnpm", "pnpm.exe"], output, cancel).await?;
    Ok(SiteTools::Pinned { node, pnpm })
}

/// The one file of a Bazel target, by `cquery --output=files`, which fetches its archive on first use. `names`
/// are the file names it may have. Bazel's own output goes to `output`, so a fetch in progress shows on the
/// placeholder page.
async fn bazel_file(
    checkout: &Path,
    label: &str,
    names: &[&str],
    output: &BuildOutput,
    cancel: &CancellationToken,
) -> Result<PathBuf, Refusal> {
    let listed = bazel(checkout, &["cquery", "--output=files", label], output, cancel).await?;
    let Some(line) = listed.lines().map(str::trim).find(|line| {
        Path::new(line)
            .file_name()
            .is_some_and(|name| names.iter().any(|want| *want == name))
    }) else {
        return Err(build_failed(format!(
            "bazel cquery --output=files {label} named no {}: {listed:?}",
            names.join(" or ")
        )));
    };
    let path = Path::new(line);
    if path.is_absolute() {
        return Ok(path.to_path_buf());
    }
    // A source file is listed as `external/<repo>/...`, relative to the execution root. The execution root links a
    // repository only once an action used it, and nothing builds these; the output base holds every fetched
    // repository under the same `external/<repo>` path.
    let root = bazel(checkout, &["info", "output_base"], output, cancel).await?;
    Ok(Path::new(root.trim()).join(path))
}

/// Runs the checkout's `bazel.cmd` with [`BAZEL_OPTIONS`] and answers its stdout; its stderr goes to `output`.
async fn bazel(checkout: &Path, args: &[&str], output: &BuildOutput, cancel: &CancellationToken) -> Result<String, Refusal> {
    let (command, rest) = args.split_first().expect("a bazel command");
    let label = format!("bazel {}", args.join(" "));
    let mut process = tokio::process::Command::new(checkout.join(BAZEL_WRAPPER));
    process
        .current_dir(checkout)
        .arg(command)
        .args(BAZEL_OPTIONS)
        .args(rest)
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .kill_on_drop(true);
    let mut child = process.spawn().map_err(|error| build_failed(format!("{label}: {error}")))?;
    let stdout = child.stdout.take();
    let err = forward_lines(child.stderr.take().map(|stream| Box::new(stream) as _), output);
    let out = tokio::spawn(async move {
        let mut text = String::new();
        if let Some(stream) = stdout {
            let mut lines = BufReader::new(stream).lines();
            while let Ok(Some(line)) = lines.next_line().await {
                text.push_str(&line);
                text.push('\n');
            }
        }
        text
    });
    let status = wait_or_cancel(&mut child, cancel, &label).await?;
    let text = out.await.unwrap_or_default();
    let _ = err.await;
    if !status.success() {
        return Err(build_failed(format!("{label}: {status}")));
    }
    Ok(text)
}

/// Runs one step with its output forwarded, and fails with the step's label and its exit status.
async fn run_step(step: &SiteStep, output: &BuildOutput, cancel: &CancellationToken) -> Result<(), Refusal> {
    let mut command = tokio::process::Command::new(&step.program);
    command
        .args(&step.args)
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .kill_on_drop(true);
    if let Some(prefix) = &step.path_prefix {
        let rest = std::env::var_os("PATH").unwrap_or_default();
        let entries = std::iter::once(prefix.clone()).chain(std::env::split_paths(&rest));
        let path = std::env::join_paths(entries)
            .map_err(|error| build_failed(format!("{}: cannot put {} on the PATH: {error}", step.label, prefix.display())))?;
        command.env("PATH", path);
    }
    let mut child = command.spawn().map_err(|error| match (error.kind(), &step.path_prefix) {
        (io::ErrorKind::NotFound, None) => Refusal::new("site_pnpm_missing", Exit::Broken, "pnpm is not on the PATH of the server"),
        _ => build_failed(format!("{}: {}: {error}", step.label, step.program.display())),
    })?;
    let out = forward_lines(child.stdout.take().map(|stream| Box::new(stream) as _), output);
    let err = forward_lines(child.stderr.take().map(|stream| Box::new(stream) as _), output);
    let status = wait_or_cancel(&mut child, cancel, &step.label).await?;
    let _ = out.await;
    let _ = err.await;
    if !status.success() {
        return Err(build_failed(format!("{}: {status}", step.label)));
    }
    Ok(())
}

/// A step of the site build that failed.
fn build_failed(message: String) -> Refusal {
    Refusal::new(code::SITE_BUILD_FAILED, Exit::Broken, message)
}

/// Forwards a child's stream to `output`, a line at a time.
fn forward_lines(stream: Option<Box<dyn tokio::io::AsyncRead + Send + Unpin>>, output: &BuildOutput) -> tokio::task::JoinHandle<()> {
    let output = Arc::clone(output);
    tokio::spawn(async move {
        let Some(stream) = stream else {
            return;
        };
        let mut lines = BufReader::new(stream).lines();
        while let Ok(Some(line)) = lines.next_line().await {
            output(&line);
        }
    })
}

/// The child's exit status, unless the server stops first.
async fn wait_or_cancel(
    child: &mut tokio::process::Child,
    cancel: &CancellationToken,
    label: &str,
) -> Result<std::process::ExitStatus, Refusal> {
    tokio::select! {
        status = child.wait() => status.map_err(|error| build_failed(format!("{label}: {error}"))),
        () = cancel.cancelled() => Err(Refusal::new("site_build_cancelled", Exit::Broken, "the server stopped before the site was built")),
    }
}
