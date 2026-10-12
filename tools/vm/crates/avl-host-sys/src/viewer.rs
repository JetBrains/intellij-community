//! The detached trace viewer on this machine: how a caller finds out that `air-trace serve` answers, and the one
//! start of a detached server, which `air-trace serve --detach` and the controller share.
//!
//! The viewer's URLs and the header that names its server are `avl_trace_tools::viewer`'s. A detached server
//! writes to `<runtime root>/viewer/serve.log` and keeps its process id in `serve.pid` beside it.

use std::ffi::OsString;
use std::io::{self, Read, Write};
use std::net::{Ipv4Addr, SocketAddr, TcpStream};
use std::path::{Path, PathBuf};
use std::process::Child;
use std::time::{Duration, Instant};

use avl_base::fs::create_private_dir;
use avl_base::{Exit, Refusal};
use avl_trace_tools::viewer::{IDENTITY, IDENTITY_HEADER, RUNS_ROUTE};

use crate::Runner;
use crate::private::{PrivateOpen, open_private_file};

#[cfg(test)]
mod tests;

/// The wrapper that builds and runs `air-trace`, relative to the checkout.
pub const SERVE_SCRIPT: &str = "community/tools/trace.cmd";

/// The output of the detached server and of its site build, in [viewer_dir]. A server the controller starts
/// through the wrapper writes the wrapper's build here too.
pub const SERVE_LOG_FILE: &str = "serve.log";
/// The process id of the detached server while it listens, in [viewer_dir].
pub const SERVE_PID_FILE: &str = "serve.pid";

/// How long a caller of [probe_viewer] waits for an answer unless it has a reason to wait less.
pub const PROBE_TIMEOUT: Duration = Duration::from_secs(1);

/// The idle stop of a detached server whose command line names none.
pub const DETACH_IDLE_DEFAULT: Duration = Duration::from_mins(30);

/// The directory of the detached viewer under the controller's runtime root.
pub fn viewer_dir(runtime_root: &Path) -> PathBuf {
    runtime_root.join("viewer")
}

/// The wrapper's command line that runs the server, `trace.cmd serve`, which [start_detached] completes with the
/// server's own arguments.
///
/// The wrapper runs `bazel run` first, so a caller that must not wait does not wait for it. The wrapper is a
/// polyglot `.cmd` with no shebang, so an exec of the file itself fails with ENOEXEC: the command line names
/// `/bin/bash`, the shell the wrapper pins for itself. On Windows the batch half of the wrapper runs, through
/// `cmd.exe /c`, as `bt_core::bazel_command` runs `bazel.cmd`.
pub fn serve_command(checkout: &Path) -> Vec<OsString> {
    let script = SERVE_SCRIPT
        .split('/')
        .fold(checkout.to_path_buf(), |path, segment| path.join(segment));
    let shell: &[&str] = if cfg!(windows) { &["cmd.exe", "/c"] } else { &["/bin/bash"] };
    shell
        .iter()
        .map(OsString::from)
        .chain([script.into_os_string(), "serve".into()])
        .collect()
}

/// Whether `air-trace serve` answers on 127.0.0.1 at port. It sends one `HEAD` of the listing, through no proxy,
/// follows no redirect, and gives up after timeout in all: a closed port, a program that does not speak HTTP, and
/// one that answers too slowly are all "not the viewer".
///
/// It is written by hand over a std socket, because an HTTP client crate is 27 crates of `air-trace` for this one
/// exchange. Blocking, and on purpose: a probe is one short exchange, and an async caller runs it on a blocking
/// thread.
pub fn probe_viewer(port: u16, timeout: Duration) -> bool {
    let deadline = Instant::now() + timeout;
    let address = SocketAddr::from((Ipv4Addr::LOCALHOST, port));
    let Ok(mut stream) = TcpStream::connect_timeout(&address, timeout) else {
        return false;
    };
    let request = format!("HEAD {RUNS_ROUTE} HTTP/1.1\r\nHost: {address}\r\nConnection: close\r\n\r\n");
    if stream
        .set_write_timeout(Some(timeout))
        .and_then(|()| stream.write_all(request.as_bytes()))
        .is_err()
    {
        return false;
    }
    let mut head = Vec::new();
    let mut chunk = [0; 1024];
    while !head.windows(4).any(|end| end == b"\r\n\r\n") && head.len() < MAX_PROBE_HEAD_BYTES {
        let left = deadline.saturating_duration_since(Instant::now());
        if left.is_zero() || stream.set_read_timeout(Some(left)).is_err() {
            return false;
        }
        match stream.read(&mut chunk) {
            Ok(0) | Err(_) => break,
            Ok(read) => head.extend_from_slice(&chunk[..read]),
        }
    }
    is_viewer_head(&String::from_utf8_lossy(&head))
}

