//! `air-trace serve`: the local server behind the trace viewer.
//!
//! It finds bundles in directories and zips on this machine, serves them with byte ranges, tells the viewer when
//! runs change, answers the planner, and serves the built docs site. It listens on 127.0.0.1 only, on
//! [avl_trace_tools::viewer::DEFAULT_SERVE_PORT] unless told otherwise.
//!
//! # The routes
//!
//! The docs site's `server.ts`, `runJournal.ts`, `controllerRuns.ts` and `router.ts` are the other half of each.
//!
//! - `GET /__air/runs`: every bundle found, grouped by run, the controller's runs from their journals, and the
//!   roots it looked in.
//! - `GET /__air/run/<runId>?from=<offset>`: one controller run's summary and its journal's records from the
//!   offset on.
//! - `GET /__air/bundle/<id>/` lists one bundle's files; `GET /__air/bundle/<id>/<path>` serves one of them, with
//!   `Range`. The id is opaque and stable: the same bundle at the same place has the same id across restarts, so a
//!   viewer URL survives one.
//! - `GET /__air/events`: Server-Sent Events, `runs-changed`, `bundle-append` and `run-appended`.
//! - `POST /__air/plan`: the planner over `{text}`, or over a dropped file sent as its raw bytes with `?fileName=`,
//!   joined with the bundles already on disk for the scenarios it names.
//! - `/air/...`: the docs site from `out/air-site`, with the single-page fallback to `index.html`; `/` redirects to
//!   `/air/runs`. A site that is missing, or older than `plugins/air/docs/src`, is built once in the background, and
//!   a placeholder page says so meanwhile.
//!
//! # One server for each machine
//!
//! `--detach` starts the server in a new session and returns when it answers, or at once when one already answers
//! on the port. A detached server writes to `<runtime root>/viewer/serve.log`, keeps its process id in `serve.pid`
//! beside it, and stops after 30 minutes with no request and no open event stream (`--idle-exit`). Every answer
//! carries [avl_trace_tools::viewer::IDENTITY_HEADER], which is how [avl_host_sys::viewer::probe_viewer] knows the
//! server. The controller starts a server for a run in a terminal through [avl_host_sys::viewer::start_detached], the
//! start `--detach` makes, and does not wait for it.
//!
//! # What it refuses
//!
//! Every file it serves is realpath-checked against the roots it was given, and anything outside them is 403, a
//! symbolic link out of a root included. It answers only requests addressed to a loopback name, which is what stops
//! a DNS-rebinding page from reading bundles through the browser, and it refuses a request the browser marks
//! `cross-site`.

mod events;
mod files;
mod http;
mod journals;
mod service;
mod state;

use std::ffi::OsString;
use std::fs::File;
use std::io::{self, Read, Seek, SeekFrom};
use std::path::PathBuf;
use std::process::Command;
use std::sync::Arc;
use std::time::Duration;

use avl_host_sys::viewer::{DETACH_IDLE_DEFAULT, DetachRequest, format_duration};
use avl_host_sys::{Interrupts, Runner};
use avl_trace_tools::discover::{Root, RootKind, VM_RUNS_DIR, default_roots};
use avl_trace_tools::viewer::DEFAULT_SERVE_PORT;
use avl_trace_tools::viewer::RUNS_ROUTE;
use tokio::net::TcpListener;
use tokio_util::sync::CancellationToken;

use crate::{Exit, Refusal, code, usage};
use service::{PidFile, detach};
use state::{Log, Server, Settings};

pub(crate) const RUN_ROUTE: &str = "/__air/run/";
pub(crate) const BUNDLE_ROUTE: &str = "/__air/bundle/";
pub(crate) const EVENTS_ROUTE: &str = "/__air/events";
pub(crate) const PLAN_ROUTE: &str = "/__air/plan";

/// How long the open connections get to finish once the server stops.
const SHUTDOWN_GRACE: Duration = Duration::from_secs(3);

