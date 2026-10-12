//! Every subprocess this controller starts, and the one interface a guest command goes through.
//!
//! Two things here are contracts rather than conveniences.
//!
//! **A host command fails with what it said; a guest command does not.** `prlctl` and its peers act on the VM from
//! the host and their output carries paths and VM state, so withholding it - which the controller used to - turned
//! every backend failure into `prlctl exited with 1` and nothing else. Guest output stays withheld, because a guest
//! process can echo the UI-test bridge token. That asymmetry is [`Runner::checked`] versus
//! [`Runner::checked_quietly`], and it is the reason they are two methods.
//!
//! **[`Channel`] is the seam the fakes implement.** A test implementation answers a guest command directly, so no
//! layer that makes a real process behave like a guest has to exist. It also opens a [`GuestStream`] to a guest
//! port, which a test implementation answers with an in-process stream.
//!
//! Every captured child has a timeout, because [`SpawnOptions`] has no default: each spawn site names its own. When
//! it expires, the runner kills the process group of the child and answers [`ProcError::TimedOut`]. Three shapes
//! have no timeout, because their end belongs to somebody else: [`Runner::inherited`], an interactive session that
//! the operator ends; [`Runner::spawn_piped`], the relay stream that its HTTP client bounds; and
//! [`Runner::spawn_session`], a child that is meant to outlive the controller.
//!
//! Every child runs in its own process group, so a timeout, a cancellation or a forwarded signal reaches the whole
//! tree: `tart exec` spawns an ssh client which spawns the guest command, and killing only the parent leaves the
//! guest running and the next operation finds the run slot taken. A spawn whose future is dropped signals its group
//! as it goes, so an abandoned operation strands nothing. The exception is [`Runner::spawn_session`], whose child
//! is meant to outlive the controller. The `group` module is the seam that makes a group on Unix and on Windows.

use std::ffi::OsStr;
use std::fmt;
use std::fs::File;
use std::path::{Path, PathBuf};
use std::process::{ExitStatus, Stdio};
use std::sync::Arc;
use std::time::{Duration, Instant};

use async_trait::async_trait;
use avl_base::clock::millis;
use avl_base::config::{CAPTURE_LIMIT_BYTES, HostOs};
use avl_base::{Exit, OrRefuse, Refusal};
use serde_json::json;
use tokio::io::{AsyncRead, AsyncReadExt, AsyncWriteExt};
use tokio::process::{Child, Command};

use crate::ctx::Ctx;
use crate::interrupt::{GroupRegistration, Interrupts};
use crate::private::{PrivateOpen, open_private_file};
use avl_base::RefusalExt;
use group::{ProcessGroup, exit_code};

mod answer;
pub(crate) mod group;
mod probe;
mod stream;
// The tests drive `/bin/sh`.
#[cfg(test)]
#[cfg(unix)]
mod tests;

pub use answer::{ProbeExit, ProbeOutput, probe_unanswered};
pub use probe::{ProcessTable, PsField};
pub use stream::{GuestStream, PipedChild};

/// How much of a failed host command's output travels in the refusal, newest bytes last. Bounded because this text
/// ends up inside a JSON envelope an agent reads, and a build failure's whole stderr is not a message.
pub const FAILURE_OUTPUT_TAIL_BYTES: usize = 2000;

/// The two variables no child may inherit. They are how a *guest* process is told which worker it is and how to
/// authenticate to the bridge; a host subprocess that inherited them would hand a tool that has no business with
/// them a live credential, and the two names are exactly what a leaked environment dump would be searched for.
const SCRUBBED: [&str; 2] = ["TART_VM_TOKEN", "TART_VM_WORKER"];

/// What a subprocess said.
///
/// The two truncation flags are separate from the text because "8 MiB of output" and "8 MiB of output, and there
/// was more" are different facts, and only the second one invalidates a parse of it.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct Captured {
    pub exit_code: i32,
    pub stdout: String,
    pub stderr: String,
    pub stdout_truncated: bool,
    pub stderr_truncated: bool,
}

