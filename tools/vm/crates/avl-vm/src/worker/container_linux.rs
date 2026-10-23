//! The container-linux backend: the `container.cmd` script of the `testing-ui` skill, against the one container of this
//! checkout.
//!
//! A container-linux worker is the Linux guest of the skill's image. The script starts the container and mounts the
//! read-only Bazel share at its host path, and no checkout; the guest writes the control port and its bearer into the skill's output
//! root, `out/testing-ui`. The runtime's exec serves the developer terminal of `exec` in text mode and `pull`. Every
//! other guest command goes through the control port
//! ([`ControlPortChannel`](crate::worker::channel::ControlPortChannel)). The guest runs as the one unprivileged
//! account of the image.
//!
//! Smaller than the Docker backend: the script owns the image, the container name and the ports, and `list` is the
//! one question about liveness.

use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::time::Duration;

use avl_base::config::HostOs;
use avl_base::format::clip;
use avl_base::{Config, Exit, OrRefuse, Refusal, Reporter, Scope};
use avl_host_sys::paths::GuestPaths;
use avl_host_sys::proc::FAILURE_OUTPUT_TAIL_BYTES;
use avl_host_sys::share::shares;
use avl_host_sys::{Captured, Ctx, ProcError, Runner, SpawnOptions};
use serde::Deserialize;

use crate::worker::docker::{log_options, remove_if_present};
use crate::worker::hypervisor::host_argv;
use crate::worker::secure::read_json_record;

#[cfg(test)]
#[cfg(unix)]
mod tests;

/// The timeout of `list`, one query of the container runtime.
const LIST_TIMEOUT: Duration = Duration::from_mins(1);

/// The timeout of `start`. The first start builds the image of the skill, which takes minutes, and every start waits
/// until the guest is ready.
const START_TIMEOUT: Duration = Duration::from_mins(30);

/// The timeout of `stop`, which waits until the runtime removed the container.
const STOP_TIMEOUT: Duration = Duration::from_mins(2);

/// The one-line file with the TCP port of the control port on `127.0.0.1`, which the script writes.
const PORT_FILE: &str = "container.ctl_port";

/// The one-line file with the bearer of the control port, which the guest writes.
const BEARER_FILE: &str = "container.ctl_bearer";

/// The flat JSON object the guest writes about its display: the noVNC page and the VNC password.
const DESCRIPTION_FILE: &str = "container.json";

/// The record of the `start` argv a worker's container was made with, beside its other host state. Another argv
/// names another container: other mounts or another published port.
const START_RECORD_FILE: &str = "container-linux-start.json";

/// The environment of `start`: `APP_EXEC=/bin/true` keeps the guest up with no program of its own, because the
/// lane starts the IDE itself.
const START_ENVIRONMENT: [(&str, &str); 1] = [("APP_EXEC", "/bin/true")];

/// The separator of the three fields of a `list` line: the container name, the word `running` and the checkout root.
/// A field is matched on it and not on whitespace, because a checkout root can hold a space.
const LIST_SEPARATOR: &str = "  running  ";

/// The container-linux backend, one of the four a [`Machine`](crate::worker::hypervisor::Machine) is.
pub(crate) struct ContainerLinux {
    settings: Arc<Config>,
    runner: Runner,
    reporter: Reporter,
}

/// What the guest wrote about its display.
#[derive(Clone, Debug, PartialEq, Eq, Deserialize)]
pub(crate) struct NoVnc {
    /// The noVNC page on the host, `http://127.0.0.1:<port>/vnc.html?...`.
    #[serde(rename = "novnc")]
    pub url: String,
    #[serde(rename = "vncPassword")]
    pub password: String,
}

impl ContainerLinux {
    pub(crate) const fn new(settings: Arc<Config>, runner: Runner, reporter: Reporter) -> Self {
        Self {
            settings,
            runner,
            reporter,
        }
    }

    /// The script as the head of every argv of this backend. It is the bash driver beside the polyglot
    /// `container.cmd`, because that file has no shebang and the kernel refuses to run it; the `.cmd` itself only
    /// execs the driver.
    fn program(&self) -> String {
        program_of(&self.settings.container_linux_script, HostOs::CURRENT)
    }

    fn command(&self, arguments: &[&str]) -> Vec<String> {
        host_argv(self.program(), arguments)
    }

    fn note(&self, worker: &str, message: String) {
        self.reporter.note(message, Some(&Scope::worker(worker)));
    }