/// `air-trace serve`'s command line.
#[derive(clap::Args, Clone, Debug)]
pub(crate) struct ServeArgs {
    /// The port on 127.0.0.1 to listen on; 0 picks a free one.
    #[arg(long, default_value_t = DEFAULT_SERVE_PORT)]
    pub port: u16,
    /// The built docs site to serve at /air/ (default <repo>/out/air-site).
    #[arg(long, value_name = "DIR")]
    pub site: Option<PathBuf>,
    /// Another directory or zip to find bundles in; repeatable.
    #[arg(long = "root", value_name = "DIR")]
    pub roots: Vec<PathBuf>,
    /// Look only in the --root values, not in out/air-traces, the AIR lanes' bazel-testlogs and the VM runs and
    /// reports.
    #[arg(long)]
    pub no_default_roots: bool,
    /// Make sure a server answers on the port, starting one in the background when none does, then exit.
    #[arg(long)]
    pub detach: bool,
    /// Stop after this long with no request and no open event stream, such as 30m; 0 never stops (default 30m with
    /// --detach).
    #[arg(long, value_name = "DURATION", value_parser = parse_duration)]
    pub idle_exit: Option<Duration>,
    /// A file to hold the process id while the server listens; --detach sets it.
    #[arg(long, value_name = "FILE", hide = true)]
    pub pid_file: Option<PathBuf>,
}

/// How a detached server is started: this program again, with the arguments that select the serve verb before the
/// server's own. `air-trace` passes its own executable and `serve`.
#[derive(Clone, Debug)]
pub(crate) struct DetachProgram {
    pub exe: PathBuf,
    pub leading_args: Vec<OsString>,
}

impl DetachProgram {
    /// The running executable, with these arguments before the server's.
    pub(crate) fn current(leading_args: impl IntoIterator<Item = impl Into<OsString>>) -> io::Result<Self> {
        Ok(Self {
            exe: std::env::current_exe()?,
            leading_args: leading_args.into_iter().map(Into::into).collect(),
        })
    }

    /// The command line before the server's arguments.
    pub(crate) fn argv(&self) -> Vec<OsString> {
        std::iter::once(self.exe.clone().into_os_string())
            .chain(self.leading_args.iter().cloned())
            .collect()
    }
}

/// What the command line reads of the machine: the checkout and the controller's runtime root.
pub(crate) struct Context {
    pub repo_root: Result<PathBuf, Refusal>,
    pub runtime_root: Result<PathBuf, Refusal>,
}

impl Context {
    pub(crate) fn from_process() -> Self {
        // The planner of the server resolves in the Air area, so a checkout without it counts as no checkout.
        let repo_root = repo_root().and_then(|root| {
            avl_affected::bridge::install(&root).map_err(crate::from_controller)?;
            Ok(root)
        });
        Self {
            repo_root,
            runtime_root: crate::vm_runtime_root(),
        }
    }
}

/// Where the command line's own output goes: a line at a time, without its newline.
#[derive(Clone)]
pub(crate) struct Streams {
    pub out: Log,
    pub err: Log,
}

impl Streams {
    fn stdio() -> Self {
        Self {
            out: Arc::new(|line| println!("{line}")),
            err: Arc::new(|line| eprintln!("{line}")),
        }
    }
}

/// Runs `air-trace serve` until a signal stops it: 0 then, 1 when it could not start, 2 for a command line it
/// cannot use. `air-trace` passes the arguments of its `serve` verb.
pub(crate) fn serve_main(args: ServeArgs) -> u8 {
    let streams = Streams::stdio();
    let program = match DetachProgram::current(["serve"]) {
        Ok(program) => program,
        Err(error) => {
            (streams.err)(&format!("air-trace serve: cannot find this program: {error}"));
            return Exit::Broken.code();
        }
    };
    let runtime = match tokio::runtime::Builder::new_multi_thread().enable_all().build() {
        Ok(runtime) => runtime,
        Err(error) => {
            (streams.err)(&format!("air-trace serve: {error}"));
            return Exit::Broken.code();
        }
    };
    let code = runtime.block_on(async {
        let cancel = CancellationToken::new();
        tokio::spawn(stop_on_signal(cancel.clone()));
        serve(args, &program, Context::from_process(), cancel, streams).await
    });
    // A request's scan still on a blocking thread would otherwise hold the exit until it ends.
    runtime.shutdown_timeout(SHUTDOWN_GRACE);
    code
}