/// Why a subprocess did not answer as asked.
///
/// Each case carries the refusal that a command answers with, so a caller that does not branch passes the error on
/// with `?`, and a caller that does branch matches the case and never the refusal code.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum ProcError {
    /// The program did not start, or the wait for it failed: `spawn_failed`.
    SpawnFailed(Refusal),
    /// The program ran and exited with a failure status: `subprocess_failed`.
    Exited { exit_code: i32, refusal: Refusal },
    /// The program ran past the timeout of its [`SpawnOptions`], and the runner killed its process group:
    /// `subprocess_timeout`, or the code that [`SpawnOptions::timeout_code`] names, at [`Exit::TEMP_FAIL`].
    TimedOut { timeout: Duration, refusal: Refusal },
    /// [`Runner::checked_to_file`] cannot open or share its destination: `capture_destination_exists`.
    DestinationUnusable(Refusal),
    /// Every other failure: an argv that cannot run, output over the capture limit.
    Other(Refusal),
}

impl ProcError {
    /// The refusal a command answers with.
    pub const fn refusal(&self) -> &Refusal {
        match self {
            Self::SpawnFailed(refusal)
            | Self::Exited { refusal, .. }
            | Self::TimedOut { refusal, .. }
            | Self::DestinationUnusable(refusal)
            | Self::Other(refusal) => refusal,
        }
    }
}

impl From<ProcError> for Refusal {
    fn from(error: ProcError) -> Self {
        match error {
            ProcError::SpawnFailed(refusal)
            | ProcError::Exited { refusal, .. }
            | ProcError::TimedOut { refusal, .. }
            | ProcError::DestinationUnusable(refusal)
            | ProcError::Other(refusal) => refusal,
        }
    }
}

impl fmt::Display for ProcError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.refusal().message)
    }
}

impl std::error::Error for ProcError {}

/// The ways one spawn can differ. There is no default, because every spawn names its timeout: start from
/// [`SpawnOptions::within`] or [`SpawnOptions::timeout`].
#[derive(Clone, Debug)]
pub struct SpawnOptions {
    pub dir: Option<PathBuf>,
    /// The child's whole environment instead of the runner's.
    pub env: Option<Vec<(String, String)>>,
    pub stdin: Option<Vec<u8>>,
    /// Permits output past the capture limit instead of refusing. Set only where the caller wants a tail rather
    /// than a document - a log dump, not a JSON reply.
    pub allow_truncated: bool,
    /// How long the child may run. When it expires, the runner kills the process group of the child and answers
    /// [`ProcError::TimedOut`].
    pub timeout: Duration,
    /// Names the refusal when the timeout fires (default `subprocess_timeout`), so a caller can tell "the guest
    /// never answered" from "a host tool hung" without matching on prose.
    pub timeout_code: Option<&'static str>,
    /// Sends the child's stderr to the destination of [`Runner::checked_to_file`] too, for a destination that is a
    /// *log* rather than a captured document: a build log with the diagnostics stripped out of it is the half a
    /// human does not need. Ignored elsewhere.
    pub merge_stderr_into_file: bool,
    /// Leaves the destination of [`Runner::checked_to_file`] in place when the command fails. The default removes
    /// it, because a partial *capture* must not be mistaken for a complete one; a log is the opposite, since the
    /// failure is exactly when someone needs to read it. Ignored elsewhere.
    pub keep_destination_on_failure: bool,
    /// [`Interruptible::No`] keeps the child out of the interrupt service, so the first signal does not reach it.
    /// For a teardown that must still run after an interrupt, such as the removal of a run's secret files: a child
    /// spawned after the signal would otherwise be signalled at once. Only for a short command under a tight timeout,
    /// since nothing but the timeout and a second Ctrl-C ends it. Honoured by [`Runner::capture`]; ignored elsewhere.
    pub interruptible: Interruptible,
}

impl SpawnOptions {
    /// Options with a timeout, which fires as `subprocess_timeout`. Every other field has its plain value.
    pub const fn within(timeout: Duration) -> Self {
        Self {
            dir: None,
            env: None,
            stdin: None,
            allow_truncated: false,
            timeout,
            timeout_code: None,
            merge_stderr_into_file: false,
            keep_destination_on_failure: false,
            interruptible: Interruptible::Yes,
        }
    }

    /// The same options for a teardown that outlives an interrupt; see [`SpawnOptions::interruptible`].
    #[must_use]
    pub const fn surviving_interrupt(mut self) -> Self {
        self.interruptible = Interruptible::No;
        self
    }