    // --- what the machine asks of the backend ------------------------------------------------------------

    /// Refuses unless the script is on disk and its `list` answers, which proves the container runtime it drives.
    /// The worker name is ignored, as on Docker: the pool has one slot, and the script names the container.
    pub(crate) async fn require_available(&self, ctx: &Ctx, _worker: &str) -> Result<(), Refusal> {
        let script = &self.settings.container_linux_script;
        if !script.is_file() {
            return Err(Refusal::new(
                "container_linux_script_missing",
                Exit::UNAVAILABLE,
                format!(
                    "the testing-ui script is not at {}: this checkout has no testing-ui skill",
                    script.display()
                ),
            ));
        }
        self.list(ctx).await.map(drop)
    }

    /// `<script> exec <argv…>`: the runtime's exec into the container, for the developer terminal and `pull`. Every
    /// other guest command goes through the control port. `interactive` has no flag here: the runtime's exec carries
    /// stdin as it is. The head is declared on the timeline, so phase timing counts the spawn as a guest call.
    pub(crate) fn guest_argv(&self, ctx: &Ctx, _worker: &str, argv: &[String], _interactive: bool) -> Vec<String> {
        let head = self.program();
        ctx.timeline().declare_guest_head(&head);
        let mut line = vec![head, "exec".to_owned()];
        line.extend_from_slice(argv);
        line
    }

    /// Whether the container of this checkout runs: `list` names it with the checkout root the script derived, and
    /// that root is the host repository of these settings. A container of another checkout on the same host is not
    /// this worker.
    pub(crate) async fn running(&self, ctx: &Ctx, _worker: &str) -> Result<bool, Refusal> {
        let listed = self.list(ctx).await?;
        Ok(running_in(&listed.stdout, self.listed_checkout()))
    }

    /// The checkout root as a `list` row prints it: the skill's output root without its `out/testing-ui` tail.
    /// Taken from the setting and not from the resolved host paths, so `vnc` and a lease release can ask before
    /// `ensure_host_paths` ran.
    fn listed_checkout(&self) -> &Path {
        let root = self.settings.container_linux_root.as_path();
        if root.ends_with("out/testing-ui") {
            return root.ancestors().nth(2).unwrap_or(root);
        }
        root
    }

    /// `<script> list`, or `container_linux_unavailable` when the script cannot run or exits nonzero: the runtime is not
    /// installed, or it does not answer.
    async fn list(&self, ctx: &Ctx) -> Result<Captured, Refusal> {
        let argv = self.command(&["list"]);
        let captured = match self.runner.capture(ctx, &argv, &SpawnOptions::within(LIST_TIMEOUT)).await {
            Ok(captured) => captured,
            Err(ProcError::SpawnFailed(refusal)) => {
                return Err(unavailable(format!("cannot run {}: {}", self.program(), refusal.message)));
            }
            Err(other) => return Err(other.into()),
        };
        if captured.exit_code != 0 {
            return Err(unavailable(format!(
                "{} list exited with {}: {}",
                self.program(),
                captured.exit_code,
                clip(captured.stderr.trim(), FAILURE_OUTPUT_TAIL_BYTES)
            )));
        }
        Ok(captured)
    }

    // --- start and stop ----------------------------------------------------------------------------------

    /// The `start` argv of one worker: the worker directory as the program directory, the Bazel share read-only at its
    /// guest root, which is its host path, and the daemon's guest port published at the host port of the settings.
    ///
    /// A host path with a colon is refused, because the runtime splits a mount at it, as Docker refuses the characters
    /// of its own mount grammar. Refused before the host paths are resolved, as the shares are.
    pub(crate) fn start_argv(&self, worker: &str) -> Result<Vec<String>, Refusal> {
        let settings = &self.settings;
        let paths = GuestPaths::of(settings)?;
        let mut argv = self.command(&["start", &path_text(&settings.worker_dir(worker))]);
        for (share, guest) in shares(settings)?.iter().zip([paths.bazel_user_root()]) {
            let path = mount_path(&share.path, &share.name)?;
            argv.push("--ro".to_owned());
            argv.push(format!("{path}:{guest}"));
        }
        argv.push("--publish".to_owned());
        argv.push(format!("{}:{}", settings.daemon_host_port, settings.daemon.port));
        Ok(argv)
    }