/// Cancels on SIGINT or SIGTERM.
#[cfg(unix)]
async fn stop_on_signal(cancel: CancellationToken) {
    use tokio::signal::unix::{SignalKind, signal};
    match signal(SignalKind::terminate()) {
        Ok(mut terminate) => {
            tokio::select! {
                _ = tokio::signal::ctrl_c() => {}
                _ = terminate.recv() => {}
            }
        }
        Err(_) => {
            let _ = tokio::signal::ctrl_c().await;
        }
    }
    cancel.cancel();
}

/// Cancels on Ctrl-C, and when the console closes or the system shuts down: the two events Windows sends where
/// Unix sends SIGTERM. A handler that cannot be installed leaves Ctrl-C alone to stop the server.
#[cfg(windows)]
async fn stop_on_signal(cancel: CancellationToken) {
    use tokio::signal::windows::{ctrl_close, ctrl_shutdown};
    match (ctrl_close(), ctrl_shutdown()) {
        (Ok(mut close), Ok(mut shutdown)) => {
            tokio::select! {
                _ = tokio::signal::ctrl_c() => {}
                _ = close.recv() => {}
                _ = shutdown.recv() => {}
            }
        }
        _ => {
            let _ = tokio::signal::ctrl_c().await;
        }
    }
    cancel.cancel();
}