    /// Whether the child outlives the first signal; see [`SpawnOptions::interruptible`].
    pub fn survives_interrupt(&self) -> bool {
        self.interruptible == Interruptible::No
    }

    /// Options with a timeout and the refusal code it fires with.
    pub const fn timeout(timeout: Duration, code: &'static str) -> Self {
        let mut options = Self::within(timeout);
        options.timeout_code = Some(code);
        options
    }
}

/// Starts subprocesses.
///
/// A value rather than free functions because the environment is part of it: a value that carries the scrubbed
/// environment once cannot forget to scrub it. A clone shares the environment.
#[derive(Clone)]
pub struct Runner {
    environment: Arc<[(String, String)]>,
    capture_limit: usize,
    interrupts: Interrupts,
    /// What [`Runner::probe_process`] asks in place of the host, when set.
    process_table: Option<Arc<dyn ProcessTable>>,
}

impl Runner {
    /// A runner over an environment, removing the two bridge credentials no child may inherit. Its children are
    /// registered with `interrupts`, so the first signal the process receives reaches them.
    pub fn new(environment: impl IntoIterator<Item = (String, String)>, interrupts: Interrupts) -> Self {
        let host = HostOs::CURRENT;
        let mut pairs: Vec<(String, String)> = environment
            .into_iter()
            .filter(|(name, _)| !name.is_empty() && !SCRUBBED.iter().any(|scrubbed| host.compare_variables(name, scrubbed).is_eq()))
            .collect();
        // Stable and by name only, so the first of two entries with one name is the one kept. The host decides
        // what one name is: Windows compares the names without case.
        pairs.sort_by(|left, right| host.compare_variables(&left.0, &right.0));
        pairs.dedup_by(|later, earlier| host.compare_variables(&later.0, &earlier.0).is_eq());
        Self {
            environment: pairs.into(),
            capture_limit: CAPTURE_LIMIT_BYTES,
            interrupts,
            process_table: None,
        }
    }

    /// A runner whose process probes ask `table`, and not the host, about each pid that the table holds. A clone and a
    /// runner made [`Runner::with_overrides`] keep the table.
    #[must_use]
    pub fn with_process_table(mut self, table: Arc<dyn ProcessTable>) -> Self {
        self.process_table = Some(table);
        self
    }

    /// A runner whose children see extra variables. The scrub still applies: an override cannot reintroduce a name
    /// [`Runner::new`] removed.
    #[must_use]
    pub fn with_overrides(&self, overrides: &[(&str, &str)]) -> Self {
        let mut environment: Vec<(String, String)> = overrides
            .iter()
            .map(|(name, value)| ((*name).to_owned(), (*value).to_owned()))
            .collect();
        // Overrides first: `new` keeps the first of each name after its stable sort.
        environment.extend(self.environment.iter().cloned());
        Self {
            capture_limit: self.capture_limit,
            process_table: self.process_table.clone(),
            ..Self::new(environment, self.interrupts.clone())
        }
    }

    /// The environment this runner gives its children, already scrubbed: what a caller composes an override from,
    /// such as `image`'s `PATH`.
    pub fn environment(&self) -> Vec<(String, String)> {
        self.environment.to_vec()
    }

    /// Starts `argv` in a session of its own and does not wait for it: with this runner's scrubbed environment,
    /// stdin from the null device, and its output into the files given. Answers the child, for a caller that waits
    /// for what it started to come up; dropping it neither waits nor kills.
    ///
    /// A session of its own, and so a process group whose id is its pid: the child survives the controller's exit
    /// and its Ctrl-C, and the whole group can still be signalled. On Windows the child has no console and breaks
    /// away from the job of the controller where it can. It is not registered with the interrupt service,
    /// because it is meant to outlive the call and usually the controller. It goes through the runner all the same,
    /// because the scrub of the bridge credentials is the runner's rule, and a detached process is the longest-lived
    /// one on the host.
    pub fn spawn_session(
        &self,
        argv: &[impl AsRef<OsStr>],
        directory: Option<&Path>,
        stdout: File,
        stderr: File,
    ) -> std::io::Result<std::process::Child> {
        let (program, arguments) = argv.split_first().ok_or_else(|| std::io::Error::other("no command to start"))?;
        group::spawn_session(program.as_ref(), arguments, &self.environment, directory, stdout, stderr)
    }