    /// Where the output of the last `start` went: the image build and the ready wait of the script.
    pub(crate) fn start_log_path(&self, worker: &str) -> PathBuf {
        self.settings.worker_dir(worker).join("container-linux-start.log")
    }

    pub(crate) fn start_record_path(&self, worker: &str) -> PathBuf {
        self.settings.worker_dir(worker).join(START_RECORD_FILE)
    }

    /// Whether the recorded `start` argv of `worker` is the argv a start would use now. No record is not current.
    pub(crate) fn start_record_is_current(&self, worker: &str) -> Result<bool, Refusal> {
        let current = self.start_argv(worker)?;
        let recorded: Option<Vec<String>> = read_json_record(&self.start_record_path(worker)).ok().flatten();
        Ok(recorded.as_ref() == Some(&current))
    }

    /// Records the `start` argv of `worker` as the one its container was made with.
    pub(crate) fn record_start(&self, worker: &str) -> Result<(), Refusal> {
        let path = self.start_record_path(worker);
        let bytes = serde_json::to_vec_pretty(&self.start_argv(worker)?)
            .or_refuse("state_write_failed", Exit::FAILURE, || format!("cannot encode {}", path.display()))?;
        std::fs::write(&path, bytes).or_refuse("state_write_failed", Exit::FAILURE, || format!("cannot write {}", path.display()))
    }

    /// Starts the container of this checkout, or keeps the one that runs with the same image and mounts, and
    /// returns once the guest is ready. The output is a log, kept on failure, because a failed image build is
    /// read there.
    pub(crate) async fn start(&self, ctx: &Ctx, worker: &str) -> Result<(), Refusal> {
        let argv = self.start_argv(worker)?;
        // The worker directory is the program directory the script mounts, so it exists before the script resolves it.
        let worker_dir = self.settings.worker_dir(worker);
        std::fs::create_dir_all(&worker_dir).or_refuse("state_write_failed", Exit::FAILURE, || {
            format!("cannot create the mount directory {}", worker_dir.display())
        })?;
        let log = self.start_log_path(worker);
        remove_if_present(&log)?;
        self.note(
            worker,
            format!(
                "starting the testing-ui container of this checkout; the first start builds its image and takes \
                 minutes (log: {})",
                log.display()
            ),
        );
        self.runner
            .with_overrides(&START_ENVIRONMENT)
            .checked_to_file(ctx, &argv, &log, &log_options(START_TIMEOUT))
            .await
            .map_err(|error| match error {
                ProcError::Exited { refusal, .. } => Refusal::new(
                    "container_linux_start_failed",
                    refusal.exit,
                    format!("{}; the start log is {}", refusal.message, log.display()),
                ),
                other => other.into(),
            })
            .map(drop)
    }

    /// Stops and removes the container of this checkout, and with it the port, the bearer and the description
    /// files the script keeps.
    pub(crate) async fn stop(&self, ctx: &Ctx, _worker: &str) -> Result<(), Refusal> {
        self.runner
            .checked(ctx, &self.command(&["stop"]), &SpawnOptions::within(STOP_TIMEOUT))
            .await?;
        Ok(())
    }

    // --- what the container wrote --------------------------------------------------------------------------

    /// The noVNC page and the VNC password, or `None` while the guest has not written them: before the first start,
    /// and after a stop. The port and the bearer of the control port are [`control_port`] and [`bearer`], which the
    /// channel reads on every call.
    pub(crate) fn novnc(&self) -> Result<Option<NoVnc>, Refusal> {
        novnc(&self.settings)
    }
}

/// The TCP port of the control port on `127.0.0.1`, from the file the script writes under the testing-ui root.
pub(crate) fn control_port(settings: &Config) -> Result<u16, Refusal> {
    let path = settings.container_linux_root.join(PORT_FILE);
    let text = read_state_file(&path)?;
    text.trim().parse::<u16>().or_refuse("state_read_failed", Exit::FAILURE, || {
        format!("{} does not hold a TCP port: {:?}", path.display(), text.trim())
    })
}

/// The bearer of the control port, from the file the guest writes under the testing-ui root.
pub(crate) fn bearer(settings: &Config) -> Result<String, Refusal> {
    let path = settings.container_linux_root.join(BEARER_FILE);
    let text = read_state_file(&path)?;
    let bearer = text.trim();
    if bearer.is_empty() {
        return Err(Refusal::new(
            "state_read_failed",
            Exit::FAILURE,
            format!("{} holds no bearer", path.display()),
        ));
    }
    Ok(bearer.to_owned())
}