/// Runs `air-trace serve` until cancel ends, and answers its exit code.
pub(crate) async fn serve(args: ServeArgs, program: &DetachProgram, context: Context, cancel: CancellationToken, streams: Streams) -> u8 {
    let err = |line: String| (streams.err)(&line);
    // Prints a refusal as `air-trace <command>: <message>` and answers its exit.
    let fail = |command: &str, refused: Refusal| {
        err(format!("air-trace {command}: {}", refused.message));
        refused.exit.code()
    };
    if args.no_default_roots && args.roots.is_empty() {
        return fail("serve", usage("no roots to look in; pass --root DIR"));
    }
    if args.detach {
        if args.port == 0 {
            return fail(
                "serve",
                usage("--detach needs a fixed --port, so that the next call finds the server"),
            );
        }
        let mut roots = Vec::new();
        for root in &args.roots {
            match std::path::absolute(root) {
                Ok(absolute) => roots.push(absolute),
                Err(error) => return fail("serve", usage(format!("--root {}: {error}", root.display()))),
            }
        }
        let request = DetachRequest {
            port: args.port,
            site: args
                .site
                .as_ref()
                .map(|site| std::path::absolute(site).unwrap_or_else(|_| site.clone())),
            roots,
            no_default_roots: args.no_default_roots,
            idle_exit: args.idle_exit.unwrap_or(DETACH_IDLE_DEFAULT),
        };
        let command = program.argv();
        let runtime_root = context.runtime_root;
        let detached = tokio::task::spawn_blocking(move || {
            let (mut out, mut err) = (Vec::new(), Vec::new());
            let runner = Runner::new(std::env::vars(), Interrupts::detached());
            let code = detach(&request, runtime_root, &runner, &command, &mut out, &mut err);
            (code, out, err)
        })
        .await;
        let Ok((code, out, error)) = detached else {
            return fail(
                "serve --detach",
                Refusal::new("detach_interrupted", Exit::Broken, "the start was interrupted"),
            );
        };
        for (text, sink) in [(out, &streams.out), (error, &streams.err)] {
            for line in String::from_utf8_lossy(&text).lines() {
                sink(line);
            }
        }
        return code;
    }

    let repo = context.repo_root;
    if let Err(error) = &repo
        && (!args.no_default_roots || args.site.is_none())
    {
        err(format!(
            "air-trace serve: {}; the default roots and site need the checkout",
            error.message
        ));
    }
    let repo = repo.ok();
    let Some(cache_dir) = repo
        .as_ref()
        .map(|repo| repo.join("out").join("air-traces-cache"))
        .or_else(|| user_cache_dir().map(|cache| cache.join("air-trace")))
    else {
        return fail(
            "serve",
            Refusal::new("no_cache_dir", Exit::Broken, "no directory for the inflate cache"),
        );
    };
    let runtime_root = context.runtime_root;
    let mut roots = if args.no_default_roots {
        Vec::new()
    } else {
        default_roots(repo.as_deref(), runtime_root.as_deref().map_err(|refused| refused.message.as_str()))
    };
    for root in &args.roots {
        match std::path::absolute(root) {
            Ok(absolute) => roots.push(Root::new(RootKind::Flag, absolute)),
            Err(error) => return fail("serve", usage(format!("--root {}: {error}", root.display()))),
        }
    }
    let mut settings = Settings::new(cache_dir, roots);
    settings.log = Arc::clone(&streams.err);
    settings.idle_exit = args.idle_exit.filter(|idle| !idle.is_zero());
    settings.pid_file = args.pid_file.clone();
    settings.site_dir = args.site.clone().or_else(|| Some(repo.as_ref()?.join("out").join("air-site")));
    if args.site.is_none() {
        settings.site_project = repo.as_ref().map(|repo| repo.join("plugins").join("air").join("docs"));
    }
    settings.repo_root = repo;
    if !args.no_default_roots
        && let Ok(runtime) = &runtime_root
    {
        settings.runs_dir = Some(runtime.join(VM_RUNS_DIR));
    }
    let idle_exit = settings.idle_exit;
    let pid_file = settings.pid_file.clone();

    let server = Server::new(settings);
    let listener = match TcpListener::bind(("127.0.0.1", args.port)).await {
        Ok(listener) => listener,
        Err(error) => {
            let message = format!("cannot listen on 127.0.0.1:{}: {error}", args.port);
            return fail("serve", Refusal::new("listen_failed", Exit::Broken, message));
        }
    };
    let address = match listener.local_addr() {
        Ok(address) => address,
        Err(error) => return fail("serve", Refusal::new("listen_failed", Exit::Broken, error.to_string())),
    };
    let out = |line: String| (streams.out)(&line);
    out(format!("air-trace serve: {}", avl_trace_tools::viewer::runs_url(address.port())));
    out(format!("  api: http://{address}{RUNS_ROUTE}"));
    if let Some(idle) = idle_exit {
        out(format!("  stops after {} with no request", format_duration(idle)));
    }
    let first = server.blocking(Server::scan).await;
    for state in &first.found.roots {
        let mut note = if state.exists {
            format!("{} bundles", state.bundles)
        } else {
            "absent".to_owned()
        };
        if let Some(error) = &state.error {
            note.push_str(": ");
            note.push_str(error);
        }
        out(format!("  root ({}): {}, {note}", state.kind, state.path.display()));
    }
    let _pid_file = match pid_file.as_deref().map(PidFile::write).transpose() {
        Ok(pid_file) => pid_file,
        Err(error) => {
            let message = format!("cannot write {}: {error}", pid_file.unwrap_or_default().display());
            return fail("serve", Refusal::new("pid_file_unwritable", Exit::Broken, message));
        }
    };
    if let Err(error) = server.run(listener, cancel).await {
        return fail("serve", Refusal::new("serve_failed", Exit::Broken, error.to_string()));
    }
    Exit::Ok.code()
}