    /// [`Runner::spawn_session`], answering the pid only. Never reaped, because a child that died at once must stay
    /// visible to [`Runner::probe_process`] until the caller has identified it and read what it said.
    pub fn spawn_detached(&self, argv: &[impl AsRef<OsStr>], directory: Option<&Path>, stdout: File, stderr: File) -> std::io::Result<u32> {
        Ok(self.spawn_session(argv, directory, stdout, stderr)?.id())
    }

    /// The interrupt service this runner registers its children with.
    pub const fn interrupts(&self) -> &Interrupts {
        &self.interrupts
    }

    /// Only the Unix tests, which drive `/bin/sh`, lower the limit.
    #[cfg(test)]
    #[cfg(unix)]
    pub(crate) const fn with_capture_limit(mut self, limit: usize) -> Self {
        self.capture_limit = limit;
        self
    }

    /// Runs a command and answers what it said, whatever it exited with.
    ///
    /// Timed from before the spawn to after the wait and recorded into the context's phase even when it fails: a
    /// guest exec that spent its whole timeout is exactly the cost worth seeing.
    pub async fn capture(&self, ctx: &Ctx, argv: &[String], options: &SpawnOptions) -> Result<Captured, ProcError> {
        check_argv(argv).map_err(ProcError::Other)?;
        let started = Instant::now();
        let captured = self.run_captured(ctx, argv, options, options.interruptible).await;
        ctx.phase().record_subprocess(argv, started.elapsed());
        captured
    }

    pub(super) async fn run_captured(
        &self,
        ctx: &Ctx,
        argv: &[String],
        options: &SpawnOptions,
        interruptible: Interruptible,
    ) -> Result<Captured, ProcError> {
        let mut command = self.command(argv, options.dir.as_deref(), options.env.as_deref());
        command
            .stdin(if options.stdin.is_some() { Stdio::piped() } else { Stdio::null() })
            .stdout(Stdio::piped())
            .stderr(Stdio::piped());
        let mut spawned = self.spawn(command, argv, interruptible).map_err(ProcError::SpawnFailed)?;
        if let (Some(bytes), Some(mut stdin)) = (options.stdin.clone(), spawned.child.stdin.take()) {
            // A child that exits without reading its stdin closes the pipe; that is its business, not a failure.
            tokio::spawn(async move {
                let _ = stdin.write_all(&bytes).await;
            });
        }
        let limit = self.capture_limit;
        let stdout = spawned.child.stdout.take().map(|pipe| drain(pipe, limit));
        let stderr = spawned.child.stderr.take().map(|pipe| drain(pipe, limit));
        let (status, (stdout, stderr)) = spawned
            .wait(
                ctx,
                Some(options.timeout),
                futures::future::join(join_drain(stdout), join_drain(stderr)),
            )
            .await;
        let status = status.map_err(|error| ProcError::SpawnFailed(wait_failed(argv, &error)))?;
        let WaitOutcome::Exited(status) = status else {
            return Err(timed_out(argv, options));
        };
        let captured = Captured {
            exit_code: exit_code(status),
            stdout: String::from_utf8_lossy(&stdout.bytes).into_owned(),
            stderr: String::from_utf8_lossy(&stderr.bytes).into_owned(),
            stdout_truncated: stdout.truncated,
            stderr_truncated: stderr.truncated,
        };
        if !options.allow_truncated && (captured.stdout_truncated || captured.stderr_truncated) {
            return Err(ProcError::Other(
                Refusal::new(
                    "subprocess_output_limit",
                    Exit::SOFTWARE,
                    format!("{} exceeded the {}-byte capture limit", base_name(&argv[0]), self.capture_limit),
                )
                .with_details(json!({
                    "stdoutTruncated": captured.stdout_truncated,
                    "stderrTruncated": captured.stderr_truncated,
                })),
            ));
        }
        Ok(captured)
    }