/// What the guest wrote about its display, or `None` when the description file is not there.
pub(crate) fn novnc(settings: &Config) -> Result<Option<NoVnc>, Refusal> {
    let path = settings.container_linux_root.join(DESCRIPTION_FILE);
    let content = match std::fs::read(&path) {
        Ok(content) => content,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(None),
        Err(error) => return Err(state_read_failed(format!("cannot read {}: {error}", path.display()))),
    };
    serde_json::from_slice(&content)
        .map(Some)
        .or_refuse("state_read_failed", Exit::FAILURE, || {
            format!("{} is not the description of the container", path.display())
        })
}

/// One of the two one-line files of the control port. A missing file is `container_linux_not_started`: the script writes
/// the port when it starts the container and removes it when it stops the container.
fn read_state_file(path: &Path) -> Result<String, Refusal> {
    match std::fs::read_to_string(path) {
        Ok(text) => Ok(text),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Err(Refusal::new(
            "container_linux_not_started",
            Exit::UNAVAILABLE,
            format!(
                "{} is not there, so the testing-ui container of this checkout is not started; run pool start",
                path.display()
            ),
        )),
        Err(error) => Err(state_read_failed(format!("cannot read {}: {error}", path.display()))),
    }
}

/// Whether a `list` answer names a running container of the checkout `repo`. The third field of a container line is
/// the checkout root as the script spelled it; it is compared as a path, so a trailing slash does not count, and it
/// is not resolved, so a symlinked checkout is compared as the script spelled it too.
pub(crate) fn running_in(listed: &str, repo: &Path) -> bool {
    running_in_on(listed, repo, HostOs::CURRENT)
}

/// [`running_in`] as `host` compares a path: Windows without case, because the batch half prints the checkout as the
/// shell spelled it, and a drive letter arrives in either case.
pub(crate) fn running_in_on(listed: &str, repo: &Path, host: HostOs) -> bool {
    let same = |root: &str| match host {
        HostOs::Windows => root.eq_ignore_ascii_case(&repo.to_string_lossy()),
        HostOs::Macos | HostOs::Linux => Path::new(root) == repo,
    };
    listed.lines().any(|line| {
        if line.starts_with(char::is_whitespace) {
            return false;
        }
        line.split_once(LIST_SEPARATOR).is_some_and(|(_, root)| same(root.trim()))
    })
}

/// The program that runs the skill: the bash half of `script` on a Unix host, where a `.cmd` is not executable, and
/// the `.cmd` itself on Windows, which the standard library runs through `cmd.exe` with its batch quoting.
pub(crate) fn program_of(script: &Path, host: HostOs) -> String {
    if host != HostOs::Windows && script.extension().is_some_and(|extension| extension == "cmd") {
        return script.with_extension("sh").to_string_lossy().into_owned();
    }
    script.to_string_lossy().into_owned()
}

/// A host path as one side of a mount pair: the runtime splits the pair at a colon, so a path with one is refused.
/// The drive colon of a Windows path is the exception, which the runtime reads as Docker does.
fn mount_path(path: &Path, name: &str) -> Result<String, Refusal> {
    mount_path_on(path, name, HostOs::CURRENT)
}

pub(crate) fn mount_path_on(path: &Path, name: &str, host: HostOs) -> Result<String, Refusal> {
    let text = path_text(path);
    let colons = text.matches(':').count();
    let drive_colon = host == HostOs::Windows
        && colons == 1
        && text.bytes().next().is_some_and(|letter| letter.is_ascii_alphabetic())
        && text.as_bytes().get(1) == Some(&b':');
    if text.is_empty() || (colons > 0 && !drive_colon) {
        return Err(Refusal::new(
            "unsafe_share_path",
            Exit::DATA_ERR,
            format!(
                "the testing-ui script cannot mount {text:?} for {name}: the runtime splits a mount at a colon, so \
                 the host path must be non-empty and free of ':' beyond a Windows drive letter"
            ),
        ));
    }
    Ok(text)
}

fn path_text(path: &Path) -> String {
    path.to_string_lossy().into_owned()
}

fn unavailable(message: String) -> Refusal {
    Refusal::new("container_linux_unavailable", Exit::UNAVAILABLE, message)
}

fn state_read_failed(message: String) -> Refusal {
    Refusal::new("state_read_failed", Exit::FAILURE, message)
}