impl Server {
    /// Serves until cancel or the server's own shutdown ends, the idle stop included, then shuts down: the open
    /// event streams end with it. It builds the site in the background meanwhile, when the site needs it.
    pub(crate) async fn run(self: Arc<Self>, listener: TcpListener, cancel: CancellationToken) -> io::Result<()> {
        let stop = self.shutdown.clone();
        let forward = {
            let stop = stop.clone();
            tokio::spawn(async move {
                tokio::select! {
                    () = cancel.cancelled() => stop.cancel(),
                    () = stop.cancelled() => {}
                }
            })
        };
        if let Some(idle) = self.settings.idle_exit {
            tokio::spawn(Arc::clone(&self).stop_when_idle(idle, stop.clone()));
        }
        tokio::spawn(Arc::clone(&self).build_site_once(stop.clone()));
        let stop_watching = CancellationToken::new();
        let watch = tokio::spawn(Arc::clone(&self).watch_loop(stop_watching.clone()));

        let app = http::router(Arc::clone(&self));
        let served = axum::serve(listener, app).with_graceful_shutdown(stop.clone().cancelled_owned());
        let result = tokio::select! {
            result = served.into_future() => result,
            () = async {
                stop.cancelled().await;
                tokio::time::sleep(SHUTDOWN_GRACE).await;
            } => Ok(()),
        };
        stop.cancel();
        stop_watching.cancel();
        let _ = watch.await;
        forward.abort();
        self.scanner.close();
        result
    }
}

/// The checkout: `BUILD_WORKSPACE_DIRECTORY`, which `bazel run` and `trace.cmd` set, or else the top of the Git
/// work tree the process runs in.
fn repo_root() -> Result<PathBuf, Refusal> {
    if let Some(workspace) = std::env::var_os("BUILD_WORKSPACE_DIRECTORY").filter(|value| !value.is_empty()) {
        return Ok(PathBuf::from(workspace));
    }
    Command::new("git")
        .args(["rev-parse", "--show-toplevel"])
        .output()
        .ok()
        .filter(|output| output.status.success())
        .map(|output| PathBuf::from(String::from_utf8_lossy(&output.stdout).trim()))
        .ok_or_else(|| {
            Refusal::new(
                code::NO_CHECKOUT,
                Exit::Broken,
                "the checkout is unknown: BUILD_WORKSPACE_DIRECTORY is unset and git rev-parse --show-toplevel failed",
            )
        })
}

/// The user's cache directory, for an inflate cache outside any checkout.
fn user_cache_dir() -> Option<PathBuf> {
    let variable = |name: &str| std::env::var_os(name).filter(|value| !value.is_empty()).map(PathBuf::from);
    if cfg!(windows) {
        variable("LOCALAPPDATA")
    } else if cfg!(target_os = "macos") {
        variable("HOME").map(|home| home.join("Library").join("Caches"))
    } else {
        variable("XDG_CACHE_HOME").or_else(|| variable("HOME").map(|home| home.join(".cache")))
    }
}

/// Reads up to `length` bytes of a file from `offset`, fewer at its end.
pub(crate) fn read_at_most(file: &File, offset: u64, length: u64) -> io::Result<Vec<u8>> {
    let mut handle = file;
    handle.seek(SeekFrom::Start(offset))?;
    let mut buffer = Vec::with_capacity(usize::try_from(length).unwrap_or(0));
    handle.take(length).read_to_end(&mut buffer)?;
    Ok(buffer)
}

/// Reads `--idle-exit`: `0`, or a duration such as `30m`, `1h30m`, `2s` or `300ms`.
fn parse_duration(value: &str) -> Result<Duration, Refusal> {
    if value.trim() == "0" {
        return Ok(Duration::ZERO);
    }
    let signed: jiff::SignedDuration = value
        .parse()
        .map_err(|error| usage(format!("{value} is not a duration such as 30m or 2s: {error}")))?;
    Duration::try_from(signed).map_err(|error| usage(format!("{value} is negative: {error}")))
}

#[cfg(test)]
mod tests;