    /// Runs a **host** command and fails with what it actually said.
    pub async fn checked(&self, ctx: &Ctx, argv: &[String], options: &SpawnOptions) -> Result<Captured, ProcError> {
        let captured = self.capture(ctx, argv, options).await?;
        if captured.exit_code == 0 {
            return Ok(captured);
        }
        let mut said = output_tail(&captured.stderr);
        if said.is_empty() {
            // Some of these tools report on the wrong stream.
            said = output_tail(&captured.stdout);
        }
        let mut message = format!("{} {} exited with {}", base_name(&argv[0]), argv[1..].join(" "), captured.exit_code);
        if said.is_empty() {
            message.push_str("; it printed nothing");
        } else {
            message.push_str(": ");
            message.push_str(&said);
        }
        let refusal =
            Refusal::new("subprocess_failed", Exit::from_status(captured.exit_code, Exit::FAILURE), message).with_details(json!({
                "exitCode": captured.exit_code,
                "command": argv,
                "output": said,
            }));
        Err(ProcError::Exited {
            exit_code: captured.exit_code,
            refusal,
        })
    }

    /// Runs a command whose output must not travel in the refusal.
    ///
    /// Every **guest** command goes through this rather than [`Runner::checked`], because a guest process can echo
    /// the UI-test bridge token and the refusal ends up in an envelope an agent reads and may log. The caller still
    /// has the output on success; what it may not do is put it in the error.
    pub async fn checked_quietly(&self, ctx: &Ctx, argv: &[String], options: &SpawnOptions) -> Result<Captured, ProcError> {
        let captured = self.capture(ctx, argv, options).await?;
        if captured.exit_code == 0 {
            return Ok(captured);
        }
        Err(withheld_failure(argv, captured.exit_code))
    }

    /// Runs a command with its stdout going straight to a new file, and answers what it said on stderr.
    ///
    /// The destination is created exclusively at 0600 and removed again on any failure (unless
    /// [`SpawnOptions::keep_destination_on_failure`]), so a caller cannot mistake a partial capture for a complete
    /// one, and two concurrent callers cannot both think they own it. For output too large to hold - a lane's Bazel
    /// build log, a `tart` console log, a pulled file. The answer is empty when
    /// [`SpawnOptions::merge_stderr_into_file`] puts stderr into the file. The caller of a guest command keeps it out
    /// of every refusal, as [`Runner::checked_quietly`] does.
    pub async fn checked_to_file(
        &self,
        ctx: &Ctx,
        argv: &[String],
        destination: &Path,
        options: &SpawnOptions,
    ) -> Result<String, ProcError> {
        check_argv(argv).map_err(ProcError::Other)?;
        let output = open_private_file(destination, PrivateOpen::CreateNew)
            .or_refuse("capture_destination_exists", Exit::CANT_CREATE, || {
                format!("cannot open {} for this command's output", destination.display())
            })
            .map_err(ProcError::DestinationUnusable)?;
        let remove = || {
            let _ = std::fs::remove_file(destination);
        };

        let mut command = self.command(argv, options.dir.as_deref(), options.env.as_deref());
        command.stdin(Stdio::null());
        // The child's stderr either joins its stdout in the destination, or is captured so a refusal can quote it.
        // Never both, and never neither: a caller reading the destination must know whether the diagnostics are in
        // it, and a caller reading the refusal must know whether they are in that.
        let stderr_to_file = if options.merge_stderr_into_file {
            match output.try_clone() {
                Ok(clone) => Some(clone),
                Err(error) => {
                    remove();
                    return Err(ProcError::DestinationUnusable(Refusal::new(
                        "capture_destination_exists",
                        Exit::CANT_CREATE,
                        format!("cannot share {}: {error}", destination.display()),
                    )));
                }
            }
        } else {
            None
        };
        command.stdout(Stdio::from(output));
        match stderr_to_file {
            Some(file) => command.stderr(Stdio::from(file)),
            None => command.stderr(Stdio::piped()),
        };

        let started = Instant::now();
        let mut spawned = match self.spawn(command, argv, Interruptible::Yes) {
            Ok(spawned) => spawned,
            Err(refusal) => {
                remove();
                return Err(ProcError::SpawnFailed(refusal));
            }
        };
        let stderr = spawned.child.stderr.take().map(|pipe| drain(pipe, self.capture_limit));
        let (status, stderr) = spawned.wait(ctx, Some(options.timeout), join_drain(stderr)).await;
        ctx.phase().record_subprocess(argv, started.elapsed());

        let discard_unless_kept = || {
            if !options.keep_destination_on_failure {
                remove();
            }
        };
        let status = match status {
            Ok(WaitOutcome::Exited(status)) => status,
            Ok(WaitOutcome::TimedOut) => {
                discard_unless_kept();
                return Err(timed_out(argv, options));
            }
            Err(error) => {
                discard_unless_kept();
                return Err(ProcError::SpawnFailed(wait_failed(argv, &error)));
            }
        };
        if stderr.truncated {
            discard_unless_kept();
            return Err(ProcError::Other(Refusal::new(
                "subprocess_output_limit",
                Exit::SOFTWARE,
                format!(
                    "{} exceeded the {}-byte stderr capture limit",
                    base_name(&argv[0]),
                    self.capture_limit
                ),
            )));
        }
        if !status.success() {
            discard_unless_kept();
            let code = status.code().filter(|code| *code > 0).unwrap_or(1);
            return Err(withheld_failure(argv, code));
        }
        Ok(String::from_utf8_lossy(&stderr.bytes).into_owned())
    }