/// Bounds the answer head that [probe_viewer] reads. The viewer's head is a few short lines.
const MAX_PROBE_HEAD_BYTES: usize = 16 << 10;

/// Whether an answer head is the viewer's: the status 200, and [IDENTITY_HEADER] with the value [IDENTITY].
fn is_viewer_head(head: &str) -> bool {
    let mut lines = head.split("\r\n");
    let ok = lines
        .next()
        .is_some_and(|status| status.starts_with("HTTP/1.") && status.split(' ').nth(1) == Some("200"));
    ok && lines.take_while(|line| !line.is_empty()).any(|line| {
        line.split_once(':')
            .is_some_and(|(name, value)| name.trim().eq_ignore_ascii_case(IDENTITY_HEADER) && value.trim() == IDENTITY)
    })
}

/// What a detached server is started with.
pub struct DetachRequest {
    pub port: u16,
    pub site: Option<PathBuf>,
    pub roots: Vec<PathBuf>,
    pub no_default_roots: bool,
    pub idle_exit: Duration,
}

impl DetachRequest {
    /// A server on the port with the command line's defaults: the default roots and site, and the idle stop of a
    /// detached server. What the controller starts for a run in a terminal.
    pub const fn for_port(port: u16) -> Self {
        Self {
            port,
            site: None,
            roots: Vec::new(),
            no_default_roots: false,
            idle_exit: DETACH_IDLE_DEFAULT,
        }
    }

    /// The server's arguments after the verb, without `--detach`.
    pub fn args(&self, pid_file: &Path) -> Vec<OsString> {
        let mut args: Vec<OsString> = vec![
            "--port".into(),
            self.port.to_string().into(),
            "--idle-exit".into(),
            format_duration(self.idle_exit).into(),
            "--pid-file".into(),
            pid_file.into(),
        ];
        if let Some(site) = &self.site {
            args.extend(["--site".into(), site.into()]);
        }
        for root in &self.roots {
            args.extend(["--root".into(), root.into()]);
        }
        if self.no_default_roots {
            args.push("--no-default-roots".into());
        }
        args
    }
}

/// A server [start_detached] started, and where it writes.
pub struct Started {
    /// Dropping it neither waits for the server nor stops it.
    pub child: Child,
    pub log_path: PathBuf,
}

/// Starts one server and does not wait for it to answer: `command` and the request's arguments, in a session of
/// its own through the runner, with its output appended to `<runtime root>/viewer/serve.log`.
///
/// The one start of a detached server. `--detach` waits after it for the server to answer; the controller does
/// not, because the wrapper's `bazel run` can take longer than the run's own build. A session of its own, so the
/// terminal that started it can close and its interrupt does not reach the server; through the runner, so a
/// server left running for half an hour inherits no bridge credential. The directory is private and the log is the
/// owner's alone, because the log names the checkout and the bundles.
pub fn start_detached(
    runner: &Runner,
    command: &[OsString],
    directory: Option<&Path>,
    request: &DetachRequest,
    runtime_root: &Path,
) -> Result<Started, Refusal> {
    let dir = viewer_dir(runtime_root);
    create_private_dir(&dir)?;
    let log_path = dir.join(SERVE_LOG_FILE);
    let unusable = |error: io::Error| Refusal::new("viewer_log_unusable", Exit::CANT_CREATE, format!("{}: {error}", log_path.display()));
    let mut log = open_private_file(&log_path, PrivateOpen::Append).map_err(unusable)?;
    let log_err = log.try_clone().map_err(unusable)?;
    // A header the log cannot take costs only the header.
    let _ = writeln!(
        log,
        "\n--- {}: air-trace serve started detached",
        jiff::Zoned::now().strftime("%Y-%m-%dT%H:%M:%S%:z")
    );
    let mut argv = command.to_vec();
    argv.extend(request.args(&dir.join(SERVE_PID_FILE)));
    let child = runner.spawn_session(&argv, directory, log, log_err).map_err(|error| {
        Refusal::new(
            "viewer_start_failed",
            Exit::UNAVAILABLE,
            format!("cannot start the server: {error}"),
        )
    })?;
    Ok(Started { child, log_path })
}

/// A duration the way `--idle-exit` takes one back: `30m`, `2s`, `300ms`.
pub fn format_duration(duration: Duration) -> String {
    if duration.is_zero() {
        return "0s".to_owned();
    }
    match jiff::SignedDuration::try_from(duration) {
        Ok(signed) => format!("{signed:#}"),
        Err(_) => format!("{}s", duration.as_secs()),
    }
}