    /// Runs a command with the controller's own descriptors, and answers its exit code. For the one case where
    /// output is the point rather than a capture: an interactive guest shell, a VNC helper. It has no timeout: the
    /// operator ends the session, and an interrupt reaches its group.
    pub async fn inherited(&self, ctx: &Ctx, argv: &[String], dir: Option<&Path>) -> Result<i32, ProcError> {
        check_argv(argv).map_err(ProcError::Other)?;
        let mut command = self.command(argv, dir, None);
        command.stdin(Stdio::inherit()).stdout(Stdio::inherit()).stderr(Stdio::inherit());
        let mut spawned = self.spawn(command, argv, Interruptible::Yes).map_err(ProcError::SpawnFailed)?;
        match spawned.wait(ctx, None, std::future::ready(())).await.0 {
            Ok(WaitOutcome::Exited(status)) => Ok(exit_code(status)),
            Ok(WaitOutcome::TimedOut) => unreachable!("a wait without a deadline does not time out"),
            Err(error) => Err(ProcError::SpawnFailed(wait_failed(argv, &error))),
        }
    }

    /// The child every spawn shape here shares: the runner's scrubbed environment unless the options name their
    /// own, and its own process group.
    fn command(&self, argv: &[String], dir: Option<&Path>, env: Option<&[(String, String)]>) -> Command {
        let mut command = Command::new(&argv[0]);
        command.args(&argv[1..]).env_clear();
        ProcessGroup::prepare(&mut command);
        let environment = env.unwrap_or(&self.environment);
        command.envs(environment.iter().map(|(name, value)| (name, value)));
        if let Some(dir) = dir {
            command.current_dir(dir);
        }
        // The group guard signals the group first; this reaps the leader of a dropped spawn.
        command.kill_on_drop(true);
        command
    }

    fn spawn(&self, mut command: Command, argv: &[String], interruptible: Interruptible) -> Result<Spawned, Refusal> {
        let child = command
            .spawn()
            .or_refuse("spawn_failed", Exit::UNAVAILABLE, || format!("could not start {}", argv[0]))?;
        // A child that the group cannot take is dropped here, and the drop kills it.
        let group = ProcessGroup::attach(&child).or_refuse("spawn_failed", Exit::UNAVAILABLE, || {
            format!("could not take {} into a process group", argv[0])
        })?;
        let registration = group
            .as_ref()
            .filter(|_| interruptible == Interruptible::Yes)
            .map(|group| self.interrupts.register_group(group));
        let guard = GroupGuard {
            group,
            armed: true,
            registration,
        };
        Ok(Spawned { guard, child })
    }
}

impl fmt::Debug for Runner {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("Runner")
            .field("variables", &self.environment.len())
            .field("capture_limit", &self.capture_limit)
            .finish_non_exhaustive()
    }
}

/// Whether a spawn's group is registered with the interrupt service. [`Runner::probe_process`] says no, and so does
/// a teardown through [`SpawnOptions::surviving_interrupt`].
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Interruptible {
    Yes,
    No,
}

/// One running child and the guard over its group. The guard is declared first so it drops first: the group is
/// signalled while the unreaped leader still holds its pid, so the group id cannot have been reused.
struct Spawned {
    guard: GroupGuard,
    child: Child,
}

enum WaitOutcome {
    Exited(ExitStatus),
    TimedOut,
}

impl Spawned {
    /// Waits for the child *and* for `output`, the drains of its pipes, killing its group on the timeout and asking
    /// it to stop when the operation is cancelled. A cancelled child is still waited for, so what it said on its way
    /// out is answered like any other exit; the caller reads the cancellation off its context.
    ///
    /// The timeout, the cancellation and the drop guard all stay armed until both are done, not just until the
    /// leader is reaped: `sh -c 'sleep 600 & echo hi'` exits at once while the `sleep` still holds its stdout, and
    /// a wait that stopped watching there would hang for the whole `sleep` and then answer a clean exit. Signalling
    /// the group after the leader is reaped stays safe while a member holds the pipe, since a group id cannot be
    /// reused while the group has a member.
    async fn wait<T>(
        &mut self,
        ctx: &Ctx,
        timeout: Option<Duration>,
        output: impl Future<Output = T>,
    ) -> (std::io::Result<WaitOutcome>, T) {
        let deadline = timeout.map(|timeout| tokio::time::Instant::now() + timeout);
        let mut output = std::pin::pin!(output);
        let mut status = None;
        let mut said = None;
        let mut timed_out = false;
        let mut asked_to_stop = false;
        let (status, said) = loop {
            match (status.take(), said.take()) {
                (Some(exited), Some(value)) => break (exited, value),
                (exited, value) => (status, said) = (exited, value),
            }
            tokio::select! {
                exited = self.child.wait(), if status.is_none() => status = Some(exited),
                value = &mut output, if said.is_none() => said = Some(value),
                () = sleep_until(deadline), if !timed_out => {
                    self.guard.kill();
                    timed_out = true;
                }
                () = ctx.cancelled(), if !asked_to_stop && !timed_out => {
                    // The cooperative abort: `shard` once synthesised entries on SIGINT because an iteration had
                    // no way to be told to stop.
                    self.guard.stop();
                    asked_to_stop = true;
                }
            }
        };
        // Nothing of the group holds the pipes any more: from here its id may be reused, so the guard must never
        // signal it.
        self.guard.armed = false;
        let outcome = if timed_out {
            status.map(|_| WaitOutcome::TimedOut)
        } else {
            status.map(WaitOutcome::Exited)
        };
        (outcome, said)
    }
}

async fn sleep_until(deadline: Option<tokio::time::Instant>) {
    match deadline {
        Some(deadline) => tokio::time::sleep_until(deadline).await,
        None => std::future::pending().await,
    }
}

/// Ends a child's group when a spawn is abandoned before its child was reaped and its pipes drained: a dropped
/// future is a cancelled operation, and the group must not outlive it. `None` is a child that ended before its group
/// could be taken, so there is nothing to reach.
struct GroupGuard {
    group: Option<ProcessGroup>,
    armed: bool,
    registration: Option<GroupRegistration>,
}

impl GroupGuard {
    fn stop(&self) {
        if let Some(group) = &self.group {
            group.stop();
        }
    }

    fn kill(&self) {
        if let Some(group) = &self.group {
            group.kill();
        }
    }
}

impl Drop for GroupGuard {
    fn drop(&mut self) {
        if self.armed
            && let Some(group) = &self.group
        {
            group.abandon();
        }
    }
}

/// What one stream said, bounded.
#[derive(Default)]
struct Drained {
    bytes: Vec<u8>,
    truncated: bool,
}

/// Keeps the first `limit` bytes of a stream and *keeps reading* past them.
///
/// Keeping the reader draining is the point, not tidiness: a child whose stdout stopped being consumed blocks on
/// the pipe, and the controller would then report a timeout for a command that had already finished its work.
async fn drain(mut pipe: impl AsyncRead + Unpin, limit: usize) -> Drained {
    let mut drained = Drained::default();
    let mut chunk = vec![0; 64 * 1024];
    loop {
        match pipe.read(&mut chunk).await {
            Ok(0) | Err(_) => return drained,
            Ok(read) => {
                let room = limit.saturating_sub(drained.bytes.len());
                if read > room {
                    drained.truncated = true;
                }
                drained.bytes.extend_from_slice(&chunk[..read.min(room)]);
            }
        }
    }
}

async fn join_drain(drain: Option<impl Future<Output = Drained>>) -> Drained {
    match drain {
        Some(drain) => drain.await,
        None => Drained::default(),
    }
}

fn check_argv(argv: &[String]) -> Result<(), Refusal> {
    match argv.first() {
        None => Err(Refusal::internal("empty subprocess argv")),
        // An empty executable is a host-side Tart command that ran before the Tart gate resolved the binary; a guest
        // line resolves its head itself. The argv is not printed: a guest argv can carry the UI-test bridge token.
        Some(program) if program.is_empty() => Err(Refusal::new(
            "hypervisor_unresolved",
            Exit::SOFTWARE,
            format!(
                "`{}` ran before the hypervisor gate resolved its executable",
                argv.get(1).map_or("host", String::as_str)
            ),
        )),
        Some(_) => Ok(()),
    }
}

fn withheld_failure(argv: &[String], exit_code: i32) -> ProcError {
    let refusal = Refusal::new(
        "subprocess_failed",
        Exit::from_status(exit_code, Exit::FAILURE),
        format!("{} exited with {exit_code}; subprocess output was withheld", base_name(&argv[0])),
    )
    .with_details(json!({ "exitCode": exit_code }));
    ProcError::Exited { exit_code, refusal }
}

fn timed_out(argv: &[String], options: &SpawnOptions) -> ProcError {
    let milliseconds = millis(options.timeout);
    let refusal = Refusal::new(
        options.timeout_code.unwrap_or("subprocess_timeout"),
        Exit::TEMP_FAIL,
        format!("{} exceeded its {milliseconds} ms timeout", base_name(&argv[0])),
    )
    .with_details(json!({ "timeoutMs": milliseconds }));
    ProcError::TimedOut {
        timeout: options.timeout,
        refusal,
    }
}

fn wait_failed(argv: &[String], error: &std::io::Error) -> Refusal {
    Refusal::new(
        "spawn_failed",
        Exit::UNAVAILABLE,
        format!("could not wait for {}: {error}", argv[0]),
    )
}

fn base_name(program: &str) -> &str {
    HostOs::CURRENT.program_name(program)
}

/// The newest [`FAILURE_OUTPUT_TAIL_BYTES`] of an output, trimmed, cut on a character boundary.
fn output_tail(text: &str) -> String {
    let trimmed = text.trim();
    if trimmed.len() <= FAILURE_OUTPUT_TAIL_BYTES {
        return trimmed.to_owned();
    }
    let mut start = trimmed.len() - FAILURE_OUTPUT_TAIL_BYTES;
    while !trimmed.is_char_boundary(start) {
        start += 1;
    }
    format!("…{}", &trimmed[start..])
}

// --- the guest channel ---------------------------------------------------------------------------------------

/// How a command reaches inside a worker VM.
///
/// One trait with one implementation that reaches either backend, plus the test ones. What it deliberately does
/// *not* expose is how the command is spelled: a caller hands over an argv and gets a result, and whether that
/// became `tart exec worker argv…` or `prlctl exec worker 'one shell string'` is the implementation's business.
#[async_trait]
pub trait Channel: Send + Sync {
    /// Runs one command inside the worker and answers what it said.
    async fn exec(&self, ctx: &Ctx, argv: &[String], options: &SpawnOptions) -> Result<Captured, Refusal>;

    /// A byte stream to `127.0.0.1:port` inside the worker, through the guest agent's `relay` verb.
    ///
    /// The stream goes through the exec channel and not through the network address of the guest, because macOS
    /// Local Network privacy refuses that address to a host app that is not Apple's. A port where nothing listens
    /// is not a refusal here: the first read of the stream fails with [`std::io::ErrorKind::ConnectionRefused`],
    /// as a socket connect would.
    async fn connect(&self, ctx: &Ctx, port: u16) -> Result<GuestStream, Refusal>;

    /// Which VM this channel reaches, for messages and for path building.
    fn worker(&self) -> &str;
}
